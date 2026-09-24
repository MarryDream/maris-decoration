package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「某一种伪装方块」在放置器里的适配器。
 *
 * <h2>它负责什么 / 不负责什么</h2>
 * <b>负责</b>：把一份与方块类型无关的 {@link PlacementConfig} 翻译成这个方块自己的表达方式，
 * 具体说就是三件事——
 * <ol>
 *   <li>{@link #stateFrom}：把「特殊结构属性」写进方块状态（例如护栏的四个方向）；</li>
 *   <li>{@link #slots}：枚举这个方块有哪些 material slot、当前配置下哪些真的存在；</li>
 *   <li>{@link #apply}：把预设里各 slot 的伪装材质写进刚放好的方块实体。</li>
 * </ol>
 *
 * <b>不负责</b>：判断玩家有没有物品、扣物品、决定要不要放置、含水状态。那些全在
 * {@code PlacementService} / {@code PlacementTransaction} 里，对全部 adapter 一视同仁。
 *
 * <h2>为什么必须是 adapter 而不是在 GUI / 放置器里 if-else</h2>
 * Create 的 {@code CopycatBlock}（单材质）、Copycats+ 的 {@code ICopycatBlock}（单材质）、
 * Copycats+ 的 {@code IMultiStateCopycatBlock}（按 property 分材质）、以及本 mod 的两个自定义
 * 方块（材质键完全不同）四套存储模型互不兼容。把它们的分支收在这里之后，GUI 与放置流程
 * 只认识 {@link AdapterSlot} 这一种数据，新增一种伪装方块只需要加一个 adapter。
 *
 * <h2>实现约定</h2>
 * <ul>
 *   <li>所有方法都只在<b>服务端</b>被调用，实现里不需要判断 {@code world.isClient}。</li>
 *   <li>{@link #apply} 必须对同一个 slot 幂等：已经伪装过的 slot 直接跳过，
 *       不要覆盖玩家或上一次放置的结果。</li>
 *   <li>{@link #apply} 不许扣玩家的东西。它只通过 {@link PlacementContext#markPaid} 声明
 *       「我用了这个材质」，扣物品由放置事务统一在最后一步做。</li>
 *   <li>{@link #stateFrom} 返回 {@code null} 表示「这份配置做不出合法状态」，
 *       放置器会据此放弃整次放置（不放置、不扣任何东西）。</li>
 * </ul>
 */
public interface CopycatPlacementAdapter {

    /** 这个 adapter 能不能处理给定的方块。判据只看方块类型，不看方块状态。 */
    boolean supports(Block block);

    /**
     * 解析优先级：数字小的先被问。
     *
     * <p>并不要求全局唯一——本 mod 的两个自定义 adapter 编号靠前只是为了「顺序稳定、可读」，
     * 它们与其它 adapter 的 {@link #supports} 本来就互斥。真正的兜底是最后一个
     * {@link GenericCopycatAdapter}，它排在所有 adapter 之后。
     */
    int priority();

    /** 调试与日志用的短名，例如 {@code copycats:multistate}。 */
    String name();

    /**
     * 把配置里的「特殊结构属性」写进将要放置的方块状态。
     *
     * <p>{@code state} 是已经去掉 {@code WATERLOGGED} 的预设状态（见
     * {@link PlacementConfig#stateForPlacement}），实现只按需 {@code with(...)}。
     * 默认实现原样返回：绝大多数伪装方块的结构信息不在方块状态里（而在方块实体里），
     * 那些 adapter 应当在 {@link #apply} 里处理。
     */
    default @Nullable BlockState stateFrom(BlockState state, PlacementConfig config) {
        return state;
    }

    /**
     * 枚举这个方块的全部 material slot（<b>包括</b>当前配置下结构不存在的那些，用
     * {@link AdapterSlot#structure()} 标记）。GUI 靠它建树，放置流程靠它遍历。
     *
     * <p><b>{@code state} 必须是 config 自己的状态</b>（即
     * {@link #displayState(BlockState, PlacementConfig)} 的结果），不是「为了发现槽位而临时
     * 拼出来的状态」。GUI 的槽位勾选、结构提示都以此为准，所以一旦这里偷换成别的状态，
     * 界面与世界就会各说各话（这是踩过的坑，见 {@link #displayState} 的注释）。
     *
     * @param state  config 自己的方块状态
     * @param config 当前预设
     */
    List<AdapterSlot> slots(BlockState state, PlacementConfig config);

    /**
     * config 自己的方块状态——GUI 显示属性、点击修改、NBT 保存、最终放置<b>全部</b>以它为准。
     *
     * <p>默认实现只做一件事：把 {@link #stateFrom} 的结果拿来用。对护栏这种「结构属性就在方块
     * 状态里」的方块，这一步是必须的，否则默认状态四面全 false、GUI 里所有材质槽都会标成不存在。
     *
     * <h2>为什么不能在这里「把状态提升成所有 part 都开着」</h2>
     * 曾经为了让 GUI 发现 Copycats+ multistate 的材质槽，这里返回过「storageProperties 全部置 true」
     * 的<b>临时状态</b>。后果是 GUI 显示（全 true）与 config 实际值（全 false）分裂：
     * 玩家看到六个面都是「是」却改不动，而放下去的方块没有任何可见结构——幽灵方块。
     *
     * <p>现在那条需求改由 {@link #slots} 在内部自行处理（它可以在自己的实现里临时提升状态来
     * <b>发现潜在槽位</b>），这个方法的返回值则严格等于「config 的忠实投影」。
     */
    default BlockState displayState(BlockState state, PlacementConfig config) {
        BlockState shaped = stateFrom(state, config);
        return shaped == null ? state : shaped;
    }

    /**
     * 玩家第一次选中这个方块时要生成的默认配置。
     *
     * <p>默认实现用 {@link #stateFrom} 把方块默认状态规范化一遍。**不能**直接用
     * {@code block.getDefaultState()} 就完事：有些伪装方块的默认状态是合法状态但代表
     * 「零个部件」（Copycats+ 的 half_layer 就是 negative_layers=0 / positive_layers=0），
     * 那样放下去就是一个看不见的幽灵方块。
     *
     * <p>所以每个 adapter 都要保证「返回的配置至少有一个可见部件」，必要时覆写它。
     */
    default PlacementConfig defaultConfig(Block block) {
        BlockState shaped = stateFrom(block.getDefaultState(), PlacementConfig.of(block.getDefaultState()));
        return PlacementConfig.of(shaped == null ? block.getDefaultState() : shaped);
    }

    /**
     * 这份配置能不能形成<b>至少一个可见部件</b>。
     *
     * <p>这是放置前的最后一道闸：即使 GUI 与默认配置都出了 bug，服务端也绝不会把一个
     * 零部件的方块写进世界。返回 {@code null} 表示合法，否则返回一句给玩家看的原因。
     *
     * @param state  最终要写进世界的方块状态（已经过 {@link #stateFrom} 与含水处理）
     * @param config 预设
     */
    default @Nullable String validateStructure(BlockState state, PlacementConfig config) {
        return null;
    }

    /**
     * 这个 adapter 的 virtual property（结构项），<b>只包含当前配置下真正存在的那些</b>。
     *
     * <p>参数是当前配置而不是没有参数：像「东北角柱只在与它相邻的两个面之一存在时才有意义」
     * 「某个方向没有板就没有开窗项」「交汇点只在候选 ≥ 2 条边时才出现」这类<b>动态展开</b>，
     * 只有 adapter 拿到配置才算得出来，GUI 不该知道这些规则。
     *
     * <p>被隐藏的项只是没被返回，配置里对应的值<b>原样保留</b>——
     * 「隐藏之后再出现」不会重置玩家之前的选择。
     */
    default List<VirtualSpec> virtualSpecs(PlacementConfig config) {
        return VirtualSpec.none();
    }

    /**
     * 这个方块能不能当作本 adapter 当前 slot 的伪装材质。
     *
     * <p>给材质选择界面过滤候选用。<b>服务端仍然会独立再验一次</b>——客户端的过滤只是 UX，
     * 不能替代校验。
     *
     * @param material 候选材质（方块默认状态）
     * @param slotKey  当前正在编辑的 material slot 键名；扁平单槽的 adapter 可以忽略它
     * @param config   当前预设（multistate 需要它来判断 slot 对应的 part 存不存在）
     * @param world    <b>形状查询用的世界视图，传真实世界</b>。Create 与 Copycats+ 的「轮廓必须是
     *                 完整立方体 / 碰撞箱非空」检查都写在 {@code world != null} 里面，所以只有
     *                 真实世界才能得到与正常右键放置<b>完全相同</b>的判定；退化成 {@code null}
     *                 或空视图会让整段形状检查被跳过（半砖、玻璃板会混进候选，这是实测踩到的）。
     * @param pos      查询位置；判据只关心候选方块自身的形状，所以位置本身不影响结果，
     *                 客户端用玩家所在处即可。
     */
    default boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config,
                                    @Nullable BlockView world, @Nullable BlockPos pos) {
        if (material.isAir()) {
            return false;
        }
        // 伪装方块自己不能当材质（Create 与 Copycats+ 的规则都是这样）
        return !PlacementAdapters.isPlaceable(material.getBlock());
    }

    /**
     * 把「形状查询视图」转成真实世界；不是世界时返回 {@code null}。
     *
     * <p>Create 与 Copycats+ 的材质判据都在 {@code world != null} 内做「轮廓必须是完整立方体」
     * 检查，所以：<b>服务端与客户端界面都应当传真实世界</b>，判据才完整；
     * 只有在确实没有世界上下文时才退化成 {@code null}（那会跳过形状检查）。
     */
    static @Nullable World castWorld(@Nullable BlockView view) {
        return view instanceof World world ? world : null;
    }

    /**
     * 没有世界上下文时的候选过滤视图（只在实在拿不到世界时使用）。
     *
     * <p><b>它不是等价替代</b>：用它会跳过 Create / Copycats+ 的形状检查，半砖、玻璃板、
     * 栅栏这类形状不完整的方块也会混进候选。GUI 有 {@code MinecraftClient.world}，
     * 所以正常都传真实世界；这个视图只作为极少数情况（世界还没加载）的兜底。
     */
    BlockView NO_MATERIAL_CONTEXT = new BlockView() {
        @Override
        public net.minecraft.block.entity.BlockEntity getBlockEntity(net.minecraft.util.math.BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(net.minecraft.util.math.BlockPos pos) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }

        @Override
        public net.minecraft.fluid.FluidState getFluidState(net.minecraft.util.math.BlockPos pos) {
            return net.minecraft.fluid.Fluids.EMPTY.getDefaultState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getBottomY() {
            return -64;
        }
    };

    /**
     * 把预设的伪装材质写进已经放好的方块实体。
     *
     * <p>实现里对每个想铺的槽调 {@link PlacementContext#markApplied}，
     * 对每个「配了但没铺上」的槽调 {@link PlacementContext#markSkipped}。
     * 一个槽都没铺是合法结果（例如预设里什么都没配），不算失败。
     */
    void apply(PlacementContext context);
}
