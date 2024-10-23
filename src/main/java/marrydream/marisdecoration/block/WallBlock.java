package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.utils.BlockShape;
import marrydream.marisdecoration.block.utils.DirectionConnectBlockGroup;
import net.minecraft.block.BlockState;

public class WallBlock extends DirectionShapeHalfConnectBlock {
    // 底部直线
    protected static final BlockShape NORTH_STRAIGHT_BOTTOM = new BlockShape( 0.0, 0.0, 0.0, 16.0, 16.0, 1.0, -1 );
    protected static final BlockShape SOUTH_STRAIGHT_BOTTOM = new BlockShape( 0.0, 0.0, 15.0, 16.0, 16.0, 16.0, -1 );
    protected static final BlockShape WEST_STRAIGHT_BOTTOM = new BlockShape( 0.0, 0.0, 0.0, 1.0, 16.0, 16.0, -1 );
    protected static final BlockShape EAST_STRAIGHT_BOTTOM = new BlockShape( 15.0, 0.0, 0.0, 16.0, 16.0, 16.0, -1 );

    // 底部内角
    protected static final BlockShape NORTH_WEST_INNER_BOTTOM = new BlockShape( NORTH_STRAIGHT_BOTTOM, WEST_STRAIGHT_BOTTOM );
    protected static final BlockShape SOUTH_WEST_INNER_BOTTOM = new BlockShape( SOUTH_STRAIGHT_BOTTOM, WEST_STRAIGHT_BOTTOM );
    protected static final BlockShape NORTH_EAST_INNER_BOTTOM = new BlockShape( NORTH_STRAIGHT_BOTTOM, EAST_STRAIGHT_BOTTOM );
    protected static final BlockShape SOUTH_EAST_INNER_BOTTOM = new BlockShape( SOUTH_STRAIGHT_BOTTOM, EAST_STRAIGHT_BOTTOM );

    // 底部外角柱
    protected static final BlockShape NORTH_WEST_OUTER_BOTTOM = new BlockShape( 0.0, 0.0, 0.0, 1.0, 16.0, 1.0, -1 );
    protected static final BlockShape SOUTH_WEST_OUTER_BOTTOM = new BlockShape( 0.0, 0.0, 15.0, 1.0, 16.0, 16.0, -1 );
    protected static final BlockShape NORTH_EAST_OUTER_BOTTOM = new BlockShape( 15.0, 0.0, 0.0, 16.0, 16.0, 1.0, -1 );
    protected static final BlockShape SOUTH_EAST_OUTER_BOTTOM = new BlockShape( 15.0, 0.0, 15.0, 16.0, 16.0, 16.0, -1 );


    // 通用顶部板子
    protected static final BlockShape COMMON_TOP = new BlockShape( 0.0, 15.0, 0.0, 16.0, 16.0, 16.0, -1 );

    // 顶部直线
    protected static final BlockShape NORTH_STRAIGHT_TOP = new BlockShape( NORTH_STRAIGHT_BOTTOM, COMMON_TOP );
    protected static final BlockShape SOUTH_STRAIGHT_TOP = new BlockShape( SOUTH_STRAIGHT_BOTTOM, COMMON_TOP );
    protected static final BlockShape WEST_STRAIGHT_TOP = new BlockShape( WEST_STRAIGHT_BOTTOM, COMMON_TOP );
    protected static final BlockShape EAST_STRAIGHT_TOP = new BlockShape( EAST_STRAIGHT_BOTTOM, COMMON_TOP );

    // 顶部内角
    protected static final BlockShape NORTH_WEST_INNER_TOP = new BlockShape( NORTH_WEST_INNER_BOTTOM, COMMON_TOP );
    protected static final BlockShape SOUTH_WEST_INNER_TOP = new BlockShape( SOUTH_WEST_INNER_BOTTOM, COMMON_TOP );
    protected static final BlockShape NORTH_EAST_INNER_TOP = new BlockShape( NORTH_EAST_INNER_BOTTOM, COMMON_TOP );
    protected static final BlockShape SOUTH_EAST_INNER_TOP = new BlockShape( SOUTH_EAST_INNER_BOTTOM, COMMON_TOP );

    // 顶部外角柱
    protected static final BlockShape NORTH_WEST_OUTER_TOP = new BlockShape( NORTH_WEST_OUTER_BOTTOM, COMMON_TOP );
    protected static final BlockShape SOUTH_WEST_OUTER_TOP = new BlockShape( SOUTH_WEST_OUTER_BOTTOM, COMMON_TOP );
    protected static final BlockShape NORTH_EAST_OUTER_TOP = new BlockShape( NORTH_EAST_OUTER_BOTTOM, COMMON_TOP );
    protected static final BlockShape SOUTH_EAST_OUTER_TOP = new BlockShape( SOUTH_EAST_OUTER_BOTTOM, COMMON_TOP );

    // 底部连接组
    private static final DirectionConnectBlockGroup bottomShapeGroup = new DirectionConnectBlockGroup(
            NORTH_STRAIGHT_BOTTOM, SOUTH_STRAIGHT_BOTTOM, WEST_STRAIGHT_BOTTOM, EAST_STRAIGHT_BOTTOM,
            NORTH_WEST_OUTER_BOTTOM, SOUTH_WEST_OUTER_BOTTOM, NORTH_EAST_OUTER_BOTTOM, SOUTH_EAST_OUTER_BOTTOM,
            NORTH_WEST_INNER_BOTTOM, SOUTH_WEST_INNER_BOTTOM, NORTH_EAST_INNER_BOTTOM, SOUTH_EAST_INNER_BOTTOM
    );

    // 顶部连接组
    private static final DirectionConnectBlockGroup topShapeGroup = new DirectionConnectBlockGroup(
            NORTH_STRAIGHT_TOP, SOUTH_STRAIGHT_TOP, WEST_STRAIGHT_TOP, EAST_STRAIGHT_TOP,
            NORTH_WEST_OUTER_TOP, SOUTH_WEST_OUTER_TOP, NORTH_EAST_OUTER_TOP, SOUTH_EAST_OUTER_TOP,
            NORTH_WEST_INNER_TOP, SOUTH_WEST_INNER_TOP, NORTH_EAST_INNER_TOP, SOUTH_EAST_INNER_TOP
    );

    public WallBlock( BlockState baseBlockState, Settings settings ) {
        super( baseBlockState, settings, bottomShapeGroup, topShapeGroup, true );
    }

    protected WallBlock( BlockState baseBlockState, Settings settings, boolean allowSwitchTexture ) {
        super( baseBlockState, settings, bottomShapeGroup, topShapeGroup, allowSwitchTexture );
    }
}
