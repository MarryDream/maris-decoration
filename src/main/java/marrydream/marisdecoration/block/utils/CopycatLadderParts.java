package marrydream.marisdecoration.block.utils;

import marrydream.marisdecoration.block.VerticalLadderBlock;
import marrydream.marisdecoration.block.enums.PropLadderShape;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact material-slot geometry shared by copycat ladder hit testing and rendering. */
public final class CopycatLadderParts {
    public static final String MATERIAL = "material";
    public static final String SUPPORT = "support";
    public static final String RUNG = "rung";

    private static final double P = 1.0 / 16.0;

    private CopycatLadderParts() {
    }

    public static List<String> fixedSlots() {
        return List.of(MATERIAL);
    }

    public static List<String> verticalSlots() {
        return List.of(SUPPORT, RUNG);
    }

    public static Map<String, List<Box>> fixedBoxes(BlockState state) {
        List<Box> boxes = List.of(
                box(13, 12, 2, 16, 13, 3), box(13, 12, 13, 16, 13, 14), box(13, 12, 3, 14, 13, 13),
                box(13, 4, 2, 16, 5, 3), box(13, 4, 13, 16, 5, 14), box(13, 4, 3, 14, 5, 13));
        return Map.of(MATERIAL, rotate(boxes, state.get(Properties.HORIZONTAL_FACING)));
    }

    /** Equal-sized source regions used only by the unpainted copycat-base texture. */
    public static Map<String, List<Box>> fixedDefaultUvBoxes(BlockState state) {
        List<Box> boxes = List.of(
                box(13, 15, 0, 16, 16, 1), box(13, 15, 15, 16, 16, 16), box(15, 15, 3, 16, 16, 13),
                box(13, 0, 0, 16, 1, 1), box(13, 0, 15, 16, 1, 16), box(15, 0, 3, 16, 1, 13));
        return Map.of(MATERIAL, rotate(boxes, state.get(Properties.HORIZONTAL_FACING)));
    }

    public static Map<String, List<Box>> verticalBoxes(BlockState state) {
        List<Box> supports = new ArrayList<>();
        supports.add(box(13, 0, 2, 14, 16, 3));
        supports.add(box(13, 0, 13, 14, 16, 14));
        if (state.get(VerticalLadderBlock.SHAPE) == PropLadderShape.START) {
            supports.add(box(14, 15, 2, 16, 16, 3));
            supports.add(box(14, 15, 13, 16, 16, 14));
        }
        List<Box> rungs = List.of(box(13, 12, 3, 14, 13, 13), box(13, 4, 3, 14, 5, 13));
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        Map<String, List<Box>> result = new LinkedHashMap<>();
        result.put(SUPPORT, rotate(supports, facing));
        result.put(RUNG, rotate(rungs, facing));
        return result;
    }

    /** Equal-sized source regions used only by the unpainted copycat-base texture. */
    public static Map<String, List<Box>> verticalDefaultUvBoxes(BlockState state) {
        List<Box> supports = new ArrayList<>();
        supports.add(box(15, 0, 0, 16, 16, 1));
        supports.add(box(15, 0, 15, 16, 16, 16));
        if (state.get(VerticalLadderBlock.SHAPE) == PropLadderShape.START) {
            supports.add(box(14, 15, 0, 16, 16, 1));
            supports.add(box(14, 15, 15, 16, 16, 16));
        }
        List<Box> rungs = List.of(box(15, 15, 3, 16, 16, 13), box(15, 0, 3, 16, 1, 13));
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        Map<String, List<Box>> result = new LinkedHashMap<>();
        result.put(SUPPORT, rotate(supports, facing));
        result.put(RUNG, rotate(rungs, facing));
        return result;
    }

    public static String verticalSlotAt(BlockState state, Vec3d localHit) {
        Vec3d westLocal = unrotate(localHit, state.get(Properties.HORIZONTAL_FACING));
        double e = 1.0e-4;
        if (between(westLocal.z, 2 * P, 3 * P, e) || between(westLocal.z, 13 * P, 14 * P, e))
            return SUPPORT;
        if (between(westLocal.z, 3 * P, 13 * P, e))
            return RUNG;
        return null;
    }

    private static boolean between(double value, double min, double max, double tolerance) {
        return value >= min - tolerance && value <= max + tolerance;
    }

    /** Converts a hit point from the placed facing back into the WEST-authored model space. */
    private static Vec3d unrotate(Vec3d point, Direction facing) {
        int turns = switch (facing) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
            default -> 0;
        };
        Vec3d result = point;
        for (int i = 0; i < turns; i++)
            result = new Vec3d(result.z, result.y, 1.0 - result.x);
        return result;
    }

    private static Box box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Box(minX * P, minY * P, minZ * P, maxX * P, maxY * P, maxZ * P);
    }

    private static List<Box> rotate(List<Box> boxes, Direction facing) {
        int turns = switch (facing) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
            default -> 0;
        };
        List<Box> result = new ArrayList<>(boxes.size());
        for (Box original : boxes) {
            Box rotated = original;
            for (int i = 0; i < turns; i++) {
                rotated = new Box(1.0 - rotated.maxZ, rotated.minY, rotated.minX,
                        1.0 - rotated.minZ, rotated.maxY, rotated.maxX);
            }
            result.add(rotated);
        }
        return result;
    }
}
