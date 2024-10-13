package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.utils.BlockShape;
import marrydream.marisdecoration.block.utils.DirectionConnectBlockGroup;
import net.minecraft.block.*;

public class GuardrailBlock extends DirectionShapeHalfConnectBlock {
    // 直线
    protected static final BlockShape NORTH_STRAIGHT = new BlockShape( 0.0, 0.0, 0.0, 16.0, 16.0, 1.0, 24.0 );
    protected static final BlockShape SOUTH_STRAIGHT = new BlockShape( 0.0, 0.0, 15.0, 16.0, 16.0, 16.0, 24.0 );
    protected static final BlockShape WEST_STRAIGHT = new BlockShape( 0.0, 0.0, 0.0, 1.0, 16.0, 16.0, 24.0 );
    protected static final BlockShape EAST_STRAIGHT = new BlockShape( 15.0, 0.0, 0.0, 16.0, 16.0, 16.0, 24.0 );

    // 外角柱
    protected static final BlockShape NORTH_WEST_OUTER = new BlockShape( 0.0, 0.0, 0.0, 1.0, 16.0, 1.0, 24.0 );
    protected static final BlockShape SOUTH_WEST_OUTER = new BlockShape( 0.0, 0.0, 15.0, 1.0, 16.0, 16.0, 24.0 );
    protected static final BlockShape NORTH_EAST_OUTER = new BlockShape( 15.0, 0.0, 0.0, 16.0, 16.0, 1.0, 24.0 );
    protected static final BlockShape SOUTH_EAST_OUTER = new BlockShape( 15.0, 0.0, 15.0, 16.0, 16.0, 16.0, 24.0 );

    // 内角
    protected static final BlockShape NORTH_WEST_INNER = new BlockShape( NORTH_STRAIGHT, WEST_STRAIGHT );
    protected static final BlockShape SOUTH_WEST_INNER = new BlockShape( SOUTH_STRAIGHT, WEST_STRAIGHT );
    protected static final BlockShape NORTH_EAST_INNER = new BlockShape( NORTH_STRAIGHT, EAST_STRAIGHT );
    protected static final BlockShape SOUTH_EAST_INNER = new BlockShape( SOUTH_STRAIGHT, EAST_STRAIGHT );

    private static final DirectionConnectBlockGroup bottomShapeGroup = new DirectionConnectBlockGroup(
            NORTH_STRAIGHT, SOUTH_STRAIGHT, WEST_STRAIGHT, EAST_STRAIGHT,
            NORTH_WEST_OUTER, SOUTH_WEST_OUTER, NORTH_EAST_OUTER, SOUTH_EAST_OUTER,
            NORTH_WEST_INNER, SOUTH_WEST_INNER, NORTH_EAST_INNER, SOUTH_EAST_INNER
    );

    public GuardrailBlock( BlockState baseBlockState, AbstractBlock.Settings settings ) {
        super( baseBlockState, settings, bottomShapeGroup, null, false );
    }

    @Override
    protected boolean isDirectionShapeHalfConnectBlock( BlockState state ) {
        return state.getBlock() instanceof GuardrailBlock;
    }
}
