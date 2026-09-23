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
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 每个面的本地坐标系，按 {@link FaceDir#ordinal()} 直查。
     *
     * <p>热路径里每个 voxel 都要问好几次「这个面的 u/v 是哪个世界轴」，所以刻意用数组而不是
     * Map——Map 查询要装箱、要算哈希，在 4096 格的循环里是纯浪费。
     */
    private static final LocalFrame[] FRAMES = new LocalFrame[FaceDir.values().length];

    static {
        // 水平面：规格直接给出了 u/v 与 X/Z 的对应（UP 的 y=15..16、DOWN 的 y=0..1 等）。
        FRAMES[FaceDir.UP.ordinal()] = new LocalFrame( Direction.Axis.X, 1, Direction.Axis.Z, 1 );
        FRAMES[FaceDir.DOWN.ordinal()] = new LocalFrame( Direction.Axis.X, 1, Direction.Axis.Z, -1 );
        // 侧面：v 恒为 +Y，u = up × outward。
        FRAMES[FaceDir.NORTH.ordinal()] = new LocalFrame( Direction.Axis.X, 1, Direction.Axis.Y, 1 );
        FRAMES[FaceDir.SOUTH.ordinal()] = new LocalFrame( Direction.Axis.X, -1, Direction.Axis.Y, 1 );
        FRAMES[FaceDir.WEST.ordinal()] = new LocalFrame( Direction.Axis.Z, 1, Direction.Axis.Y, 1 );
        FRAMES[FaceDir.EAST.ordinal()] = new LocalFrame( Direction.Axis.Z, -1, Direction.Axis.Y, 1 );
    }

    private static LocalFrame frame( FaceDir face ) {
        return FRAMES[face.ordinal()];
    }

    /**
     * 缓存的枚举数组。
     *
     * <p>{@code values()} 每次调用都会克隆一份数组——在 4096 格的循环里直接写
     * {@code for ( Direction d : Direction.values() )} 就是每格新分配一个数组。
     * 这三个数组只读，任何线程都能安全共用。
     *
     * <p>刻意声明在类的最前面：后面所有静态表的初始化都可能间接用到它们。
     */
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final FaceDir[] FACES = FaceDir.values();
    private static final BoardLayer[] LAYERS = BoardLayer.values();

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

    /** 区域序号 → 优先级，省掉热路径里的一次枚举比较。 */
    private static final int[] AREA_PRIORITY = areaPriority( );

    private static int[] areaPriority( ) {
        int[] priority = new int[BoardArea.values().length];
        for ( BoardArea area : BoardArea.values() ) {
            priority[area.ordinal()] = priorityOf( area );
        }
        return priority;
    }

    /** 每格属于哪个区域（角已落到具体的边上），<b>不含窗</b>。 */
    private static final BoardArea[][] AREAS = areaMap( );

    /**
     * 角格子的面本地坐标 → 是哪个角，按 {@code u * 16 + v} 直查（256 项）。
     *
     * <p>与 {@link #FRAMES} 同理：热路径里每格都要问一次「这是不是角像素」，
     * 用 Map&lt;Integer, BoardCorner&gt; 会为每次查询装箱一个 Integer。
     */
    private static final BoardCorner[] CORNER_AT = cornerTable( );

    private static BoardCorner[] cornerTable( ) {
        BoardCorner[] table = new BoardCorner[256];
        table[key( 15, 0 )] = BoardCorner.TOP_RIGHT;
        table[key( 15, 15 )] = BoardCorner.BOTTOM_RIGHT;
        table[key( 0, 15 )] = BoardCorner.BOTTOM_LEFT;
        table[key( 0, 0 )] = BoardCorner.TOP_LEFT;
        return table;
    }

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

    /** 与 {@link #FRAMES} 同理，按 ordinal 直查，避免热路径里的 Map 装箱。 */
    private static final LocalDirs[] LOCAL_DIRS = new LocalDirs[FaceDir.values().length];

    static {
        LOCAL_DIRS[FaceDir.UP.ordinal()] =
                new LocalDirs( Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH );
        LOCAL_DIRS[FaceDir.DOWN.ordinal()] =
                new LocalDirs( Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH );
        LOCAL_DIRS[FaceDir.NORTH.ordinal()] =
                new LocalDirs( Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN );
        LOCAL_DIRS[FaceDir.SOUTH.ordinal()] =
                new LocalDirs( Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN );
        LOCAL_DIRS[FaceDir.WEST.ordinal()] =
                new LocalDirs( Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN );
        LOCAL_DIRS[FaceDir.EAST.ordinal()] =
                new LocalDirs( Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN );
    }

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
        LocalDirs dirs = LOCAL_DIRS[face.ordinal()];
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
        for ( FaceDir face : FACES ) {
            for ( BoardLayer layer : LAYERS ) {
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
            for ( FaceDir face : FACES ) {
                if ( face.getName().equals( name ) ) {
                    return face;
                }
            }
            return null;
        }

        @Nullable
        private static BoardLayer layerByName( String name ) {
            for ( BoardLayer layer : LAYERS ) {
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
        BoardCorner corner = CORNER_AT[key( u, v )];
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
        LocalFrame frame = frame( face );
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

    // ---------------------------------------------------------------- 槽位编码
    //
    // 4096 voxel 的拓扑解析一律用紧凑 int 表示「哪个面的哪一层的哪个区域」，
    // 只有在真正要跟方块实体的 materials Map / 最终输出对接时才拼字符串键。

    /** 区域数量，用来把 (槽位下标, 区域序号) 打包成一个 int。 */
    private static final int AREA_COUNT = BoardArea.values().length;

    /** {@code (槽位下标, 区域序号)} → 紧凑槽码，范围 0..71。 */
    private static int slotCode( FaceDir face, BoardLayer layer, BoardArea area ) {
        return LayeredBoardSlots.slotBit( face, layer ) * AREA_COUNT + area.ordinal();
    }

    private static int codeBit( int code ) {
        return code / AREA_COUNT;
    }

    private static int codeAreaOrdinal( int code ) {
        return code % AREA_COUNT;
    }

    private static FaceDir codeFace( int code ) {
        return faceOfSlot( codeBit( code ) );
    }

    private static BoardLayer codeLayer( int code ) {
        return layerOfSlot( codeBit( code ) );
    }

    private static BoardArea codeArea( int code ) {
        return BoardArea.values()[codeAreaOrdinal( code )];
    }

    /** 槽码 → 材质键字符串。热路径里不要调它。 */
    private static String codeName( int code ) {
        BoardArea area = codeArea( code );
        FaceDir face = codeFace( code );
        return area.isWindow( )
                ? LayeredBoardSlots.windowKey( face )
                : LayeredBoardSlots.materialKey( face, codeLayer( code ), area );
    }

    /**
     * 槽码按<b>槽名</b>排序的名次表。
     *
     * <p>候选的循环顺序必须与原来「按槽名排序」完全一致，而热路径里不能再拼字符串去比较，
     * 所以预先算一张名次表：排序时比 {@code RANK[code]}，等价于原来的
     * {@code Comparator.comparing(Slot::name)}，但一次字符串都不用建。
     */
    private static final int[] CODE_RANK = codeRanks( );

    private static int[] codeRanks( ) {
        int size = FaceDir.values().length * BoardLayer.values().length * AREA_COUNT;
        Integer[] order = new Integer[size];
        for ( int code = 0; code < size; code++ ) {
            order[code] = code;
        }
        // List.sort 是稳定排序：同名（同一个面的 OUTER / INNER 窗槽）的顺序由槽码顺序决定，可复现
        java.util.Arrays.sort( order, Comparator.comparing( LayeredBoardParts::codeName ) );
        int[] rank = new int[size];
        for ( int i = 0; i < size; i++ ) {
            rank[order[i]] = i;
        }
        return rank;
    }

    /**
     * 原地按槽名名次升序排序 + 去重，返回新的个数。
     *
     * <p>候选只有 1~3 个，所以用插入排序。全程不创建任何容器、不拼任何字符串——
     * 这是原先 {@code canonical()} 每格造 4 份容器 + 反复拼 key 的替代品。
     */
    private static int canonicalInPlace( int[] values, int base, int count ) {
        for ( int i = 1; i < count; i++ ) {
            int value = values[base + i];
            int rank = CODE_RANK[value];
            int j = i - 1;
            while ( j >= 0 && CODE_RANK[values[base + j]] > rank ) {
                values[base + j + 1] = values[base + j];
                j--;
            }
            values[base + j + 1] = value;
        }
        int unique = 0;
        for ( int i = 0; i < count; i++ ) {
            if ( unique == 0 || values[base + unique - 1] != values[base + i] ) {
                values[base + unique++] = values[base + i];
            }
        }
        return unique;
    }

    /**
     * physical edge run 的紧凑 identity：<b>层 + 世界轴向 + 另外两个固定坐标</b>，范围 0..1535。
     *
     * <p>刻意<b>不含</b>「是哪个 Face 的哪条 Edge」——几何上共线的 Face-edge 条本来就属于同一条
     * 物理棱，所以 NORTH 的右边与 EAST 的左边算出同一个 run。
     *
     * <p>也不含沿轴的长度范围：在同一个「层 + 轴 + 两个固定坐标」上，占位的格子永远是一整段连续
     * 区间（每条 Edge 条本身连续，且同一层的重合条共享同一段范围），所以范围不需要进 key。
     */
    private static int runCode( BoardLayer layer, Direction.Axis axis, int x, int y, int z ) {
        return ( ( layer.ordinal( ) * 3 + axis.ordinal( ) ) * 16 + planeFirst( axis, x, y, z ) ) * 16
                + planeSecond( axis, x, y, z );
    }

    /** {@link #runCode} 的存档键名——必须与改动前逐字符一致，只在需要查 override 时才拼。 */
    private static String runKey( int runCode ) {
        return "r" + ( runCode / 768 ) + ":" + ( ( runCode / 256 ) % 3 )
                + ":" + ( ( runCode / 16 ) % 16 ) + ":" + ( runCode % 16 );
    }

    /**
     * 多条 physical run 交汇点的存档 identity：接到这里的那几条 run 的键名，排序后连起来。
     *
     * <p>用 run 的 identity 而不是各 run 当前显示的槽，所以玩家改了某条 run 的 owner 之后，
     * 这个交汇点的 key 不会漂移（漂移的话原来的 override 就会失效）。
     */
    private static String cornerKey( int[] runs, int base, int count ) {
        String[] keys = new String[count];
        for ( int i = 0; i < count; i++ ) {
            keys[i] = runKey( runs[base + i] );
        }
        java.util.Arrays.sort( keys );
        return "c:" + String.join( "+", keys );
    }

    /**
     * 一条 physical run 的候选键：候选的「槽位下标 : 区域序号」按槽名排序后连起来。
     *
     * <p>编码的是<b>槽位本身</b>（{@code slotBit} 由面与层唯一决定，永远不会变），不是候选列表里的
     * 位置。这条 key 只用于「几何重合的格子」——那些候选只由占用掩码决定，不会被 override 改动，
     * 所以不会漂移。
     */
    private static String junctionKeyOfCodes( int[] candidates, int base, int count ) {
        StringBuilder builder = new StringBuilder( );
        for ( int i = 0; i < count; i++ ) {
            if ( builder.length( ) > 0 ) {
                builder.append( '+' );
            }
            builder.append( codeBit( candidates[base + i] ) )
                    .append( ':' ).append( codeAreaOrdinal( candidates[base + i] ) );
        }
        return builder.toString( );
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

    /** 这一格上几何完全重合的那几个槽码，写进 {@code out[base..]}，返回个数。 */
    private static int coincidingCodes( int present, int windows, int x, int y, int z, int[] out, int base ) {
        int count = 0;
        for ( int bit = 0; bit < SLOT_COUNT; bit++ ) {
            if ( ( present & ( 1 << bit ) ) == 0 ) {
                continue;
            }
            FaceDir face = faceOfSlot( bit );
            BoardLayer layer = layerOfSlot( bit );
            out[base + count++] = slotCode( face, layer, plateAreaOf(
                    localU( face, x, y, z ), localV( face, x, y, z ),
                    LayeredBoardSlots.hasWindow( windows, face ) ) );
        }
        return count;
    }

    /**
     * 角格上的交汇点：候选是<b>每条实际接到这里的 physical run 当前显示的槽</b>。
     *
     * <p>这一点是关键：某条 run 的 owner 可能是另一个 Face，它显示的槽与本 Face 在角上的默认
     * 区域并不是同一个；若改成「按 Face 各解析一次」，就会把一个屏幕上根本没显示的槽塞进候选，
     * 同时漏掉真正可见的那条边——表现就是「肉眼看得到三条边，凿子却只循环得出两条 + 一个待伪装」。
     *
     * <p>接法：看这一格的六个邻格。邻格必须<b>同一层</b>、且落在某条 Edge 条上；那个邻格所在的
     * run 就是接到这个交汇点的一条 run，它当前显示的槽就是这条可见边。
     *
     * <p>槽码写进 {@code out[base..]}，入射 run 码写进 {@code runs[runsBase..]}，返回候选个数。
     */
    private static int collectCorner( int[] present, int[] owner, int windows, int x, int y, int z,
                                      int[] out, int base, int[] runs, int runsBase ) {
        BoardLayer layer = layerOfSlot( Integer.numberOfTrailingZeros( owner[index( x, y, z )] ) );
        int count = 0;
        int runCount = 0;
        for ( Direction direction : DIRECTIONS ) {
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
            int run = runCode( layer, axis, nx, ny, nz );
            boolean seen = false;
            for ( int i = 0; i < runCount; i++ ) {
                if ( runs[runsBase + i] == run ) {
                    seen = true;
                    break;
                }
            }
            if ( seen ) {
                continue;
            }
            runs[runsBase + runCount++] = run;
            out[base + count++] = slotCode( neighbourFace, layer, neighbourArea );
        }
        return count;
    }

    /**
     * 没有 override 时交汇点显示哪条边。
     *
     * <p>优先用 owner 板自己在这个角上的默认区域——它按构造一定等于接到这里某一条 run 的显示槽，
     * 所以默认画面不会跳。万一不等（比如那条 run 被玩家改过），退回候选里的第一个。
     */
    private static int defaultCornerCode( int ownerBit, int[] candidates, int base, int count,
                                          int u, int v ) {
        int bit = Integer.numberOfTrailingZeros( ownerBit );
        FaceDir face = faceOfSlot( bit );
        BoardLayer layer = layerOfSlot( bit );
        BoardCorner corner = CORNER_AT[key( u, v )];
        if ( corner != null ) {
            int preferred = slotCode( face, layer, cornerArea( corner ) );
            for ( int i = 0; i < count; i++ ) {
                if ( candidates[base + i] == preferred ) {
                    return preferred;
                }
            }
        }
        return candidates[base];
    }

    /** 把 override 套到默认显示上；override 指向的槽已经不在候选里就自动回退默认。 */
    private static int applyOverrideCode( int[] candidates, int base, int count, int fallback,
                                          Map<String, String> overrides, String key ) {
        String chosen = overrides.get( key );
        if ( chosen == null ) {
            return fallback;
        }
        for ( int i = 0; i < count; i++ ) {
            if ( codeName( candidates[base + i] ).equals( chosen ) ) {
                return candidates[base + i];
            }
        }
        return fallback;
    }

    // resolveInto 的输出布局
    private static final int OUT_COUNT = 0;
    private static final int OUT_SHOWN = 1;
    private static final int OUT_CORNER = 2;
    private static final int OUT_CANDIDATES = 3;
    private static final int OUT_MAX_CANDIDATES = 6;
    private static final int OUT_RUNS = OUT_CANDIDATES + OUT_MAX_CANDIDATES;
    private static final int OUT_RUN_COUNT = OUT_RUNS + OUT_MAX_CANDIDATES;
    /** 一次解析需要的 scratch 长度。 */
    private static final int RESOLVE_SCRATCH = OUT_RUN_COUNT + 1;

    /**
     * 一格最终「显示什么 / 可切换什么」的完整解析，全部用槽码表示，全程无分配。
     *
     * <p>渲染、材质点击、扳手、命中判定共用这一个判断，所以「画面显示 A Edge，右键材质却操作
     * B Edge」不可能发生。结果写进调用方给的 {@code out}：
     * <ul>
     *   <li>{@code out[OUT_COUNT]} —— 候选个数；0/1 表示这一格没有可切换的东西</li>
     *   <li>{@code out[OUT_SHOWN]} —— 实际显示的槽码</li>
     *   <li>{@code out[OUT_CORNER]} —— 1 表示这是一个「多条 run 相接」的交汇点</li>
     *   <li>{@code out[OUT_CANDIDATES..]} —— 候选槽码（按槽名排序去重）</li>
     *   <li>{@code out[OUT_RUNS..]} —— 入射 run 码，个数在 {@code out[OUT_RUN_COUNT]}</li>
     * </ul>
     */
    private static int resolveInto( int[] present, int[] owner, int windows, Map<String, String> overrides,
                                    int x, int y, int z, int[] out ) {
        int at = index( x, y, z );
        int ownerBit = owner[at];
        int bit = Integer.numberOfTrailingZeros( ownerBit );
        FaceDir face = faceOfSlot( bit );
        BoardLayer layer = layerOfSlot( bit );
        int u = localU( face, x, y, z );
        int v = localV( face, x, y, z );
        int ownerCode = slotCode( face, layer, plateAreaOf( u, v,
                LayeredBoardSlots.hasWindow( windows, face ) ) );

        boolean corner = CORNER_AT[key( u, v )] != null;
        out[OUT_CORNER] = corner ? 1 : 0;
        int count;
        if ( corner ) {
            count = collectCorner( present, owner, windows, x, y, z,
                    out, OUT_CANDIDATES, out, OUT_RUNS );
            out[OUT_RUN_COUNT] = count;
        } else {
            count = coincidingCodes( present[at], windows, x, y, z, out, OUT_CANDIDATES );
            out[OUT_RUN_COUNT] = 0;
        }
        if ( count < 2 ) {
            // 单候选：没有可切换的东西，直接返回，不排序、不查 override、不建任何容器
            out[OUT_COUNT] = count;
            out[OUT_SHOWN] = ownerCode;
            return count;
        }
        count = canonicalInPlace( out, OUT_CANDIDATES, count );
        out[OUT_COUNT] = count;
        int fallback = corner ? defaultCornerCode( ownerBit, out, OUT_CANDIDATES, count, u, v ) : ownerCode;
        int shown = fallback;
        if ( overrides != null && !overrides.isEmpty( ) ) {
            // 只有真的要查 override 时才拼 key 字符串
            String key = corner
                    ? cornerKey( out, OUT_RUNS, out[OUT_RUN_COUNT] )
                    : junctionKeyOfCodes( out, OUT_CANDIDATES, count );
            shown = applyOverrideCode( out, OUT_CANDIDATES, count, fallback, overrides, key );
        }
        out[OUT_SHOWN] = shown;
        return count;
    }

    /** 某一格最终显示的槽码——热路径入口，不建任何对象。 */
    private static int shownCode( int[] present, int[] owner, int windows,
                                  Map<String, String> overrides, int x, int y, int z, int[] out ) {
        resolveInto( present, owner, windows, overrides, x, y, z, out );
        return out[OUT_SHOWN];
    }

    // ---------------------------------------------------------------- 结构缓存
    //
    // 只依赖 (occupancy, windows) 的那部分派生结果被缓存起来。1000 个结构完全相同的薄板
    // 因此只解析一次 4096 voxel，其余全部命中；junction override 与材质都不进缓存。

    /**
     * 只依赖 {@code (occupancy, windows)} 的结构派生结果，构建完成后<b>完全只读</b>。
     *
     * <p>刻意不持有任何 per-block 的东西：没有方块实体、World、BlockPos、materials、
     * junctionOwners。所以同一个 entry 可以被任意多个方块、任意多个线程同时读。
     *
     * <p>这里也没有任何 scratch：可复用的临时缓冲一律留在 {@link #boxesByKey} 的调用栈上。
     */
    private static final class Topology {
        /** 每格的 owner 板（槽位位掩码）。 */
        private final int[] owner;
        /** 每格在<b>没有 override</b> 时显示的槽码。只读，任何路径都不得原地修改。 */
        private final int[] baseCode;
        /** 这个结构属于哪个 (occupancy, windows)，建 junction 表时要重新跑一遍时用。 */
        private final int occupancy;
        private final int windows;
        /**
         * 交汇点表，<b>惰性构建</b>：只有真的有方块带着 junction override 来查时才需要它。
         * 绝大多数方块没有 override，所以绝大多数结构根本不会为它付出代价。
         */
        private volatile JunctionTables tables;

        Topology( int[] owner, int[] baseCode, int occupancy, int windows ) {
            this.owner = owner;
            this.baseCode = baseCode;
            this.occupancy = occupancy;
            this.windows = windows;
        }

        JunctionTables tables( ) {
            JunctionTables built = tables;
            if ( built != null ) {
                return built;
            }
            synchronized ( this ) {
                if ( tables == null ) {
                    tables = buildJunctionTables( occupancy, windows );
                }
                return tables;
            }
        }

        /** 估算的常驻字节数——只用于上报和给缓存容量找依据，不是精确计量。 */
        int estimatedBytes( ) {
            int bytes = 16 + 3 * 4 + 8 * 3;
            bytes += 16 + owner.length * 4;
            bytes += 16 + baseCode.length * 4;
            JunctionTables built = tables;
            if ( built != null ) {
                bytes += built.estimatedBytes( );
            }
            return bytes;
        }
    }

    /** 一个结构的交汇点表：identity ↔ 它覆盖的格子 / 它的候选。 */
    private static final class JunctionTables {
        private final String[] keys;
        private final int[][] cells;
        private final int[][] candidates;
        private final Map<String, Integer> index;

        JunctionTables( String[] keys, int[][] cells, int[][] candidates, Map<String, Integer> index ) {
            this.keys = keys;
            this.cells = cells;
            this.candidates = candidates;
            this.index = index;
        }

        int estimatedBytes( ) {
            int bytes = 16 + 4 * 4;
            bytes += 16 + keys.length * 4;
            for ( String key : keys ) {
                bytes += 48 + key.length( ) * 2;
            }
            for ( int[] list : cells ) {
                bytes += 16 + list.length * 4;
            }
            for ( int[] list : candidates ) {
                bytes += 16 + list.length * 4;
            }
            bytes += index.size( ) * 48;
            return bytes;
        }
    }

    /** 缓存里的一项：结构 + 一个给近似 LRU 用的「最近被命中过」标记。 */
    private static final class TopologyEntry {
        private final Topology topology;
        private volatile boolean referenced;

        TopologyEntry( Topology topology ) {
            this.topology = topology;
        }
    }

    /**
     * 有界结构缓存。
     *
     * <p>容量按「一个 entry 的实测体积」定：一个 entry 固定就要两个 {@code int[4096]}（owner +
     * baseCode）≈ 32KB，再加交汇点表。所以 1024 个 entry 就是 33MB 起步，对客户端来说过于激进；
     * 这里取 {@value #TOPOLOGY_CACHE_CAPACITY} 个，上限约 9MB，而真实存档里出现过的
     * (occupancy, windows) 组合通常只有几十种，命中率不会因此下降。
     *
     * <p>{@code ConcurrentHashMap} 只保护「查表 / 放表」；结构对象的构建在锁外完成，
     * 所以不会有全局大锁把区块烘焙串行化。两个线程同时 miss 同一个 key 时最多重复算一次，
     * 谁先放进表谁赢，两边拿到的都是**同一份**不可变结构，输出因此必然一致。
     */
    private static final int TOPOLOGY_CACHE_CAPACITY = 256;

    private static final ConcurrentHashMap<Integer, TopologyEntry> TOPOLOGY_CACHE = new ConcurrentHashMap<>();

    /** {@code (occupancy, windows)} → 紧凑 key：低 12 位占用、高 6 位窗。 */
    private static int topologyKey( int occupancy, int windows ) {
        return occupancy | ( windows << 12 );
    }

    private static Topology topology( int occupancy, int windows ) {
        int occ = occupancy & LayeredBoardSlots.FULL_OCCUPANCY;
        int win = windows & ( ( 1 << FaceDir.values().length ) - 1 );
        int key = topologyKey( occ, win );
        TopologyEntry cached = TOPOLOGY_CACHE.get( key );
        if ( cached != null ) {
            cached.referenced = true;
            return cached.topology;
        }
        // 构建刻意放在锁外：computeIfAbsent 会在构建期间占住桶锁，几百微秒的构建会把同桶的
        // 其它线程一起堵住。宁可允许重复构建一次，也不要让区块烘焙互相等。
        Topology built = buildTopology( occ, win );
        TopologyEntry entry = new TopologyEntry( built );
        TopologyEntry winner = TOPOLOGY_CACHE.putIfAbsent( key, entry );
        if ( winner != null ) {
            winner.referenced = true;
            return winner.topology;
        }
        evictIfOverCapacity( );
        return built;
    }

    /**
     * 近似 CLOCK 淘汰：只在超容量时走一趟，把「自上次淘汰以来没被命中过」的 entry 删掉，
     * 命中过的给一次机会。没有全局锁，也没有每 entry 的时间戳。
     *
     * <p>淘汰纯粹是性能问题：被删掉的结构下次重新解析即可，输出不变。
     */
    private static void evictIfOverCapacity( ) {
        if ( TOPOLOGY_CACHE.size( ) <= TOPOLOGY_CACHE_CAPACITY ) {
            return;
        }
        int scanned = 0;
        int scanLimit = TOPOLOGY_CACHE_CAPACITY * 4;
        for ( Map.Entry<Integer, TopologyEntry> candidate : TOPOLOGY_CACHE.entrySet( ) ) {
            if ( TOPOLOGY_CACHE.size( ) <= TOPOLOGY_CACHE_CAPACITY || scanned++ >= scanLimit ) {
                break;
            }
            TopologyEntry value = candidate.getValue( );
            if ( value.referenced ) {
                value.referenced = false;
                continue;
            }
            TOPOLOGY_CACHE.remove( candidate.getKey( ), value );
        }
    }

    /**
     * 在占用掩码的有效范围内，给每一格定下唯一的 owner 与它显示的槽码，并把交汇点的拓扑记下来。
     *
     * <p>只有 owner 会写这一格：非 owner 的板在这一格上完全不生成几何，也不生成命中区域。
     * scratch 只分配一次、逐格复用，整趟解析不产生额外对象。
     */
    private static Topology buildTopology( int occupancy, int windows ) {
        Ownership ownership = ownership( occupancy );
        int[] present = ownership.present();
        int[] owner = ownership.owner();
        int[] baseCode = new int[CELLS];
        int[] scratch = new int[RESOLVE_SCRATCH];
        for ( int at = 0; at < CELLS; at++ ) {
            if ( owner[at] == 0 ) {
                continue;
            }
            resolveInto( present, owner, windows, null, at >> 8, ( at >> 4 ) & 15, at & 15, scratch );
            baseCode[at] = scratch[OUT_SHOWN];
        }
        // 交付路径与缓存之前完全一致：这里不做任何交汇点分组，所以 miss 的代价 == 原来那一次 bake
        return new Topology( owner, baseCode, occupancy, windows );
    }

    /**
     * 建交汇点表——只在真的有方块带 override 来查时才跑一次。
     *
     * <p>这里按「结构身份」分组：先用一个便宜的 long 散列粗筛，命中后再逐元素精确比对，
     * 所以既不需要给每个 junction 格都拼一次 key 字符串，也不可能误合并。
     */
    private static JunctionTables buildJunctionTables( int occupancy, int windows ) {
        Ownership ownership = ownership( occupancy );
        int[] present = ownership.present();
        int[] owner = ownership.owner();
        int[] scratch = new int[RESOLVE_SCRATCH];

        long[] ids = new long[16];
        boolean[] corners = new boolean[16];
        IntList[] cellLists = new IntList[16];
        IntList[] candidateLists = new IntList[16];
        IntList[] runLists = new IntList[16];
        int distinct = 0;

        for ( int at = 0; at < CELLS; at++ ) {
            if ( owner[at] == 0 ) {
                continue;
            }
            int count = resolveInto( present, owner, windows, null,
                    at >> 8, ( at >> 4 ) & 15, at & 15, scratch );
            if ( count < 2 ) {
                continue;
            }
            boolean corner = scratch[OUT_CORNER] == 1;
            int runCount = corner ? scratch[OUT_RUN_COUNT] : 0;
            long id = identityHash( corner, scratch, count, runCount );

            int slot = -1;
            for ( int i = 0; i < distinct; i++ ) {
                if ( ids[i] == id && corners[i] == corner
                        && sameIdentity( candidateLists[i], runLists[i], scratch, count, runCount ) ) {
                    slot = i;
                    break;
                }
            }
            if ( slot < 0 ) {
                if ( distinct == ids.length ) {
                    int grown = distinct * 2;
                    ids = java.util.Arrays.copyOf( ids, grown );
                    corners = java.util.Arrays.copyOf( corners, grown );
                    cellLists = java.util.Arrays.copyOf( cellLists, grown );
                    candidateLists = java.util.Arrays.copyOf( candidateLists, grown );
                    runLists = java.util.Arrays.copyOf( runLists, grown );
                }
                slot = distinct++;
                ids[slot] = id;
                corners[slot] = corner;
                cellLists[slot] = new IntList( );
                candidateLists[slot] = new IntList( );
                runLists[slot] = new IntList( );
                for ( int i = 0; i < count; i++ ) {
                    candidateLists[slot].add( scratch[OUT_CANDIDATES + i] );
                }
                for ( int i = 0; i < runCount; i++ ) {
                    runLists[slot].add( scratch[OUT_RUNS + i] );
                }
            }
            cellLists[slot].add( at );
        }

        // key 字符串只在这里、每个<b>不同</b>的交汇点拼一次，而不是每个交汇格拼一次
        String[] keys = new String[distinct];
        int[][] cells = new int[distinct][];
        int[][] candidates = new int[distinct][];
        Map<String, Integer> index = new java.util.HashMap<>( Math.max( 4, distinct * 2 ) );
        for ( int i = 0; i < distinct; i++ ) {
            int[] candidateCodes = candidateLists[i].toArray( );
            keys[i] = corners[i]
                    ? cornerKey( runLists[i].toArray( ), 0, runLists[i].size( ) )
                    : junctionKeyOfCodes( candidateCodes, 0, candidateCodes.length );
            cells[i] = cellLists[i].toArray( );
            candidates[i] = candidateCodes;
            index.put( keys[i], i );
        }
        return new JunctionTables( keys, cells, candidates, Map.copyOf( index ) );
    }

    /** 交汇点结构身份的粗筛散列。 */
    private static long identityHash( boolean corner, int[] out, int count, int runCount ) {
        long hash = corner ? 1L : 2L;
        hash = hash * 31 + count;
        for ( int i = 0; i < count; i++ ) {
            hash = hash * 31 + out[OUT_CANDIDATES + i];
        }
        for ( int i = 0; i < runCount; i++ ) {
            hash = hash * 31 + out[OUT_RUNS + i];
        }
        return hash;
    }

    /** 散列相等之后的精确比对。 */
    private static boolean sameIdentity( IntList candidates, IntList runs, int[] out, int count, int runCount ) {
        if ( candidates.size( ) != count || runs.size( ) != runCount ) {
            return false;
        }
        for ( int i = 0; i < count; i++ ) {
            if ( candidates.get( i ) != out[OUT_CANDIDATES + i] ) {
                return false;
            }
        }
        for ( int i = 0; i < runCount; i++ ) {
            if ( runs.get( i ) != out[OUT_RUNS + i] ) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把当前方块的 junction override 套到缓存的基础槽码上。
     *
     * <p><b>没有 override 时直接返回缓存里的数组本身</b>（只读，不复制）；有 override 时才克隆
     * 一份 per-call 的工作副本再改。所以任何一个方块改过 junction owner，都绝不可能改到共享的
     * 结构缓存上、把别的方块带偏。
     */
    private static int[] codesWithOverrides( Topology topology, Map<String, String> overrides ) {
        if ( overrides == null || overrides.isEmpty( ) ) {
            return topology.baseCode;
        }
        JunctionTables tables = topology.tables( );
        if ( tables.index.isEmpty( ) ) {
            return topology.baseCode;
        }
        int[] codes = null;
        for ( Map.Entry<String, String> override : overrides.entrySet( ) ) {
            Integer junction = tables.index.get( override.getKey( ) );
            if ( junction == null ) {
                continue;
            }
            int chosen = -1;
            for ( int candidate : tables.candidates[junction] ) {
                if ( codeName( candidate ).equals( override.getValue( ) ) ) {
                    chosen = candidate;
                    break;
                }
            }
            if ( chosen < 0 ) {
                // override 指向的槽已经不在候选里 → 保持默认，自动回退
                continue;
            }
            if ( codes == null ) {
                codes = topology.baseCode.clone( );
            }
            for ( int cell : tables.cells[junction] ) {
                codes[cell] = chosen;
            }
        }
        return codes == null ? topology.baseCode : codes;
    }

    /** {@link #worldCell} 的逆：格子 (x,y,z) 落在该板面本地的哪个 u。 */
    private static int localU( FaceDir face, int x, int y, int z ) {
        int a = face == FaceDir.WEST || face == FaceDir.EAST ? z : x;
        return frame( face ).uSign( ) > 0 ? a : 15 - a;
    }

    /** {@link #worldCell} 的逆：格子 (x,y,z) 落在该板面本地的哪个 v。 */
    private static int localV( FaceDir face, int x, int y, int z ) {
        int b = face == FaceDir.UP || face == FaceDir.DOWN ? z : y;
        return frame( face ).vSign( ) > 0 ? b : 15 - b;
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
        LocalFrame frame = frame( face );
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
        for ( FaceDir face : FACES ) {
            for ( BoardLayer layer : LAYERS ) {
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
        LocalFrame frame = frame( face );
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
     * 平面身份直查表：{@code [direction.ordinal()][along]}，along 取 0..16。
     *
     * <p>每个发出的格子面都要拿一个 {@link PlaneKey} 当 Map 键，直接查表就不必每个面 new 一个。
     * 表里一共 6 × 17 个不可变实例，只读，任何线程都能安全共用。
     */
    private static final PlaneKey[][] PLANE_KEYS = planeKeyTable();

    private static PlaneKey[][] planeKeyTable( ) {
        Direction[] directions = Direction.values();
        PlaneKey[][] table = new PlaneKey[directions.length][17];
        for ( Direction direction : directions ) {
            for ( int along = 0; along <= 16; along++ ) {
                table[direction.ordinal()][along] = new PlaneKey( direction.getAxis(), along, direction );
            }
        }
        return table;
    }

    private static PlaneKey planeKey( Direction direction, int along ) {
        return PLANE_KEYS[direction.ordinal()][along];
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
        Topology topology = topology( occupancy, windows );
        int[] owner = topology.owner;
        int[] codes = codesWithOverrides( topology, junctionOwners );

        // 逐格逐方向挑出暴露的面，按「平面 + 槽码」分组。
        // 这里刻意用槽码而不是材质键字符串做 key：字符串只在最后真正输出时才拼，
        // 4096 格的解析里一次都不建。
        Map<PlaneKey, Map<Integer, IntList>> planes = new LinkedHashMap<>();
        for ( int x = 0; x < 16; x++ ) {
            for ( int y = 0; y < 16; y++ ) {
                for ( int z = 0; z < 16; z++ ) {
                    int at = index( x, y, z );
                    if ( owner[at] == 0 ) {
                        continue;
                    }
                    // 用缓存的数组做下标循环：Direction.values() 每次调用都会克隆一份数组
                    for ( int d = 0; d < DIRECTIONS.length; d++ ) {
                        Direction direction = DIRECTIONS[d];
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
                                // 本板内部、同一个槽 → 板内部的面
                                if ( codes[at] == codes[nAt] ) {
                                    continue;
                                }
                                // 本板、不同槽：只有 BODY / WINDOW 的接触属于这一种。
                                // 按区域优先级裁决，四条窗内壁走同一个对称算法。
                                if ( AREA_PRIORITY[codeAreaOrdinal( codes[at] )]
                                        <= AREA_PRIORITY[codeAreaOrdinal( codes[nAt] )] ) {
                                    continue;
                                }
                            }
                        }
                        addPlaneCell( planes, direction, x, y, z, codes[at] );
                    }
                }
            }
        }

        // 每个平面上做一次贪心矩形合并；槽码 → 材质键字符串只在这里发生一次（每个不同槽一次）。
        // scratch 是这一次调用的局部对象，随调用栈传递，不与并行的区块烘焙共享。
        MergeScratch scratch = new MergeScratch();
        Map<String, List<Box>> boxes = new LinkedHashMap<>();
        for ( Map.Entry<PlaneKey, Map<Integer, IntList>> planeEntry : planes.entrySet() ) {
            PlaneKey plane = planeEntry.getKey();
            for ( Map.Entry<Integer, IntList> codeEntry : planeEntry.getValue().entrySet() ) {
                List<Box> list = boxes.computeIfAbsent( codeName( codeEntry.getKey() ),
                        unused -> new ArrayList<>() );
                int count = mergeRectangles( codeEntry.getValue(), scratch );
                for ( int i = 0; i < count; i++ ) {
                    int base = i * 4;
                    list.add( planeBox( plane, scratch.rects[base], scratch.rects[base + 1],
                            scratch.rects[base + 2], scratch.rects[base + 3] ) );
                }
            }
        }
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

    /**
     * 一次 {@link #boxesByKey} 调用内的可增长 int 列表。
     *
     * <p>替代 {@code ArrayList<Integer>}：平面上的格子现在打包成一个 int，用原始数组存就既不用
     * 每个格子 new 一个 {@code int[2]}，也不会逐元素装箱。
     */
    private static final class IntList {
        private int[] values = new int[8];
        private int size;

        void add( int value ) {
            if ( size == values.length ) {
                values = java.util.Arrays.copyOf( values, size * 2 );
            }
            values[size++] = value;
        }

        int get( int index ) {
            return values[index];
        }

        int size( ) {
            return size;
        }

        int[] toArray( ) {
            return java.util.Arrays.copyOf( values, size );
        }
    }

    /** 平面上的格子：两个坐标各 0..15，打包成一个 int。 */
    private static int packCell( int first, int second ) {
        return first | ( second << 4 );
    }

    private static int cellFirst( int packed ) {
        return packed & 15;
    }

    private static int cellSecond( int packed ) {
        return ( packed >> 4 ) & 15;
    }

    /**
     * 一次 {@link #boxesByKey} 调用内复用的合并 scratch。
     *
     * <p>刻意<b>由调用方创建、随调用栈传递</b>，绝不做 static 全局共享：区块网格可以并行烘焙，
     * 多个线程同时跑 {@code boxesByKey} 时各用自己的一份，不会互相踩。生命周期就是一次调用，
     * 所以也不需要任何同步。
     */
    private static final class MergeScratch {
        private final boolean[] grid = new boolean[256];
        private final boolean[] duplicatedFlag = new boolean[256];
        /** 一个平面上最多 256 个矩形，每个 4 个 int。 */
        private final int[] rects = new int[256 * 4];

        /** 填格子；返回是否出现过重复登记。 */
        boolean fill( IntList cells ) {
            java.util.Arrays.fill( grid, false );
            java.util.Arrays.fill( duplicatedFlag, false );
            boolean duplicated = false;
            for ( int i = 0; i < cells.size(); i++ ) {
                int packed = cells.get( i );
                int at = ( cellSecond( packed ) << 4 ) | cellFirst( packed );
                if ( grid[at] ) {
                    duplicatedFlag[at] = true;
                    duplicated = true;
                }
                grid[at] = true;
            }
            return duplicated;
        }

        String firstDuplicate( ) {
            for ( int at = 0; at < 256; at++ ) {
                if ( duplicatedFlag[at] ) {
                    return ( at & 15 ) + "," + ( at >> 4 );
                }
            }
            return "?";
        }

        boolean occupied( int first, int second ) {
            return grid[( second << 4 ) | first];
        }

        void clear( int first, int second ) {
            grid[( second << 4 ) | first] = false;
        }
    }

    /** 把一格的某个面登记到它所在的平面上。 */
    private static void addPlaneCell( Map<PlaneKey, Map<Integer, IntList>> planes,
                                      Direction direction, int x, int y, int z, int code ) {
        // 格子 (x,y,z) 朝 direction 的那一面，就在该格子朝 direction 的那条边界上
        int along = switch ( direction ) {
            case DOWN, UP -> y + ( direction == Direction.UP ? 1 : 0 );
            case NORTH, SOUTH -> z + ( direction == Direction.SOUTH ? 1 : 0 );
            case WEST, EAST -> x + ( direction == Direction.EAST ? 1 : 0 );
        };
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
        planes.computeIfAbsent( planeKey( direction, along ), unused -> new LinkedHashMap<>() )
                .computeIfAbsent( code, unused -> new IntList() )
                .add( packCell( first, second ) );
    }

    /** 把平面上的格子集合贪心合并成互不重叠的矩形，写进 {@code scratch.rects}，返回矩形个数。 */
    private static int mergeRectangles( IntList cells, MergeScratch scratch ) {
        if ( scratch.fill( cells ) ) {
            throw new IllegalStateException(
                    "layered_copyboard 自检失败：平面内格子 " + scratch.firstDuplicate( ) + " 被同一材质重复登记" );
        }
        int count = 0;
        for ( int second = 0; second < 16; second++ ) {
            for ( int first = 0; first < 16; first++ ) {
                if ( !scratch.occupied( first, second ) ) {
                    continue;
                }
                int width = 1;
                while ( first + width < 16 && scratch.occupied( first + width, second ) ) {
                    width++;
                }
                int height = 1;
                outer:
                while ( second + height < 16 ) {
                    for ( int i = 0; i < width; i++ ) {
                        if ( !scratch.occupied( first + i, second + height ) ) {
                            break outer;
                        }
                    }
                    height++;
                }
                for ( int i = 0; i < width; i++ ) {
                    for ( int j = 0; j < height; j++ ) {
                        scratch.clear( first + i, second + j );
                    }
                }
                int base = count * 4;
                scratch.rects[base] = first;
                scratch.rects[base + 1] = second;
                scratch.rects[base + 2] = first + width;
                scratch.rects[base + 3] = second + height;
                count++;
            }
        }
        return count;
    }

    /** 平面上的一个矩形 → 方块本地 0..1 坐标的盒子。 */
    private static Box planeBox( PlaneKey plane, int firstMin, int secondMin, int firstMax, int secondMax ) {
        double along = plane.along() / 16.0;
        double firstLow = firstMin / 16.0;
        double secondLow = secondMin / 16.0;
        double firstHigh = firstMax / 16.0;
        double secondHigh = secondMax / 16.0;
        return switch ( plane.axis() ) {
            case X -> new Box( along, firstLow, secondLow, along, firstHigh, secondHigh );
            case Y -> new Box( firstLow, along, secondLow, firstHigh, along, secondHigh );
            case Z -> new Box( firstLow, secondLow, along, firstHigh, secondHigh, along );
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
     * <p>与 {@link #boxesByKey} 用同一个 {@link #resolveInto}：材质点击、扳手、以及「命中在哪条
     * 边上」全部按它判定，所以共享几何上<b>画面显示哪条边，右键就操作哪条边</b>。
     * 对象只在这一次交互里建，不在 4096 格的 bake 路径上。
     */
    @Nullable
    public static Slot slotAt( int occupancy, int windows, Map<String, String> junctionOwners, Vec3d hit ) {
        Ownership ownership = ownership( occupancy );
        int at = nearestCell( ownership.owner(), hit );
        if ( at < 0 ) {
            return null;
        }
        int[] out = new int[RESOLVE_SCRATCH];
        resolveInto( ownership.present(), ownership.owner(), windows, junctionOwners,
                at >> 8, ( at >> 4 ) & 15, at & 15, out );
        return slotOf( out[OUT_SHOWN] );
    }

    private static Slot slotOf( int code ) {
        return new Slot( codeFace( code ), codeLayer( code ), codeArea( code ) );
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
        int[] out = new int[RESOLVE_SCRATCH];
        int count = resolveInto( ownership.present(), ownership.owner(), windows, junctionOwners, x, y, z, out );
        if ( count < 2 ) {
            return null;
        }
        List<Slot> candidates = new ArrayList<>( count );
        for ( int i = 0; i < count; i++ ) {
            candidates.add( slotOf( out[OUT_CANDIDATES + i] ) );
        }
        candidates = List.copyOf( candidates );
        boolean corner = out[OUT_CORNER] == 1;
        String key = corner
                ? cornerKey( out, OUT_RUNS, out[OUT_RUN_COUNT] )
                : junctionKeyOfCodes( out, OUT_CANDIDATES, count );
        return new Junction( key, candidates, slotOf( out[OUT_SHOWN] ), corner );
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
        BoardCorner corner = CORNER_AT[key( cu, cv )];
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
        LocalFrame frame = frame( face );
        return new int[] { frame.u( hit ), frame.v( hit ) };
    }
}
