package marrydream.marisdecoration.block.utils;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Coordinate transform shared by steel plug door roof hit-testing and rendering. */
public final class SteelPlugDoorRoof {
    private static final double EPSILON = 1.0E-5;
    public static final double MIN_Y = 15.0 / 16.0;

    private SteelPlugDoorRoof() {
    }

    /** Existing door JSON uses EAST as the unrotated model. */
    public static int quarterTurns(Direction facing) {
        return switch (facing) {
            case EAST -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;
        };
    }

    /** Converts a block-local world hit into the unrotated EAST-facing roof frame. */
    public static Vec3 toCanonical(Vec3 local, Direction facing) {
        double x = local.x;
        double z = local.z;
        for (int i = 0; i < quarterTurns(facing); i++) {
            double nextX = z;
            z = 1.0 - x;
            x = nextX;
        }
        return new Vec3(x, local.y, z);
    }

    public static Vec3 toWorld(Vec3 canonical, Direction facing) {
        double x = canonical.x;
        double z = canonical.z;
        for (int i = 0; i < quarterTurns(facing); i++) {
            double nextX = 1.0 - z;
            z = x;
            x = nextX;
        }
        return new Vec3(x, canonical.y, z);
    }

    /** Rotates a canonical roof render box into the door's facing. */
    public static AABB toWorld(AABB box, Direction facing) {
        AABB result = box;
        for (int i = 0; i < quarterTurns(facing); i++) {
            result = new AABB(1.0 - result.maxZ, result.minY, result.minX,
                    1.0 - result.minZ, result.maxY, result.maxX);
        }
        return result;
    }

    public static boolean isRoofHit(Vec3 local) {
        return local.x >= -EPSILON && local.x <= 1.0 + EPSILON
                && local.z >= -EPSILON && local.z <= 1.0 + EPSILON
                && local.y >= MIN_Y - EPSILON && local.y <= 1.0 + EPSILON;
    }

    /** True when an original door-model quad is wholly covered by the inserted roof. */
    public static boolean isCoveredDoorQuad(double y0, double y1, double y2, double y3) {
        double threshold = MIN_Y - EPSILON;
        return y0 >= threshold && y1 >= threshold && y2 >= threshold && y3 >= threshold;
    }
}
