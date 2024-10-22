package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.property.Properties;
import marrydream.marisdecoration.block.utils.ThinDoor.LintelThresholdDoorShape;
import marrydream.marisdecoration.block.utils.ThinDoor.RoofThresholdDoorShape;
import marrydream.marisdecoration.init.ModItem;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/* 带房顶门槛细门 */
public class RoofThresholdThinDoorBlock extends DoorBlock {
    protected static final float field_31083 = 2.0F;

    public static final BooleanProperty TEXTURE = Properties.DOOR_TEXTURE; // 是否有纹理

    protected static final VoxelShape ROOF_SHAPE = Block.createCuboidShape( 0.0, 15.0, 0.0, 16.0, 16.0, 16.0 );
    protected static final RoofThresholdDoorShape NORTH_SHAPE = new RoofThresholdDoorShape( 0.0, 0.0, 16.0, 2, ROOF_SHAPE );
    protected static final RoofThresholdDoorShape SOUTH_SHAPE = new RoofThresholdDoorShape( 0.0, 14.0, 16.0, 16.0, ROOF_SHAPE );
    protected static final RoofThresholdDoorShape EAST_SHAPE = new RoofThresholdDoorShape( 14.0, 0.0, 16.0, 16.0, ROOF_SHAPE );
    protected static final RoofThresholdDoorShape WEST_SHAPE = new RoofThresholdDoorShape( 0.0, 0.0, 2.0, 16.0, ROOF_SHAPE );

    static {
        NORTH_SHAPE.setOpenShape( EAST_SHAPE, WEST_SHAPE );
        SOUTH_SHAPE.setOpenShape( WEST_SHAPE, EAST_SHAPE );
        EAST_SHAPE.setOpenShape( SOUTH_SHAPE, NORTH_SHAPE );
        WEST_SHAPE.setOpenShape( NORTH_SHAPE, SOUTH_SHAPE );
    }

    public RoofThresholdThinDoorBlock( Settings settings, BlockSetType blockSetType ) {
        super( settings, blockSetType );
        this.setDefaultState(
                this.stateManager
                        .getDefaultState()
                        .with( FACING, Direction.NORTH )
                        .with( OPEN, false )
                        .with( HINGE, DoorHinge.LEFT )
                        .with( POWERED, false )
                        .with( HALF, DoubleBlockHalf.LOWER )
                        .with( TEXTURE, true )
        );
    }

    protected VoxelShape getShape( RoofThresholdDoorShape shape, boolean isOpen, boolean isLower, boolean isRight ) {
        if ( !isOpen ) {
            return isLower ? shape.close.bottom : shape.close.top;
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

    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( HALF, FACING, OPEN, HINGE, POWERED, TEXTURE );
    }

    // 右键时更换纹理
    @Override
    public ActionResult onUse( BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit ) {
        // 只有手持钢铲刀且目标为上半部分方块时，才支持更换纹理
        if ( state.get( HALF ) == DoubleBlockHalf.UPPER && player.getStackInHand( hand ).isOf( ModItem.STEEL_SPATULA ) ) {
            state = state.with( TEXTURE, !state.get( TEXTURE ) );

            world.setBlockState( pos, state, Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD );
            world.emitGameEvent( player, GameEvent.BLOCK_CHANGE, pos );
            return ActionResult.success( world.isClient );
        }
        return super.onUse( state, world, pos, player, hand, hit );
    }
}
