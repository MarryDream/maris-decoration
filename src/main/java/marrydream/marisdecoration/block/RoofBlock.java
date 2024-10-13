package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.enums.PropHalf;
import marrydream.marisdecoration.block.property.Properties;
import net.minecraft.block.*;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class RoofBlock extends Block implements Waterloggable {
    public static final EnumProperty<PropHalf> HALF = Properties.PROP_HALF;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    protected static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape( 0.0, 0.0, 0.0, 16.0, 1.0, 16.0 );
    protected static final VoxelShape TOP_SHAPE = Block.createCuboidShape( 0.0, 15.0, 0.0, 16.0, 16.0, 16.0 );

    public RoofBlock( AbstractBlock.Settings settings ) {
        super( settings );
        this.setDefaultState( this.getDefaultState().with( HALF, PropHalf.BOTTOM ).with( WATERLOGGED, false ) );
    }

    @Override
    public boolean hasSidedTransparency( BlockState state ) {
        return true;
    }

    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( HALF, WATERLOGGED );
    }

    @Override
    public VoxelShape getOutlineShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        PropHalf propHalf = state.get( HALF );
        switch ( propHalf ) {
            case TOP:
                return TOP_SHAPE;
            default:
                return BOTTOM_SHAPE;
        }
    }

    @Nullable
    @Override
    public BlockState getPlacementState( ItemPlacementContext ctx ) {
        BlockPos blockPos = ctx.getBlockPos();
        FluidState fluidState = ctx.getWorld().getFluidState( blockPos );
        BlockState blockState = this.getDefaultState().with( HALF, PropHalf.BOTTOM ).with( WATERLOGGED, fluidState.getFluid() == Fluids.WATER );
        Direction direction = ctx.getSide();
        return direction != Direction.DOWN && ( direction == Direction.UP || !( ctx.getHitPos().y - ( double ) blockPos.getY() > 0.5 ) )
                ? blockState
                : blockState.with( HALF, PropHalf.TOP );
    }

    @Override
    public FluidState getFluidState( BlockState state ) {
        return state.get( WATERLOGGED ) ? Fluids.WATER.getStill( false ) : super.getFluidState( state );
    }

    @Override
    public boolean tryFillWithFluid( WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState ) {
        return  Waterloggable.super.tryFillWithFluid( world, pos, state, fluidState );
    }

    @Override
    public boolean canFillWithFluid( BlockView world, BlockPos pos, BlockState state, Fluid fluid ) {
        return Waterloggable.super.canFillWithFluid( world, pos, state, fluid );
    }

    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
    ) {
        if ( ( Boolean ) state.get( WATERLOGGED ) ) {
            world.scheduleFluidTick( pos, Fluids.WATER, Fluids.WATER.getTickRate( world ) );
        }

        return super.getStateForNeighborUpdate( state, direction, neighborState, world, pos, neighborPos );
    }

    @Override
    public boolean canPathfindThrough( BlockState state, BlockView world, BlockPos pos, NavigationType type ) {
        switch ( type ) {
            case LAND:
                return false;
            case WATER:
                return world.getFluidState( pos ).isIn( FluidTags.WATER );
            case AIR:
                return false;
            default:
                return false;
        }
    }
}
