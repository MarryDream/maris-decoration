package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 伪装护栏（{@code maris-decoration:copycat_guardrail}）的 adapter。
 *
 * <h2>结构模型：四个面开关 + 四个角柱开关</h2>
 * 界面上是八行独立的开关：
 * <ul>
 *   <li><b>四个面</b>：北 / 东 / 南 / 西，一直显示；</li>
 *   <li><b>四个角柱</b>：东北 / 西北 / 东南 / 西南，<b>按当前开着的面动态出现</b>
 *       ——一个角柱只在与它相邻的两个面至少有一个存在时才有几何。</li>
 * </ul>
 * 内部存储仍然是两个 4 位掩码（{@link #facesMask} / {@link #cornersMask}），因为方块自身的
 * 表达就是「四个方向布尔」，而「哪些角柱存在」正好也是四个位置。掩码是<b>实现细节</b>：
 * 界面上一行一个开关，永远不会出现「16 种朝向组合」那种要玩家自己认的组合项。
 *
 * <p>为什么原来的组合枚举不行：16 个候选里玩家只想要「北面 + 东面」时得先找到那一项，
 * 想再加一个面又要换另一项；而角柱根本没法塞进「朝向组合」里——角柱的有无不是「面的组合」，
 * 是「这个角要不要立柱」，两个方向的面都关掉之后它还应该有自己的记忆。
 *
 * <h2>角柱的语义</h2>
 * 角柱开关为「开」= 这个角上<b>有</b>柱子；为「关」= 这个角上没有柱子。
 * 实现直接复用方块实体已有的隐藏角柱集合（{@code hidden_columns}，细工凿用的就是它），
 * 所以渲染、材质、隐藏行为与「细工凿藏起来一根柱子」完全一致：柱子不画，
 * 相邻横梁把预留的 1px 补齐（那是护栏本来就有几何规则，不是这一层新加的）。
 *
 * <h2>材质槽：随结构动态变化</h2>
 * 只列当前结构下<b>真的有几何</b>的槽（{@link GuardrailParts#visibleKeys(BlockState, Set)}）：
 * 北面关掉就没有「北横梁」，东北角柱关掉就没有「东北角柱」。判据与渲染同一份，不会出现
 * 「列出来却什么都看不见」的槽。
 */
public final class GuardrailCopycatAdapter implements CopycatPlacementAdapter {

    /** 结构属性：四个方向的 4 位掩码。 */
    public static final String STRUCTURE_FACES = PlacementConfig.Key.GUARDRAIL_FACES;
    /** 结构属性：四个角柱的 4 位掩码（1 = 有柱子）。 */
    public static final String STRUCTURE_CORNERS = PlacementConfig.Key.GUARDRAIL_CORNERS;

    /** 没配结构属性时的默认朝向：正北（掩码就是 NORTH 的位）。 */
    public static final int DEFAULT_FACES = CopycatGuardrailBlock.bit(Direction.NORTH);
    /** 没配角柱掩码时的默认：四个角柱都在。 */
    public static final int DEFAULT_CORNERS = 0xF;

    /**
     * 四个角柱。顺序固定为东北、西北、东南、西南（界面按这个顺序显示）。
     *
     * <p>{@code columnKey} 是方块实体与 {@link GuardrailParts} 用的角点身份
     * （{@code <x>_<z>}，单位 1/16），不是这里另立的编号——角柱的位置只有一份定义。
     * {@code adjacent} 是「哪些面存在时这个角柱才有意义」：角柱在几何上属于两个面的交点。
     */
    public enum Corner {
        NORTHEAST("northeast", "15_0", Direction.NORTH, Direction.EAST),
        NORTHWEST("northwest", "0_0", Direction.NORTH, Direction.WEST),
        SOUTHEAST("southeast", "15_15", Direction.SOUTH, Direction.EAST),
        SOUTHWEST("southwest", "0_15", Direction.SOUTH, Direction.WEST);

        private final String name;
        private final String columnKey;
        private final Direction first;
        private final Direction second;

        Corner(String name, String columnKey, Direction first, Direction second) {
            this.name = name;
            this.columnKey = columnKey;
            this.first = first;
            this.second = second;
        }

        /** 小写名字，用于翻译键。 */
        public String getName() {
            return name;
        }

        /** 角点身份，与 {@link GuardrailParts#columnKeys()} 中的键名逐字符一致。 */
        public String columnKey() {
            return columnKey;
        }

        /**
         * 这个角柱在当前朝向配置下有没有意义。
         *
         * <p>规则（按需求）：只要相邻两个面<b>之一</b>存在，就显示这个角柱项。
         */
        public boolean relevant(int facesMask) {
            return CopycatGuardrailBlock.maskHas(facesMask, first)
                    || CopycatGuardrailBlock.maskHas(facesMask, second);
        }

        /** 这个角柱在掩码里的位。 */
        public int bit() {
            return 1 << ordinal();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String LABEL_PREFIX = "maris-decoration.copycat_placer.slot.guardrail.";
    private static final String GROUP_PREFIX = "maris-decoration.copycat_placer.group.guardrail.";
    /** 结构项（面 / 角柱开关）的翻译键前缀。 */
    private static final String STRUCTURE_LABEL_PREFIX = "item.maris-decoration.copycat_placer.structure.guardrail.";
    /** 横梁一组、柱子一组，GUI 折叠成两个节点。 */
    private static final String GROUP_ROW = GROUP_PREFIX + "row";
    private static final String GROUP_COLUMN = GROUP_PREFIX + "column";

    @Override
    public boolean supports(Block block) {
        return block instanceof CopycatGuardrailBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_OWN_CUSTOM;
    }

    @Override
    public String name() {
        return "maris-decoration:copycat_guardrail";
    }

    // ---------------------------------------------------------------- 结构：读取

    /** 预设里的方向掩码（0..15）。没配或配坏时是 {@link #DEFAULT_FACES}。 */
    public static int facesMask(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_FACES, 4, DEFAULT_FACES,
                GuardrailCopycatAdapter::faceNameToMask);
    }

    /** 预设里的角柱掩码（0..15）。没配或配坏时是「四个角柱都在」。 */
    public static int cornersMask(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_CORNERS, 4, DEFAULT_CORNERS,
                GuardrailCopycatAdapter::cornerNameToMask);
    }

    /** 某一个面开着吗。 */
    public static boolean faceEnabled(PlacementConfig config, Direction face) {
        return CopycatGuardrailBlock.maskHas(facesMask(config), face);
    }

    /** 某一个角柱开着吗。 */
    public static boolean cornerEnabled(PlacementConfig config, Corner corner) {
        return (cornersMask(config) & corner.bit()) != 0;
    }

    /**
     * 这个角柱存在吗——「角柱开关 = 开」<b>且</b>「相邻两个面之一存在」。
     *
     * <p>两个条件都要：相邻面都关掉时，那个位置本来就没有柱子，掩码里那一位没有意义
     * （界面也不显示这一项）。
     */
    public static boolean cornerPresent(PlacementConfig config, Corner corner) {
        return cornerEnabled(config, corner) && corner.relevant(facesMask(config));
    }

    /**
     * 这个结构下应当被隐藏的角柱（写进方块实体的 {@code hidden_columns}）。
     *
     * <p>就是「开关为关」的那几个角。相邻面都不存在的位置本来就没有柱子，隐藏与否都不影响几何，
     * 所以无需特判。
     */
    public static Set<String> hiddenColumns(PlacementConfig config) {
        int corners = cornersMask(config);
        Set<String> hidden = new LinkedHashSet<>();
        for (Corner corner : Corner.values()) {
            if ((corners & corner.bit()) == 0) {
                hidden.add(corner.columnKey());
            }
        }
        return hidden;
    }

    // ---------------------------------------------------------------- 结构：写入

    /** 写回方向掩码。 */
    public static PlacementConfig withFaces(PlacementConfig config, int mask) {
        return StructureMasks.with(config, STRUCTURE_FACES, mask, 4);
    }

    /** 写回角柱掩码。 */
    public static PlacementConfig withCorners(PlacementConfig config, int mask) {
        return StructureMasks.with(config, STRUCTURE_CORNERS, mask, 4);
    }

    /**
     * 开关一个面。
     *
     * <p><b>只动这一位</b>：其它面、以及四个角柱开关全部原样保留。角柱的开关值与非它相邻的面
     * 无关，所以关闭一个面之后重新打开，之前对各个角柱做过的选择都还在
     * （界面上「隐藏—再出现」也不会重置，见 {@link #cornerEnabled}）。
     */
    public static PlacementConfig withFace(PlacementConfig config, Direction face, boolean enabled) {
        int mask = facesMask(config);
        return withFaces(config, enabled
                ? mask | CopycatGuardrailBlock.bit(face)
                : mask & ~CopycatGuardrailBlock.bit(face));
    }

    /** 开关一个角柱；同样只动这一位，四个角柱的值互相独立。 */
    public static PlacementConfig withCorner(PlacementConfig config, Corner corner, boolean enabled) {
        int mask = cornersMask(config);
        return withCorners(config, enabled ? mask | corner.bit() : mask & ~corner.bit());
    }

    /**
     * 旧配置里的「名字」形式 → 位掩码。
     *
     * <p>更早的界面用组合枚举表示朝向，配置里可能留下 {@code north}、{@code north+east}、
     * {@code all}、{@code none} 这类值。它们的语义是明确的，读取时换算回等价的掩码，
     * 不整份作废、也不显示成垃圾。
     */
    private static Integer faceNameToMask(String name) {
        return switch (name) {
            case "none", "empty" -> 0;
            case "all", "full" -> 0xF;
            case "north" -> CopycatGuardrailBlock.bit(Direction.NORTH);
            case "east" -> CopycatGuardrailBlock.bit(Direction.EAST);
            case "south" -> CopycatGuardrailBlock.bit(Direction.SOUTH);
            case "west" -> CopycatGuardrailBlock.bit(Direction.WEST);
            default -> null;
        };
    }

    /** 角柱掩码的名字形式（{@code all} / {@code none} / 四个角名）。 */
    private static Integer cornerNameToMask(String name) {
        return switch (name) {
            case "none", "empty" -> 0;
            case "all", "full" -> 0xF;
            case "northeast" -> Corner.NORTHEAST.bit();
            case "northwest" -> Corner.NORTHWEST.bit();
            case "southeast" -> Corner.SOUTHEAST.bit();
            case "southwest" -> Corner.SOUTHWEST.bit();
            default -> null;
        };
    }

    @Override
    public BlockState stateFrom(BlockState state, PlacementConfig config) {
        if (!(state.getBlock() instanceof CopycatGuardrailBlock)) {
            return state;
        }
        int mask = facesMask(config);
        // 从「四面全 false」出发，再把掩码选中的置位——与方块自己的 getPlacementState 同一套做法，
        // 避免默认状态里预置的方向混进来。
        BlockState result = state;
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            result = result.setValue(CopycatGuardrailBlock.PROPERTY_BY_DIRECTION.get(dir),
                    CopycatGuardrailBlock.maskHas(mask, dir));
        }
        return result;
    }

    // ---------------------------------------------------------------- 槽位

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        BlockState shaped = stateFrom(state, config);
        // 当前结构下真的有几何的槽：方向关掉 → 没有那根横梁；角柱关掉 → 没有那根柱子。
        // 柱子按角点存，所以「哪些柱子存在」只能按几何算（共享柱由别的方向补画），这里直接复用渲染那份。
        Set<String> visible = new LinkedHashSet<>(
                GuardrailParts.visibleKeys(shaped, hiddenColumns(config)));

        List<AdapterSlot> slots = new ArrayList<>(GuardrailParts.allKeys().size());
        int index = 0;
        for (String key : GuardrailParts.allKeys()) {
            // 前四个是横梁，后四个是柱子（见 GuardrailParts#allKeys 的实现）
            boolean isRow = index < CopycatGuardrailBlock.FACES.size();
            index++;
            String group = isRow ? GROUP_ROW : GROUP_COLUMN;
            slots.add(AdapterSlot.of(key, LABEL_PREFIX + key, group, visible.contains(key),
                    config.slots().get(key)));
        }
        return slots;
    }

    // ---------------------------------------------------------------- 结构项（GUI）

    /**
     * 八行结构开关：四个面（一直显示）+ 四个角柱（按当前开着的面动态显示）。
     *
     * <p>它还声明接管了 {@code north/east/south/west} 四个方块状态属性：那四个属性与这里的
     * 四个面开关表达的是同一件事，GUI 只保留一个入口（见 {@link VirtualSpec#managedProperties()}）。
     */
    @Override
    public List<VirtualSpec> virtualSpecs(PlacementConfig config) {
        List<VirtualSpec> specs = new ArrayList<>(8);
        int faces = facesMask(config);
        for (Direction face : CopycatGuardrailBlock.FACES) {
            // 四个面开关接管了方块状态里的同名布尔属性：同一个结构不能有两套编辑入口。
            specs.add(VirtualSpec.toggle(
                    "face." + face.getName(),
                    List.of(VirtualSpec.LabelPart.of(STRUCTURE_LABEL_PREFIX + "face." + face.getName(),
                            faceLabelText(face))),
                    current -> faceEnabled(current, face),
                    (current, value) -> withFace(current, face, value))
                    .managing(List.of(face.getName())));
        }
        for (Corner corner : Corner.values()) {
            // 动态出现：只在与它相邻的两个面之一存在时才给这一项。
            // 没有出现的角柱，它那一位开关值仍然留在配置里，再次出现时原样带回来。
            if (!corner.relevant(faces)) {
                continue;
            }
            specs.add(VirtualSpec.toggle(
                    "corner." + corner.getName(),
                    List.of(VirtualSpec.LabelPart.of(STRUCTURE_LABEL_PREFIX + "corner." + corner.getName(),
                            cornerLabelText(corner))),
                    current -> cornerEnabled(current, corner),
                    (current, value) -> withCorner(current, corner, value)));
        }
        return List.copyOf(specs);
    }

    /** 播放器可见的兜底文本（翻译缺失时才用）。 */
    private static String faceLabelText(Direction face) {
        return switch (face) {
            case NORTH -> "北面";
            case EAST -> "东面";
            case SOUTH -> "南面";
            case WEST -> "西面";
            default -> face.getName();
        };
    }

    private static String cornerLabelText(Corner corner) {
        return switch (corner) {
            case NORTHEAST -> "东北角柱";
            case NORTHWEST -> "西北角柱";
            case SOUTHEAST -> "东南角柱";
            case SOUTHWEST -> "西南角柱";
        };
    }

    /**
     * 默认配置：<b>只要正北一面，四个角柱都在</b>。
     *
     * <p>不能用方块默认状态——那样四个方向全 false，是一个<b>零部件的护栏</b>：放下去占着格子
     * 却什么都看不见，再想放别的东西还会提示「这里放不下」。所以默认给一个真正有几何的形态。
     */
    @Override
    public PlacementConfig defaultConfig(Block block) {
        return withCorners(withFaces(PlacementConfig.of(block.defaultBlockState()), DEFAULT_FACES),
                DEFAULT_CORNERS);
    }

    /** 至少一个方向存在，否则就是那个看不见的幽灵方块。 */
    @Override
    public String validateStructure(BlockState state, PlacementConfig config) {
        if (facesMask(config) == 0) {
            return "guardrail_no_face";
        }
        return null;
    }

    /** 护栏的材质准入直接复用方块自己那份判据（Create 的 copycat_allow/deny + 完整立方体检查）。 */
    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config, BlockGetter world, BlockPos pos) {
        return CopycatGuardrailBlock.getAcceptedMaterial(world, pos,
                new ItemStack(material.getBlock()), null) != null;
    }

    @Override
    public void apply(PlacementContext context) {
        if (!(context.world().getBlockEntity(context.pos()) instanceof CopycatGuardrailBlockEntity blockEntity)) {
            return;
        }
        Set<String> hidden = hiddenColumns(context.config());
        // 先立结构：角柱开关就是对「隐藏角柱集合」的一次完整写入。
        blockEntity.setHiddenColumns(hidden);
        for (String key : GuardrailParts.visibleKeys(context.state(), hidden)) {
            BlockState material = context.material(key);
            if (material == null) {
                continue;
            }
            // 已经有材质的槽不动它（右键 / 上一次放置留下的都算）
            if (blockEntity.hasMaterial(key)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
                continue;
            }
            // 服务端准入校验：不合法的材质留空，不阻止放置（客户端过滤只是 UX）
            if (!context.acceptsMaterial(material, key)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
                continue;
            }
            // 同一个 BlockPos 上同一种材质只付一次：已经付过就只写材质、不记账。
            // 拿不出这个材质就留空，不阻止放置。
            boolean alreadyPaid = context.paid().contains(material);
            if (!context.claim(material)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
                continue;
            }
            blockEntity.setMaterial(key, material,
                    alreadyPaid ? null : new ItemStack(material.getBlock()));
            context.markApplied(key);
        }
    }
}
