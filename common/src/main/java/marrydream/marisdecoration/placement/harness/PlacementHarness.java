package marrydream.marisdecoration.placement.harness;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 代码级验证器：不依赖任何测试框架，直接在<b>真实的开发服务端</b>里跑一遍。
 *
 * <p>为什么要有它：这一阶段只做底层（配置 / adapter / 放置流水线），没有 GUI、没有工具物品，
 * 手工在游戏里点不出来。唯一的验证方式就是「自己造一份配置 → 调放置服务 → 检查世界」。
 * 这个类把这件事做成一串可断言的检查，由 {@code /marisplacer self-test} 触发。
 *
 * <h2>它能验证什么、不能验证什么</h2>
 * 全部检查都在真实世界里执行真实的放置与真实的方块实体，所以「材质到底写进去了没有」
 * 「扣了几个物品」「放进水里含不含水」这些都是真结论，不是模拟。但它<b>不</b>验证渲染——
 * 伪装是否显示正确仍然要靠进游戏看，那是客户端的事。
 *
 * <h2>隔离</h2>
 * 它在世界的一个专用角落里工作（默认 y=200 的空中平台），每次跑之前先把那一带清空，
 * 跑完不清（留着可以进游戏复核）。所以只应该在开发存档里用。
 */
public final class PlacementHarness {

    /** 测试平台所在的 y 层：足够高，不会碰到正常地形。 */
    public static final int PLATFORM_Y = 200;

    private final ServerLevel world;
    private final BlockPos origin;
    private final Assertions assertions = new Assertions();
    private int nextOffset;

    public PlacementHarness(ServerLevel world, BlockPos origin) {
        this.world = world;
        this.origin = origin;
    }

    // ---------------------------------------------------------------- 场景

    /** 平台在水平方向向外扩展多少格（清方块与清实体用同一个范围）。 */
    private static final int PLATFORM_MARGIN = 2;
    /**
     * 平台在 X 方向要铺多远。
     *
     * <p>自检会一个接一个地新增测试位置，累计下来的下标并不小，所以这里给得宽裕一些：
     * 范围不够的后果是「最外侧那几个位置脚下没有地基」，而 {@code canPlaceAt} 与碰撞检查
     * 都会因此失败——表现为一堆莫名其妙的放置失败，而不是一条清楚的「平台太小」。
     */
    private static final int PLATFORM_LENGTH = 64;

    /** 清空平台并铺一层地基，让「可替换 / 不可替换」两类位置都有。 */
    public PlacementHarness reset() {
        // 先清实体。
        //
        // 这一步是必须的，而且是踩过坑才加上的：测试里往背包塞物品时，装不下的部分会掉成
        // 物品实体飘在平台上方。而放置服务现在会正确地把「目标格里有实体」判为放不下
        // （与原版一致），于是后续每一条放置断言都会莫名其妙地失败。
        // 也就是说，这条清理不是为了让测试「好过」，而是为了让测试环境不携带上一次的残留。
        clearEntities();

        for (int dx = -PLATFORM_MARGIN; dx <= PLATFORM_LENGTH; dx++) {
            for (int dz = -PLATFORM_MARGIN; dz <= PLATFORM_LENGTH; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    world.setBlockAndUpdate(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int dx = -PLATFORM_MARGIN; dx <= PLATFORM_LENGTH; dx++) {
            for (int dz = -PLATFORM_MARGIN; dz <= PLATFORM_LENGTH; dz++) {
                // 地基用石头：既能验证「替换空气」，也能验证「替换石头时因为不可替换而被挡」
                world.setBlockAndUpdate(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
            }
        }
        nextOffset = 0;
        return this;
    }

    /**
     * 清掉平台上方的全部实体（掉落物、经验球、假玩家放出来的东西）。
     *
     * <p>范围比平台本身高出一截，因为被扔出来的物品会先上升一段再落下来。
     */
    public void clearEntities() {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                origin.getX() - PLATFORM_MARGIN, origin.getY() - 2, origin.getZ() - PLATFORM_MARGIN,
                origin.getX() + PLATFORM_LENGTH + 1, origin.getY() + 24, origin.getZ() + PLATFORM_LENGTH + 1);
        for (net.minecraft.world.entity.Entity entity : world.getEntities(null, area)) {
            entity.discard();
        }
    }

    /**
     * 下一个可用的测试位置：沿 X 排开、互不干扰，便于逐个复核。
     *
     * <p>同时保证「这一格是空气、脚下是石头、格子里没有实体」——三条都是放置的前提。
     * 把这些前提收在这里，调用方不需要在每个测试点重复交代一遍，也不会出现
     * 「上一个测试掉了个物品、下一个测试就莫名失败」这种连锁。
     */
    public BlockPos next() {
        BlockPos pos = origin.offset(nextOffset++, 0, 0);
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        for (net.minecraft.world.entity.Entity entity : world.getEntities(null,
                new net.minecraft.world.phys.AABB(pos))) {
            entity.discard();
        }
        return pos;
    }

    /**
     * 像 {@link #next()} 一样准备一个位置，但<b>脚下不放石头</b>。
     *
     * <p>给「落点解析」这类测试用：它们要自己摆被点的方块与目标格，不能被帮忙铺的地基干扰。
     */
    public BlockPos nextBare() {
        BlockPos pos = origin.offset(nextOffset++, 0, 0);
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        for (net.minecraft.world.entity.Entity entity : world.getEntities(null,
                new net.minecraft.world.phys.AABB(pos))) {
            entity.discard();
        }
        return pos;
    }

    /**
     * 清掉某一格（以及紧邻一格）里的实体。
     *
     * <p>给「先摆好方块再放置」的测试用：薄板与护栏都有碰撞箱，测试里紧挨着摆的石头、
     * 或者上一次测试掉出来的物品，都会让放置被正确判为「放不下」。
     */
    public void clearEntitiesAt(BlockPos pos) {
        for (net.minecraft.world.entity.Entity entity : world.getEntities(null,
                new net.minecraft.world.phys.AABB(pos).inflate(1.0))) {
            entity.discard();
        }
    }

    public ServerLevel world() {
        return world;
    }

    /** 检查结果。 */
    public Assertions assertions() {
        return assertions;
    }

    // ---------------------------------------------------------------- 便捷读取

    public BlockState stateAt(BlockPos pos) {
        return world.getBlockState(pos);
    }

    public BlockEntity blockEntityAt(BlockPos pos) {
        return world.getBlockEntity(pos);
    }

    // ---------------------------------------------------------------- 玩家

    /**
     * 一个「没有网络连接」的假玩家。
     *
     * <p>用真实的 {@link ServerPlayer}（而不是自己实现的 {@code PlayerEntity} 子类），
     * 这样 {@code getInventory()}、{@code isCreative()}、以及库存同步走的是真正的实现，
     * 测出来的扣物品结果才有意义。
     *
     * <p>刻意<b>不</b>调 {@code refreshPositionAfterTeleport}：那条路要求
     * {@code networkHandler} 非空（会把位置同步给客户端），假玩家没有连接。用
     * {@link net.minecraft.world.entity.Entity#setPos} 就够，因为我们只关心坐标、不关心同步。
     */
    public ServerPlayer fakePlayer(double x, double y, double z) {
        String name = "maris-harness-" + (System.nanoTime() % 100000L);
        ServerPlayer player = new ServerPlayer(world.getServer(), world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name));
        player.setPos(x, y, z);
        return player;
    }

    /**
     * 把假玩家设成创造 / 生存。
     *
     * <p><b>不能走 {@code ServerPlayerEntity#changeGameMode}</b>：它会往客户端发包，而假玩家没有
     * 网络连接，直接 NPE。也不能只改 {@code abilities.creativeMode}——
     * {@code ServerPlayerEntity.isCreative()} 读的是
     * {@code interactionManager.getGameMode() == GameMode.CREATIVE}（字节码确认过）。
     *
     * <p>所以两处都要写：gameMode（给 {@code isCreative()} 看）+ abilities（给飞行/瞬间破坏等看）。
     * 这与 {@code changeGameMode} 内部做的事一致，只是省掉了发包那一步。
     */
    public static void creative(ServerPlayer player, boolean value) {
        if (value) {
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        } else {
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        }
        player.getAbilities().instabuild = value;
        player.getAbilities().flying = value;
    }

    /** 清空背包并塞进这些东西（每种一个，除非指定个数）。 */
    public static void give(ServerPlayer player, Item item, int count) {
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(item, count));
    }

    /** 清空背包。 */
    public static void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
    }

    /** 玩家背包里某种物品的总数。 */
    public static int countOf(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- 断言

    /**
     * 极简断言器：只累积「第 n 条检查是否通过」以及失败原因，最后一次性输出。
     *
     * <p>刻意不抛异常：一次跑完能看到<b>所有</b>问题，而不是被第一个失败打断。
     */
    public static final class Assertions {

        private int total;
        private final List<String> failures = new ArrayList<>();

        public void check(String what, boolean ok, String detail) {
            total++;
            if (!ok) {
                failures.add(what + " —— " + detail);
            }
        }

        /** 两个值相等（{@code null} 也按值比较）。 */
        public void equal(String what, Object expected, Object actual) {
            check(what, java.util.Objects.equals(expected, actual),
                    "期望 " + expected + "，实际 " + actual);
        }

        /** 与 {@link #equal} 同义，读起来更顺的别名。 */
        public void isEqual(String what, Object expected, Object actual) {
            equal(what, expected, actual);
        }

        public void isTrue(String what, boolean actual) {
            check(what, actual, "期望 true，实际 false");
        }

        public void isFalse(String what, boolean actual) {
            check(what, !actual, "期望 false，实际 true");
        }

        /** 方块状态是空气。 */
        public void isAir(String what, net.minecraft.world.level.block.state.BlockState state) {
            check(what, state.isAir(), "实际是 " + state);
        }

        public void notNull(String what, Object actual) {
            check(what, actual != null, "期望非 null，实际 null");
        }

        public void contains(String what, String needle, String haystack) {
            check(what, haystack != null && haystack.contains(needle),
                    (haystack == null ? "null" : haystack) + " 里找不到 " + needle);
        }

        public int total() {
            return total;
        }

        public List<String> failures() {
            return List.copyOf(failures);
        }

        public boolean passed() {
            return failures.isEmpty();
        }
    }
}
