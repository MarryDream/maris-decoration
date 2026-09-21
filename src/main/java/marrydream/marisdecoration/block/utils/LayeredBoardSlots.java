package marrydream.marisdecoration.block.utils;

import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code layered_copycat_board} 的状态寻址。
 *
 * <p>整块有 <b>6 个面 × 2 层 = 12 个 1px 槽位</b>，这些槽位是「存在与否」的独立状态，
 * 全部塞进方块实体的一个 12 位掩码（见 {@link #slotBit}）。方块状态里<b>没有</b>任何
 * 表示槽位的属性——12 个 {@code BooleanProperty} 会让方块状态组合爆炸到 4096 种，
 * 而占用信息本来就是方块实体级别的数据。
 *
 * <p>材质槽的数量与槽位不同：
 * <ul>
 *   <li>每个「面 + 层」各有 {@code BODY / TOP_EDGE / BOTTOM_EDGE / LEFT_EDGE / RIGHT_EDGE}
 *       五个独立材质槽；</li>
 *   <li>Corner 没有自己的材质槽，它只是借用相邻两条边之一（{@link BoardCorner}）；</li>
 *   <li>Window 属于<b>面</b>而不是层：一个面只有一份窗材质，该面的 OUTER / INNER 共用它。</li>
 * </ul>
 * 于是每个面有 {@code 2×5 + 1 = 11} 个材质槽，整块 66 个，键名见 {@link #materialKey}。
 */
public final class LayeredBoardSlots {

    /** 六个面。顺序与 {@link Direction#values()} 一致，可以按 ordinal 互查。 */
    public enum FaceDir {
        DOWN( "down" ),
        UP( "up" ),
        NORTH( "north" ),
        SOUTH( "south" ),
        WEST( "west" ),
        EAST( "east" );

        private final String name;

        FaceDir( String name ) {
            this.name = name;
        }

        /** 小写名字，用于材质键名与 NBT 键名。 */
        public String getName( ) {
            return name;
        }

        public Direction toDirection( ) {
            return Direction.values()[ordinal()];
        }

        /** 反方向。{@link #values()} 的顺序保证相反方向总是成对相邻（0↔1、2↔3、4↔5）。 */
        public FaceDir opposite( ) {
            return VALUES[ordinal() ^ 1];
        }
    }

    public static final List<FaceDir> FACES = List.of( FaceDir.values() );

    private static final FaceDir[] VALUES = FaceDir.values();

    /** {@link Direction#ordinal()} 到 {@link FaceDir} 的直查表。 */
    private static final FaceDir[] BY_DIRECTION = new FaceDir[Direction.values().length];

    static {
        for ( FaceDir face : VALUES ) {
            BY_DIRECTION[face.toDirection().ordinal()] = face;
        }
    }

    @Nullable
    public static FaceDir of( Direction direction ) {
        return BY_DIRECTION[direction.ordinal()];
    }

    /** 同一个面的两层：OUTER 贴方块边界，INNER 再向内 1px。两层互相独立。 */
    public enum BoardLayer {
        OUTER( "outer" ),
        INNER( "inner" );

        private final String name;

        BoardLayer( String name ) {
            this.name = name;
        }

        public String getName( ) {
            return name;
        }
    }

    public static final List<BoardLayer> LAYERS = List.of( BoardLayer.values() );

    /** 面的矩形分区。坐标是面本地 (u, v)，16×16 像素。 */
    public enum BoardArea {
        WINDOW( "window" ),
        BODY( "body" ),
        TOP_EDGE( "top_edge" ),
        BOTTOM_EDGE( "bottom_edge" ),
        LEFT_EDGE( "left_edge" ),
        RIGHT_EDGE( "right_edge" );

        private final String name;

        BoardArea( String name ) {
            this.name = name;
        }

        public String getName( ) {
            return name;
        }

        public boolean isWindow( ) {
            return this == WINDOW;
        }
    }

    /**
     * 四个角。Corner <b>没有独立材质槽</b>，只保存「当前归属哪条相邻边」这一个 bit。
     *
     * <p>构造参数是两条相邻边，顺序即 owner bit 的语义：{@code bit = 0} 取第一条，
     * {@code bit = 1} 取第二条，{@code bit = -1} 表示默认取第一条。
     */
    public enum BoardCorner {
        TOP_RIGHT( BoardArea.TOP_EDGE, BoardArea.RIGHT_EDGE, 0 ),      // 默认 TOP
        BOTTOM_RIGHT( BoardArea.RIGHT_EDGE, BoardArea.BOTTOM_EDGE, 1 ), // 默认 RIGHT
        BOTTOM_LEFT( BoardArea.BOTTOM_EDGE, BoardArea.LEFT_EDGE, 1 ),   // 默认 BOTTOM
        TOP_LEFT( BoardArea.LEFT_EDGE, BoardArea.TOP_EDGE, 1 );         // 默认 LEFT

        private final BoardArea first;
        private final BoardArea second;
        private final int defaultBit;

        BoardCorner( BoardArea first, BoardArea second, int defaultBit ) {
            this.first = first;
            this.second = second;
            this.defaultBit = defaultBit;
        }

        public BoardArea area( int bit ) {
            return bit == 0 ? first : second;
        }

        /** 另一条相邻边（chisel 在该角的两条边之间切换时用）。 */
        public BoardArea other( BoardArea area ) {
            return area == first ? second : first;
        }

        /** 规格里锁定的默认归属：TR→TOP、BR→RIGHT、BL→BOTTOM、TL→LEFT。 */
        public BoardArea defaultArea( ) {
            return area( defaultBit );
        }

        /** 默认归属写成 bit 值。 */
        public int defaultBit( ) {
            return defaultBit;
        }
    }

    public static final List<BoardCorner> CORNERS = List.of( BoardCorner.values() );

    /** 每个角占 1 bit，共 6 面 × 2 层 × 4 角 = 48 bit，用 12 个 long 装下。 */
    public static final int CORNER_BITS = FACES.size() * LAYERS.size() * CORNERS.size(); // 48
    public static final int CORNER_LONGS = ( CORNER_BITS + 63 ) / 64;                     // 12
    /** 48 个角全部取默认归属时的位模式。 */
    public static final long[] DEFAULT_CORNER_OWNERS = defaultCornerOwners();

    /** 12 个槽位全满。 */
    public static final int FULL_OCCUPANCY = ( 1 << ( FACES.size() * LAYERS.size() ) ) - 1;

    private LayeredBoardSlots( ) {
    }

    // ---------------------------------------------------------------- 槽位

    /**
     * 槽位在位掩码里的下标：{@code face.ordinal() * 2 + layer.ordinal()}。
     *
     * <p>于是掩码的字节布局就是「一个面一个 nibble」，序列化、调试、位序都直观。
     */
    public static int slotBit( FaceDir face, BoardLayer layer ) {
        return face.ordinal() * LAYERS.size() + layer.ordinal();
    }

    public static boolean hasSlot( int occupancy, FaceDir face, BoardLayer layer ) {
        return ( occupancy & ( 1 << slotBit( face, layer ) ) ) != 0;
    }

    public static int withSlot( int occupancy, FaceDir face, BoardLayer layer ) {
        return occupancy | ( 1 << slotBit( face, layer ) );
    }

    public static int withoutSlot( int occupancy, FaceDir face, BoardLayer layer ) {
        return occupancy & ~( 1 << slotBit( face, layer ) );
    }

    /** 这个面是否至少有一层。 */
    public static boolean hasFace( int occupancy, FaceDir face ) {
        return hasSlot( occupancy, face, BoardLayer.OUTER ) || hasSlot( occupancy, face, BoardLayer.INNER );
    }

    // ---------------------------------------------------------------- 窗

    /** 窗按面记，不是按层记：一个 bit 表示这个面开着窗。 */
    public static int windowBit( FaceDir face ) {
        return 1 << face.ordinal();
    }

    public static boolean hasWindow( int windows, FaceDir face ) {
        return ( windows & windowBit( face ) ) != 0;
    }

    public static int withWindow( int windows, FaceDir face ) {
        return windows | windowBit( face );
    }

    public static int withoutWindow( int windows, FaceDir face ) {
        return windows & ~windowBit( face );
    }

    public static int toggleWindow( int windows, FaceDir face ) {
        return windows ^ windowBit( face );
    }

    // ---------------------------------------------------------------- 角归属

    /** 某个角在 48 bit 里的下标。 */
    public static int cornerBit( FaceDir face, BoardLayer layer, BoardCorner corner ) {
        return ( face.ordinal() * LAYERS.size() + layer.ordinal() ) * CORNERS.size() + corner.ordinal();
    }

    /** 读取一个角的 owner bit。越界的数组按默认值补齐。 */
    public static int cornerOwner( long[] owners, FaceDir face, BoardLayer layer, BoardCorner corner ) {
        int bit = cornerBit( face, layer, corner );
        int index = bit >>> 6;
        if ( owners == null || index >= owners.length ) {
            return corner.defaultBit();
        }
        return (int) ( ( owners[index] >>> ( bit & 63 ) ) & 1L );
    }

    /** 复制成固定长度的 12 个 long；缺失的位按默认归属补齐。 */
    public static long[] normalize( long[] owners ) {
        long[] result = DEFAULT_CORNER_OWNERS.clone();
        if ( owners != null ) {
            System.arraycopy( owners, 0, result, 0, Math.min( owners.length, CORNER_LONGS ) );
        }
        return result;
    }

    /**
     * 写入一个角的 owner bit，原地修改并返回同一个数组。
     *
     * <p>数组永远由 {@link #normalize} / {@link #DEFAULT_CORNER_OWNERS} 保证是 12 个 long，
     * 所以调用方可以把它当成一个 48 位的可变 bitset 用。
     */
    public static void writeCornerOwner( long[] owners, FaceDir face, BoardLayer layer, BoardCorner corner, int bit ) {
        int position = cornerBit( face, layer, corner );
        long mask = 1L << ( position & 63 );
        int index = position >>> 6;
        owners[index] = bit == 0 ? owners[index] & ~mask : owners[index] | mask;
    }

    public static long[] copyCornerOwners( long[] owners ) {
        return owners == null ? DEFAULT_CORNER_OWNERS.clone() : owners.clone();
    }

    /** 48 个 owner bit 压缩成一个 long——快照里只需要它来判等，不必背着 12 个 long。 */
    public static long cornerSignature( long[] owners ) {
        long signature = 0L;
        for ( int i = 0; i < CORNER_LONGS; i++ ) {
            signature = signature * 31L + ( owners == null || i >= owners.length ? 0L : owners[i] );
        }
        return signature;
    }

    private static long[] defaultCornerOwners( ) {
        long[] owners = new long[CORNER_LONGS];
        for ( FaceDir face : VALUES ) {
            for ( BoardLayer layer : LAYERS ) {
                for ( BoardCorner corner : CORNERS ) {
                    if ( corner.defaultBit() == 0 ) {
                        continue;
                    }
                    int bit = cornerBit( face, layer, corner );
                    owners[bit >>> 6] |= 1L << ( bit & 63 );
                }
            }
        }
        return owners;
    }

    // ---------------------------------------------------------------- 材质键

    /** 一个「面 + 层」里带独立材质槽的区域，顺序固定，便于枚举全部键。 */
    public static final List<BoardArea> MATERIAL_AREAS = List.of(
            BoardArea.BODY, BoardArea.TOP_EDGE, BoardArea.BOTTOM_EDGE, BoardArea.LEFT_EDGE, BoardArea.RIGHT_EDGE );

    /** 一个「面 + 层 + 区域」的材质槽键名，例如 {@code north.outer.top_edge}。 */
    public static String materialKey( FaceDir face, BoardLayer layer, BoardArea area ) {
        return face.getName() + "." + layer.getName() + "." + area.getName();
    }

    /** 窗材质按面共享，键名形如 {@code north.window}。 */
    public static String windowKey( FaceDir face ) {
        return face.getName() + "." + BoardArea.WINDOW.getName();
    }

    /**
     * 全部 66 个材质槽键名：每个面 2 层 × 5 区域 + 1 份窗。
     *
     * <p>键名同时是渲染数据的键，两边必须完全一致。
     */
    public static List<String> allMaterialKeys( ) {
        List<String> keys = new ArrayList<>( FACES.size() * ( LAYERS.size() * MATERIAL_AREAS.size() + 1 ) );
        for ( FaceDir face : VALUES ) {
            for ( BoardLayer layer : LAYERS ) {
                for ( BoardArea area : MATERIAL_AREAS ) {
                    keys.add( materialKey( face, layer, area ) );
                }
            }
            keys.add( windowKey( face ) );
        }
        return List.copyOf( keys );
    }

    /** 调试 / 日志用的短名。 */
    public static String describe( FaceDir face, BoardLayer layer ) {
        return face.getName().toUpperCase( Locale.ROOT ) + "." + layer.getName().toUpperCase( Locale.ROOT );
    }
}
