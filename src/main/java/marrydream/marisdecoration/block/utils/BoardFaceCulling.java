package marrydream.marisdecoration.block.utils;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 「一张 quad 最终落在哪里」到「它该不该带 cullFace」的判定。
 *
 * <p>分层伪装薄板的几何全部来自 {@link LayeredBoardParts#boxesByKey}，输出的是<b>零厚度的平面盒</b>。
 * 渲染时材质模型自己的 quad 会被 {@code BakedModelHelper.cropAndMove} 搬进这些平面里，
 * 而这个搬运是<b>逐顶点 clamp</b>、不是三维求交：顶点被夹进盒子之后，法线与平面不平行的源面
 * 会被压成一条零面积的线，而法线与平面平行的两个相对面（材质的 up / down，或 north / south、
 * west / east）会<b>一起落在同一个平面上</b>。
 *
 * <p>所以最终 quad 的 cullFace 不能沿用源材质模型的 cullFace——crop / move 之后源 quad 的几何
 * 位置已经不可信了。规则只有两条，六个方向完全对称：
 *
 * <ul>
 *   <li>这张 quad 所在的平面正好贴在 BlockPos 某个外边界上 → 它是贴着邻居的，带上该方向的
 *       cullFace，交给原版正常的邻居遮挡剔除。零厚度平面让材质的两个相对面共面重叠，带上同
 *       一个方向之后，只要有完整方块遮挡就会一起被剔除，不会再互相打架；邻居是空气时谁都
 *       不会被剔除，背面照常可见。</li>
 *   <li>平面在方块内部（INNER 层、方块内部平面、窗内壁）→ cullFace 必须是 {@code null}，
 *       否则原版会去问「那个方向的相邻 BlockPos 是不是实心方块」，把内部几何一并剔掉。</li>
 * </ul>
 *
 * <p>放在 main 侧是为了让无客户端环境下的代码级验证能直接调用这一份实现，而不是复制一遍。
 */
public final class BoardFaceCulling {

    /** 平面厚度容差：真实几何是 1/16 的整数倍，这个值只要不被浮点误差骗到就够。 */
    public static final float PLANE_EPSILON = 1.0E-6F;

    /** 顶点数组长度：4 个顶点 × (x, y, z)。 */
    public static final int VERTEX_FLOATS = 12;

    private BoardFaceCulling( ) {
    }

    /**
     * 这个盒子所在平面的法线轴：0 = X、1 = Y、2 = Z。
     *
     * <p>不是「恰好一个轴零厚度」的平面盒就返回 {@code -1}（调用方按「无法判定」处理）。
     */
    public static int planeAxis( Box box ) {
        boolean flatX = box.maxX - box.minX <= PLANE_EPSILON;
        boolean flatY = box.maxY - box.minY <= PLANE_EPSILON;
        boolean flatZ = box.maxZ - box.minZ <= PLANE_EPSILON;
        if ( flatY && !flatX && !flatZ ) {
            return 1;
        }
        if ( flatX && !flatY && !flatZ ) {
            return 0;
        }
        if ( flatZ && !flatX && !flatY ) {
            return 2;
        }
        return -1;
    }

    /**
     * 这张 quad 是否与给定法线轴的平面平行。
     *
     * <p>平行才可能被 clamp 出一块有面积的四边形；不平行的一定退化成零面积，画不出任何像素。
     *
     * @param vertices 4 个顶点的 {@code x, y, z}，共 {@value #VERTEX_FLOATS} 个数
     */
    public static boolean isParallelTo( float[] vertices, int axis ) {
        if ( axis < 0 || axis > 2 || vertices.length < VERTEX_FLOATS ) {
            return true;
        }
        float first = vertices[axis];
        for ( int vertex = 1; vertex < 4; vertex++ ) {
            if ( Math.abs( vertices[vertex * 3 + axis] - first ) > PLANE_EPSILON ) {
                return false;
            }
        }
        return true;
    }

    /**
     * 这张 quad 自己所在平面的法线轴；四个顶点在三个轴上都不重合就返回 {@code -1}。
     *
     * <p>只用于验证与调试，渲染路径用的是 {@link #isParallelTo}。
     */
    public static int quadPlaneAxis( float[] vertices ) {
        for ( int axis = 0; axis < 3; axis++ ) {
            if ( isParallelTo( vertices, axis ) ) {
                return axis;
            }
        }
        return -1;
    }

    /**
     * 这个平面正好贴在 BlockPos 某个外边界上时返回该方向；在方块内部返回 {@code null}。
     *
     * <p>六个方向同一套规则，没有方向特判：{@code 0} 那一侧给负方向、{@code 1} 那一侧给正方向。
     */
    @Nullable
    public static Direction boundaryCullFace( Box box ) {
        int axis = planeAxis( box );
        if ( axis == 0 ) {
            if ( box.minX <= PLANE_EPSILON ) {
                return Direction.WEST;
            }
            if ( box.minX >= 1.0 - PLANE_EPSILON ) {
                return Direction.EAST;
            }
            return null;
        }
        if ( axis == 1 ) {
            if ( box.minY <= PLANE_EPSILON ) {
                return Direction.DOWN;
            }
            if ( box.minY >= 1.0 - PLANE_EPSILON ) {
                return Direction.UP;
            }
            return null;
        }
        if ( axis == 2 ) {
            if ( box.minZ <= PLANE_EPSILON ) {
                return Direction.NORTH;
            }
            if ( box.minZ >= 1.0 - PLANE_EPSILON ) {
                return Direction.SOUTH;
            }
            return null;
        }
        return null;
    }
}
