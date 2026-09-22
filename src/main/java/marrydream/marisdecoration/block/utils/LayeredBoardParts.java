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
import java.util.Comparator;
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

    /**
     * 面本地的两个轴。{@code sign} 为 {@code -1} 表示沿该轴的反方向递增。
     */
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
        // 四个 1×1 的角先取默认归属的那条边；玩家用细工凿改过之后由 Junction override 覆盖
        areas[15][0] = BoardCorner.TOP_RIGHT.defaultArea();
        areas[15][15] = BoardCorner.BOTTOM_RIGHT.defaultArea();
        areas[0][15] = BoardCorner.BOTTOM_LEFT.defaultArea();
        areas[0][0] = BoardCorner.TOP_LEFT.defaultArea();
        return areas;
    }

    /** 这个角像素默认借哪条边的材质。玩家 override 只改显示，不改这里的默认。 */
    private static BoardArea cornerArea( BoardCorner corner ) {
        return corner.defaultArea();
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

    // ---------------------------------------------------------------- 相交几何
    //
    // 两块板互相垂直时会在方块角上抢同一批格子。这里把「谁画、画到哪」一次性定下来，
    // 渲染与命中都只读这份结果：
    //   · OUTER × OUTER、INNER × INNER：同一格只能有一个 owner，只画 owner 那一面；
    //   · OUTER × INNER：INNER 在那个端点缩 1px，两块板不再重叠（见 effectiveRange）；
    //   · 三面共角：owner 判定是全局的，角落那一个 voxel 也只有一个 owner。
    // 规则里没有任何「NORTH / EAST 特判」，四个方向走的是同一套局部计算。

    private static final int CELLS = 16 * 16 * 16;

    /**
     * 一个面的本地 {@code u+ / u- / v+ / v-} 各对应哪个世界方向。
     *
     * <p>这四条就是「与本面垂直的四个面」，也就是可能压住本层板端边的那几块板。
     * 与 {@link #FRAMES} 一一对应，同样手写常量表，理由见 {@link #worldComponent}。
     */
    private record LocalDirs( Direction uPos, Direction uNeg, Direction vPos, Direction vNeg ) {
    }

    private static final Map<FaceDir, LocalDirs> LOCAL_DIRS = Map.of(
            FaceDir.UP, new LocalDirs( Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH ),
            FaceDir.DOWN, new LocalDirs( Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH ),
            FaceDir.NORTH, new LocalDirs( Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN ),
            FaceDir.SOUTH, new LocalDirs( Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN ),
            FaceDir.WEST, new LocalDirs( Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN ),
            FaceDir.EAST, new LocalDirs( Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN ) );

    /** 一层板在面本地 (u, v) 上的有效范围，半开区间。 */
    private record Range( int u0, int v0, int u1, int v1 ) {
        boolean isFull( ) {
            return u0 == 0 && v0 == 0 && u1 == 16 && v1 == 16;
        }
    }

    private static final Range FULL_RANGE = new Range( 0, 0, 16, 16 );

    private static boolean hasOuter( int occupancy, Direction direction ) {
        FaceDir face = LayeredBoardSlots.of( direction );
        return face != null && LayeredBoardSlots.hasSlot( occupancy, face, BoardLayer.OUTER );
    }

    /**
     * 一层板的<b>有效范围</b>。
     *
     * <p>OUTER 永远是整块 16×16。INNER 只要在某一个本地端点撞上垂直面的 OUTER，就在那一端缩 1px：
     * NORTH.INNER 的 EAST 端会缩、EAST.INNER 的 NORTH 端会缩，两个方向用的是同一条计算，没有特判。
     * 两端都撞上就两头各缩 1px。
     *
     * <p><b>只有 INNER 缩、OUTER 不动</b>，所以缩完之后同一格里绝不会同时出现 OUTER 和 INNER——
     * 这条不变式是下面 owner 规则成立的前提。
     *
     * <p>缩的是<b>整层</b>的有效范围而不是某张面：区域分割（BODY / Edge / Corner / Window）随后
     * 就在缩完的范围上照常做，所以被切掉的那一端的 Edge 与 Corner 是不存在的，不是「画出来再裁掉」。
     */
    private static Range effectiveRange( int occupancy, FaceDir face, BoardLayer layer ) {
        if ( layer == BoardLayer.OUTER ) {
            return FULL_RANGE;
        }
        LocalDirs dirs = LOCAL_DIRS.get( face );
        return new Range(
                hasOuter( occupancy, dirs.uNeg() ) ? 1 : 0,
                hasOuter( occupancy, dirs.vNeg() ) ? 1 : 0,
                hasOuter( occupancy, dirs.uPos() ) ? 15 : 16,
                hasOuter( occupancy, dirs.vPos() ) ? 15 : 16 );
    }

    private static FaceDir faceOfSlot( int slot ) {
        return FaceDir.values()[slot / BoardLayer.values().length];
    }

    private static BoardLayer layerOfSlot( int slot ) {
        return BoardLayer.values()[slot % BoardLayer.values().length];
    }

    /** 从上方俯视时的顺时针邻居；{@code UP} / {@code DOWN} 不在这个环上。 */
    @Nullable
    private static FaceDir clockwise( FaceDir face ) {
        return switch ( face ) {
            case NORTH -> FaceDir.EAST;
            case EAST -> FaceDir.SOUTH;
            case SOUTH -> FaceDir.WEST;
            case WEST -> FaceDir.NORTH;
            default -> null;
        };
    }

    /** 是不是四个侧面。{@link FaceDir} 的顺序保证 NORTH 起都是水平面。 */
    private static boolean isHorizontal( FaceDir face ) {
        return face.ordinal() >= FaceDir.NORTH.ordinal();
    }

    /**
     * 同一格被两块以上互相垂直的板同时占据时，<b>谁负责这一格</b>。
     *
     * <p>这就是「共享棱 owner」：那段可见的 1px 由 owner 那一面画，用的也是 owner 那一面的区域
     * 材质槽——不新增「共享棱材质」，也不合并两面的槽位。owner 不在场时候选里就只剩另一面，
     * 它会自动接手，所以拆掉一面之后另一面自己补上。
     *
     * <p>判定与遍历顺序无关：先让四个侧面优先于 {@code UP} / {@code DOWN}（房间的角由墙面构成，
     * 地板与天花板是塞进墙里的），两块侧面相遇时再按固定手性——<b>逆时针方向的那一面说了算</b>。
     * 这与伪装护栏的共享柱 owner 是同一套手性：WEST 胜 NORTH、NORTH 胜 EAST、EAST 胜 SOUTH、
     * SOUTH 胜 WEST。
     *
     * @param claimed 这一格被哪些板占据（槽位位掩码）
     * @return owner 的槽位位掩码；这一格没人占则返回 0
     */
    private static int ownerOf( int claimed ) {
        if ( Integer.bitCount( claimed ) <= 1 ) {
            return claimed;
        }
        int horizontal = 0;
        for ( int slot = 0; slot < SLOT_COUNT; slot++ ) {
            if ( ( claimed & ( 1 << slot ) ) != 0 && isHorizontal( faceOfSlot( slot ) ) ) {
                horizontal |= 1 << slot;
            }
        }
        if ( horizontal == 0 ) {
            // 同格里只剩 UP / DOWN 的情况不存在（两者占的格子不相交），取位序最小的兜底。
            return Integer.lowestOneBit( claimed );
        }
        for ( int slot = 0; slot < SLOT_COUNT; slot++ ) {
            if ( ( horizontal & ( 1 << slot ) ) == 0 ) {
                continue;
            }
            FaceDir neighbour = clockwise( faceOfSlot( slot ) );
            if ( neighbour != null && ( horizontal
                    & ( 1 << LayeredBoardSlots.slotBit( neighbour, layerOfSlot( slot ) ) ) ) != 0 ) {
                return 1 << slot;
            }
        }
        // 兜底：三块互相垂直的水平面不会同时出现，到不了这里。
        return Integer.lowestOneBit( horizontal );
    }

    /**
     * 每格实际在场的板（槽位位掩码）与这一格唯一的 owner 板。
     *
     * <p>只取决于占用掩码：收缩量与 owner 判定都只看「哪些槽位在场」。
     * 渲染与命中都从这一个函数取，两边的「最终可见几何」因此天然是同一份。
     */
    private record Ownership( int[] present, int[] owner ) {
    }

    private static Ownership ownership( int occupancy ) {
        int[] present = new int[CELLS];
        for ( FaceDir face : FaceDir.values() ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( !LayeredBoardSlots.hasSlot( occupancy, face, layer ) ) {
                    continue;
                }
                int slot = 1 << LayeredBoardSlots.slotBit( face, layer );
                Range range = effectiveRange( occupancy, face, layer );
                for ( int u = range.u0(); u < range.u1(); u++ ) {
                    for ( int v = range.v0(); v < range.v1(); v++ ) {
                        int[] world = worldCell( face, layer, u, v );
                        present[index( world[0], world[1], world[2] )] |= slot;
                    }
                }
            }
        }
        int[] owner = new int[CELLS];
        for ( int at = 0; at < CELLS; at++ ) {
            owner[at] = ownerOf( present[at] );
        }
        return new Ownership( present, owner );
    }

    // ---------------------------------------------------------------- 物理交汇点

    /** 一个材质槽的身份：面 + 层 + 区域。 */
    public record Slot( FaceDir face, BoardLayer layer, BoardArea area ) {
        /**
         * 稳定的槽名，就是材质键。
         *
         * <p>Junction override 按槽名保存，<b>不存「候选列表第几个」</b>——occupancy 一变候选列表
         * 就会变，下标会指到另一条边上。窗是面级的，所以 area 为 WINDOW 时层不参与键名。
         */
        public String name( ) {
            return area.isWindow( )
                    ? LayeredBoardSlots.windowKey( face )
                    : LayeredBoardSlots.materialKey( face, layer, area );
        }

        /**
         * {@link #name()} 的逆。解析失败返回 {@code null}。
         *
         * <p>override 存的是槽名，清理失效状态时要能反查出「这个槽属于哪块板」。
         * 窗是面级的，所以 {@code north.window} 只有面、没有层。
         */
        @Nullable
        public static Slot parse( @Nullable String name ) {
            if ( name == null ) {
                return null;
            }
            String[] parts = name.split( "\\." );
            if ( parts.length == 2 ) {
                FaceDir face = faceByName( parts[0] );
                return face == null || !BoardArea.WINDOW.getName().equals( parts[1] )
                        ? null : new Slot( face, BoardLayer.OUTER, BoardArea.WINDOW );
            }
            if ( parts.length != 3 ) {
                return null;
            }
            FaceDir face = faceByName( parts[0] );
            BoardLayer layer = layerByName( parts[1] );
            BoardArea area = areaByName( parts[2] );
            return face == null || layer == null || area == null ? null : new Slot( face, layer, area );
        }

        @Nullable
        private static FaceDir faceByName( String name ) {
            for ( FaceDir face : FaceDir.values() ) {
                if ( face.getName().equals( name ) ) {
                    return face;
                }
            }
            return null;
        }

        @Nullable
        private static BoardLayer layerByName( String name ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( layer.getName().equals( name ) ) {
                    return layer;
                }
            }
            return null;
        }

        @Nullable
        private static BoardArea areaByName( String name ) {
            for ( BoardArea area : BoardArea.values() ) {
                if ( area.getName().equals( name ) ) {
                    return area;
                }
            }
            return null;
        }
    }

    /**
     * 一个可交互的几何位置。两种来源，语义不同：
     *
     * <ul>
     *   <li><b>physical edge run</b>（{@code corner == false}）——一条实际可见的连续棱线。
     *       它的候选是这条棱上<b>几何完全重合的那几个 Face-edge 材质槽</b>，比如 NORTH 的右边
     *       与 EAST 的左边；切换的是「这条整段物理棱当前采用哪个 Face-edge 材质」。</li>
     *   <li><b>corner junction</b>（{@code corner == true}）——多条 physical run 在同一个 1px
     *       位置相接。它的候选是<b>每条实际接到这里的 run 当前显示的槽</b>，不是「每个 Face 各挑一条」。</li>
     * </ul>
     *
     * <p>{@code candidates} 按槽名排序，循环顺序与遍历顺序无关；{@code key} 是写回 override 用的
     * 稳定 identity，两种来源各自独立、互不串味。
     */
    public record Junction( String key, List<Slot> candidates, Slot shown, boolean corner ) {
        /** 当前显示的那条边在候选里的位置。 */
        public int indexOfShown( ) {
            for ( int i = 0; i < candidates.size(); i++ ) {
                if ( candidates.get( i ).name().equals( shown.name() ) ) {
                    return i;
                }
            }
            return 0;
        }
    }

    /**
     * 一块板在自己的 (u, v) 上属于哪个区域。角像素借它<b>默认归属</b>的那条边——
     * 玩家改过的显示由 override 覆盖，不改这里的默认。
     */
    private static BoardArea plateAreaOf( int u, int v, boolean window ) {
        BoardCorner corner = CORNER_AT.get( key( u, v ) );
        if ( corner != null ) {
            return cornerArea( corner );
        }
        if ( window && isWindowCell( u, v ) ) {
            return BoardArea.WINDOW;
        }
        return AREAS[u][v];
    }

    /** 是不是四条 1×14 的 Edge 条之一。BODY / WINDOW 不是棱。 */
    private static boolean isEdgeArea( BoardArea area ) {
        return area == BoardArea.TOP_EDGE || area == BoardArea.BOTTOM_EDGE
                || area == BoardArea.LEFT_EDGE || area == BoardArea.RIGHT_EDGE;
    }

    /**
     * 一条 Edge 条延伸的世界轴向。
     *
     * <p>LEFT / RIGHT 把本地 u 钉死，所以沿 v 轴延伸；TOP / BOTTOM 把本地 v 钉死，沿 u 轴延伸。
     */
    @Nullable
    private static Direction.Axis runAxisOf( FaceDir face, BoardArea area ) {
        LocalFrame frame = FRAMES.get( face );
        return switch ( area ) {
            case LEFT_EDGE, RIGHT_EDGE -> frame.vAxis();
            case TOP_EDGE, BOTTOM_EDGE -> frame.uAxis();
            default -> null;
        };
    }

    /** 平面内两个坐标的取法，与 {@link #addPlaneCell} 保持一致。 */
    private static int planeFirst( Direction.Axis axis, int x, int y, int z ) {
        return axis == Direction.Axis.X ? y : x;
    }

    private static int planeSecond( Direction.Axis axis, int x, int y, int z ) {
        return axis == Direction.Axis.Z ? y : z;
    }

    /**
     * 一条 physical edge run 的稳定 identity：<b>层 + 世界轴向 + 另外两个固定坐标</b>。
     *
     * <p>刻意<b>不含</b>「是哪个 Face 的哪条 Edge」——几何上共线的 Face-edge 条本来就属于同一条
     * 物理棱，所以 NORTH 的右边与 EAST 的左边算出同一个 run。
     *
     * <p>也不含沿轴的长度范围：在同一个「层 + 轴 + 两个固定坐标」上，占位的格子永远是一整段连续
     * 区间（每条 Edge 条本身连续，且同一层的重合条共享同一段范围），所以范围不需要进 key。
     */
    private static String runKey( BoardLayer layer, Direction.Axis axis, int x, int y, int z ) {
        return "r" + layer.ordinal() + ":" + axis.ordinal()
                + ":" + planeFirst( axis, x, y, z ) + ":" + planeSecond( axis, x, y, z );
    }

    /**
     * 多条 physical run 交汇点的稳定 identity：接到这里的那几条 run 的 identity。
     *
     * <p>用的是 run 的 identity 而不是各 run 当前显示的槽，所以玩家改了某条 run 的 owner 之后，
     * 这个交汇点的 key 不会漂移（漂移的话原来的 override 就会失效）。
     */
    private static String cornerKey( List<String> runs ) {
        return "c:" + String.join( "+", runs );
    }

    /** 按槽名排序去重，得到与遍历顺序无关的稳定候选顺序。 */
    private static List<Slot> canonical( List<Slot> slots ) {
        List<Slot> sorted = new ArrayList<>( slots );
        sorted.sort( Comparator.comparing( Slot::name ) );
        List<Slot> unique = new ArrayList<>( sorted.size() );
        String previous = null;
        for ( Slot slot : sorted ) {
            if ( !slot.name().equals( previous ) ) {
                unique.add( slot );
                previous = slot.name();
            }
        }
        return List.copyOf( unique );
    }

    /**
     * 一条 physical run 的候选键：候选的「槽位下标 : 区域序号」按槽名排序后连起来。
     *
     * <p>编码的是<b>槽位本身</b>（{@code slotBit} 由面与层唯一决定，永远不会变），不是候选列表里的
     * 位置。这条 key 只用于「几何重合的格子」——那些候选只由占用掩码决定，不会被 override 改动，
     * 所以不会漂移。
     */
    public static String junctionKey( List<Slot> candidates ) {
        StringBuilder builder = new StringBuilder();
        for ( Slot slot : candidates ) {
            if ( builder.length() > 0 ) {
                builder.append( '+' );
            }
            builder.append( LayeredBoardSlots.slotBit( slot.face(), slot.layer() ) )
                    .append( ':' ).append( slot.area().ordinal() );
        }
        return builder.toString();
    }

    /**
     * 找出所有 value 指向的板已经不在场的 override。
     *
     * <p>两块来源的 key 格式不同，所以统一按<b>值</b>判断：存的槽所属的板不在了，这条 override
     * 就再也匹配不上，留着不会生效、只是白占地方。值解析不出来（格式不对）也算失效。
     */
    public static List<String> staleJunctionOwners( Map<String, String> overrides, int occupancy ) {
        List<String> stale = new ArrayList<>();
        for ( Map.Entry<String, String> entry : overrides.entrySet() ) {
            Slot slot = Slot.parse( entry.getValue() );
            if ( slot == null ) {
                stale.add( entry.getKey() );
                continue;
            }
            boolean alive = slot.area().isWindow()
                    ? LayeredBoardSlots.hasFace( occupancy, slot.face() )
                    : LayeredBoardSlots.hasSlot( occupancy, slot.face(), slot.layer() );
            if ( !alive ) {
                stale.add( entry.getKey() );
            }
        }
        return stale;
    }

    /** 这一格上几何完全重合的那几个材质槽——一条 physical run 的全部材质来源。 */
    private static List<Slot> coincidingSlots( int present, int windows, int x, int y, int z ) {
        List<Slot> candidates = new ArrayList<>( 2 );
        for ( int bit = 0; bit < SLOT_COUNT; bit++ ) {
            if ( ( present & ( 1 << bit ) ) == 0 ) {
                continue;
            }
            FaceDir face = faceOfSlot( bit );
            BoardLayer layer = layerOfSlot( bit );
            candidates.add( new Slot( face, layer, plateAreaOf(
                    localU( face, x, y, z ), localV( face, x, y, z ),
                    LayeredBoardSlots.hasWindow( windows, face ) ) ) );
        }
        return candidates;
    }

    /**
     * 多条 physical edge run 在同一个 1px 交汇位置相接时的交汇点。
     *
     * <p>候选是<b>每条实际接到这里的 run 当前显示的槽</b>。这一点是关键：某条 run 的 owner 可能
     * 是另一个 Face，它显示的槽与本 Face 在角上的默认区域并不是同一个；若改成「按 Face 各解析
     * 一次」，就会把一个屏幕上根本没显示的槽塞进候选，同时漏掉真正可见的那条边——表现就是
     * 「肉眼看得到三条边，凿子却只循环得出两条 + 一个待伪装」。
     *
     * <p>接法：看这一格的六个邻格。邻格必须<b>同一层</b>、且落在某条 Edge 条上；那个邻格所在的
     * run 就是接到这个交汇点的一条 run，它当前显示的槽就是这条可见边。
     */
    @Nullable
    private static Junction cornerJunction( int[] present, int[] owner, int windows, int x, int y, int z ) {
        int ownerBit = owner[index( x, y, z )];
        BoardLayer layer = layerOfSlot( Integer.numberOfTrailingZeros( ownerBit ) );
        Map<String, Slot> byRun = new LinkedHashMap<>();
        for ( Direction direction : Direction.values() ) {
            int nx = x + worldComponent( direction, Direction.Axis.X );
            int ny = y + worldComponent( direction, Direction.Axis.Y );
            int nz = z + worldComponent( direction, Direction.Axis.Z );
            if ( !inside( nx, ny, nz ) ) {
                continue;
            }
            int nAt = index( nx, ny, nz );
            if ( present[nAt] == 0 ) {
                continue;
            }
            int neighbourBit = Integer.numberOfTrailingZeros( owner[nAt] );
            FaceDir neighbourFace = faceOfSlot( neighbourBit );
            if ( layerOfSlot( neighbourBit ) != layer ) {
                continue;
            }
            BoardArea neighbourArea = plateAreaOf(
                    localU( neighbourFace, nx, ny, nz ), localV( neighbourFace, nx, ny, nz ),
                    LayeredBoardSlots.hasWindow( windows, neighbourFace ) );
            Direction.Axis axis = isEdgeArea( neighbourArea ) ? runAxisOf( neighbourFace, neighbourArea ) : null;
            if ( axis == null ) {
                continue;
            }
            byRun.putIfAbsent( runKey( layer, axis, nx, ny, nz ),
                    new Slot( neighbourFace, layer, neighbourArea ) );
        }
        if ( byRun.size() < 2 ) {
            return null;
        }
        List<String> runs = new ArrayList<>( byRun.keySet() );
        runs.sort( String::compareTo );
        List<Slot> candidates = canonical( new ArrayList<>( byRun.values() ) );
        return new Junction( cornerKey( runs ), candidates,
                defaultCornerShown( ownerBit, candidates, x, y, z ), true );
    }

    /**
     * 没有 override 时交汇点显示哪条边。
     *
     * <p>优先用 owner 板自己在这个角上的默认区域——它按构造一定等于接到这里某一条 run 的显示槽，
     * 所以默认画面不会跳。万一不等（比如那条 run 被玩家改过），退回候选里的第一个。
     */
    private static Slot defaultCornerShown( int ownerBit, List<Slot> candidates, int x, int y, int z ) {
        int bit = Integer.numberOfTrailingZeros( ownerBit );
        FaceDir face = faceOfSlot( bit );
        BoardLayer layer = layerOfSlot( bit );
        BoardCorner corner = CORNER_AT.get( key( localU( face, x, y, z ), localV( face, x, y, z ) ) );
        if ( corner != null ) {
            String preferred = LayeredBoardSlots.materialKey( face, layer, cornerArea( corner ) );
            for ( Slot candidate : candidates ) {
                if ( candidate.name().equals( preferred ) ) {
                    return candidate;
                }
            }
        }
        return candidates.get( 0 );
    }

    /** 把 override 套到默认显示上；override 指向的槽已经不在候选里就自动回退默认。 */
    private static Slot applyOverride( String key, List<Slot> candidates, Slot fallback,
                                       Map<String, String> overrides ) {
        if ( overrides == null || overrides.isEmpty() ) {
            return fallback;
        }
        String chosen = overrides.get( key );
        if ( chosen == null ) {
            return fallback;
        }
        for ( Slot candidate : candidates ) {
            if ( candidate.name().equals( chosen ) ) {
                return candidate;
            }
        }
        return fallback;
    }

    /**
     * 算出某一格上的可交互位置；候选不足 2 条就返回 {@code null}（那一格没有可切换的东西）。
     *
     * <ul>
     *   <li><b>角格</b> → {@link #cornerJunction}：候选来自实际接到这里的 physical edge run；</li>
     *   <li><b>非角格、多块板</b> → 几何重合的那几个 Face-edge 槽；</li>
     *   <li>其余（单板普通边 / BODY / WINDOW）→ 没有可切换的，返回 {@code null}。</li>
     * </ul>
     */
    @Nullable
    private static Junction junctionAtCell( int[] present, int[] owner, int windows, int x, int y, int z ) {
        int ownerBit = owner[index( x, y, z )];
        if ( ownerBit == 0 ) {
            return null;
        }
        int at = index( x, y, z );
        int bit = Integer.numberOfTrailingZeros( ownerBit );
        FaceDir face = faceOfSlot( bit );
        BoardLayer layer = layerOfSlot( bit );
        BoardCorner corner = CORNER_AT.get( key( localU( face, x, y, z ), localV( face, x, y, z ) ) );
        if ( corner != null ) {
            return cornerJunction( present, owner, windows, x, y, z );
        }
        List<Slot> candidates = canonical( coincidingSlots( present[at], windows, x, y, z ) );
        if ( candidates.size() < 2 ) {
            return null;
        }
        int ownerOfPresent = Integer.numberOfTrailingZeros( ownerOf( present[at] ) );
        FaceDir shownFace = faceOfSlot( ownerOfPresent );
        return new Junction( junctionKey( candidates ), candidates,
                new Slot( shownFace, layerOfSlot( ownerOfPresent ), plateAreaOf(
                        localU( shownFace, x, y, z ), localV( shownFace, x, y, z ),
                        LayeredBoardSlots.hasWindow( windows, shownFace ) ) ), false );
    }

    /**
     * 某一格最终显示的材质槽——渲染、材质点击、扳手、命中判定共用这一个判断。
     *
     * <p>交汇点上显示的是 override 选中的那条边（没有 override 就是默认 owner），其余格子就是
     * owner 板自己的区域。所以「画面显示 A Edge，右键材质却操作 B Edge」不可能发生。
     */
    private static Slot shownAt( int[] present, int[] owner, int windows, Map<String, String> overrides,
                                 int x, int y, int z ) {
        Junction junction = junctionAtCell( present, owner, windows, x, y, z );
        if ( junction != null ) {
            return applyOverride( junction.key(), junction.candidates(), junction.shown(), overrides );
        }
        int bit = Integer.numberOfTrailingZeros( owner[index( x, y, z )] );
        FaceDir face = faceOfSlot( bit );
        BoardLayer layer = layerOfSlot( bit );
        return new Slot( face, layer, plateAreaOf( localU( face, x, y, z ), localV( face, x, y, z ),
                LayeredBoardSlots.hasWindow( windows, face ) ) );
    }

    /** 最终几何：每格的 owner、实际显示的材质槽，以及 owner 自己的区域（只给优先级裁决用）。 */
    private record Grid( int[] owner, String[] key, BoardArea[] area ) {
    }

    /**
     * 在占用掩码的有效范围内，给每一格定下唯一的 owner 与它最终显示的材质槽。
     *
     * <p>只有 owner 会写这一格：非 owner 的板在这一格上完全不生成几何，也不生成命中区域。
     */
    private static Grid buildGrid( int occupancy, int windows, Map<String, String> overrides ) {
        Ownership ownership = ownership( occupancy );
        int[] present = ownership.present();
        int[] owner = ownership.owner();
        String[] keys = new String[CELLS];
        BoardArea[] areas = new BoardArea[CELLS];
        for ( int at = 0; at < CELLS; at++ ) {
            int ownerBit = owner[at];
            if ( ownerBit == 0 ) {
                continue;
            }
            int x = at >> 8;
            int y = ( at >> 4 ) & 15;
            int z = at & 15;
            int bit = Integer.numberOfTrailingZeros( ownerBit );
            FaceDir face = faceOfSlot( bit );
            // areas[] 只服务「同板不同区域接触时由谁发面」的优先级裁决，所以用 owner 自己的区域。
            areas[at] = plateAreaOf( localU( face, x, y, z ), localV( face, x, y, z ),
                    LayeredBoardSlots.hasWindow( windows, face ) );
            keys[at] = shownAt( present, owner, windows, overrides, x, y, z ).name();
        }
        return new Grid( owner, keys, areas );
    }

    /** {@link #worldCell} 的逆：格子 (x,y,z) 落在该板面本地的哪个 u。 */
    private static int localU( FaceDir face, int x, int y, int z ) {
        int a = face == FaceDir.WEST || face == FaceDir.EAST ? z : x;
        return FRAMES.get( face ).uSign( ) > 0 ? a : 15 - a;
    }

    /** {@link #worldCell} 的逆：格子 (x,y,z) 落在该板面本地的哪个 v。 */
    private static int localV( FaceDir face, int x, int y, int z ) {
        int b = face == FaceDir.UP || face == FaceDir.DOWN ? z : y;
        return FRAMES.get( face ).vSign( ) > 0 ? b : 15 - b;
    }

    /**
     * 有效范围换算到某个本地轴上的世界区段（0..1）。{@code sign < 0} 时本地轴与世界轴反向，
     * 所以两端要翻过来。
     */
    private static double[] spanFor( int lo, int hi, int sign ) {
        if ( sign > 0 ) {
            return new double[] { lo / 16.0, hi / 16.0 };
        }
        return new double[] { ( 16 - hi ) / 16.0, ( 16 - lo ) / 16.0 };
    }

    /**
     * 一层板<b>缩完之后</b>的包围盒。
     *
     * <p>选取箱 / 碰撞箱必须和渲染用的是同一份几何，否则会出现「看得见却点不到」。
     * 放置用的 {@link #plateBox} 是<b>没缩过</b>的整块板，两者刻意分开：收缩只影响几何，
     * 不影响槽位中心，放置规则一点没动。
     */
    public static Box trimmedPlateBox( int occupancy, FaceDir face, BoardLayer layer ) {
        Box base = plateBox( face, layer );
        Range range = effectiveRange( occupancy, face, layer );
        if ( range.isFull( ) ) {
            return base;
        }
        LocalFrame frame = FRAMES.get( face );
        double[] us = spanFor( range.u0( ), range.u1( ), frame.uSign( ) );
        double[] vs = spanFor( range.v0( ), range.v1( ), frame.vSign( ) );
        return switch ( face ) {
            case UP, DOWN -> new Box( us[0], base.minY, vs[0], us[1], base.maxY, vs[1] );
            case NORTH, SOUTH -> new Box( us[0], vs[0], base.minZ, us[1], vs[1], base.maxZ );
            case WEST, EAST -> new Box( base.minX, vs[0], us[0], base.maxX, vs[1], us[1] );
        };
    }

    private static VoxelShape buildShape( int mask ) {
        VoxelShape shape = VoxelShapes.empty();
        for ( FaceDir face : FaceDir.values() ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( LayeredBoardSlots.hasSlot( mask, face, layer ) ) {
                    shape = VoxelShapes.union( shape, VoxelShapes.cuboid( trimmedPlateBox( mask, face, layer ) ) );
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
     * <p>几何来自 {@link #buildGrid}：每层板先按 {@link #effectiveRange} 算出<b>有效范围</b>
     * （INNER 撞上垂直面的 OUTER 就在那一端缩 1px），再按 {@link #ownerOf} 给每一格定一个唯一
     * 的 owner。于是相交处的归属在进入这一步之前就已经定死了，这里只需要逐格逐方向判断
     * 「这一格面朝外是不是空的」：
     *
     * <blockquote>
     * 一格朝 {@code direction} 的面要画 ⟺ 那一侧没有别的格子挡着。
     * </blockquote>
     *
     * <p>相邻格有东西时的三种情况：
     * <ul>
     *   <li>相邻格归<b>别的板</b>（垂直穿插、或者同一面的另一层贴上来）→ 两边都是实体，这是内部面，丢弃；</li>
     *   <li>相邻格归<b>本板、同一个材质槽</b> → 板内部的面，丢弃；</li>
     *   <li>相邻格归<b>本板、不同材质槽</b> → 只有窗洞内壁属于这一种。这里按
     *       {@link #priorityOf} 裁决：BODY 优先，整圈洞口内壁一律由 BODY 发，四条边走同一个
     *       对称算法。否则窗用玻璃时，先被遍历到 WINDOW 的那两条内壁会被透明材质顶掉，
     *       看起来就是内壁凭空消失。</li>
     * </ul>
     *
     * <p>每一格只有一个 owner，而格子与「平面上的 1×1 矩形」是一一对应的，所以<b>不需要任何
     * 去重兜底</b>，也就不存在「谁先被遍历到谁赢」。
     *
     * <p>最后按「平面 + 材质」分组做一次贪心矩形合并，把 1×1 的格子面并成大面。
     */
    public static Map<String, List<Box>> boxesByKey( int occupancy, int windows,
                                                     Map<String, String> junctionOwners ) {
        Grid grid = buildGrid( occupancy, windows, junctionOwners );
        int[] owner = grid.owner();
        String[] keys = grid.key();
        BoardArea[] areas = grid.area();

        // 逐格逐方向挑出暴露的面，按「平面 + 材质」分组
        Map<PlaneKey, Map<String, List<int[]>>> planes = new LinkedHashMap<>();
        for ( int x = 0; x < 16; x++ ) {
            for ( int y = 0; y < 16; y++ ) {
                for ( int z = 0; z < 16; z++ ) {
                    int at = index( x, y, z );
                    if ( owner[at] == 0 ) {
                        continue;
                    }
                    for ( Direction direction : Direction.values() ) {
                        int nx = x + worldComponent( direction, Direction.Axis.X );
                        int ny = y + worldComponent( direction, Direction.Axis.Y );
                        int nz = z + worldComponent( direction, Direction.Axis.Z );
                        if ( inside( nx, ny, nz ) ) {
                            int nAt = index( nx, ny, nz );
                            if ( owner[nAt] != 0 ) {
                                // 相邻格归别的板（垂直穿插，或者同一面的另一层贴上来）→ 内部面
                                if ( owner[nAt] != owner[at] ) {
                                    continue;
                                }
                                // 本板内部、同一个材质槽 → 板内部的面
                                if ( keys[at].equals( keys[nAt] ) ) {
                                    continue;
                                }
                                // 本板、不同材质槽：只有 BODY / WINDOW 的接触属于这一种。
                                // 按区域优先级裁决，四条窗内壁走同一个对称算法。
                                if ( priorityOf( areas[at] ) <= priorityOf( areas[nAt] ) ) {
                                    continue;
                                }
                            }
                        }
                        addPlaneCell( planes, direction, x, y, z, keys[at] );
                    }
                }
            }
        }

        // 每个平面上做一次贪心矩形合并
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
     * 命中的一格：owner 是哪个面的哪一层，以及它在那个面上的本地像素 (u, v)。
     *
     * <p>带上 (u, v) 是为了让区域判定<b>精确地</b>跟渲染对齐：命中点常常落在像素边界上，
     * 再按坐标现算一次可能得到隔壁那一格，于是「看到的是 LEFT_EDGE、命中的却是 RIGHT_EDGE」。
     * 直接用 owner 格的 (u, v) 就没有这个问题。
     */
    public record Cell( FaceDir face, BoardLayer layer, int u, int v ) {
    }

    /** 离命中点最近的、有几何的格子；没有几何时返回 -1。 */
    private static int nearestCell( int[] owner, Vec3d hit ) {
        int bestAt = -1;
        double bestDistance = Double.MAX_VALUE;
        for ( int at = 0; at < CELLS; at++ ) {
            if ( owner[at] == 0 ) {
                continue;
            }
            double distance = distanceSquared( at >> 8, ( at >> 4 ) & 15, at & 15, hit );
            if ( distance < bestDistance ) {
                bestDistance = distance;
                bestAt = at;
            }
        }
        return bestAt;
    }

    /**
     * 命中点落在哪个「面 + 层」上——按<b>最终生效的几何</b>取最近的格子。
     *
     * <p>用的就是渲染那份 owner 结果（{@link #ownership}），所以相交处<b>看到谁就能选中谁</b>：
     * 共享棱归哪一面，命中也归那一面；被 {@link #effectiveRange} 缩掉的端点在几何上根本不存在，
     * 自然点不中。不存在的层同样点不中，细工凿不会去改一个看不见的层。
     */
    @Nullable
    public static Cell cellAt( int occupancy, Vec3d hit ) {
        int[] owner = ownership( occupancy ).owner();
        int at = nearestCell( owner, hit );
        if ( at < 0 ) {
            return null;
        }
        int slot = Integer.numberOfTrailingZeros( owner[at] );
        FaceDir face = faceOfSlot( slot );
        BoardLayer layer = layerOfSlot( slot );
        int x = at >> 8;
        int y = ( at >> 4 ) & 15;
        int z = at & 15;
        return new Cell( face, layer, localU( face, x, y, z ), localV( face, x, y, z ) );
    }

    /**
     * 命中点最终显示的材质槽。
     *
     * <p>与 {@link #boxesByKey} 用同一个 {@link #shownAt}：材质点击、扳手、以及「命中在哪条边上」
     * 全部按它判定，所以共享几何上<b>画面显示哪条边，右键就操作哪条边</b>。
     */
    @Nullable
    public static Slot slotAt( int occupancy, int windows, Map<String, String> junctionOwners, Vec3d hit ) {
        Ownership ownership = ownership( occupancy );
        int at = nearestCell( ownership.owner(), hit );
        if ( at < 0 ) {
            return null;
        }
        return shownAt( ownership.present(), ownership.owner(), windows, junctionOwners,
                at >> 8, ( at >> 4 ) & 15, at & 15 );
    }

    /**
     * 命中点所在的可交互位置；那一格没有可切换的东西（候选不足 2 条）时返回 {@code null}。
     *
     * <p>{@code shown} 已经把玩家的 override 算进去了，所以细工凿可以直接从它往后循环一位；
     * {@code key} 就是写回 override 时要用的 identity。
     */
    @Nullable
    public static Junction junctionAt( int occupancy, int windows, Map<String, String> junctionOwners, Vec3d hit ) {
        Ownership ownership = ownership( occupancy );
        int at = nearestCell( ownership.owner(), hit );
        if ( at < 0 ) {
            return null;
        }
        int x = at >> 8;
        int y = ( at >> 4 ) & 15;
        int z = at & 15;
        Junction junction = junctionAtCell( ownership.present(), ownership.owner(), windows, x, y, z );
        if ( junction == null ) {
            return null;
        }
        return new Junction( junction.key(), junction.candidates(),
                applyOverride( junction.key(), junction.candidates(), junction.shown(), junctionOwners ),
                junction.corner() );
    }

    /**
     * 某个面某一层的本地像素 (u, v) 属于哪个区域。
     *
     * <p>窗区域按<b>中央 8×8 的几何位置</b>判定，与这个面当前是否开着窗无关——
     * 细工凿就是靠这个把「点在中央」解释成「切换窗」的。角像素报它默认归属的那条边。
     * 这里收的是 (u, v) 而不是命中点，调用方直接用 {@link Cell} 里那一份，保证与渲染同一格。
     *
     * <p>注意：交汇点优先于这个函数——落在交汇点上时细工凿循环的是候选边，不是走这里。
     */
    public static BoardArea areaAt( boolean windowOpen, int u, int v ) {
        int cu = Math.max( 0, Math.min( 15, u ) );
        int cv = Math.max( 0, Math.min( 15, v ) );
        BoardCorner corner = CORNER_AT.get( key( cu, cv ) );
        if ( corner != null ) {
            return cornerArea( corner );
        }
        // windowOpen 是这个面「当前」是否开着窗：关着的时候中央 8×8 属于 BODY，
        // 窗槽在交互层面根本不存在——否则玩家对中央右键会把材质写进一个没有显示的窗槽，
        // 看起来像「伪装了个寂寞」。细工凿判断「点在不在中央」时传 true，这样关着也能点开。
        if ( windowOpen && isWindowCell( cu, cv ) ) {
            return BoardArea.WINDOW;
        }
        return AREAS[cu][cv];
    }

    /** 面的本地 (u, v)，调试用。 */
    public static int[] local( FaceDir face, Vec3d hit ) {
        LocalFrame frame = FRAMES.get( face );
        return new int[] { frame.u( hit ), frame.v( hit ) };
    }
}
