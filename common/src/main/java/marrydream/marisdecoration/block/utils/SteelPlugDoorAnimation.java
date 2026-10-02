package marrydream.marisdecoration.block.utils;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared local-space geometry for the animated leaf of the base steel plug door. */
public final class SteelPlugDoorAnimation {
    public static final double THICKNESS = 2.0 / 16.0;
    private static final double HALF_THICKNESS = THICKNESS / 2.0;
    private static final double EPSILON = 1.0E-5;

    private SteelPlugDoorAnimation() {
    }

    /** The threshold/lintel remains in the chunk model while the leaf is rendered by the BER. */
    public static AABB fixedFrameBox(Direction facing, DoubleBlockHalf half) {
        double minY = half == DoubleBlockHalf.LOWER ? 0.0 : 15.0 / 16.0;
        double maxY = half == DoubleBlockHalf.LOWER ? 1.0 / 16.0 : 1.0;
        return SteelPlugDoorRoof.toWorld(new AABB(0.0, minY, 0.0, THICKNESS, maxY, 1.0), facing);
    }

    public static boolean contains(AABB box, double x, double y, double z) {
        return x >= box.minX - EPSILON && x <= box.maxX + EPSILON
                && y >= box.minY - EPSILON && y <= box.maxY + EPSILON
                && z >= box.minZ - EPSILON && z <= box.maxZ + EPSILON;
    }

    /** EAST is the unrotated JSON-model direction. */
    public static float facingAngle(Direction facing) {
        return -90.0F * SteelPlugDoorRoof.quarterTurns(facing);
    }

    /** The axis runs through the centre of the 2px hinge corner shared by both final poses. */
    public static Vec3 hingePivot(DoorHingeSide hinge) {
        return new Vec3(HALF_THICKNESS, 0.0,
                hinge == DoorHingeSide.LEFT ? HALF_THICKNESS : 1.0 - HALF_THICKNESS);
    }

    public static float smoothProgress(float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    public static float swingAngle(DoorHingeSide hinge, float progress) {
        float sign = hinge == DoorHingeSide.LEFT ? 1.0F : -1.0F;
        return sign * 90.0F * smoothProgress(progress);
    }

    /** Fixed beam caps are only needed once the moving leaf has left the closed seam. */
    public static boolean shouldRenderFrameCaps(float progress) {
        return progress > 1.0E-4F;
    }

    public static boolean shouldRenderUpperFrameCap(float progress, boolean hasRoof) {
        return !hasRoof && shouldRenderFrameCaps(progress);
    }

    /** Identifies the threshold top or lintel underside after the quad is known to belong to the frame. */
    public static boolean isInnerFrameCap(DoubleBlockHalf half,
                                          double y0, double y1, double y2, double y3) {
        double capY = half == DoubleBlockHalf.LOWER ? 1.0 / 16.0 : 15.0 / 16.0;
        return Math.abs(y0 - capY) <= EPSILON
                && Math.abs(y1 - capY) <= EPSILON
                && Math.abs(y2 - capY) <= EPSILON
                && Math.abs(y3 - capY) <= EPSILON;
    }

    /** Mirrors the renderer transform for endpoint and facing regression tests. */
    public static Vec3 transform(Vec3 canonical, Direction facing, DoorHingeSide hinge, float progress) {
        Vec3 pivot = hingePivot(hinge);
        double angle = Math.toRadians(swingAngle(hinge, progress));
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double dx = canonical.x - pivot.x;
        double dz = canonical.z - pivot.z;
        Vec3 swung = new Vec3(
                pivot.x + cos * dx + sin * dz,
                canonical.y,
                pivot.z - sin * dx + cos * dz);
        return SteelPlugDoorRoof.toWorld(swung, facing);
    }
}
