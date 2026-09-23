package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardArea;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层伪装薄板（{@code maris-decoration:layered_copycat_board}）的 adapter。
 *
 * <h2>暴露什么</h2>
 * 严格按要求，只暴露「玩家需要提前决定的东西」：
 * <ul>
 *   <li><b>12 个 Face/Layer occupancy</b> —— {@link PlacementConfig.Key#LAYERED_BOARD_OCCUPANCY}，
 *       一个 12 位掩码。位序直接用 {@link LayeredBoardSlots#slotBit}，
 *       不另立编号（这是与方块实体、渲染层共用的唯一一份编码）；</li>
 *   <li><b>Face window flags</b> —— {@link PlacementConfig.Key#LAYERED_BOARD_WINDOWS}，6 位掩码，
 *       位序用 {@link LayeredBoardSlots#windowBit}；</li>
 *   <li><b>当前有效的 BODY / TOP_EDGE / BOTTOM_EDGE / LEFT_EDGE / RIGHT_EDGE</b> ——
 *       就是每个「Face + Layer」下的五个材质槽，键名与方块实体逐字符一致
 *       （{@link LayeredBoardSlots#materialKey}）；</li>
 *   <li><b>Face 级 Window material</b> —— {@link LayeredBoardSlots#windowKey}，
 *       一个面一份、两层共用（这是方块自身的设计，不是这里的选择）。</li>
 * </ul>
 *
 * <h2>刻意不暴露什么</h2>
 * <ul>
 *   <li><b>topology / cache</b>：{@code LayeredBoardParts} 里那套按 (occupancy, windows)
 *       缓存的结构派生结果，是纯性能实现细节，预设里存它没有意义；</li>
 *   <li><b>physical run / junction override</b>：{@code junction_owners} 是「细工凿」在交汇点上
 *       选出来的显示归属，属于<b>放置之后</b>的本地编辑状态，而且 key 依赖当时的 occupancy，
 *       预存下来会在结构变化时漂移。放置器一个字节都不碰它；</li>
 *   <li><b>paidMaterials 内部字段</b>：那是方块实体的账本，不是预设。</li>
 * </ul>
 *
 * <h2>付账语义：同一 BlockPos 同一种材质只付一次</h2>
 * 完全沿用 {@link LayeredCopycatBoardBlockEntity#hasPaidFor} 那套：一块薄板最多 66 个材质槽，
 * 若逐槽扣一次，拆掉整块时要还 66 份，对不上。放置器通过 {@link PlacementContext#isPaid} 去重，
 * 并用 {@link PlacementContext#markPaid} 把「本次已经付过」的材质记下来。
 */
public final class LayeredBoardCopycatAdapter implements CopycatPlacementAdapter {

    /** 结构属性：12 位占用掩码。 */
    public static final String STRUCTURE_OCCUPANCY = PlacementConfig.Key.LAYERED_BOARD_OCCUPANCY;
    /** 结构属性：6 位窗掩码。 */
    public static final String STRUCTURE_WINDOWS = PlacementConfig.Key.LAYERED_BOARD_WINDOWS;

    /** 12 个槽位里默认只占 DOWN.OUTER：见 {@link #defaultConfig} 的说明。 */
    public static final int DEFAULT_OCCUPANCY = 1;
    /** 一个窗都不开（默认结构）。 */
    public static final int DEFAULT_WINDOWS = 0;

    private static final String GROUP_PREFIX = "maris-decoration.copycat_placer.group.face.";
    private static final String OCCUPANCY_LABEL_PREFIX = "maris-decoration.copycat_placer.occupancy.";
    private static final String WINDOW_LABEL_PREFIX = "maris-decoration.copycat_placer.window.";

    @Override
    public boolean supports(Block block) {
        return block instanceof LayeredCopycatBoardBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_OWN_CUSTOM;
    }

    @Override
    public String name() {
        return "maris-decoration:layered_copycat_board";
    }

    // ---------------------------------------------------------------- 结构

    /**
     * 预设里的占用掩码（0..4095）。
     *
     * <p>没配时是 {@link #DEFAULT_OCCUPANCY}。
     */
    public static int occupancy(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_OCCUPANCY, 12, DEFAULT_OCCUPANCY);
    }

    /** 预设里的窗掩码（0..63）。没配时是「一个窗都不开」。 */
    public static int windows(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_WINDOWS, 6, DEFAULT_WINDOWS);
    }

    /** 写回占用掩码。 */
    public static PlacementConfig withOccupancy(PlacementConfig config, int occupancy) {
        return StructureMasks.with(config, STRUCTURE_OCCUPANCY, occupancy, 12);
    }

    /** 写回窗掩码。 */
    public static PlacementConfig withWindows(PlacementConfig config, int windows) {
        return StructureMasks.with(config, STRUCTURE_WINDOWS, windows, 6);
    }

    /**
     * 结构属性写进方块状态。
     *
     * <p><b>当前是恒等映射</b>：{@code LayeredCopycatBoardBlock} 的方块状态只有一个
     * {@code WATERLOGGED}，12 位占用与 6 个面的窗全都在方块实体的掩码里。所以这里没有可写的
     * 结构属性——占用与窗由 {@link #virtualSpecs()} 暴露、由 {@link #apply} 写进方块实体。
     *
     * @return 原样返回
     */
    @Override
    public BlockState stateFrom(BlockState state, PlacementConfig config) {
        return state;
    }

    /**
     * 两个 virtual property：占用层级与窗开关。
     *
     * <p>两者都是 12/6 位掩码。占用给的是<b>常用组合</b>而不是全部 4096 种：精确到单层的需求由
     * 六个面的 {@code BooleanProperty} 满足不了（那只有「这个面有没有板」），所以这里额外提供
     * 「只贴外/内层」「两层都贴」这类候选，覆盖真实建造时绝大多数用法。
     *
     * <p>窗开关同样给候选组合，而不是让玩家逐位拼。
     */
    @Override
    public List<VirtualSpec> virtualSpecs() {
        List<VirtualSpec.Option> occupancyOptions = new ArrayList<>();
        // <b>默认值必须放在第一位</b>：GUI 的循环按钮靠「当前值在候选里的下标」定位，
        // 找不到就退化成下标 0。如果 0 不是默认值，玩家打开界面点一下会直接跳到候选里的第一项
        // （曾经就是「空」），看起来像被重置了。
        occupancyOptions.add(occupied("all", LayeredBoardCopycatAdapter.DEFAULT_OCCUPANCY));
        occupancyOptions.add(occupied("outer_only", outerOnly()));
        occupancyOptions.add(occupied("inner_only", innerOnly()));
        for (FaceDir face : FaceDir.values()) {
            occupancyOptions.add(occupied(face.getName(),
                    LayeredBoardSlots.slotBitMask(face, BoardLayer.OUTER)
                            | LayeredBoardSlots.slotBitMask(face, BoardLayer.INNER)));
            occupancyOptions.add(occupied(face.getName() + "_outer",
                    LayeredBoardSlots.slotBitMask(face, BoardLayer.OUTER)));
        }
        occupancyOptions.add(occupied("empty", 0));

        List<VirtualSpec.Option> windowOptions = new ArrayList<>();
        // 同理：默认「一个窗都不开」放第一位
        windowOptions.add(new VirtualSpec.Option(WINDOW_LABEL_PREFIX + "none", "none",
                LayeredBoardCopycatAdapter.DEFAULT_WINDOWS));
        int allWindows = (1 << FaceDir.values().length) - 1;
        windowOptions.add(new VirtualSpec.Option(WINDOW_LABEL_PREFIX + "all", "all faces", allWindows));
        for (FaceDir face : FaceDir.values()) {
            windowOptions.add(new VirtualSpec.Option(WINDOW_LABEL_PREFIX + face.getName(),
                    face.getName(), LayeredBoardSlots.windowBit(face)));
        }

        return List.of(
                VirtualSpec.options(STRUCTURE_OCCUPANCY,
                        "maris-decoration.copycat_placer.virtual.layered_occupancy",
                        "Occupied layers",
                        LayeredBoardCopycatAdapter::occupancy,
                        (config, value) -> withOccupancy(config, value),
                        occupancyOptions),
                VirtualSpec.options(STRUCTURE_WINDOWS,
                        "maris-decoration.copycat_placer.virtual.layered_windows",
                        "Windows",
                        LayeredBoardCopycatAdapter::windows,
                        (config, value) -> withWindows(config, value),
                        windowOptions));
    }

    private static VirtualSpec.Option occupied(String name, int mask) {
        // 兜底文本用候选名本身（all / outer_only / north_outer …）：已经是可读英文，
        // 而且绝不暴露掩码数值。翻译缺失时界面上出现的是它，不是 0x3 这类内部表示。
        return new VirtualSpec.Option(OCCUPANCY_LABEL_PREFIX + name,
                name.replace('_', ' '), mask);
    }

    /** 只贴外层：每个面的 OUTER。 */
    public static int outerOnly() {
        int mask = 0;
        for (FaceDir face : FaceDir.values()) {
            mask |= LayeredBoardSlots.slotBitMask(face, BoardLayer.OUTER);
        }
        return mask;
    }

    /** 只贴内层：每个面的 INNER。 */
    public static int innerOnly() {
        int mask = 0;
        for (FaceDir face : FaceDir.values()) {
            mask |= LayeredBoardSlots.slotBitMask(face, BoardLayer.INNER);
        }
        return mask;
    }

    // ---------------------------------------------------------------- 槽位

    /**
     * 这个预设下「真的有板」的 Face/Layer 组合，顺序固定为
     * {@link FaceDir#values()} × {@link BoardLayer#values()}。
     */
    public static List<LayerRef> occupiedLayers(PlacementConfig config) {
        int occupancy = occupancy(config);
        List<LayerRef> out = new ArrayList<>(Integer.bitCount(occupancy));
        for (FaceDir face : FaceDir.values()) {
            for (BoardLayer layer : BoardLayer.values()) {
                if (LayeredBoardSlots.hasSlot(occupancy, face, layer)) {
                    out.add(new LayerRef(face, layer));
                }
            }
        }
        return out;
    }

    /** 一个「面 + 层」的引用。给 GUI 按层折叠用。 */
    public record LayerRef(FaceDir face, BoardLayer layer) {
        /** 这个层的分组翻译键（GUI 把同一层的 5 个区域折叠在一起）。 */
        public String groupKey() {
            return GROUP_PREFIX + face.getName() + "." + layer.getName();
        }

        @Override
        public String toString() {
            return LayeredBoardSlots.describe(face, layer);
        }
    }

    /**
     * 枚举槽位时用的展示状态。
     *
     * <p>薄板的方块状态里只有 {@code WATERLOGGED}，没有「哪些层被占用」的信息（那是方块实体的
     * 12 位掩码），所以这里实际上就是原状态。保留这个覆写是为了把意图写清楚：
     * <b>槽位可见性与当前占用无关</b>——12 层的材质槽永远全部可配置，真正放置时再按占用铺。
     * 玩家因此可以「先把材质配好、再回头调占用」，不会因为改了占用就丢材质。
     */
    @Override
    public BlockState displayState(BlockState state, PlacementConfig config) {
        return state;
    }

    /**
     * 默认配置：<b>只占 DOWN.OUTER 一层</b>。
     *
     * <p>不能用「12 层全占」当默认：那虽然可见，但对「只想要一块竖直板」的玩家来说每次都要
     * 先删掉 11 层。也不能用 occupancy=0——那是零部件的幽灵方块。取一个最小且可见的形态。
     */
    @Override
    public PlacementConfig defaultConfig(Block block) {
        return withOccupancy(PlacementConfig.of(block.getDefaultState()), DEFAULT_OCCUPANCY);
    }

    /** 一层都没占 = 放下去什么都看不见的幽灵方块，必须挡住。 */
    @Override
    public String validateStructure(BlockState state, PlacementConfig config) {
        if (occupancy(config) == 0) {
            return "layered_board_empty_occupancy";
        }
        return null;
    }

    /** 材质准入复用薄板自己那份判据（与护栏同一套 Create 规则）。 */
    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config, BlockView world, BlockPos pos) {
        return LayeredCopycatBoardBlock.getAcceptedMaterial(world, pos,
                new ItemStack(material.getBlock()), null) != null;
    }

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        int occupancy = occupancy(config);
        int windows = windows(config);
        List<AdapterSlot> slots = new ArrayList<>(FaceDir.values().length
                * (BoardLayer.values().length * LayeredBoardSlots.MATERIAL_AREAS.size() + 1));

        for (FaceDir face : FaceDir.values()) {
            boolean faceHasWindow = LayeredBoardSlots.hasWindow(windows, face);
            for (BoardLayer layer : BoardLayer.values()) {
                boolean occupied = LayeredBoardSlots.hasSlot(occupancy, face, layer);
                String group = GROUP_PREFIX + face.getName() + "." + layer.getName();
                for (BoardArea area : LayeredBoardSlots.MATERIAL_AREAS) {
                    String key = LayeredBoardSlots.materialKey(face, layer, area);
                    slots.add(AdapterSlot.of(key, labelKey(face, layer, area), group, occupied,
                            config.slots().get(key)));
                }
            }
            // 窗是面级的：两层共用一条材质，所以它在循环外面单独出现一次
            String windowKey = LayeredBoardSlots.windowKey(face);
            slots.add(AdapterSlot.of(windowKey, labelKey(face, null, BoardArea.WINDOW),
                    GROUP_PREFIX + face.getName() + ".window",
                    faceHasWindow && LayeredBoardSlots.hasFace(occupancy, face),
                    config.slots().get(windowKey)));
        }
        return slots;
    }

    private static String labelKey(FaceDir face, BoardLayer layer, BoardArea area) {
        String suffix = layer == null
                ? "window"
                : layer.getName() + "." + area.getName();
        return "maris-decoration.copycat_placer.slot.layered_board." + face.getName() + "." + suffix;
    }

    // ---------------------------------------------------------------- 应用

    @Override
    public void apply(PlacementContext context) {
        if (!(context.world().getBlockEntity(context.pos()) instanceof LayeredCopycatBoardBlockEntity board)) {
            return;
        }
        int occupancy = occupancy(context.config());
        int windows = windows(context.config());
        // 先立结构、再铺材质：材质写进方块实体之后，occupancy 变化会让
        // LayeredCopycatBoardBlockEntity 顺手清掉失效的 junction override（见 setOccupancy），
        // 所以结构必须先到位。
        //
        // 注意这里会覆盖已有的 occupancy，所以下面判断「这个槽是不是已经有材质」时不能只看
        // 目标 key——那块材质可能存在于同一物理位置上的别的槽位（例如想贴 up.outer.body，
        // 而板子当前开着窗、上一位玩家贴的是 up.window）。see isAlreadyCamouflaged。
        board.setOccupancy(occupancy);
        for (FaceDir face : FaceDir.values()) {
            boolean want = LayeredBoardSlots.hasWindow(windows, face);
            if (board.hasWindow(face) != want) {
                board.toggleWindow(face);
            }
        }

        for (LayerRef ref : occupiedLayers(context.config())) {
            for (BoardArea area : LayeredBoardSlots.MATERIAL_AREAS) {
                applySlot(context, board,
                        LayeredBoardSlots.materialKey(ref.face(), ref.layer(), area),
                        ref.face(), ref.layer(), area);
            }
            // 窗材质按面共享：只要这个面开着窗就铺一次（两层都会看到同一份材质）
            if (LayeredBoardSlots.hasWindow(windows, ref.face())) {
                applySlot(context, board, LayeredBoardSlots.windowKey(ref.face()),
                        ref.face(), ref.layer(), BoardArea.WINDOW);
            }
        }
    }

    /** 铺一个槽：已有材质就跳过；没预设就不动；付账按「同格同材质只付一次」。 */
    private static void applySlot(PlacementContext context, LayeredCopycatBoardBlockEntity board, String key,
                                  FaceDir face, BoardLayer layer, BoardArea area) {
        BlockState material = context.material(key);
        if (material == null) {
            return;
        }
        if (isAlreadyCamouflaged(board, face, layer, area)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
            return;
        }
        // 服务端准入校验：不合法的材质留空，不阻止放置（客户端过滤只是 UX）
        if (!context.acceptsMaterial(material, key)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
            return;
        }
        // 同一个 BlockPos 上同一种材质只付一次：已经付过就只写材质、不记账。
        // 拿不出这个材质就留空，不阻止放置。
        boolean alreadyPaid = context.paid().contains(material);
        if (!context.claim(material)) {
            context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
            return;
        }
        board.setMaterial(key, material, alreadyPaid ? null : new ItemStack(material.getBlock()));
        context.markApplied(key);
    }

    /**
     * 目标槽「已经有了伪装」吗——把同一物理位置上的等价槽也算进来。
     *
     * <p>一个物理位置可能对应多个材质槽，取决于这个面现在开不开窗：
     * <ul>
     *   <li>没开窗：中央区域是 {@code BODY}；</li>
     *   <li>开了窗：同一块位置是面级的 {@code WINDOW}（两层共用），而 {@code BODY} 只剩窗框。</li>
     * </ul>
     * 只有看全这两个候选，才能保证「放置器不会在同一块几何上叠出两份材质、扣两次钱」——
     * 这正是 {@link #apply} 会覆盖已有 {@code windows} 状态所带来的隐患。
     * 边与角不属于中央区域，不受窗开关影响，所以只有 {@code BODY} 需要这种检查。
     */
    private static boolean isAlreadyCamouflaged(LayeredCopycatBoardBlockEntity board,
                                                FaceDir face, BoardLayer layer, BoardArea area) {
        if (area == BoardArea.BODY || area == BoardArea.WINDOW) {
            return board.hasMaterial(LayeredBoardSlots.windowKey(face))
                    || board.hasMaterial(LayeredBoardSlots.materialKey(face, layer, BoardArea.BODY));
        }
        return board.hasMaterial(LayeredBoardSlots.materialKey(face, layer, area));
    }
}
