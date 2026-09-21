package marrydream.marisdecoration.block.utils;

import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardArea;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardCorner;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code layered_copycat_board} 的几何数据。
 *
 * <p>与 {@link GuardrailParts} 一样，几何<b>不由模型文件描述</b>：这里按占用掩码算出一批
 * 长方体盒子，交给客户端把伪装材质模型逐面裁剪进这些盒子（见 {@code LayeredCopycatBoardModel}）。
 *
 * <h2>面本地坐标</h2>
 * 每个面用一个 16×16 的本地坐标系 {@code (u, v)} 描述：
 * <ul>
 *   <li>UP / DOWN：{@code u} 沿 X、{@code v} 沿 Z（UP 的 y=15..16、DOWN 的 y=0..1 等由规格锁定）；</li>
 *   <li>四个侧面：{@code v = +Y}（所以 TOP 永远是世界的上方），{@code u} 取从方块外面看过去的
 *       右手方向（{@code u = up × outward}，于是 NORTH 看过去 RIGHT 在东方、EAST 看过去 RIGHT 在北方）。</li>
 * </ul>
 * 于是「BODY / 四条 1×14 的边 / 四个 1×1 的角」在六个面上是同一套分割，和方向无关，
 * 可以在类初始化时算一次（{@link #AREAS}）。
 *
 * <h2>重叠与剔除</h2>
 * 每个「面 + 层」是一块 1px 厚的板，相邻两块板在方块角上会互相穿插，直接都画就会共面重叠
 * （z-fighting）。这里把整块切成 16³ 的格子，再按<b>内侧那一格属于本板、紧邻的外侧格子为空</b>
 * 这个条件挑出真正暴露的面，最后把同一平面上的相邻格子合并成矩形——细节见 {@link #boxesByKey}。
 */
public final class LayeredBoardParts {

    /** 一个「面 + 层」的身份。 */
    public record FaceLayer( FaceDir face, BoardLayer layer ) {
    }

    /** 一次几何命中：点在哪个面的哪一层，以及属于哪个区域。 */
    public record Hit( FaceDir face, BoardLayer layer, BoardArea area ) {
    }

    /** 面本地的两个轴。{@code sign} 为 {@code -1} 表示沿该轴的反方向递增。 */
    private record LocalFrame( Direction.Axis uAxis, int uSign, Direction.Axis vAxis, int vSign ) {
        int u( Vec3d point ) {
            return axis( point, uAxis, uSign );
        }

        int v( Vec3d point ) {
            return axis( point, vAxis, vSign );
        }

        private static int axis( Vec3d point, Direction.Axis axis, int sign ) {
            double value = switch ( axis ) {
                case X -> point.x;
                case Y -> point.y;
                case Z -> point.z;
            };
            // 两侧都要向下取整：格子 i 覆盖 [i/16, (i+1)/16)，朝负方向时映射成 15-i。
            double scaled = sign > 0 ? Math.floor( value * 16.0 ) : Math.ceil( value * 16.0 ) - 1.0;
            return (int) ( sign > 0 ? scaled : 15 - scaled );
        }
    }

    private static final Map<FaceDir, LocalFrame> FRAMES = new LinkedHashMap<>();

    static {
        // 水平面：规格直接给出了 u/v 与 X/Z 的对应（UP 的 y=15..16、DOWN 的 y=0..1 等）。
        FRAMES.put( FaceDir.UP, new LocalFrame( Direction.Axis.X, 1, Direction.Axis.Z, 1 ) );
        FRAMES.put( FaceDir.DOWN, new LocalFrame( Direction.Axis.X, 1, Direction.Axis.Z, -1 ) );
        // 侧面：v 恒为 +Y，u = up × outward。
        FRAMES.put( FaceDir.NORTH, new LocalFrame( Direction.Axis.X, 1, Direction.Axis.Y, 1 ) );
        FRAMES.put( FaceDir.SOUTH, new LocalFrame( Direction.Axis.X, -1, Direction.Axis.Y, 1 ) );
        FRAMES.put( FaceDir.WEST, new LocalFrame( Direction.Axis.Z, 1, Direction.Axis.Y, 1 ) );
        FRAMES.put( FaceDir.EAST, new LocalFrame( Direction.Axis.Z, -1, Direction.Axis.Y, 1 ) );
    }

    /**
     * 窗是面中央固定的 <b>8×8 正方形</b>（u = 4..12、v = 4..12），左上角落在这个偏移处。
     *
     * <p>规则：{@code window=false} 时这块区域属于 BODY；{@code window=true} 时才变成 WINDOW。
     * 所以 {@link #AREAS} 里不预存窗，由 {@link #areaAt} 按当前窗状态现算。
     */
    private static final int WINDOW_ORIGIN = 4;

    /** 这个面本地格 (u, v) 是否落在中央 8×8 窗范围内。 */
    private static boolean isWindowCell( int u, int v ) {
        return u >= WINDOW_ORIGIN && u < WINDOW_ORIGIN + 8
                && v >= WINDOW_ORIGIN && v < WINDOW_ORIGIN + 8;
    }

    /**
     * 同一块板里两个不同 region 抢同一个接触面时，谁负责发那张面。
     *
     * <p>只区分「是不是板的主体」就够：BODY 与 WINDOW / EDGE 接触时由 BODY 发。
     * 窗用玻璃时这一点是必须的——否则洞口内壁会归到透明的那一侧，看起来就是内壁消失。
     */
    private static int priorityOf( BoardArea area ) {
        return area == BoardArea.BODY ? 1 : 0;
    }

    /** 每格属于哪个区域（角已落到具体的边上），<b>不含窗</b>。 */
    private static final BoardArea[][] AREAS = areaMap( );

    /** 四个角格子的两条相邻边，顺序与 {@link BoardCorner} 的构造参数一致（即 owner bit 的语义）。 */
    private static final Map<BoardCorner, BoardArea[]> CORNER_AREAS = Map.of(
            BoardCorner.TOP_RIGHT, new BoardArea[] { BoardArea.TOP_EDGE, BoardArea.RIGHT_EDGE },
            BoardCorner.BOTTOM_RIGHT, new BoardArea[] { BoardArea.RIGHT_EDGE, BoardArea.BOTTOM_EDGE },
            BoardCorner.BOTTOM_LEFT, new BoardArea[] { BoardArea.BOTTOM_EDGE, BoardArea.LEFT_EDGE },
            BoardCorner.TOP_LEFT, new BoardArea[] { BoardArea.LEFT_EDGE, BoardArea.TOP_EDGE } );

    /** 角格子的面本地坐标 → 是哪个角。 */
    private static final Map<Integer, BoardCorner> CORNER_AT = Map.of(
            key( 15, 0 ), BoardCorner.TOP_RIGHT,
            key( 15, 15 ), BoardCorner.BOTTOM_RIGHT,
            key( 0, 15 ), BoardCorner.BOTTOM_LEFT,
            key( 0, 0 ), BoardCorner.TOP_LEFT );

    /**
     * 12 位占用掩码 → 选取 / 碰撞箱，<b>按需构建</b>。
     *
     * <p>这里刻意不做「4096 种全部预构建」：那会在类初始化时一次性拼 4096 个 {@link VoxelShape}
     * （每个还要做 12 次 union），第一次放置触发类初始化时会把主线程卡住很多秒。
     * 方块实体侧已经按掩码缓存了结果，所以每个实际出现过的掩码最多算一次。
     */
    private static final Map<Integer, VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();


    private LayeredBoardParts( ) {
    }

    private static int key( int u, int v ) {
        return u * 16 + v;
    }

    // ---------------------------------------------------------------- 区域分割（与状态无关，只算一次）

    private static BoardArea[][] areaMap( ) {
        BoardArea[][] areas = new BoardArea[16][16];
        for ( int u = 0; u < 16; u++ ) {
            for ( int v = 0; v < 16; v++ ) {
                areas[u][v] = BoardArea.BODY;
            }
        }
        // 四条 1×14 的边（去掉角）
        for ( int u = 1; u < 15; u++ ) {
            areas[u][0] = BoardArea.TOP_EDGE;
            areas[u][15] = BoardArea.BOTTOM_EDGE;
        }
        for ( int v = 1; v < 15; v++ ) {
            areas[0][v] = BoardArea.LEFT_EDGE;
            areas[15][v] = BoardArea.RIGHT_EDGE;
        }
        // 四个 1×1 的角先取默认归属的那条边，实际取材质时再按 owner bit 覆盖
        areas[15][0] = BoardCorner.TOP_RIGHT.defaultArea();
        areas[15][15] = BoardCorner.BOTTOM_RIGHT.defaultArea();
        areas[0][15] = BoardCorner.BOTTOM_LEFT.defaultArea();
        areas[0][0] = BoardCorner.TOP_LEFT.defaultArea();
        return areas;
    }

    /** 某个角格子在本层实际显示哪条边的材质。 */
    private static BoardArea cornerArea( BoardCorner corner, long[] cornerOwners, FaceDir face, BoardLayer layer ) {
        BoardArea[] candidates = CORNER_AREAS.get( corner );
        return candidates[LayeredBoardSlots.cornerOwner( cornerOwners, face, layer, corner ) == 0 ? 0 : 1];
    }

    // ---------------------------------------------------------------- 板与箱

    /** 一块板在方块本地 0..1 坐标下的包围盒。 */
    public static Box plateBox( FaceDir face, BoardLayer layer ) {
        boolean outer = layer == BoardLayer.OUTER;
        return switch ( face ) {
            case UP -> box( 0, outer ? 15 : 14, 0, 16, outer ? 16 : 15, 16 );
            case DOWN -> box( 0, outer ? 0 : 1, 0, 16, outer ? 1 : 2, 16 );
            case NORTH -> box( 0, 0, outer ? 0 : 1, 16, 16, outer ? 1 : 2 );
            case SOUTH -> box( 0, 0, outer ? 15 : 14, 16, 16, outer ? 16 : 15 );
            case WEST -> box( outer ? 0 : 1, 0, 0, outer ? 1 : 2, 16, 16 );
            case EAST -> box( outer ? 15 : 14, 0, 0, outer ? 16 : 15, 16, 16 );
        };
    }

    private static Box box( double x0, double y0, double z0, double x1, double y1, double z1 ) {
        return new Box( x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0 );
    }

    private static VoxelShape buildShape( int mask ) {
        VoxelShape shape = VoxelShapes.empty();
        for ( FaceDir face : FaceDir.values() ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( LayeredBoardSlots.hasSlot( mask, face, layer ) ) {
                    shape = VoxelShapes.union( shape, VoxelShapes.cuboid( plateBox( face, layer ) ) );
                }
            }
        }
        return shape;
    }

    /** 选取 / 碰撞箱。窗是填充不是洞，所以箱只由占用掩码决定。 */
    public static VoxelShape shape( int occupancy ) {
        int mask = occupancy & LayeredBoardSlots.FULL_OCCUPANCY;
        return SHAPES.computeIfAbsent( mask, LayeredBoardParts::buildShape );
    }

    /**
     * 某个槽在指定轴上的区间中心（0..1）。
     *
     * <p>放置时用「槽到点击面的距离」挑最近的那个空槽，于是「点哪一侧就在哪一侧长板」，
     * 四条侧面走的是同一个对称算法；而同一个轴连续加厚时，按距离排序给出的顺序和原来的
     * 「先对侧 OUTER、再对侧 INNER」完全一致，已经正常的四槽连放不会变。
     */
    public static double slotCenter( FaceDir face, BoardLayer layer, Direction.Axis axis ) {
        Box b = plateBox( face, layer );
        return switch ( axis ) {
            case X -> ( b.minX + b.maxX ) * 0.5;
            case Y -> ( b.minY + b.maxY ) * 0.5;
            case Z -> ( b.minZ + b.maxZ ) * 0.5;
        };
    }

    // ---------------------------------------------------------------- 栅格

    private static boolean inside( int x, int y, int z ) {
        return x >= 0 && x < 16 && y >= 0 && y < 16 && z >= 0 && z < 16;
    }

    private static int index( int x, int y, int z ) {
        if ( !inside( x, y, z ) ) {
            return -1;
        }
        return ( x * 16 + y ) * 16 + z;
    }

    /**
     * 面本地 (u, v) → 世界格子 (x, y, z)。
     *
     * <p>每块板占法线轴上的一个 1px 格：OUTER 贴边界、INNER 再向内一格。
     * 另外两个轴由 {@link #FRAMES} 的本地坐标系映射过去。
     */
    private static int[] worldCell( FaceDir face, BoardLayer layer, int u, int v ) {
        LocalFrame frame = FRAMES.get( face );
        int along = layer == BoardLayer.OUTER ? 0 : 1;
        int a = frame.uSign() > 0 ? u : 15 - u;
        int b = frame.vSign() > 0 ? v : 15 - v;
        return switch ( face ) {
            // UP 的 OUTER 贴 y=15..16、INNER 在 y=14..15；DOWN 的 OUTER 贴 y=0..1、INNER 在 y=1..2；
            // 四个侧面同理，统一写成「层本身的格子坐标」，OUTER 与 INNER 互为镜像。
            case UP -> new int[] { a, along == 0 ? 15 : 14, b };
            case DOWN -> new int[] { a, along == 0 ? 0 : 1, b };
            case NORTH -> new int[] { a, b, along == 0 ? 0 : 1 };
            case SOUTH -> new int[] { a, b, along == 0 ? 15 : 14 };
            case WEST -> new int[] { along == 0 ? 0 : 1, b, a };
            case EAST -> new int[] { along == 0 ? 15 : 14, b, a };
        };
    }
    // ---------------------------------------------------------------- 渲染几何

    /** 一个格子面所在的平面：法线轴 + 该轴上的格子坐标（0..16）+ 朝外的方向。 */
    private record PlaneKey( Direction.Axis axis, int along, Direction facing ) {
    }


    /**
     * 按状态算出「材质槽键名 → 该槽要画的盒子」。
     *
     * <p>先把 12 块板栅格化进 16³ 的格子空间，再逐格子逐方向判断这个格子面是不是整块的外表面：
     * <ol>
     *   <li>反向（朝板内）的那一格<b>被同一块板占据</b>——这条把两块板贴在一起时重复的那一面挑出来。
     *       例如 NORTH 板（z 0..1）与 UP 板（y 15..16）在方块角穿插，y=15 这个平面上 NORTH 板的
     *       顶面与 UP 板的底面完全重合；UP 板在 y=15 的内侧格子是空的，于是 UP 不认这个面，
     *       只有 NORTH 认——共面重叠被消掉，且<b>不需要任何全局方向优先级</b>；</li>
     *   <li>顺着这个方向<b>紧邻的外侧格子为空</b>——否则这块面被别的板顶住了。</li>
     * </ol>
     *
     * <p>最后按「平面 + 材质」分组做一次贪心矩形合并，把 16 个 1×1 的格子面并成一个大面。
     * 同一平面上不可能有两个不同材质互相重叠（重叠的那一格只能归一块板），所以按材质分开
     * 就天然不会把不同材质的格子并进同一个矩形。
     */
    /**
     * 按状态算出「材质槽键名 → 该槽要画的盒子」。
     *
     * <p>先把 12 块板栅格化进 16³ 的格子空间（{@link #grid} 的每一位就是一块板），
     * 再逐板逐像素判断它在六个方向上哪些面是<b>暴露</b>的。判据只有一条：
     *
     * <blockquote>
     * 像素 (u, v) 在 {@code direction} 那一侧的面属于本板 ⟺ 它朝那一侧的<b>外侧格子是空的</b>。
     * </blockquote>
     *
     * <p>这一条同时消掉了两类共面重叠：
     * <ul>
     *   <li>两块板<b>垂直穿插、抢同一格</b>时（例如 NORTH 板与 UP 板在方块角），某一侧的外侧格子
     *       被对方占着，于是那一侧的面被丢弃——每个外表面恰好由「外侧是空」的那块板发射一次；</li>
     *   <li>两块板<b>同轴贴合</b>时（同一面的 OUTER + INNER），接触面两侧都有板，两侧的面都被丢弃，
     *       只在最外和最内两个平面各留一份。</li>
     * </ul>
     * 全程<b>不需要任何全局方向优先级</b>，也不依赖两板之间的先后顺序。
     *
     * <p>最后按「平面 + 材质」分组做一次贪心矩形合并，把 16 个 1×1 的格子面并成一个大面。
     */
    public static Map<String, List<Box>> boxesByKey( int occupancy, int windows, long[] cornerOwners ) {
        // ① 栅格化：每个格子记住有哪些板占据它，以及这一格的材质槽是谁的
        int[] grid = new int[16 * 16 * 16];
        String[] keys = new String[16 * 16 * 16];
        // 每一格属于哪个 region。② 阶段要用它裁决「同板不同 region 的接触面由谁发」。
        BoardArea[] areas = new BoardArea[16 * 16 * 16];
        for ( FaceDir face : FaceDir.values() ) {
            // 窗是面中央固定的 8×8 正方形，并且只在「这个面开着窗」时才是独立材质槽；
            // 关着的时候那块区域就是 BODY——否则会把材质写进一个当前根本不存在、渲染也不读的窗槽。
            boolean window = LayeredBoardSlots.hasWindow( windows, face );
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( !LayeredBoardSlots.hasSlot( occupancy, face, layer ) ) {
                    continue;
                }
                int slot = LayeredBoardSlots.slotBit( face, layer );
                for ( int u = 0; u < 16; u++ ) {
                    for ( int v = 0; v < 16; v++ ) {
                        BoardArea area = AREAS[u][v];
                        BoardCorner corner = CORNER_AT.get( key( u, v ) );
                        if ( corner != null ) {
                            area = cornerArea( corner, cornerOwners, face, layer );
                        } else if ( window && isWindowCell( u, v ) ) {
                            area = BoardArea.WINDOW;
                        }
                        int[] world = worldCell( face, layer, u, v );
                        int at = index( world[0], world[1], world[2] );
                        grid[at] |= 1 << slot;
                        // 一个格子最多被两块互相垂直的板占据。材质按<b>格</b>记（后写的胜出），
                        // 几何则按<b>板</b>逐个判断，两者互不干扰。
                        //
                        // 窗的材质槽是<b>面级</b>的（一个面一份，OUTER / INNER 共用），所以这里必须
                        // 用 windowKey(face)，不能用 materialKey(face, layer, WINDOW)——后者会生成
                        // 带层号的键，和交互侧写入的面级键对不上，表现为「窗材质永远设不上」。
                        keys[at] = area.isWindow( )
                                ? LayeredBoardSlots.windowKey( face )
                                : LayeredBoardSlots.materialKey( face, layer, area );
                        areas[at] = area;
                    }
                }
            }
        }

        // ② 逐板逐像素挑出暴露的面，按「平面 + 材质」分组。
        //
        // 判据只有一条：<b>朝那一侧的外侧格子为空</b>。这条同时消掉两类共面重叠：
        //   · 两块板垂直穿插、抢同一格时，外侧被对方占着的那一面被丢弃；
        //   · 两块板同轴贴合时，接触面两侧都被丢弃，只在最外和最内两个平面各留一份。
        //
        // 极少数情况下两块板仍会给出完全相同的面（例如同一个 2px 组合里两个平面恰好重合），
        // 这里再用<b>精确的矩形重叠检测</b>兜一次底：已登记的最小矩形与当前矩形有面积交集就丢弃。
        // 刻意不用「平面格子」当键——垂直的两块板会在同一个平面格子里各占一个方向不同的 1px 条，
        // 那不是重复，按格子去重会把合法的几何砍掉。
        List<FaceRect> accepted = new ArrayList<>();
        Map<PlaneKey, Map<String, List<int[]>>> planes = new LinkedHashMap<>();
        for ( FaceDir face : FaceDir.values() ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( !LayeredBoardSlots.hasSlot( occupancy, face, layer ) ) {
                    continue;
                }
                // 本板在占用掩码里的位，用来区分「外侧格是本板自己的相邻格」还是「掺了别的板」
                int slot = LayeredBoardSlots.slotBit( face, layer );
                for ( int u = 0; u < 16; u++ ) {
                    for ( int v = 0; v < 16; v++ ) {
                        int[] world = worldCell( face, layer, u, v );
                        int at = index( world[0], world[1], world[2] );
                        for ( Direction direction : Direction.values() ) {
                            int nx = world[0] + worldComponent( direction, Direction.Axis.X );
                            int ny = world[1] + worldComponent( direction, Direction.Axis.Y );
                            int nz = world[2] + worldComponent( direction, Direction.Axis.Z );
                            if ( inside( nx, ny, nz ) ) {
                                int nAt = index( nx, ny, nz );
                                int nGrid = grid[nAt];
                                if ( nGrid != 0 ) {
                                    if ( nGrid != ( 1 << slot ) ) {
                                        // 外侧格掺了别的板（两块板垂直穿插抢同一格）：
                                        // 沿用原规则丢弃，否则两张同向共面的外表面会重叠。
                                        continue;
                                    }
                                    // 外侧格只属于本板：只有材质槽也相同（同一个 region 的两个相邻格）
                                    // 才算真正的内部面。BODY 与 WINDOW 是同一块板里两个不同的槽，
                                    // 它们的接触面必须保留——否则窗用玻璃时，从斜角看进去看不到
                                    // BODY 围出来的那圈 1px 内壁。
                                    if ( keys[at] != null && keys[at].equals( keys[nAt] ) ) {
                                        continue;
                                    }
                                    // 同板、不同 region：这一对相邻格会各发一张朝向相反的 quad，
                                    // 而下游的去重只看「平面 + 矩形」、不看 region，谁先被遍历到就归谁。
                                    // 于是四条窗内壁里，先遇到 BODY 的那两条看得见，先遇到 WINDOW
                                    // 的那两条被玻璃材质顶掉——玻璃透明，看起来就是内壁凭空消失。
                                    // 这里改成按 region 优先级裁决，四条边走同一个对称算法：
                                    // BODY 优先，整圈洞口内壁一律由 BODY 发。
                                    if ( priorityOf( areas[at] ) <= priorityOf( areas[nAt] ) ) {
                                        continue;
                                    }
                                }
                            }
                            FaceRect rect = faceRect( direction, world[0], world[1], world[2] );
                            if ( duplicated( accepted, rect ) ) {
                                continue;
                            }
                            accepted.add( rect );
                            addPlaneCell( planes, direction, world[0], world[1], world[2], keys[at] );
                        }
                    }
                }
            }
        }

        // ③ 每个平面上做一次贪心矩形合并
        Map<String, List<Box>> boxes = new LinkedHashMap<>();
        planes.forEach( ( plane, byMaterial ) -> byMaterial.forEach( ( materialKey, cells ) -> {
            for ( int[] rect : mergeRectangles( cells ) ) {
                boxes.computeIfAbsent( materialKey, unused -> new ArrayList<>() )
                        .add( planeBox( plane, rect ) );
            }
        } ) );
        return boxes;
    }

    /**
     * 世界方向在某个轴上的偏移量。
     *
     * <p>刻意手写常量表，不调用 {@code Direction.getOffsetX/Y/Z()} 或 {@link Direction#getVector()}
     * 的分量读取——那两组方法在本项目的开发环境里表现异常（读出来恒为 0），
     * 而这种错误只会表现为「几何少画一半」，极难定位。方向上只有六个常量，写死最可靠。
     */
    private static int worldComponent( Direction direction, Direction.Axis axis ) {
        return switch ( direction ) {
            case DOWN -> axis == Direction.Axis.Y ? -1 : 0;
            case UP -> axis == Direction.Axis.Y ? 1 : 0;
            case NORTH -> axis == Direction.Axis.Z ? -1 : 0;
            case SOUTH -> axis == Direction.Axis.Z ? 1 : 0;
            case WEST -> axis == Direction.Axis.X ? -1 : 0;
            case EAST -> axis == Direction.Axis.X ? 1 : 0;
        };
    }

    private static final int SLOT_COUNT = FaceDir.values().length * BoardLayer.values().length;

    /** 平面上的一个像素面：法线轴 + 平面位置 + 平面内范围（半开区间）。 */
    private record FaceRect( Direction.Axis axis, int along, int minA, int minB, int maxA, int maxB ) {
        boolean overlaps( FaceRect other ) {
            return axis == other.axis && along == other.along
                    && Math.min( maxA, other.maxA ) > Math.max( minA, other.minA )
                    && Math.min( maxB, other.maxB ) > Math.max( minB, other.minB );
        }
    }

    /** 把一个像素面变成精确的平面矩形。 */
    private static FaceRect faceRect( Direction direction, int x, int y, int z ) {
        int along = switch ( direction ) {
            case DOWN, UP -> y + ( direction == Direction.UP ? 1 : 0 );
            case NORTH, SOUTH -> z + ( direction == Direction.SOUTH ? 1 : 0 );
            case WEST, EAST -> x + ( direction == Direction.EAST ? 1 : 0 );
        };
        int a;
        int b;
        switch ( direction.getAxis() ) {
            case X -> {
                a = y;
                b = z;
            }
            case Y -> {
                a = x;
                b = z;
            }
            default -> {
                a = x;
                b = y;
            }
        }
        return new FaceRect( direction.getAxis(), along, a, b, a + 1, b + 1 );
    }

    /**
     * 已登记的矩形里有没有和 {@code rect} 有面积交集的——也就是这张脸是不是重复的。
     *
     * <p>逐块板的暴露判断已经处理掉了绝大多数共面情况，这里只是最后一道保险：两块板给出
     * 完全相同的一张脸时，只保留先到的那一张，保证既不重叠（z-fighting）也不重复画。
     */
    private static boolean duplicated( List<FaceRect> accepted, FaceRect rect ) {
        for ( FaceRect existing : accepted ) {
            if ( existing.overlaps( rect ) ) {
                return true;
            }
        }
        return false;
    }

    /** 把一格的某个面登记到它所在的平面上。 */
    private static void addPlaneCell( Map<PlaneKey, Map<String, List<int[]>>> planes,
                                      Direction direction, int x, int y, int z, String materialKey ) {
        // 格子 (x,y,z) 朝 direction 的那一面，就在该格子朝 direction 的那条边界上
        int along = switch ( direction ) {
            case DOWN, UP -> y + ( direction == Direction.UP ? 1 : 0 );
            case NORTH, SOUTH -> z + ( direction == Direction.SOUTH ? 1 : 0 );
            case WEST, EAST -> x + ( direction == Direction.EAST ? 1 : 0 );
        };
        PlaneKey plane = new PlaneKey( direction.getAxis(), along, direction );
        // 平面内的两个坐标：把法线轴拿掉，剩下两个轴各占一位
        int first;
        int second;
        switch ( direction.getAxis() ) {
            case X -> {
                first = y;
                second = z;
            }
            case Y -> {
                first = x;
                second = z;
            }
            default -> {
                first = x;
                second = y;
            }
        }
        planes.computeIfAbsent( plane, unused -> new LinkedHashMap<>() )
                .computeIfAbsent( materialKey, unused -> new ArrayList<>() )
                .add( new int[] { first, second } );
    }

    /** 把平面上的格子集合贪心合并成互不重叠的矩形。 */
    private static List<int[]> mergeRectangles( List<int[]> cells ) {
        boolean[][] grid = new boolean[16][16];
        boolean[][] duplicated = new boolean[16][16];
        for ( int[] cell : cells ) {
            if ( grid[cell[0]][cell[1]] ) {
                duplicated[cell[0]][cell[1]] = true;
            }
            grid[cell[0]][cell[1]] = true;
        }
        for ( int i = 0; i < 16; i++ ) {
            for ( int j = 0; j < 16; j++ ) {
                if ( duplicated[i][j] ) {
                    throw new IllegalStateException(
                            "layered_copyboard 自检失败：平面内格子 " + i + "," + j + " 被同一材质重复登记" );
                }
            }
        }
        List<int[]> rects = new ArrayList<>();
        for ( int second = 0; second < 16; second++ ) {
            for ( int first = 0; first < 16; first++ ) {
                if ( !grid[first][second] ) {
                    continue;
                }
                int width = 1;
                while ( first + width < 16 && grid[first + width][second] ) {
                    width++;
                }
                int height = 1;
                outer:
                while ( second + height < 16 ) {
                    for ( int i = 0; i < width; i++ ) {
                        if ( !grid[first + i][second + height] ) {
                            break outer;
                        }
                    }
                    height++;
                }
                for ( int i = 0; i < width; i++ ) {
                    for ( int j = 0; j < height; j++ ) {
                        grid[first + i][second + j] = false;
                    }
                }
                rects.add( new int[] { first, second, first + width, second + height } );
            }
        }
        return rects;
    }

    /** 平面上的一个矩形 → 方块本地 0..1 坐标的盒子。 */
    private static Box planeBox( PlaneKey plane, int[] rect ) {
        double along = plane.along() / 16.0;
        double firstMin = rect[0] / 16.0;
        double secondMin = rect[1] / 16.0;
        double firstMax = rect[2] / 16.0;
        double secondMax = rect[3] / 16.0;
        return switch ( plane.axis() ) {
            case X -> new Box( along, firstMin, secondMin, along, firstMax, secondMax );
            case Y -> new Box( firstMin, along, secondMin, firstMax, along, secondMax );
            case Z -> new Box( firstMin, secondMin, along, firstMax, secondMax, along );
        };
    }

    // ---------------------------------------------------------------- 命中

    /** 点到某个格子的最短距离平方。 */
    private static double distanceSquared( int x, int y, int z, Vec3d point ) {
        double px = point.x * 16.0;
        double py = point.y * 16.0;
        double pz = point.z * 16.0;
        double dx = Math.max( Math.max( x - px, 0.0 ), px - ( x + 1 ) );
        double dy = Math.max( Math.max( y - py, 0.0 ), py - ( y + 1 ) );
        double dz = Math.max( Math.max( z - pz, 0.0 ), pz - ( z + 1 ) );
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 命中点落在哪个「面 + 层」上——取最近的格子。
     *
     * <p>只有实际存在的板参与判定：不存在的层点不中，细工凿不会去改一个看不见的层。
     */
    @Nullable
    public static FaceLayer faceLayerAt( int occupancy, Vec3d hit ) {
        FaceLayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for ( FaceDir face : FaceDir.values() ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( !LayeredBoardSlots.hasSlot( occupancy, face, layer ) ) {
                    continue;
                }
                for ( int u = 0; u < 16; u++ ) {
                    for ( int v = 0; v < 16; v++ ) {
                        int[] world = worldCell( face, layer, u, v );
                        double distance = distanceSquared( world[0], world[1], world[2], hit );
                        if ( distance < bestDistance ) {
                            bestDistance = distance;
                            best = new FaceLayer( face, layer );
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * 命中点在该面该层里属于哪个区域。
     *
     * <p>窗区域按<b>中央 8×8 的几何位置</b>判定，与这个面当前是否开着窗无关——
     * 细工凿就是靠这个把「点在中央」解释成「切换窗」的。角则按 owner bit 报出它当前归属的那条边。
     */
    public static BoardArea areaAt( FaceDir face, BoardLayer layer, long[] cornerOwners,
                                    boolean windowOpen, Vec3d hit ) {
        LocalFrame frame = FRAMES.get( face );
        int u = Math.max( 0, Math.min( 15, frame.u( hit ) ) );
        int v = Math.max( 0, Math.min( 15, frame.v( hit ) ) );
        BoardCorner corner = CORNER_AT.get( key( u, v ) );
        if ( corner != null ) {
            return cornerArea( corner, cornerOwners, face, layer );
        }
        // windowOpen 是这个面「当前」是否开着窗：关着的时候中央 8×8 属于 BODY，
        // 窗槽在交互层面根本不存在——否则玩家对中央右键会把材质写进一个没有显示的窗槽，
        // 看起来像「伪装了个寂寞」。细工凿判断「点在不在中央」时传 true，这样关着也能点开。
        if ( windowOpen && isWindowCell( u, v ) ) {
            return BoardArea.WINDOW;
        }
        return AREAS[u][v];
    }

    /** 这个角当前归属的另一条边是谁——细工凿在两个候选之间切换时用。 */
    public static BoardArea otherArea( BoardCorner corner, BoardArea current ) {
        return corner.other( current );
    }

    /** 在给定面上判断命中点是不是角，是就返回那个角。 */
    @Nullable
    public static BoardCorner cornerAt( FaceDir face, Vec3d hit ) {
        LocalFrame frame = FRAMES.get( face );
        int u = Math.max( 0, Math.min( 15, frame.u( hit ) ) );
        int v = Math.max( 0, Math.min( 15, frame.v( hit ) ) );
        return CORNER_AT.get( key( u, v ) );
    }

    /** 面的本地 (u, v)，调试用。 */
    public static int[] local( FaceDir face, Vec3d hit ) {
        LocalFrame frame = FRAMES.get( face );
        return new int[] { frame.u( hit ), frame.v( hit ) };
    }
}
