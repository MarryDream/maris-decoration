package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.enums.PropLadderShape;
import marrydream.marisdecoration.block.property.Properties;
import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class VerticalLadderBlock extends LadderBlock {
    public final static EnumProperty<PropLadderShape> SHAPE = Properties.LADDER_SHAPE;

    public VerticalLadderBlock( AbstractBlock.Settings settings ) {
        super( settings );
    }

    @Override
    public boolean canPlaceAt( BlockState state, WorldView world, BlockPos pos ) {
        // 上方块为同方向本类型方块时，可以放置
        BlockState upState = world.getBlockState( pos.up() );
        if ( upState.getBlock() instanceof VerticalLadderBlock && upState.get( FACING ) == state.get( FACING ) ) {
            return true;
        }

        Direction direction = state.get( FACING );
        // 如果前方不是空气，可以放置
        BlockState blockState = world.getBlockState( pos.offset( direction.getOpposite() ) );
        return blockState.isSolid();
    }

    // 获取放置状态
    @Override
    public BlockState getPlacementState( ItemPlacementContext ctx ) {
        BlockState state = super.getPlacementState( ctx );
        if ( state == null ) return state;

        World world = ctx.getWorld();
        BlockState upNeighborState = world.getBlockState( ctx.getBlockPos().up() );
        // 如果上方的方块是同朝向的本类型方块，那就是 center，反之是 start
        if ( upNeighborState.getBlock() instanceof VerticalLadderBlock && upNeighborState.get( FACING ) == state.get( FACING ) ) {
            return state.with( SHAPE, PropLadderShape.NORMAL );
        }
        return state.with( SHAPE, PropLadderShape.START );
    }

    /**
     * 该方法只会在两个同类型方块相邻时，变化其中一个另一个才会被调用，不同方块相邻发生变化后不会调用该方法
     * 比如在 B 方块旁放下或删除 A 方块，那么 pos 就是 B 方块的坐标，neighborPos 就是 A 方块的坐标
     * 所以在此方法中，[当前方块]是隔壁发生变化后临近被影响到的方块，而[邻居方块]是发生变化的方块
     * @param state 此块的状态
     * @param direction 从此块到邻居块的方向（邻居在自己的: up/down/north/south/west/east）
     * @param neighborState 邻居方块更新后的状态
     * @param world 世界
     * @param pos 此块的位置
     * @param neighborPos 邻居块的位置
     * @return 此块的更新状态
     */
    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
    ) {
        if ( direction == Direction.UP ) {
            // 如果上方的方块变成了空气，那么当前方块的形状就是 start
            if ( neighborState.isAir() ) {
                // 此时不允许放置，转为空气
                if ( !canPlaceAt( state, world, pos ) ) {
                    return Blocks.AIR.getDefaultState();
                }
                state = state.with( SHAPE, PropLadderShape.START );
            }
            // 如果上方的方块是本类型方块，那么如果朝向相同，形状就是 normal，反之是 start
            if ( neighborState.getBlock() instanceof VerticalLadderBlock ) {
                state = state.with(
                        SHAPE,
                        neighborState.get( FACING ) == state.get( FACING ) ? PropLadderShape.NORMAL : PropLadderShape.START
                );
            }
        }

        return super.getStateForNeighborUpdate( state, direction, neighborState, world, pos, neighborPos );
    }

    // 注册状态属性，让方块认识这些属性
    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( FACING, SHAPE, WATERLOGGED );
    }
}
