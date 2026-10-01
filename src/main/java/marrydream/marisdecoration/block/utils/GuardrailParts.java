package marrydream.marisdecoration.block.utils;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code copycat_guardrail} 的几何数据。
 *
 * <p>几何固定为一根贴边立柱和三根横梁：
 * 一根贴边立柱 + 三根横梁。<b>不使用</b> inner/outer 拐角模型，也不涉及自动连接。
 *
 * <p>角柱归属规则：单面模型自带两根端柱，一根在它「拥有」的角落，一根与相邻方向
 * 「共享」。若四向直接叠加，共享角落会出现两份完全重合的几何体（z-fighting）。
 * 因此：
 * <ul>
 *   <li>自有柱：本方向存在即绘制；</li>
 *   <li>共享柱：本方向存在，且该角落的归属方向不存在时才绘制。</li>
 * </ul>
 * 角落归属：东北→北、东南→东、西南→南、西北→西。
 *
 * <p>这样既保证四面全放时四角各只有一根柱，也保证只放单面、或只放北+南时不会缺柱。
 */
public final class GuardrailParts {

    /** 伪装材质槽位：柱 与 横梁。 */
    public enum Slot {
        COLUMN,
        ROW
    }

    public record Part(Box box, Slot slot) {
    }

    /** 一个方向贡献的几何。 */
    private record FaceParts(Part[] rails, Part owned, Part shared) {
    }

    private static final Map<Direction, FaceParts> PARTS = new EnumMap<>(Direction.class);
    /** 本方向的共享柱所在角落，归属哪个方向。 */
    private static final Map<Direction, Direction> SHARED_OWNER = new EnumMap<>(Direction.class);
    /** 16 种方块状态的选取/碰撞箱，按位掩码缓存。 */
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    /**
     * 单个方向的选取箱：该面的完整 16×16 范围。
     *
     * <p>模型本身只有 1/16 厚、且三根横梁之间有竖直空隙。若碰撞与选取箱严格贴合模型，
     * 实际很难点中。这里按「完整一面」给出整面体积，点该面任意位置都能选中。
     */
    private static Box facePickBox(Direction dir) {
        return switch (dir) {
            case NORTH -> box(0, 0, 0, 16, 16, 1);
            case SOUTH -> box(0, 0, 15, 16, 16, 16);
            case WEST -> box(0, 0, 0, 1, 16, 16);
            case EAST -> box(15, 0, 0, 16, 16, 16);
            default -> box(0, 0, 0, 16, 16, 16);
        };
    }

    private GuardrailParts() {
    }

    private static Box box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new Box(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    private static Part column(Box b) {
        return new Part(b, Slot.COLUMN);
    }

    /** 三根横梁，x/z 为贴边范围，y 固定在 5、10、15。 */
    private static Part[] rails(double x0, double z0, double x1, double z1) {
        Part[] out = new Part[3];
        for (int i = 0; i < 3; i++) {
            double y = 5 + i * 5;
            out[i] = new Part(box(x0, y, z0, x1, y + 1, z1), Slot.ROW);
        }
        return out;
    }

    static {
        // 基准来自 guardrail/straight.json（facing=east，不旋转）：
        //   自有柱 = 南端柱（东南角），共享柱 = 北端柱（东北角）
        PARTS.put(Direction.EAST, new FaceParts(
                rails(15, 1, 16, 15),
                column(box(15, 0, 15, 16, 16, 16)),
                column(box(15, 0, 0, 16, 16, 1))));
        SHARED_OWNER.put(Direction.EAST, Direction.NORTH);

        // facing=south（y=90）
        PARTS.put(Direction.SOUTH, new FaceParts(
                rails(1, 15, 15, 16),
                column(box(0, 0, 15, 1, 16, 16)),
                column(box(15, 0, 15, 16, 16, 16))));
        SHARED_OWNER.put(Direction.SOUTH, Direction.EAST);

        // facing=west（y=180）
        PARTS.put(Direction.WEST, new FaceParts(
                rails(0, 1, 1, 15),
                column(box(0, 0, 0, 1, 16, 1)),
                column(box(0, 0, 15, 1, 16, 16))));
        SHARED_OWNER.put(Direction.WEST, Direction.SOUTH);

        // facing=north（y=270）
        PARTS.put(Direction.NORTH, new FaceParts(
                rails(1, 0, 15, 1),
                column(box(15, 0, 0, 16, 16, 1)),
                column(box(0, 0, 0, 1, 16, 1))));
        SHARED_OWNER.put(Direction.NORTH, Direction.WEST);

        for (int mask = 0; mask < 16; mask++) {
            // 选取箱用「每个存在方向的完整一面」，而不是贴合模型，否则很难点中
            VoxelShape shape = VoxelShapes.empty();
            for (Direction dir : CopycatGuardrailBlock.FACES) {
                if (CopycatGuardrailBlock.maskHas(mask, dir)) {
                    shape = VoxelShapes.union(shape, VoxelShapes.cuboid(facePickBox(dir)));
                }
            }
            SHAPES[mask] = shape;
        }
    }

    /**
     * 一次命中定位的结果：命中哪个方向的护栏、以及是柱还是横梁。
     *
     * <p>{@code columnKey} 只在 {@code slot == COLUMN} 时非空，是这根柱子的身份，见
     * {@link #columnKey(Box)}。
     */
    public record Hit(Direction face, Slot slot, @Nullable String columnKey) {
    }

    /**
     * 柱子的身份键：它所在的角点，写成 {@code <x>_<z>}（单位 1/16，取值 0 或 15）。
     *
     * <p>为什么用角点而不是「哪个方向贡献了这根柱」：同一根角柱在几何上可能由两个方向画出来
     * ——自有柱，或者归属方向缺席时由相邻方向的共享柱补上。柱子是<b>位置</b>上的实体，隐藏状态
     * 必须跟着位置走；若跟着方向走，同一个角换个方向画出来就会「自己恢复」，或者隐藏了却看不出效果。
     */
    public static String columnKey(Box box) {
        return Math.round(box.minX * 16) + "_" + Math.round(box.minZ * 16);
    }

    /** 横梁槽位键名：每个方向一根，例如 {@code north_row}。 */
    public static String rowKey(Direction face) {
        return face.asString() + "_row";
    }

    /**
     * 柱子槽位键名：四个角点各一个，就是 {@link #columnKey(Box)} 给出的角点身份
     * （{@code 0_0} / {@code 15_0} / {@code 15_15} / {@code 0_15}）。
     *
     * <p>柱子<b>刻意不按方向归属</b>。一个角点在几何上可能由两个方向之一画出来——自有柱，
     * 或者归属方向缺席时由相邻方向的共享柱补上。若按方向存材质，「这根共享柱到底用哪一面的柱材质」
     * 就没有答案，而且 NORTH + EAST 都存一份就会存出两份互相打架的材质。按角点存则天然只有一份，
     * 共享它的两个方向看到的是同一根柱子、同一份材质。
     */
    public static List<String> columnKeys() {
        List<String> keys = new ArrayList<>(4);
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            String key = columnKey(PARTS.get(dir).owned().box());
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** 一个部件对应的材质槽键名：横梁按方向，柱子按角点。 */
    public static String materialKey(Direction face, Part part) {
        return part.slot() == Slot.COLUMN ? columnKey(part.box()) : rowKey(face);
    }

    /** 一次命中对应的材质槽键名。 */
    public static String materialKey(Hit hit) {
        return hit.slot() == Slot.COLUMN ? hit.columnKey() : rowKey(hit.face());
    }

    /**
     * 全部 8 个槽位键名：4 根横梁（按方向）+ 4 根柱子（按角点）。
     *
     * <p>键名同时是渲染数据的键，两边必须保持一致。
     */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>(8);
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            keys.add(rowKey(dir));
        }
        keys.addAll(columnKeys());
        return keys;
    }

    /**
     * 当前状态下每个槽位键对应的几何体盒子。
     *
     * <p>渲染层按这个分组：同一种材质的所有盒子共用一次材质模型发射，再把每个面裁剪进各自的盒子。
     */
    public static Map<String, List<Box>> boxesByKey(BlockState state, Set<String> hiddenColumns) {
        int mask = CopycatGuardrailBlock.maskOf(state);
        Map<String, List<Box>> out = new LinkedHashMap<>();
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            for (Part part : directionParts(mask, dir, hiddenColumns)) {
                out.computeIfAbsent(materialKey(dir, part), unused -> new ArrayList<>()).add(part.box());
            }
        }
        return out;
    }

    /**
     * 当前状态下<b>真的有几何</b>的那些材质槽键，顺序与 {@link #boxesByKey} 的出现顺序一致。
     *
     * <p>给「伪装放置器」用：它按预设一次性铺材质，需要知道「哪些槽写了才看得见」。
     * 判据不能是「这个方向存不存在」——共享柱只在归属方向缺席时才由相邻方向补画，
     * 所以某个柱子的槽是否可见要按真实几何算。这里直接复用 {@link #boxesByKey}，
     * 不另写一份规则，渲染与放置因此永远一致。
     *
     * <p>{@code hiddenColumns} 传空集：隐藏柱是「细工凿」的本地状态，
     * 与「这个槽在几何上存不存在」无关——被藏起来的柱子照样应该能贴材质。
     */
    public static List<String> visibleKeys(BlockState state) {
        return visibleKeys(state, Set.of());
    }

    /**
     * 同上，但把 {@code hiddenColumns} 里的角柱也算进去。
     *
     * <p>给「伪装放置器」用：它在结构配置里可以关掉某几根角柱（关掉的角柱不画、也没有材质槽），
     * 判据必须与渲染完全一致，所以这里直接复用 {@link #boxesByKey}。
     */
    public static List<String> visibleKeys(BlockState state, Set<String> hiddenColumns) {
        return new ArrayList<>(boxesByKey(state, hiddenColumns).keySet());
    }

    /** 某个方向在本状态下实际可见的几何；{@code hiddenColumns} 里的角柱不参与。 */
    private static List<Part> directionParts(int mask, Direction dir, Set<String> hiddenColumns) {
        if (!CopycatGuardrailBlock.maskHas(mask, dir)) {
            return List.of();
        }
        FaceParts fp = PARTS.get(dir);
        List<Part> out = new ArrayList<>(5);
        for (Part rail : railsFor(fp, dir, mask, hiddenColumns)) {
            out.add(rail);
        }
        out.add(fp.owned());
        // 共享柱：只有归属方向缺席时才补画，避免与对方重合。
        if (!CopycatGuardrailBlock.maskHas(mask, SHARED_OWNER.get(dir))) {
            out.add(fp.shared());
        }
        if (!hiddenColumns.isEmpty()) {
            out.removeIf(part -> part.slot() == Slot.COLUMN && hiddenColumns.contains(columnKey(part.box())));
        }
        return out;
    }

    /**
     * 横梁当前的范围：两端各预留的 1px，在那一端的角柱被隐藏时由<b>横梁自己</b>补上。
     *
     * <p>模型是按「两端都有柱子」设计的，横梁只占长轴上的 [1,15]，两端各留 1px 给柱子。
     * 柱子被细工凿藏起来后这 1px 就空了，同一排相邻的护栏之间会露出一道缝，所以横梁要顶到方块边界：
     * 藏低端 → 1 变 0，藏高端 → 15 变 16，两端都藏就是 [0,16]。
     *
     * <p><b>由谁补</b>：一个角落会被两根横梁同时触及（比如西北角同时属于北面和西面），
     * 如果两根都补，那 1px 立方体上会压两份完全重合的几何体，产生 z-fighting。所以补位沿用角柱已有的
     * 归属规则，保证「一定补上，且只补一次」：
     * <ul>
     *   <li>自有一端：这个角归本方向，柱子一藏就由本方向的横梁补；</li>
     *   <li>共享一端：这个角归 {@link #SHARED_OWNER}，归属方向在场时由它的自有一端去补，本方向不补。</li>
     * </ul>
     * 归属方向不在场时，本方向就是唯一能补的那一根，照补不误。
     */
    private static Part[] railsFor(FaceParts fp, Direction dir, int mask, Set<String> hiddenColumns) {
        Part[] rails = fp.rails();
        if (hiddenColumns.isEmpty()) {
            return rails;
        }
        // 三根横梁的 x/z 范围完全一样，只有 y 不同，取第一根当模板。
        // 长轴 = 跨度 14/16 的那一轴，另一轴只有 1/16 厚。
        Box template = rails[0].box();
        boolean alongX = (template.maxX - template.minX) > (template.maxZ - template.minZ);

        boolean fillLow = endNeedsFilling(fp, dir, mask, alongX, true, hiddenColumns);
        boolean fillHigh = endNeedsFilling(fp, dir, mask, alongX, false, hiddenColumns);
        if (!fillLow && !fillHigh) {
            return rails;
        }

        // 把预留的那 1px 吃回来：低端 1 → 0，高端 15 → 16
        double low = fillLow ? 0.0 : (alongX ? template.minX : template.minZ);
        double high = fillHigh ? 1.0 : (alongX ? template.maxX : template.maxZ);

        Part[] out = new Part[rails.length];
        for (int i = 0; i < rails.length; i++) {
            Box b = rails[i].box();
            out[i] = new Part(alongX
                    ? new Box(low, b.minY, b.minZ, high, b.maxY, b.maxZ)
                    : new Box(b.minX, b.minY, low, b.maxX, b.maxY, high),
                    Slot.ROW);
        }
        return out;
    }

    /** 长轴某一端那根角柱是否被隐藏了、并且这一端该由本方向的横梁来补。 */
    private static boolean endNeedsFilling(FaceParts fp, Direction dir, int mask,
                                           boolean alongX, boolean lowEnd, Set<String> hiddenColumns) {
        Box owned = fp.owned().box();
        // 角柱要么贴在低端 [0,1/16]，要么贴在高端 [15/16,1]，据此认出这一端是不是自有一端
        boolean ownedAtLow = (alongX ? owned.minX : owned.minZ) == 0.0;
        boolean ownedEnd = ownedAtLow == lowEnd;

        // 共享端：归属方向在场时，那个角由归属方向的自有一端去补，本方向补了就是两份重合几何
        if (!ownedEnd && CopycatGuardrailBlock.maskHas(mask, SHARED_OWNER.get(dir))) {
            return false;
        }
        return hiddenColumns.contains(columnKey(ownedEnd ? owned : fp.shared().box()));
    }

    /** 按位掩码取出可见几何（顺序：北、东、南、西；每个方向先横梁后角柱）。 */
    private static List<Part> partsFor(int mask, Set<String> hiddenColumns) {
        List<Part> out = new ArrayList<>();
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            out.addAll(directionParts(mask, dir, hiddenColumns));
        }
        return out;
    }

    public static List<Part> parts(BlockState state, Set<String> hiddenColumns) {
        return partsFor(CopycatGuardrailBlock.maskOf(state), hiddenColumns);
    }

    /**
     * 选取 / 碰撞箱。
     *
     * <p>刻意<b>不</b>随隐藏的柱子变化：这个箱是「面级」的整面箱（见 {@link #facePickBox}），
     * 只要那个方向还在就一直成立，而隐藏柱子并不会移除方向——一个面永远还有三根横梁。
     * 真正需要跟状态一致的是「点到了哪个部件」，那由 {@link #partAt} 负责跳过隐藏的柱子。
     */
    public static VoxelShape shape(BlockState state) {
        return SHAPES[CopycatGuardrailBlock.maskOf(state)];
    }

    /** 点到长方体的最短距离平方（点在内部时为 0）。 */
    private static double distanceSquared(Box b, Vec3d v) {
        double dx = Math.max(Math.max(b.minX - v.x, 0.0), v.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - v.y, 0.0), v.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - v.z, 0.0), v.z - b.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 判断命中点落在哪一个部件上——既给出方向，也给出柱/横梁。
     *
     * <p>各部件在几何上互不重叠，取距离最近的那个即可稳定判定。
     * 注意选取箱是「完整一面」，命中点在整面表面上，所以这里比的到实际几何体的距离，
     * 而不是到选取箱的距离：打面中段会落到横梁上，打两端 1/16 范围才落到柱子上。
     *
     * <p>隐藏的柱子已经不在可见几何里，因此也点不中——点到原来柱子的位置会落到最近的横梁上。
     */
    @Nullable
    public static Hit partAt(BlockState state, Set<String> hiddenColumns, Vec3d hit) {
        int mask = CopycatGuardrailBlock.maskOf(state);
        Hit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            for (Part part : directionParts(mask, dir, hiddenColumns)) {
                double d = distanceSquared(part.box(), hit);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new Hit(dir, part.slot(),
                            part.slot() == Slot.COLUMN ? columnKey(part.box()) : null);
                }
            }
        }
        return best;
    }
}
