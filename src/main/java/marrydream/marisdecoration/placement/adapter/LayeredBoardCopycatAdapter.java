package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity;
import marrydream.marisdecoration.block.utils.LayeredBoardParts;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分层伪装薄板（{@code maris-decoration:layered_copycat_board}）的 adapter。
 *
 * <h2>结构模型：12 个层开关 + 6 个方向窗开关 + 动态交汇点归属</h2>
 * <ul>
 *   <li><b>12 个层</b>（上面 / 下面 / 北面 / 南面 / 东面 / 西面 × 外层 / 内层）：
 *       一直显示，一行一个开关。默认只有<b>下面外层</b>开着。</li>
 *   <li><b>6 个方向开窗</b>：<b>按当前结构动态出现</b>——某个方向的 outer / inner 只要有一个开着，
 *       就出现这个方向的开窗项；两层都关着时这一项根本不出现。</li>
 *   <li><b>交汇点材质归属</b>：同样动态出现，见下面「交汇点」一节。</li>
 * </ul>
 * 内部存储仍然是两个掩码（{@link #occupancy} / {@link #windows}），位序直接用
 * {@link LayeredBoardSlots#slotBit} / {@link LayeredBoardSlots#windowBit}——那是与方块实体、
 * 渲染层共用的唯一一份编码。掩码是<b>实现细节</b>：界面上永远是逐项开关。
 *
 * <p>为什么原来的「占用组合枚举」不行：12 个层的组合是 4096 种，枚举里只能挑十几个常见形态，
 * 玩家想「上面外层 + 北面内层」这种组合时根本找不到；而且「某个方向开不开窗」与
 * 「这个方向有没有板」之间的依赖关系在枚举模型里无处表达。
 *
 * <h2>交汇点（Junction）</h2>
 * 两块板在几何上重合的棱 / 角，最终只能显示一个材质槽。薄板本来就有这套模型
 * （{@link LayeredBoardParts.Junction}，细工凿用的就是它），这里直接复用：
 * <ul>
 *   <li>只列出<b>候选 ≥ 2 条边</b>的交汇点——唯一归属的位置没有可选项，不出现；</li>
 *   <li>候选项就是那几条几何重合的边，显示的是边的可读名（例如「北面·外层·右边」），
 *       <b>不是</b>槽位码 / 掩码 / 十六进制；</li>
 *   <li>选中后写进配置的键是 {@link PlacementConfig.Key#LAYERED_BOARD_JUNCTION_PREFIX} +
 *       交汇点 identity，与方块实体的 {@code junction_owners} 用的是同一套 identity，
 *       所以放置时一个映射就写完了。</li>
 * </ul>
 *
 * <h2>材质槽：只列「真的有几何」的</h2>
 * 判据来自 {@link LayeredBoardParts#materialKeys}（渲染画出来的那份几何的键集合），
 * 不再把 66 个槽无脑全列。只开「下面外层」时，材质列表就只有这一层的五个区域。
 *
 * <h2>付账语义：同一 BlockPos 同一种材质只付一次</h2>
 * 完全沿用 {@link LayeredCopycatBoardBlockEntity#hasPaidFor} 那套：放置器通过
 * {@link PlacementContext#isPaid} 去重，并用 {@link PlacementContext#markPaid} 记录本次已付过的材质。
 */
public final class LayeredBoardCopycatAdapter implements CopycatPlacementAdapter {

    /** 结构属性：12 位占用掩码。 */
    public static final String STRUCTURE_OCCUPANCY = PlacementConfig.Key.LAYERED_BOARD_OCCUPANCY;
    /** 结构属性：6 位窗掩码。 */
    public static final String STRUCTURE_WINDOWS = PlacementConfig.Key.LAYERED_BOARD_WINDOWS;
    /** 结构属性：交汇点归属的键前缀。 */
    public static final String JUNCTION_PREFIX = PlacementConfig.Key.LAYERED_BOARD_JUNCTION_PREFIX;

    /** 默认只占 DOWN.OUTER：见 {@link #defaultConfig} 的说明。 */
    public static final int DEFAULT_OCCUPANCY = LayeredBoardSlots.slotBitMask(FaceDir.DOWN, BoardLayer.OUTER);
    /** 一个窗都不开（默认结构）。 */
    public static final int DEFAULT_WINDOWS = 0;

    /**
     * 界面上的面的顺序：上面、下面、北面、南面、东面、西面。
     *
     * <p>与 {@link FaceDir#values()} 不同（那个是枚举顺序，受内部位序约束）。
     * 结构开关与材质列表都用这一个顺序，玩家在上下两栏看到的东西顺序一致。
     */
    public static final List<FaceDir> DISPLAY_FACES = List.of(
            FaceDir.UP, FaceDir.DOWN, FaceDir.NORTH, FaceDir.SOUTH, FaceDir.EAST, FaceDir.WEST);

    private static final String GROUP_PREFIX = "maris-decoration.copycat_placer.group.face.";
    private static final String SLOT_LABEL_PREFIX = "maris-decoration.copycat_placer.slot.layered_board.";
    private static final String STRUCTURE_LABEL_PREFIX = "item.maris-decoration.copycat_placer.structure.board.";
    /** 交点行的标题翻译键前缀（面 / 层 / 角 / 交点）。 */
    private static final String JUNCTION_LABEL_PREFIX = "item.maris-decoration.copycat_placer.junction.";

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

    // ---------------------------------------------------------------- 结构：读取

    /** 预设里的占用掩码（0..4095）。没配或配坏时是 {@link #DEFAULT_OCCUPANCY}。 */
    public static int occupancy(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_OCCUPANCY, 12, DEFAULT_OCCUPANCY,
                LayeredBoardCopycatAdapter::occupancyNameToMask);
    }

    /** 预设里的窗掩码（0..63）。没配时是「一个窗都不开」。 */
    public static int windows(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_WINDOWS, 6, DEFAULT_WINDOWS,
                LayeredBoardCopycatAdapter::windowNameToMask);
    }

    /** 某一个层（面 + 层）开着吗。 */
    public static boolean slotEnabled(PlacementConfig config, FaceDir face, BoardLayer layer) {
        return LayeredBoardSlots.hasSlot(occupancy(config), face, layer);
    }

    /** 这个面至少有一层吗。 */
    public static boolean faceHasLayer(PlacementConfig config, FaceDir face) {
        return LayeredBoardSlots.hasFace(occupancy(config), face);
    }

    /** 这个方向的窗开着吗。 */
    public static boolean windowEnabled(PlacementConfig config, FaceDir face) {
        return LayeredBoardSlots.hasWindow(windows(config), face);
    }

    /** 交汇点归属：identity → 槽名（去掉了 {@link #JUNCTION_PREFIX}）。 */
    public static Map<String, String> junctionOwners(PlacementConfig config) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.structures().entrySet()) {
            if (entry.getKey().startsWith(JUNCTION_PREFIX)) {
                owners.put(entry.getKey().substring(JUNCTION_PREFIX.length()), entry.getValue());
            }
        }
        return owners;
    }

    // ---------------------------------------------------------------- 结构：写入

    /** 写回占用掩码。 */
    public static PlacementConfig withOccupancy(PlacementConfig config, int occupancy) {
        return StructureMasks.with(config, STRUCTURE_OCCUPANCY, occupancy, 12);
    }

    /** 写回窗掩码。 */
    public static PlacementConfig withWindows(PlacementConfig config, int windows) {
        return StructureMasks.with(config, STRUCTURE_WINDOWS, windows, 6);
    }

    /**
     * 开关一个层。
     *
     * <p><b>只动这一位</b>：其它 11 个层、6 个窗开关、以及全部交汇点归属都原样保留。
     * 所以「关掉一个面 → 它的开窗项消失 → 再把面开回来」时，之前给这个窗做过的选择还在。
     */
    public static PlacementConfig withSlot(PlacementConfig config, FaceDir face, BoardLayer layer, boolean enabled) {
        int mask = occupancy(config);
        return withOccupancy(config, enabled
                ? LayeredBoardSlots.withSlot(mask, face, layer)
                : LayeredBoardSlots.withoutSlot(mask, face, layer));
    }

    /** 开关一个方向的窗；同样只动这一位。 */
    public static PlacementConfig withWindow(PlacementConfig config, FaceDir face, boolean enabled) {
        int mask = windows(config);
        return withWindows(config, enabled
                ? LayeredBoardSlots.withWindow(mask, face)
                : LayeredBoardSlots.withoutWindow(mask, face));
    }

    /** 写一个交汇点的归属。 */
    public static PlacementConfig withJunctionOwner(PlacementConfig config, String junctionKey, String slot) {
        return config.withStructure(JUNCTION_PREFIX + junctionKey, slot);
    }

    /**
     * 旧配置里的「名字」形式 → 占用掩码。
     *
     * <p>更早的界面把占用压成了十几个组合（{@code all} / {@code outer_only} / {@code inner_only} /
     * {@code down} / {@code north_outer}…）。这些名字的语义是明确的，读取时换算回等价的层集合，
     * 玩家打开界面看到的就是对应的那几个开关亮着，而不是一份读不懂的配置。
     */
    private static Integer occupancyNameToMask(String name) {
        return switch (name) {
            case "none", "empty" -> 0;
            case "all", "full" -> LayeredBoardSlots.FULL_OCCUPANCY;
            case "outer_only", "outer" -> outerOnly();
            case "inner_only", "inner" -> innerOnly();
            default -> faceOccupancyNameToMask(name);
        };
    }

    /** {@code down} / {@code down_outer} / {@code down_inner} 这类「某个面」的名字。 */
    private static Integer faceOccupancyNameToMask(String name) {
        boolean inner = name.endsWith("_inner");
        boolean outer = name.endsWith("_outer");
        String faceName = inner || outer ? name.substring(0, name.lastIndexOf('_')) : name;
        if (faceName.endsWith("_both")) {
            faceName = faceName.substring(0, faceName.length() - "_both".length());
        }
        for (FaceDir face : FaceDir.values()) {
            if (!face.getName().equals(faceName)) {
                continue;
            }
            if (inner) {
                return LayeredBoardSlots.slotBitMask(face, BoardLayer.INNER);
            }
            if (outer) {
                return LayeredBoardSlots.slotBitMask(face, BoardLayer.OUTER);
            }
            return LayeredBoardSlots.slotBitMask(face, BoardLayer.OUTER)
                    | LayeredBoardSlots.slotBitMask(face, BoardLayer.INNER);
        }
        return null;
    }

    /** 旧配置里的窗名字：{@code none} / {@code all} / {@code north}… */
    private static Integer windowNameToMask(String name) {
        return switch (name) {
            case "none", "empty" -> 0;
            case "all", "full" -> (1 << FaceDir.values().length) - 1;
            default -> windowFaceNameToMask(name);
        };
    }

    private static Integer windowFaceNameToMask(String name) {
        for (FaceDir face : FaceDir.values()) {
            if (face.getName().equals(name)) {
                return LayeredBoardSlots.windowBit(face);
            }
        }
        return null;
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

    // ---------------------------------------------------------------- 一份结构的派生结果

    /**
     * 一个 (占用, 窗, 交汇点归属) 组合的派生结果。
     *
     * <p>两份数据都来自方块自己的几何函数，而且是「同一份几何」的两个投影：
     * 「真的有几何的槽」来自 {@link LayeredBoardParts#materialKeys}，
     * 「有歧义的交汇点」来自 {@link LayeredBoardParts#junctions}。
     */
    private record StructureView(int occupancy, int windows, Map<String, String> overrides,
                                 Set<String> presentKeys, List<LayeredBoardParts.Junction> junctions) {
    }

    /**
     * 单条缓存。
     *
     * <p><b>纯粹是性能</b>：界面每帧都会问一次槽位与交汇点，而有交汇点 override 时
     * {@code materialKeys} 要走一遍完整几何。缓存只是让同一份配置的重复询问变便宜，
     * 正确性完全不依赖它——任何一次命中失败都只是重算一遍，结果相同。
     * 只留一条：真实使用里「当前编辑的配置」永远是同一个，多留没有意义。
     */
    private volatile StructureView structureView;

    private StructureView structureView(PlacementConfig config) {
        int occupancy = occupancy(config);
        int windows = windows(config);
        Map<String, String> overrides = junctionOwners(config);
        StructureView cached = structureView;
        if (cached != null && cached.occupancy() == occupancy && cached.windows() == windows
                && cached.overrides().equals(overrides)) {
            return cached;
        }
        StructureView fresh = new StructureView(occupancy, windows, Map.copyOf(overrides),
                LayeredBoardParts.materialKeys(occupancy, windows, overrides),
                LayeredBoardParts.junctions(occupancy, windows, overrides));
        structureView = fresh;
        return fresh;
    }

    /**
     * 结构属性写进方块状态。
     *
     * <p><b>当前是恒等映射</b>：{@code LayeredCopycatBoardBlock} 的方块状态只有一个
     * {@code WATERLOGGED}，12 位占用与 6 个面的窗全都在方块实体的掩码里。所以这里没有可写的
     * 结构属性——占用与窗由 {@link #virtualSpecs} 暴露、由 {@link #apply} 写进方块实体。
     *
     * @return 原样返回
     */
    @Override
    public BlockState stateFrom(BlockState state, PlacementConfig config) {
        return state;
    }

    /**
     * 默认配置：<b>只占 DOWN.OUTER 一层</b>（界面上就是「下面外层 = 是」）。
     *
     * <p>不能用「12 层全占」当默认：那虽然可见，但对「只想要一块竖直板」的玩家来说每次都要
     * 先关掉 11 个开关。也不能用 occupancy=0——那是零部件的幽灵方块。取一个最小且可见的形态。
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
        Set<String> present = structureView(config).presentKeys();
        List<AdapterSlot> slots = new ArrayList<>(LayeredBoardSlots.allMaterialKeys().size());
        for (FaceDir face : DISPLAY_FACES) {
            for (BoardLayer layer : BoardLayer.values()) {
                String group = GROUP_PREFIX + face.getName() + "." + layer.getName();
                for (BoardArea area : LayeredBoardSlots.MATERIAL_AREAS) {
                    String key = LayeredBoardSlots.materialKey(face, layer, area);
                    slots.add(AdapterSlot.of(key, slotLabelKey(face, layer, area),
                            slotLabelText(face, layer, area), group,
                            present.contains(key), config.slots().get(key)));
                }
            }
            // 窗是面级的：两层共用一条材质，所以它在循环外面单独出现一次
            String windowKey = LayeredBoardSlots.windowKey(face);
            slots.add(AdapterSlot.of(windowKey, slotLabelKey(face, null, BoardArea.WINDOW),
                    slotLabelText(face, null, BoardArea.WINDOW),
                    GROUP_PREFIX + face.getName() + ".window",
                    present.contains(windowKey), config.slots().get(windowKey)));
        }
        return slots;
    }

    private static String slotLabelKey(FaceDir face, BoardLayer layer, BoardArea area) {
        String suffix = layer == null
                ? BoardArea.WINDOW.getName()
                : layer.getName() + "." + area.getName();
        return SLOT_LABEL_PREFIX + face.getName() + "." + suffix;
    }

    /**
     * 材质槽显示名（翻译缺失时才用）：例如 {@code 底面·外层·右边}。
     *
     * <p>面名用 {@link #junctionFaceText}（底面 / 顶面）——与语言文件里那 66 个槽位词条
     * 逐字一致，也与交点标题、交点取值一致。结构开关那一栏（{@code 下面外层}）用的是另一套
     * 用词，那是按需求写的，两者互不影响。
     */
    private static String slotLabelText(FaceDir face, BoardLayer layer, BoardArea area) {
        String faceText = junctionFaceText(face);
        if (layer == null) {
            return faceText + "·圆窗";
        }
        return faceText + "·" + layerText(layer) + "·" + areaText(area);
    }

    // ---------------------------------------------------------------- 结构项（GUI）

    /**
     * 结构项，顺序固定为三段：<b>12 个层开关 → 有板的面的开窗开关 → 顶点材质归属</b>。
     *
     * <p>这个顺序就是操作顺序：先决定「有哪些板」，再决定「哪些板开窗」，
     * 最后才处理「顶点上显示哪条边的材质」这种细节。
     * 上一版按显示名整体排序，把顶点行混到了开关中间（玩家一进界面先看到一堆顶点项），
     * 所以这里改成按段拼装，段内各自保持稳定顺序。
     *
     * <p><b>只暴露顶点（角点）级的归属项</b>：棱上、面内、中间的 junction 是几何实现细节，
     * 对玩家没有可操作性（它们的候选取舍既看不出来也难以描述），所以不放进 GUI。
     * 后端仍然照旧计算它们（细工凿的交互、渲染、缓存都没变），这里只是不列出来。
     */
    @Override
    public List<VirtualSpec> virtualSpecs(PlacementConfig config) {
        StructureView view = structureView(config);
        List<VirtualSpec> specs = new ArrayList<>(12 + 6 + view.junctions().size());

        // 第一段：12 个层开关（面 × 层）
        for (FaceDir face : DISPLAY_FACES) {
            for (BoardLayer layer : BoardLayer.values()) {
                specs.add(VirtualSpec.toggle(
                        "layer." + face.getName() + "." + layer.getName(),
                        List.of(VirtualSpec.LabelPart.of(
                                STRUCTURE_LABEL_PREFIX + "layer." + face.getName() + "." + layer.getName(),
                                layerLabelText(face, layer))),
                        current -> slotEnabled(current, face, layer),
                        (current, value) -> withSlot(current, face, layer, value)));
            }
        }

        // 第二段：开窗开关（只对当前至少有一层板的面显示）
        for (FaceDir face : DISPLAY_FACES) {
            // 动态出现：这个面一层板都没有时不显示它的开窗项（窗只对「有板的面」有意义）。
            // 关掉之后那个值仍然留在配置里，板回来时窗的开关值原样回来。
            if (!LayeredBoardSlots.hasFace(view.occupancy(), face)) {
                continue;
            }
            specs.add(VirtualSpec.toggle(
                    "window." + face.getName(),
                    List.of(VirtualSpec.LabelPart.of(
                            STRUCTURE_LABEL_PREFIX + "window." + face.getName(),
                            windowLabelText(face))),
                    current -> windowEnabled(current, face),
                    (current, value) -> withWindow(current, face, value)));
        }

        // 第三段：顶点材质归属（只取 corner 类型的 junction）
        List<VirtualSpec> vertices = new ArrayList<>();
        for (LayeredBoardParts.Junction junction : view.junctions()) {
            if (!junction.corner()) {
                continue;
            }
            List<VirtualSpec.Option> options = new ArrayList<>(junction.candidates().size());
            for (LayeredBoardParts.Slot candidate : junction.candidates()) {
                options.add(new VirtualSpec.Option(candidate.name(), List.of(slotLabelPart(candidate))));
            }
            // 标题描述「这是哪个顶点」，不是「有哪些候选」：
            // 一行只有 ~90px 可用，把候选边名拼起来必然被截断，玩家反而看不出这是什么。
            // 候选来源放在悬停提示里（见 StructureTooltip）。
            vertices.add(VirtualSpec.choice(
                    JUNCTION_PREFIX + junction.key(),
                    vertexLabel(junction),
                    current -> shownSlotId(current, junction),
                    (current, id) -> withJunctionOwner(current, junction.key(), id),
                    options));
        }
        // 顶点内部按显示名排序：顺序稳定、可复现，且与玩家从上到下的阅读习惯一致
        vertices.sort(java.util.Comparator.comparing(spec -> spec.label().get(0).labelKey()));
        specs.addAll(vertices);
        return List.copyOf(specs);
    }

    // ---------------------------------------------------------------- 顶点显示名

    /**
     * 一个<b>顶点</b>（角点）归属项的显示名。
     *
     * <p>两种形态，两种都以「角」结尾：
     * <ul>
     *   <li><b>单块板的角</b>（默认只有下面外层时就是四个这样的顶点）：
     *       {@code 底面·外层·左下角}——面 + 层 + 该面本地的角名，
     *       角名由<b>候选边的覆盖情况</b>推出来（下边 + 左边 = 左下角），与材质槽的叫法同一套；</li>
     *   <li><b>多块板共享的角</b>：{@code 底面·北面·底·西北·角}——参与的面 + 这个角在方块里的物理位置。
     *       位置只用来区分「哪个角」（同一组面之间可能有好几个角），形状词固定是「角」，
     *       因为棱 / 面 / 中间的运行点已经不列给玩家了。</li>
     * </ul>
     *
     * <p>不含任何内部标识：交点 identity / 槽位码 / 掩码都不出现在这里。
     * 详细的候选来源在悬停提示里（见 {@code StructureTooltip}）。
     */
    private static List<VirtualSpec.LabelPart> vertexLabel(LayeredBoardParts.Junction junction) {
        List<PlateRef> plates = involvedPlates(junction);
        if (plates.size() == 1) {
            // 单块板的角：用这块板自己的角名（与它的材质槽「下边 / 左边」同一套方位）
            PlateRef plate = plates.get(0);
            return List.of(facePart(plate.face()), layerPart(plate.layer()),
                    cornerPart(cornerOf(junction)));
        }
        // 多块板共享的角：参与的面 + 物理位置（形状固定为「角」）
        List<VirtualSpec.LabelPart> parts = new ArrayList<>(6);
        for (PlateRef plate : plates) {
            VirtualSpec.LabelPart face = facePart(plate.face());
            if (!parts.contains(face)) {
                parts.add(face);
            }
        }
        parts.addAll(cornerPositionParts(junction.cell()));
        return List.copyOf(parts);
    }

    /**
     * 顶点在方块里的物理位置名，例如 {@code 底·西北·角}、{@code 顶·西·角}。
     *
     * <p>三段：上下（y = 0 / 15 → 底 / 顶）、方位（x / z 贴边时的方位，两个都贴边就是
     * 东北 / 西北 / 东南 / 西南）、以及固定的「角」。
     *
     * <p>为什么上下只认 0 / 15 而不是把内层的 1 / 14 也算进来：分档越粗越容易撞名，
     * 实测把 1 / 14 并进「底 / 顶」会直接造成两个不同的顶点同名（见自检里的重名断言）。
     */
    private static List<VirtualSpec.LabelPart> cornerPositionParts(net.minecraft.util.math.Vec3i cell) {
        int x = cell.getX();
        int y = cell.getY();
        int z = cell.getZ();
        boolean xLow = x == 0;
        boolean xHigh = x == 15;
        boolean zLow = z == 0;
        boolean zHigh = z == 15;

        List<VirtualSpec.LabelPart> parts = new ArrayList<>(3);
        if (y == 0 || y == 15) {
            parts.add(positionPart("vertical." + (y == 0 ? "bottom" : "top"), y == 0 ? "底" : "顶"));
        }
        if (xLow || xHigh || zLow || zHigh) {
            parts.add(positionPart("horizontal." + horizontalName(xLow, xHigh, zLow, zHigh),
                    horizontalText(xLow, xHigh, zLow, zHigh)));
        }
        parts.add(positionPart("shape.corner", "角"));
        return List.copyOf(parts);
    }

    private static String horizontalName(boolean xLow, boolean xHigh, boolean zLow, boolean zHigh) {
        if ((xLow || xHigh) && zLow) {
            return xLow ? "northwest" : "northeast";
        }
        if ((xLow || xHigh) && zHigh) {
            return xLow ? "southwest" : "southeast";
        }
        if (zLow) {
            return "north";
        }
        if (zHigh) {
            return "south";
        }
        if (xLow) {
            return "west";
        }
        if (xHigh) {
            return "east";
        }
        return "middle";
    }

    private static String horizontalText(boolean xLow, boolean xHigh, boolean zLow, boolean zHigh) {
        return switch (horizontalName(xLow, xHigh, zLow, zHigh)) {
            case "northwest" -> "西北";
            case "northeast" -> "东北";
            case "southwest" -> "西南";
            case "southeast" -> "东南";
            case "north" -> "北";
            case "south" -> "南";
            case "west" -> "西";
            case "east" -> "东";
            default -> "中";
        };
    }

    private static VirtualSpec.LabelPart positionPart(String suffix, String fallback) {
        return new VirtualSpec.LabelPart(JUNCTION_LABEL_PREFIX + suffix, fallback);
    }

    /** 交点涉及哪些板（面 + 层），按界面顺序去重。 */
    private static List<PlateRef> involvedPlates(LayeredBoardParts.Junction junction) {
        List<PlateRef> plates = new ArrayList<>(3);
        for (FaceDir face : DISPLAY_FACES) {
            for (BoardLayer layer : BoardLayer.values()) {
                for (LayeredBoardParts.Slot candidate : junction.candidates()) {
                    if (candidate.face() == face && candidate.layer() == layer
                            && !plates.contains(new PlateRef(face, layer))) {
                        plates.add(new PlateRef(face, layer));
                    }
                }
            }
        }
        return plates;
    }

    /**
     * 单块板的角：由<b>候选边的区域</b>推回 {@link LayeredBoardSlots.BoardCorner}。
     *
     * <p>判据刻意只看「候选里出现了哪两条边」，<b>不看当前归属</b>：顶点行右边显示的可能是
     * 玩家选过的任意一条候选边，拿它去反推角名会得到错误的角（实测：左下角因为当前显示「上边」
     * 而被写成「左上角」）。所以先把候选的 { 区域 } 收集起来，再看哪一组角的两条边都被覆盖。
     *
     * <p>两条边都对不上时（理论上不该发生）退回<b>物理位置</b>：{@code cell} 就是那个顶点在
     * 方块里的坐标，用它判断是哪一类角——总能给出一个与位置相符的名字，不会给出相邻的错角。
     */
    private static LayeredBoardSlots.BoardCorner cornerOf(LayeredBoardParts.Junction junction) {
        java.util.Set<LayeredBoardSlots.BoardArea> areas = new java.util.LinkedHashSet<>();
        for (LayeredBoardParts.Slot candidate : junction.candidates()) {
            areas.add(candidate.area());
        }
        for (LayeredBoardSlots.BoardCorner corner : LayeredBoardSlots.BoardCorner.values()) {
            if (areas.contains(corner.firstArea()) && areas.contains(corner.secondArea())) {
                return corner;
            }
        }
        return cornerFromCell(junction.cell());
    }

    /**
     * 由顶点在方块内的坐标推角名，兜底用。
     *
     * <p>只比较「靠哪一边」而不是要求恰好等于 0 / 15：{@code cell} 是顶点那一格的坐标，
     * 内外层会在 1 / 14 这类位置上出现，用 {@code <= 7} 分档才对两层都成立。
     */
    private static LayeredBoardSlots.BoardCorner cornerFromCell(net.minecraft.util.math.Vec3i cell) {
        boolean lowX = cell.getX() <= 7;
        boolean lowY = cell.getY() <= 7;
        if (lowY) {
            return lowX ? LayeredBoardSlots.BoardCorner.BOTTOM_LEFT : LayeredBoardSlots.BoardCorner.BOTTOM_RIGHT;
        }
        return lowX ? LayeredBoardSlots.BoardCorner.TOP_LEFT : LayeredBoardSlots.BoardCorner.TOP_RIGHT;
    }

    /** 一块板（面 + 层）的引用。 */
    private record PlateRef(FaceDir face, BoardLayer layer) {
    }

    private static VirtualSpec.LabelPart facePart(FaceDir face) {
        return new VirtualSpec.LabelPart(JUNCTION_LABEL_PREFIX + "face." + face.getName(),
                junctionFaceText(face));
    }

    /**
     * 交点标题里的面名：底面 / 顶面 / 北面…。
     *
     * <p>刻意与结构开关那一套（下面外层 / 上面外层）用词不同、而与<b>材质槽</b>一致：
     * 交点行右边显示的就是材质槽名（「底面·外层·左边」），标题用同一套词才读得通。
     */
    private static String junctionFaceText(FaceDir face) {
        return switch (face) {
            case DOWN -> "底面";
            case UP -> "顶面";
            case NORTH -> "北面";
            case SOUTH -> "南面";
            case WEST -> "西面";
            case EAST -> "东面";
        };
    }

    private static VirtualSpec.LabelPart layerPart(BoardLayer layer) {
        return new VirtualSpec.LabelPart(JUNCTION_LABEL_PREFIX + "layer." + layer.getName(), layerText(layer));
    }

    private static VirtualSpec.LabelPart cornerPart(LayeredBoardSlots.BoardCorner corner) {
        String name = corner.name().toLowerCase(java.util.Locale.ROOT);
        return new VirtualSpec.LabelPart(JUNCTION_LABEL_PREFIX + "corner." + name,
                switch (corner) {
                    case TOP_LEFT -> "左上角";
                    case TOP_RIGHT -> "右上角";
                    case BOTTOM_LEFT -> "左下角";
                    case BOTTOM_RIGHT -> "右下角";
                });
    }

    /** 材质槽显示文本的一段（候选来源、当前归属都用它）。 */
    private static VirtualSpec.LabelPart slotLabelPart(LayeredBoardParts.Slot slot) {
        return new VirtualSpec.LabelPart(
                slotLabelKey(slot.face(), slot.layer(), slot.area()),
                slotLabelText(slot.face(), slot.layer(), slot.area()));
    }

    /** 某个交汇点在给定配置下显示的槽名：合法 override 优先，否则是几何默认归属。 */
    private static String shownSlotId(PlacementConfig config, LayeredBoardParts.Junction junction) {
        String override = junctionOwners(config).get(junction.key());
        if (override != null) {
            for (LayeredBoardParts.Slot candidate : junction.candidates()) {
                if (candidate.name().equals(override)) {
                    return override;
                }
            }
        }
        return junction.shown().name();
    }

    /** 层开关的显示名（翻译缺失时才用）：例如 {@code 北面外层}。 */
    private static String layerLabelText(FaceDir face, BoardLayer layer) {
        return faceText(face) + layerText(layer);
    }

    /** 开窗开关的显示名：例如 {@code 北面开窗}。 */
    private static String windowLabelText(FaceDir face) {
        return faceText(face) + "开窗";
    }

    private static String faceText(FaceDir face) {
        return switch (face) {
            case DOWN -> "下面";
            case UP -> "上面";
            case NORTH -> "北面";
            case SOUTH -> "南面";
            case WEST -> "西面";
            case EAST -> "东面";
        };
    }

    private static String layerText(BoardLayer layer) {
        return layer == BoardLayer.OUTER ? "外层" : "内层";
    }

    private static String areaText(BoardArea area) {
        return switch (area) {
            case BODY -> "主体";
            case TOP_EDGE -> "上边";
            case BOTTOM_EDGE -> "下边";
            case LEFT_EDGE -> "左边";
            case RIGHT_EDGE -> "右边";
            case WINDOW -> "圆窗";
        };
    }

    // ---------------------------------------------------------------- 应用

    @Override
    public void apply(PlacementContext context) {
        if (!(context.world().getBlockEntity(context.pos()) instanceof LayeredCopycatBoardBlockEntity board)) {
            return;
        }
        PlacementConfig config = context.config();
        int occupancy = occupancy(config);
        int windows = windows(config);
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
        // 结构立好之后再写交汇点归属：identity 是随占用掩码算出来的，占用变了 identity 也会变
        // （失效的那些已经被 setOccupancy 清掉了）。这里只写配置里有的那几个，
        // 不动方块实体上其它仍然有效的本地归属——与「已经伪装过的槽不动它」是同一个态度。
        for (Map.Entry<String, String> owner : junctionOwners(config).entrySet()) {
            board.setJunctionOwner(owner.getKey(), owner.getValue());
        }

        for (FaceDir face : DISPLAY_FACES) {
            for (BoardLayer layer : BoardLayer.values()) {
                if (!LayeredBoardSlots.hasSlot(occupancy, face, layer)) {
                    continue;
                }
                for (BoardArea area : LayeredBoardSlots.MATERIAL_AREAS) {
                    applySlot(context, board,
                            LayeredBoardSlots.materialKey(face, layer, area),
                            face, layer, area);
                }
            }
            // 窗材质按面共享：只要这个面开着窗就铺一次（两层都会看到同一份材质）
            if (LayeredBoardSlots.hasFace(occupancy, face) && LayeredBoardSlots.hasWindow(windows, face)) {
                applySlot(context, board, LayeredBoardSlots.windowKey(face),
                        face, BoardLayer.OUTER, BoardArea.WINDOW);
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
