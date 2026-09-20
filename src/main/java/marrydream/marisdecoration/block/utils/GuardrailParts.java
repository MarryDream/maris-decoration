package marrydream.marisdecoration.block.utils;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code copycat_guardrail} 的几何数据。
 *
 * <p>几何全部取自 black_steel_guardrail 的单面模型 {@code block/guardrail/straight.json}：
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

    /** 某个方向在当前状态下实际可见的几何。 */
    private static List<Part> directionParts(int mask, Direction dir) {
        if (!CopycatGuardrailBlock.maskHas(mask, dir)) {
            return List.of();
        }
        FaceParts fp = PARTS.get(dir);
        List<Part> out = new ArrayList<>(List.of(fp.rails()));
        out.add(fp.owned());
        // 共享柱：只有归属方向缺席时才补画，避免与对方重合。
        if (!CopycatGuardrailBlock.maskHas(mask, SHARED_OWNER.get(dir))) {
            out.add(fp.shared());
        }
        return out;
    }

    /** 按位掩码取出可见几何（顺序：北、东、南、西；每个方向先横梁后角柱）。 */
    private static List<Part> partsFor(int mask) {
        List<Part> out = new ArrayList<>();
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            out.addAll(directionParts(mask, dir));
        }
        return out;
    }

    /** 命中点落在哪个方向的几何上；用于扳手拆除单个面。 */
    public static Direction faceAt(BlockState state, Vec3d hit) {
        int mask = CopycatGuardrailBlock.maskOf(state);
        Direction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            for (Part part : directionParts(mask, dir)) {
                double d = distanceSquared(part.box(), hit);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = dir;
                }
            }
        }
        return best;
    }

    public static List<Part> parts(BlockState state) {
        return partsFor(CopycatGuardrailBlock.maskOf(state));
    }

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
     * 判断右键点到的是柱还是横梁。
     *
     * <p>角柱与横梁在几何上互不重叠，因此取距离最近的部件即可稳定判定。
     */
    public static Slot slotAt(BlockState state, Vec3d hit) {
        Slot best = Slot.ROW;
        double bestDistance = Double.MAX_VALUE;
        for (Part part : parts(state)) {
            double d = distanceSquared(part.box(), hit);
            if (d < bestDistance) {
                bestDistance = d;
                best = part.slot();
            }
        }
        return best;
    }
}
