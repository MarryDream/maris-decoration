package marrydream.marisdecoration.client;
import net.minecraft.world.phys.AABB;
public record UvProjection(int firstAxis, int secondAxis,
                                float uS, float uT, float uC, float vS, float vT, float vC) {
        public static UvProjection from(QuadCoordinates quad, int planeAxis) {
            int first = planeAxis == 0 ? 1 : 0;
            int second = planeAxis == 2 ? 1 : 2;
            if (planeAxis == 1) second = 2;

            for (int b = 1; b < 3; b++) {
                for (int c = b + 1; c < 4; c++) {
                    float s0 = coordinate(quad, 0, first), t0 = coordinate(quad, 0, second);
                    float sb = coordinate(quad, b, first), tb = coordinate(quad, b, second);
                    float sc = coordinate(quad, c, first), tc = coordinate(quad, c, second);
                    float determinant = (sb - s0) * (tc - t0) - (sc - s0) * (tb - t0);
                    if (Math.abs(determinant) < 1.0e-6f) continue;
                    float[] u = affine(s0, t0, quad.u(0), sb, tb, quad.u(b), sc, tc, quad.u(c), determinant);
                    float[] v = affine(s0, t0, quad.v(0), sb, tb, quad.v(b), sc, tc, quad.v(c), determinant);
                    return new UvProjection(first, second, u[0], u[1], u[2], v[0], v[1], v[2]);
                }
            }
            return new UvProjection(first, second, 0, 0, quad.u(0), 0, 0, quad.v(0));
        }

        public void remap(QuadCoordinates quad, AABB geometry, AABB sample) {
            double minS = boxMin(geometry, firstAxis), minT = boxMin(geometry, secondAxis);
            double sizeS = boxMax(geometry, firstAxis) - minS, sizeT = boxMax(geometry, secondAxis) - minT;
            if (sizeS <= 0 || sizeT <= 0) return;
            for (int vertex = 0; vertex < 4; vertex++) {
                float s = (float) (boxMin(sample, firstAxis)
                        + (coordinate(quad, vertex, firstAxis) - minS) / sizeS
                        * (boxMax(sample, firstAxis) - boxMin(sample, firstAxis)));
                float t = (float) (boxMin(sample, secondAxis)
                        + (coordinate(quad, vertex, secondAxis) - minT) / sizeT
                        * (boxMax(sample, secondAxis) - boxMin(sample, secondAxis)));
                quad.uv(vertex, uS * s + uT * t + uC, vS * s + vT * t + vC);
            }
        }

        private static float[] affine(float s0, float t0, float q0, float s1, float t1, float q1,
                                      float s2, float t2, float q2, float determinant) {
            float a = ((q1 - q0) * (t2 - t0) - (q2 - q0) * (t1 - t0)) / determinant;
            float b = ((s1 - s0) * (q2 - q0) - (s2 - s0) * (q1 - q0)) / determinant;
            return new float[]{a, b, q0 - a * s0 - b * t0};
        }

        private static float coordinate(QuadCoordinates quad, int vertex, int axis) {
            return switch (axis) {
                case 0 -> quad.x(vertex);
                case 1 -> quad.y(vertex);
                default -> quad.z(vertex);
            };
        }

        private static double boxMin(AABB box, int axis) {
            return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
        }

        private static double boxMax(AABB box, int axis) {
            return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
        }
    }
