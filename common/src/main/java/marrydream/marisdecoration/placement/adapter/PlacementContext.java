package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.core.registries.BuiltInRegistries.BLOCK;

/**
 * 一次放置操作里 adapter 能看到的全部上下文，同时也是它回报结果的出口。
 *
 * <p>刻意做成「只读的世界信息 + 两个可写的小账本」而不是把玩家、库存一股脑塞进去：
 * adapter 的职责仅仅是把材质写进方块实体，<b>它不负责扣物品</b>。这样做有两个好处：
 * <ul>
 *   <li>付账规则可以按 adapter 各自的历史语义实现（护栏与薄板是「同格同材质只付一次」，
 *       Create 与 Copycats+ 是各自原本的语义），却全都在同一条执行路径上；</li>
 *   <li>adapter 全部逻辑都在服务端跑，不需要分客户端——放置服务只在服务端被调用。</li>
 * </ul>
 *
 * <p>两个账本：
 * <ul>
 *   <li>{@link #claim} / {@link #paid} —— 本次放置要替玩家扣掉哪些材质。
 *       adapter 每写下一个槽就 {@code claim} 一次；<b>拿不出来</b>时 {@code claim} 返回
 *       {@code false}，那个槽就留空（缺材质不阻止放置）；</li>
 *   <li>{@link #markApplied} / {@link #markSkipped} —— 铺上了哪些、跳过了哪些。
 *       跳过要说明理由，因为「跳过」本身不是错误（缺料、已有伪装、部件不存在都会跳过），
 *       而调用方需要据此给出可诊断的结果。</li>
 * </ul>
 */
public final class PlacementContext {

    private final Level world;
    private final BlockPos pos;
    private final BlockState state;
    private final PlacementConfig config;
    private final PaidMaterials paidMaterials;
    private final Affordability affordable;
    private final CopycatPlacementAdapter adapter;

    private final List<String> applied = new ArrayList<>();
    private final List<SkippedSlot> skipped = new ArrayList<>();

    public PlacementContext(Level world, BlockPos pos, BlockState state, PlacementConfig config,
                            PaidMaterials paidMaterials, @Nullable Affordability affordable,
                            CopycatPlacementAdapter adapter) {
        this.world = world;
        this.pos = pos;
        this.state = state;
        this.config = config;
        this.paidMaterials = paidMaterials;
        this.affordable = affordable;
        this.adapter = adapter;
    }

    /**
     * 这个材质能不能写进这个 slot——服务端侧的准入校验。
     *
     * <p>判据来自 adapter（Create / Copycats+ 复用它们各自的 {@code getAcceptedBlockState}，
     * 本 mod 两个方块复用现有的 {@code getAcceptedMaterial}）。客户端材质选择界面用的是同一个方法，
     * 所以「界面里能选到」与「服务端会接受」是一套规则。
     *
     * <p><b>客户端过滤只是 UX</b>，这里才是权威：一个改过包的客户端可以塞进任意材质，
     * 全靠这一步挡。这里传的是<b>真实世界</b>，所以形状类判据（完整立方体）是精确的。
     */
    public boolean acceptsMaterial(BlockState material, String slotKey) {
        return adapter.acceptsMaterial(material, slotKey, config, world, pos);
    }

    /**
     * 「玩家拿不拿得出这个材质」的唯一出口。
     *
     * <p>做成一个只回答这一个问题的窄接口（而不是把 {@code PlayerEntity} 交给 adapter），
     * 是为了让 adapter 无论如何都碰不到库存的写入口——扣物品只能发生在放置服务里。
     */
    @FunctionalInterface
    public interface Affordability {
        /** 玩家现在拿不拿得出这个材质（一个就够）。 */
        boolean canAfford(BlockState material);
    }

    // ---------------------------------------------------------------- 只读信息

    /** 放置所在的世界（一定是服务端世界）。 */
    public Level world() {
        return world;
    }

    /** 方块最终落点。 */
    public BlockPos pos() {
        return pos;
    }

    /** 已经写进世界的方块状态（<b>已经</b>含正确的 {@code WATERLOGGED}）。 */
    public BlockState state() {
        return state;
    }

    /** 本次使用的预设。 */
    public PlacementConfig config() {
        return config;
    }

    /** 拿预设给某个 slot 的材质；没预设返回 {@code null}。 */
    public @Nullable BlockState material(String key) {
        return config.slots().get(key);
    }

    /** 目标方块的注册名，只用于日志。 */
    public ResourceLocation blockId() {
        return BLOCK.getKey(state.getBlock());
    }

    // ---------------------------------------------------------------- 付账账本

    /**
     * 这个材质本次放置里能不能付得起。
     *
     * <p>只回答「玩家的库存里有没有」这一件事（创造模式与「没有玩家」的语境下恒为 {@code true}）。
     * adapter 之外的判断——「本次是不是已经付过一次了」——由 {@link #claim} 一并处理。
     */
    public boolean affordable(BlockState material) {
        return affordable == null || affordable.canAfford(material);
    }

    /**
     * 申领一个材质：能付（或创造模式）就返回 {@code true}，并把它记进本次要扣除的清单。
     *
     * <p>幂等：同一个材质第二次申领时不会重复记账，直接返回 {@code true}。
     * <b>必须</b>只在真正把材质写进方块实体之后才调用，这样「账本」与「写了什么」永远一致。
     *
     * @return 这个材质可不可以被写进方块实体（{@code false} 表示玩家拿不出来，这个槽应当留空）
     */
    public boolean claim(BlockState material) {
        return paidMaterials.claim(material, affordable(material));
    }

    /** 本次放置到目前为止付过账的材质。 */
    public PaidMaterials paid() {
        return paidMaterials;
    }

    // ---------------------------------------------------------------- 结果账本

    /** 记下「这个槽铺上了」。 */
    public void markApplied(String key) {
        applied.add(key);
    }

    /**
     * 记下「这个槽有预设但没铺上」。
     *
     * <p><b>只在槽确实配了材质时才调用</b>：没配的槽不算「跳过」，那只是玩家没打算贴它。
     * 这样 {@link #skippedSlots()} 读起来就是「说好要贴、结果没贴上」的清单，
     * 而不是一张把 66 个槽全列出来的表。
     */
    public void markSkipped(String key, @Nullable BlockState material, SkippedSlot.Reason reason) {
        if (material == null) {
            return;
        }
        skipped.add(new SkippedSlot(key, material.getBlock().asItem(), reason));
    }

    /** 铺上的槽（保序）。 */
    public List<String> appliedSlots() {
        return applied;
    }

    /** 跳过的槽（保序）。 */
    public List<SkippedSlot> skippedSlots() {
        return skipped;
    }

    /**
     * 一个 slot 没被铺上的原因。
     *
     * @param slot     槽位键名
     * @param material 预设里给的材质方块；预设里没有时为 {@code null}
     * @param reason   原因
     */
    public record SkippedSlot(String slot, @Nullable net.minecraft.world.item.Item material, Reason reason) {

        /** 为什么这个 slot 没铺上。 */
        public enum Reason {
            /** 预设里压根没给这个槽配材质——最常见的情况，不算错误。 */
            NOT_CONFIGURED,
            /** 配了，但玩家库存里（或创造模式判定下）拿不出这个材质：<b>留空，不阻止放置</b>。 */
            NO_MATERIAL,
            /**
             * 这个材质不被当前方块接受（服务端准入校验挡下的）。
             *
             * <p>正常情况下不会出现——材质选择界面用的是同一套判据。它挡住的是
             * 「改过包的客户端塞进一个非法材质」，以及以后万一界面判据与服务端分叉的情况。
             */
            REJECTED_MATERIAL,
            /** 这个槽所在的部件在当前结构下不存在（例如薄板里没被 occupancy 选中的层）。 */
            STRUCTURE_ABSENT,
            /** 方块实体里这个槽已经有材质了，放置器不覆盖已有伪装。 */
            ALREADY_CAMOUFLAGED,
            /** 方块实体的类型不对，写不进去（理论上不该发生）。 */
            NO_BLOCK_ENTITY;

            /** 稳定短名，给翻译键与测试用。 */
            public String id() {
                return name().toLowerCase(java.util.Locale.ROOT);
            }
        }
    }
}
