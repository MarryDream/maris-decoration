package marrydream.marisdecoration.placement;

import marrydream.marisdecoration.placement.adapter.AdapterSlot;
import marrydream.marisdecoration.placement.adapter.CopycatPlacementAdapter;
import marrydream.marisdecoration.placement.adapter.PaidMaterials;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import marrydream.marisdecoration.placement.adapter.PlacementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 「伪装放置器」的服务端入口。
 *
 * <p>这一阶段（底层）唯一的执行入口。GUI、网络包、工具 Item 都还没有，但它们的调用点已经定下来了：
 * 客户端凑出一份 {@link PlacementConfig}，服务端拿着它调 {@link #place}。
 *
 * <h2>流程（顺序是有意的，每一步都为了「不产生半成品」）</h2>
 * <ol>
 *   <li><b>取出要放的方块</b> —— 预设里没有方块就直接返回，什么都不做
 *       （{@link PlacementFailure#NO_BLOCK}）；</li>
 *   <li><b>找 adapter</b> —— 不是伪装方块直接返回（{@link PlacementFailure#NOT_COPYCAT}）；</li>
 *   <li><b>算出最终方块状态</b> —— 先经 adapter 的 {@code stateFrom} 写入特殊结构属性，
 *       <b>再</b>按目标位置的水体写入 {@code WATERLOGGED}（见下面的「含水」小节）；</li>
 *   <li><b>能不能放</b> —— 目标位置必须可替换，且该状态在这个位置的
 *       {@code canPlaceAt} 必须成立，否则不放置、不扣东西；</li>
 *   <li><b>结构方块够不够</b> —— 生存模式必须有一个对应 BlockItem；
 *       <b>不够就到此为止，绝不扣任何材质</b>；</li>
 *   <li><b>预演一遍材质</b> —— 把 adapter 会在哪些槽上写什么材质先算出来
 *       （{@code preview}），据此一次性问清所有材质够不够；</li>
 *   <li><b>真正落方块</b> —— {@code setBlockState} + 显式调 {@code onPlaced}
 *       （走的是方块自己那条正在用的逻辑，不是复刻）；</li>
 *   <li><b>铺材质</b> —— adapter 写材质，缺料的槽留空，不阻止放置；</li>
 *   <li><b>扣账</b> —— 结构方块一个；材质按 adapter 报上来的「付过账的材质」每种一个
 *       （创造模式一律不扣）。</li>
 * </ol>
 *
 * <h2>含水</h2>
 * {@code WATERLOGGED} <b>不是</b>玩家可配置属性，也因此在 {@link PlacementConfig} 里根本不存在
 * 这一项；但「放进水里要含水」这条原版逻辑照常生效：第 3 步按目标位置当前的流体是不是水
 * （与护栏、分层薄板原本的 {@code getPlacementState} 用的是同一个判据）写入该属性。
 * 方块状态本身没有这个属性时什么都不写。
 *
 * <h2>为什么不走 {@code BlockItem#place}</h2>
 * 原版放置流程会「按点击面定向」并调用方块自己的 {@code getPlacementState}——那正是放置器要绕过的东西。
 * 另外本 mod 的两个方块都有「往同一格追加」的特殊放置逻辑（护栏四面叠加、薄板 12 槽），
 * 而放置器的语义是「一次放一块、结构由预设完全决定」，两者不是一回事。
 * 所以这里自己做 {@code setBlockState}，然后显式调用方块的 {@code onPlaced}：
 * 声效、进度、以及「副手自动伪装」这些方块自己的行为因此一条不少，而方向与形态完全由预设说了算。
 */
public final class PlacementService {

    private static final Logger LOGGER = LoggerFactory.getLogger("maris-decoration/copycat-placer");

    private PlacementService() {
    }

    /**
     * 由「被点方块 + 点击面」算出真正的落点格。
     *
     * <p>这就是标准 BlockItem 的目标格语义，与 {@code ItemPlacementContext} 的内部判定一致：
     * <ol>
     *   <li>被点的那一格如果<b>可替换</b>（空气、水、草……），就把东西放进那一格；</li>
     *   <li>否则放进它沿点击面方向相邻的那一格。</li>
     * </ol>
     *
     * <p><b>点击面只用来决定「相邻格在哪」</b>。它绝不能反过来决定方块的朝向、分层薄板的
     * Face/Layer、护栏的方向或任何别的结构属性——那些完全由预设说了算（见
     * {@link #place(Level, BlockPos, PlacementConfig, Player)} 第 3 步）。
     *
     * <p>把这段逻辑放在服务里而不是 {@code CopycatPlacerItem} 里，是为了让「落点怎么算」
     * 只有一份实现：工具调它、自检也调它，不会出现两边各写一套而慢慢分叉。
     *
     * @param world      目标世界
     * @param clickedPos 被点中的方块坐标（原版 {@code ItemUsageContext#getBlockPos()} 就是这个）
     * @param side       点击面
     * @return 真正的落点格
     */
    public static BlockPos resolveTargetPos(Level world, BlockPos clickedPos, Direction side) {
        BlockState clicked = world.getBlockState(clickedPos);
        if (clicked.canBeReplaced()) {
            return clickedPos;
        }
        return clickedPos.relative(side);
    }

    /** 无玩家的放置（创造式语义：不要求库存、也不消耗）。 */
    public static PlacementResult place(Level world, BlockPos pos, PlacementConfig config) {
        return place(world, pos, config, null);
    }

    /**
     * 执行一次放置。
     *
     * @param world  目标世界
     * @param pos    目标位置
     * @param config 预设
     * @param player 发起放置的玩家；{@code null} 表示「不用付账」（测试 / 自动补全）
     * @return 结果；失败时也在结果里说明原因，不抛异常
     */
    public static PlacementResult place(Level world, BlockPos pos, PlacementConfig config,
                                        @Nullable Player player) {
        if (world.isClientSide) {
            // 放置只由服务端执行。客户端调用是调用方的 bug，安静地不做任何事。
            return PlacementResult.failed(PlacementFailure.BLOCKED);
        }

        // ---- 1. 预设里有没有方块
        BlockState configured = config.state();
        Block block = config.block();
        if (configured == null || block == null || block == Blocks.AIR) {
            return PlacementResult.failed(PlacementFailure.NO_BLOCK);
        }

        // ---- 2. 找 adapter
        Optional<CopycatPlacementAdapter> resolved = PlacementAdapters.resolve(block);
        if (resolved.isEmpty()) {
            // adapter 表没装上（忘了 init()），或者某个伪装方块真的一个 adapter 都不匹配。
            // 后者在 GenericCopycatAdapter 在场时不该发生，所以这里区分一下原因，便于定位。
            return PlacementResult.failed(PlacementAdapters.all().isEmpty()
                    ? PlacementFailure.NO_ADAPTER
                    : PlacementFailure.NOT_COPYCAT);
        }
        CopycatPlacementAdapter adapter = resolved.get();

        // ---- 3. 最终状态 = 预设状态 → adapter 写入特殊结构属性 → 按目标位置的水体写 WATERLOGGED
        BlockState shaped = adapter.stateForPlacement(configured, config, world, pos);
        if (shaped == null || shaped.getBlock() != block) {
            // adapter 把方块换掉了 / 做不出合法状态：预设与方块对不上
            return PlacementResult.failed(PlacementFailure.INVALID_STATE);
        }
        // 含水必须在 adapter 之后：stateForPlacement 是按 shaped 派生的，
        // 直接拿 configured 去算会把 adapter 刚写进去的结构属性丢掉。
        BlockState target = stateWithWater(shaped, isWaterAt(world, pos));
        if (target == null || target.getBlock() != shaped.getBlock()) {
            return PlacementResult.failed(PlacementFailure.INVALID_STATE);
        }

        // ---- 4. 这份配置能不能形成可见结构
        // 服务端的最后一道闸：即使 GUI 与默认配置都出了 bug，也绝不允许把「零部件的方块」
        // 写进世界——那种方块占着格子却什么都看不见，再想放别的东西还会提示「这里放不下」。
        // 放在 setBlockState 之前，所以失败时不需要回滚，也不会扣任何东西。
        String structureProblem = adapter.validateStructure(target, config);
        if (structureProblem != null) {
            LOGGER.warn("Copycat placer at {}: invalid structure ({}) — block={} config={}",
                    pos.toShortString(), structureProblem, config.blockIdSafe(), config.structures());
            return PlacementResult.failed(PlacementFailure.INVALID_STRUCTURE);
        }

        // ---- 5. 位置放不放得下
        // 注意：这里校的全都是「最终落点 pos」——调用方传进来的已经是 resolveTargetPos 的结果。
        // 点击面在这一步之后不再参与任何判断，更不会影响 target 的形态（那在第 3 步就定完了）。
        if (!canPlace(world, pos, target, player)) {
            LOGGER.warn("Copycat placer at {}: blocked — existing={}, canPlaceAt={}",
                    pos.toShortString(), world.getBlockState(pos), target.canSurvive(world, pos));
            return PlacementResult.failed(PlacementFailure.BLOCKED);
        }

        InventoryPayment payment = new InventoryPayment(player);

        // ---- 6. 结构方块够不够（不够就到此为止，材质一分不扣）
        Item structureItem = config.structureItem();
        if (!payment.free()) {
            if (structureItem == null) {
                // 预设里的方块没有物品形态（理论上不会发生，因为能放进世界的方块都有物品）
                return PlacementResult.failed(PlacementFailure.NO_STRUCTURE_BLOCK_ITEM);
            }
            if (!payment.has(structureItem)) {
                return PlacementResult.failed(PlacementFailure.NO_STRUCTURE_ITEM);
            }
        }

        // ---- 7. 预演一遍 adapter 打算铺的材质。
        // 这一步不改任何东西、也不阻止放置，只是让日志与测试能回答「这次打算铺哪些材质、够不够」。
        List<BlockState> planned = plannedMaterials(adapter, target, config);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Copycat placer at {}: block={}, adapter={}, planned materials={}",
                    pos.toShortString(), config.blockIdSafe(), adapter.name(), planned.size());
        }

        // ---- 8. 真正落方块
        // 记下原来的状态，供第 9 步初始化失败时回滚（正常情况下用不到，但一旦用到就是
        // 「世界里多出一个半成品方块 + 玩家白扣东西」这种很难查的问题）
        BlockState previousState = world.getBlockState(pos);
        boolean placed = world.setBlock(pos, target, Block.UPDATE_ALL);
        if (!placed && world.getBlockState(pos) != target) {
            return PlacementResult.failed(PlacementFailure.BLOCKED);
        }
        // 走方块自己那条正在用的放置回调（声效、进度、副手自动伪装都在里面），
        // 而不是在放置器里复刻一遍。
        target.getBlock().setPlacedBy(world, pos, target, player, ItemStack.EMPTY);

        // ---- 9. 铺材质
        // 把 apply 包起来：adapter 初始化真的抛异常时，方块已经进了世界但材质没铺上，
        // 必须把世界恢复原状再放弃，让这次放置表现为「完全没发生」。
        // 注意「材质不足」不在这里——那是正常结果，adapter 会把槽跳过、放置照样成功。
        PaidMaterials paid = new PaidMaterials(payment.free());
        PlacementContext context = new PlacementContext(world, pos, target, config, paid,
                material -> payment.has(material.getBlock().asItem()), adapter);
        try {
            adapter.apply(context);
        } catch (RuntimeException exception) {
            LOGGER.error("Copycat placer at {}: adapter 初始化失败，已回滚这次放置",
                    pos.toShortString(), exception);
            world.setBlock(pos, previousState, Block.UPDATE_ALL);
            return PlacementResult.failed(PlacementFailure.INVALID_STRUCTURE);
        }

        // ---- 9. 扣账：结构方块一个，材质每种一个（创造模式一律不扣）。
        // 都在方块已经确实放好之后才扣，所以不存在「扣了东西但方块没放上」的中间态。
        List<Item> paidItems = new ArrayList<>();
        if (payment.free()) {
            return PlacementResult.placed(adapter.name(), target, context.appliedSlots(),
                    context.skippedSlots(), List.of(), false);
        }

        boolean structurePaid = false;
        if (structureItem != null) {
            structurePaid = !payment.extract(structureItem).isEmpty();
        }
        for (Item item : paid.requiredItems()) {
            if (payment.extract(item).isEmpty()) {
                // 正常情况下不会发生：adapter 申领之前已经用同一个判据确认过库存
                continue;
            }
            paidItems.add(item);
        }

        return PlacementResult.placed(adapter.name(), target, context.appliedSlots(),
                context.skippedSlots(), paidItems, structurePaid);
    }

    /**
     * 把 {@code WATERLOGGED} 按实际水体写进状态。
     *
     * <p>含水<b>不是</b>玩家可配置项（{@link PlacementConfig} 里根本没有这一项），但「放进水里要含水」
     * 这条原版逻辑照常生效——判据与护栏、分层薄板原本的 {@code getPlacementState} 完全一致：
     * 目标位置当前的流体是不是水。方块状态本身没有这个属性时什么都不写。
     *
     * <p>必须在 adapter 的 {@code stateFrom} <b>之后</b>调用：它是从传入状态派生的，
     * 顺序反了就会把 adapter 刚写进去的结构属性丢掉。
     */
    private static BlockState stateWithWater(BlockState state, boolean waterlogged) {
        if (!state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state;
        }
        return state.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
    }

    /**
     * 预演 adapter 打算铺哪些材质。
     *
     * <p>做法是「在真正的方块<b>之前</b>问一遍」：{@link CopycatPlacementAdapter#slots} 已经把
     * 「当前结构下这个槽存不存在」算清楚了，这里只取其中「存在 + 预设里配了材质」的那些。
     * 靠的是 adapter 的声明而不是它的执行——执行里还包含「这是不是已经伪装过了」这类
     * 只有真实方块实体才知道的信息，预演阶段没有这些信息，也不该有。
     *
     * <p>结果<b>可能偏多</b>（某个槽其实已经有材质、执行时会被跳过）。这只会让日志上的「打算铺」
     * 比实际多一点，不影响扣账：扣账用的是 adapter 报上来的实际付账集合（{@link PaidMaterials}）。
     */
    private static List<BlockState> plannedMaterials(CopycatPlacementAdapter adapter, BlockState shaped,
                                                     PlacementConfig config) {
        List<BlockState> materials = new ArrayList<>();
        for (var slot : adapter.slots(shaped, config)) {
            if (!slot.structure()) {
                continue;
            }
            BlockState material = config.slots().get(slot.key());
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    /**
     * 目标位置能不能放这个状态。判据全部落在<b>最终落点</b>上，逐条与原版
     * {@code ItemPlacementContext#canPlace} 对齐。
     *
     * @param player 发起放置的玩家；{@code null}（测试 / 自动补全）时跳过权限这一项
     */
    private static boolean canPlace(Level world, BlockPos pos, BlockState state, @Nullable Player player) {
        return diagnoseCanPlace(world, pos, state, player) == null;
    }

    /**
     * 逐条检查落点，返回<b>第一条拦住它的原因</b>；全部通过返回 {@code null}。
     *
     * <p>做成「返回原因」而不是直接返回 boolean，是因为 {@code canPlace} 失败时对外只有
     * 一个 BLOCKED，而「哪一层拦住的」决定了排查方向完全不同：世界边界 / 格子被占 /
     * {@code canPlaceAt} / 实体碰撞 / 权限。自检里就是用它在报告里直接写出原因的
     * （曾经有一次是平台上方飘着测试掉落物挡住了实体碰撞检查，只看 BLOCKED 完全无从下手）。
     *
     * <p>{@link #canPlace} 也走这一个实现，所以「检查了什么」只有一份，不会两边分叉。
     */
    public static @Nullable String diagnoseCanPlace(Level world, BlockPos pos, BlockState state,
                                                    @Nullable Player player) {
        if (!world.getWorldBorder().isWithinBounds(pos)) {
            return "world_border";
        }
        BlockState existing = world.getBlockState(pos);
        if (!existing.canBeReplaced() && !existing.isAir()) {
            return "not_replaceable:" + existing;
        }
        if (!state.canSurvive(world, pos)) {
            return "canPlaceAt_false";
        }
        // 实体碰撞：只有方块真的有碰撞箱时才查，而且与原版同一条规则——
        // 旁观者不算、玩家不算，其它有碰撞箱的实体才算。
        // 玩家不算是有意的：放置方块本来就应该能垫在自己脚下。
        if (!state.getCollisionShape(world, pos).isEmpty()) {
            for (net.minecraft.world.entity.Entity entity
                    : world.getEntities(null, new net.minecraft.world.phys.AABB(pos))) {
                if (entity.isSpectator() || entity instanceof Player) {
                    continue;
                }
                return "entity:" + entity.getType().toString() + "@" + entity.position();
            }
        }
        if (player != null && !player.mayInteract(world, pos)) {
            return "cannot_modify";
        }
        return null;
    }

    /** 目标位置现在是不是水。与护栏 / 薄板原本 {@code getPlacementState} 用的是同一个判据。 */
    private static boolean isWaterAt(Level world, BlockPos pos) {
        return world.getFluidState(pos).getType() == net.minecraft.world.level.material.Fluids.WATER;
    }

    /**
     * 便捷方法：把一个「结构掩码」预设直接放到世界上。
     *
     * <p>只是 {@link #place} 的一个薄封装，存在的意义是让手工测试与以后的调试命令少写两行。
     */
    public static PlacementResult placeFrom(ServerLevel world, BlockPos pos, PlacementConfig config,
                                            @Nullable Player player) {
        return place(world, pos, config, player);
    }

    /** 某个方块状态有没有 {@code WATERLOGGED} 属性——只用来给日志/测试做说明。 */
    public static boolean hasWaterlogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED);
    }

    /** 目标位置上现在有没有伪装方块（判断「这里放过了」用）。 */
    public static boolean isCopycat(Level world, BlockPos pos) {
        return PlacementAdapters.isPlaceable(world.getBlockState(pos).getBlock());
    }
}
