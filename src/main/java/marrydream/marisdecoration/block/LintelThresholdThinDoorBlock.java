package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.utils.ThinDoor.LintelThresholdDoorShape;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/* 带门楣门槛细门 */
public class LintelThresholdThinDoorBlock extends DoorBlock {
    protected static final float field_31083 = 2.0F;

    protected static final LintelThresholdDoorShape NORTH_SHAPE = new LintelThresholdDoorShape( 0.0, 0.0, 16.0, 2 );
    protected static final LintelThresholdDoorShape SOUTH_SHAPE = new LintelThresholdDoorShape( 0.0, 14.0, 16.0, 16.0 );
    protected static final LintelThresholdDoorShape EAST_SHAPE = new LintelThresholdDoorShape( 14.0, 0.0, 16.0, 16.0 );
    protected static final LintelThresholdDoorShape WEST_SHAPE = new LintelThresholdDoorShape( 0.0, 0.0, 2.0, 16.0 );

    static {
        NORTH_SHAPE.setOpenShape( EAST_SHAPE, WEST_SHAPE );
        SOUTH_SHAPE.setOpenShape( WEST_SHAPE, EAST_SHAPE );
        EAST_SHAPE.setOpenShape( SOUTH_SHAPE, NORTH_SHAPE );
        WEST_SHAPE.setOpenShape( NORTH_SHAPE, SOUTH_SHAPE );
    }

    public LintelThresholdThinDoorBlock( AbstractBlock.Settings settings, BlockSetType blockSetType ) {
        super( settings, blockSetType );
    }

    protected VoxelShape getShape( LintelThresholdDoorShape shape, boolean isOpen, boolean isLower, boolean isRight ) {
        if ( !isOpen ) {
            return shape.base;
        }

        if ( isRight ) {
            return isLower ? shape.open.right.bottom : shape.open.right.top;
        }
        return isLower ? shape.open.left.bottom : shape.open.left.top;
    }

    @Override
    public VoxelShape getOutlineShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        Direction direction = state.get( FACING );
        boolean isOpen = state.get( OPEN );
        boolean isLower = state.get( HALF ) == DoubleBlockHalf.LOWER;
        boolean isRight = state.get( HINGE ) == DoorHinge.RIGHT;
        return switch ( direction ) {
            case SOUTH -> getShape( NORTH_SHAPE, isOpen, isLower, isRight );
            case WEST -> getShape( EAST_SHAPE, isOpen, isLower, isRight );
            case NORTH -> getShape( SOUTH_SHAPE, isOpen, isLower, isRight );
            default -> getShape( WEST_SHAPE, isOpen, isLower, isRight );
        };
    }
}
