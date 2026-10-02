package marrydream.marisdecoration.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;

import java.util.ArrayList;
import java.util.List;

/** Create-style equal-sized source cropping used only by unpainted copycat geometry. */
public class DefaultMaterialGeometry {
    private static final double EPSILON = 1.0e-6;

    protected DefaultMaterialGeometry() {
    }

    public static AABB withTopSample(AABB target) {
        double height = target.maxY - target.minY;
        return new AABB(target.minX, 1.0 - height, target.minZ, target.maxX, 1.0, target.maxZ);
    }

    /**
     * Treats the central window as one independent 8x8 panel.  The panel's two
     * in-plane axes start at the matching texture edges, while its normal axis
     * remains anchored to the corresponding outer/inner face.  Using one source
     * cuboid for every emitted face is the same crop-and-move construction used
     * by Create's CopycatPanelModel: front/back and all four thin sides therefore
     * share a coherent local UV frame without scaling the texture.
     */
    public static AABB windowPanelSource(FaceDir face, AABB target) {
        PanelFrame frame = panelFrame(face);

        double[][] spans = {
                {target.minX, target.maxX},
                {target.minY, target.maxY},
                {target.minZ, target.maxZ}
        };
        spans[frame.normal().ordinal()] = edgeInterval(
                spans[frame.normal().ordinal()][0], spans[frame.normal().ordinal()][1]);
        spans[frame.uAxis().ordinal()] = panelInterval(
                spans[frame.uAxis().ordinal()][0], spans[frame.uAxis().ordinal()][1], frame.uSign());
        spans[frame.vAxis().ordinal()] = panelInterval(
                spans[frame.vAxis().ordinal()][0], spans[frame.vAxis().ordinal()][1], frame.vSign());
        return new AABB(spans[0][0], spans[1][0], spans[2][0],
                spans[0][1], spans[1][1], spans[2][1]);
    }

    /**
     * Copycats+ assembles an 8px byte from four 4px pieces instead of cropping
     * one continuous 8px source region.  Split the default window at its local
     * U/V midlines for the same reason: every quadrant can independently sample
     * the matching 0..4 or 12..16 material edge.
     */
    public static List<AABB> splitWindowPanel(FaceDir face, AABB target) {
        PanelFrame frame = panelFrame(face);
        List<AABB> pieces = new ArrayList<>(4);
        pieces.add(target);
        splitAtCentre(pieces, frame.uAxis());
        splitAtCentre(pieces, frame.vAxis());
        return pieces;
    }

    private static void splitAtCentre(List<AABB> pieces, Direction.Axis axis) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            AABB box = pieces.get(i);
            double min = min(box, axis);
            double max = max(box, axis);
            if (min >= 0.5 - EPSILON || max <= 0.5 + EPSILON)
                continue;
            pieces.remove(i);
            pieces.add(i, withSpan(box, axis, 0.5, max));
            pieces.add(i, withSpan(box, axis, min, 0.5));
        }
    }

    private static double min(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double max(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    private static AABB withSpan(AABB box, Direction.Axis axis, double min, double max) {
        return switch (axis) {
            case X -> new AABB(min, box.minY, box.minZ, max, box.maxY, box.maxZ);
            case Y -> new AABB(box.minX, min, box.minZ, box.maxX, max, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, min, box.maxX, box.maxY, max);
        };
    }

    private static PanelFrame panelFrame(FaceDir face) {
        return switch (face) {
            case UP -> new PanelFrame(Direction.Axis.Y, Direction.Axis.X, 1, Direction.Axis.Z, 1);
            case DOWN -> new PanelFrame(Direction.Axis.Y, Direction.Axis.X, 1, Direction.Axis.Z, -1);
            case NORTH -> new PanelFrame(Direction.Axis.Z, Direction.Axis.X, 1, Direction.Axis.Y, 1);
            case SOUTH -> new PanelFrame(Direction.Axis.Z, Direction.Axis.X, -1, Direction.Axis.Y, 1);
            case WEST -> new PanelFrame(Direction.Axis.X, Direction.Axis.Z, 1, Direction.Axis.Y, 1);
            case EAST -> new PanelFrame(Direction.Axis.X, Direction.Axis.Z, -1, Direction.Axis.Y, 1);
        };
    }

    private record PanelFrame(Direction.Axis normal, Direction.Axis uAxis, int uSign,
                              Direction.Axis vAxis, int vSign) {
    }

    private static double[] panelInterval(double min, double max, int sign) {
        double size = max - min;
        double localMin = sign > 0 ? min : 1.0 - max;
        double localMax = sign > 0 ? max : 1.0 - min;

        double sourceLocalMin;
        double sourceLocalMax;
        if (size <= EPSILON) {
            double edge = localMin < 0.5 ? 0.0 : 1.0;
            sourceLocalMin = edge;
            sourceLocalMax = edge;
        } else if (localMax <= 0.5 + EPSILON) {
            // LEFT / BOTTOM: sample from the minimum end of the material axis.
            sourceLocalMin = 0.0;
            sourceLocalMax = size;
        } else if (localMin >= 0.5 - EPSILON) {
            // RIGHT / TOP: sample from the maximum end of the material axis.
            sourceLocalMin = 1.0 - size;
            sourceLocalMax = 1.0;
        } else {
            // The 8x8 main face spans the centre and keeps the small-panel origin.
            sourceLocalMin = 0.0;
            sourceLocalMax = size;
        }

        return sign > 0
                ? new double[]{sourceLocalMin, sourceLocalMax}
                : new double[]{1.0 - sourceLocalMax, 1.0 - sourceLocalMin};
    }

    private static double[] edgeInterval(double min, double max) {
        double size = max - min;
        if (size <= EPSILON) {
            double edge = min < 0.5 ? 0.0 : 1.0;
            return new double[]{edge, edge};
        }
        if (max <= 0.5 + EPSILON) return new double[]{0.0, size};
        if (min >= 0.5 - EPSILON) return new double[]{1.0 - size, 1.0};
        return new double[]{min, max};
    }
}
