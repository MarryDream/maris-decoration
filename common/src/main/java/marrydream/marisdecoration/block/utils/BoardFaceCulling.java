package marrydream.marisdecoration.block.utils;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 「一张 quad 最终落在哪里」到「它该不该带 cullFace」的判定。
 *
 * <p>两种动态模型都靠 {@code BakedModelHelper.cropAndMove} 把伪装材质模型自己的 quad 搬进自己的
 * 几何体，而这个搬运是<b>逐顶点 clamp</b>、不是三维求交——搬运之后源 quad 的 cullFace
 * <b>已经不可信</b>了：材质模型里一个「贴着方块西面」的 quad，被 clamp 进一个东侧盒子的内部平面之后，
 * 仍然带着 {@code WEST}，于是原版会去问「西边那个 BlockPos 是不是实心方块」，把方块<b>内部</b>的
 * 几何一并剔掉。所以四类方法都必须由「最终落在哪里」重新判定，绝不能让源 cullFace 活下来。
 *
 * <h2>两种情况</h2>
 * <ul>
 *   <li><b>零厚度平面盒</b>（{@code layered_copycat_board} 的 1px 板）：
 *       {@link #planeAxis} 认出所在平面，{@link #boundaryCullFace} 给结论。
 *       法线与平面平行的两个相对面会一起落在同一个平面上，带同一个 cullFace 之后不会互相打架；</li>
 *   <li><b>有厚度的盒子</b>（{@code copycat_guardrail} 的 1px 横梁、16px 角柱）：
 *       先按 {@link #isParallelTo} 找出 quad 垂直于哪个轴，再用
 *       {@link #boxCullFace} 判断那一层是不是盒子的外沿、且压在 BlockPos 边界上。</li>
 * </ul>
 *
 * <p>规则本身只有一条，六个方向完全对称：<b>只有正好贴在 BlockPos 外边界上的面才允许参与邻居
 * 遮挡剔除</b>；落在方块内部的平面（INNER 层、横梁端面、横梁与角柱的接触面、两方向之间的内部面）
 * cullFace 必须是 {@code null}。
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
    public static int planeAxis( AABB box ) {
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
     * 这张 quad 落在「有厚度的盒子」的某个完整面上时，该带哪个 cullFace。
     *
     * <p>与 {@link #boundaryCullFace} 是同一件事的两种几何：那边处理零厚度平面盒，
     * 这边处理护栏那种真正有体积的盒子（1px 横梁、16px 角柱）。规则完全一致：
     * <b>只有正好贴在 BlockPos 外边界上的那个面才允许参与邻居遮挡剔除</b>，
     * 落在方块内部的那些面（横梁的端面、横梁与角柱的接触面、两方向之间的内部面）一律
     * {@code null}。
     *
     * <p>判据只看两件事，不需要方向特判：
     * <ul>
     *   <li>quad 是否与 {@code axis} 垂直（顶点在这个轴上共面）——垂直于某个轴的源面被 clamp
     *       进盒子之后才会落在盒子的某个面上，其它朝向的源面会退化成零面积；</li>
     *   <li>{@code planeCoord}（quad 最终所在的那一层）是否等于盒子的某个外沿，且该外沿
     *       正好压在 BlockPos 的边界上。</li>
     * </ul>
     *
     * @param axis      盒子/quad 的法线轴，0 = X、1 = Y、2 = Z
     * @param planeCoord quad 最终所在那一层的坐标（盒子坐标系 0..1）
     * @param box       目标盒子
     * @return 贴在外边界时返回该方向；落在方块内部时返回 {@code null}
     */
    @Nullable
    public static Direction boxCullFace( int axis, float planeCoord, AABB box ) {
        if ( axis < 0 || axis > 2 ) {
            return null;
        }
        double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
        double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
        boolean atMin = Math.abs( planeCoord - min ) <= PLANE_EPSILON;
        boolean atMax = Math.abs( planeCoord - max ) <= PLANE_EPSILON;
        if ( atMin && atMax ) {
            // 零厚度：这一层同时是盒子的两面（平面盒就是这么被判定成外边界的）
            if ( min <= PLANE_EPSILON ) {
                return negativeOf( axis );
            }
            if ( min >= 1.0 - PLANE_EPSILON ) {
                return positiveOf( axis );
            }
            return null;
        }
        if ( atMin && min <= PLANE_EPSILON ) {
            return negativeOf( axis );
        }
        if ( atMax && max >= 1.0 - PLANE_EPSILON ) {
            return positiveOf( axis );
        }
        return null;
    }

    /** 某个轴负方向：X→WEST、Y→DOWN、Z→NORTH。 */
    private static Direction negativeOf( int axis ) {
        return axis == 0 ? Direction.WEST : axis == 1 ? Direction.DOWN : Direction.NORTH;
    }

    /** 某个轴正方向：X→EAST、Y→UP、Z→SOUTH。 */
    private static Direction positiveOf( int axis ) {
        return axis == 0 ? Direction.EAST : axis == 1 ? Direction.UP : Direction.SOUTH;
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
    public static Direction boundaryCullFace( AABB box ) {
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
