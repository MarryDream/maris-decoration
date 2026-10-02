package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.enums.PropLadderShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class VerticalLadderBlock extends LadderBlock {
    public final static EnumProperty<PropLadderShape> SHAPE = marrydream.marisdecoration.block.property.Properties.LADDER_SHAPE;

    public VerticalLadderBlock( BlockBehaviour.Properties settings ) {
        super( settings );
    }

    /**
     * 能不能挂在这里。
     *
     * <h2>原版判据到底是什么</h2>
     * 原版 {@code LadderBlock#canPlaceOn} 的唯一判据是
     * {@code state.isSideSolidFullSquare(world, pos, facing.getOpposite())}：它读的是支撑方块
     * 在那一面上的<b>几何</b>（{@code SideShapeType.FULL} → 碰撞箱的那一面是不是完整的
     * 16×16 方块面），跟「这个方块注册时算不算实心」毫无关系。
     *
     * <h2>本类原来错在哪</h2>
     * 原来这里写的是 {@code blockState.isSolid()}。那看起来「也是实心」，实际完全是另一回事：
     * {@code BlockState#isSolid()} 返回的是方块<b>注册期</b>用 {@code EmptyBlockView} 烤好的一个
     * boolean（{@code AbstractBlockState#solid}），既不看你贴的是哪一个面，也看不到任何方块实体。
     * 于是几何来自方块实体的 {@code layered_copycat_board}（乃至 Copycats+ 的伪装方块）永远得到
     * {@code false}——钢梯挂不上去，而这跟薄板本身能不能附着没有关系。
     *
     * <h2>现在的写法</h2>
     * 不再自己复刻判据，而是<b>直接委托给原版</b>：
     * {@code super.canPlaceAt(state, world, pos)}，只在自己特有的那一条上加分支。
     * 自己抄一遍判据迟早会与上游分叉——本类就是活例子；Create 的 {@code MetalLadderBlock}
     * 用的也是 {@code super} 委托。这样三种支撑面（原版完整方块、Copycats+ 伪装方块、
     * 本 mod 那个几何来自方块实体的薄板）的判定与 {@code minecraft:ladder} 逐字节一致，
     * 不需要给薄板加任何针对梯子的特判。
     *
     * <p>保留本类自己的那一条：<b>上方是朝向相同的同类梯子</b>时也能挂（爬梯「接着往上长」
     * 的语义，原版没有这个概念）。
     */
    @Override
    public boolean canSurvive( BlockState state, LevelReader world, BlockPos pos ) {
        // 上方块为同方向本类型方块时，可以放置
        BlockState upState = world.getBlockState( pos.above() );
        if ( upState.getBlock() instanceof VerticalLadderBlock && upState.getValue( FACING ) == state.getValue( FACING ) ) {
            return true;
        }

        // 其余情况一律交给原版 LadderBlock 的 canPlaceAt——那是唯一权威的附着判据，
        // 直接 return super 而不是自己把判据抄一遍：抄一遍就会在「判据到底是什么」这件事上
        // 与上游分叉（本类历史上就是抄成了 isSolid()，于是动态方块上永远挂不住）。
        // Create 的 MetalLadderBlock 用的也是这个写法。
        return super.canSurvive( state, world, pos );
    }

    // 获取放置状态
    @Override
    public BlockState getStateForPlacement( BlockPlaceContext ctx ) {
        BlockState state = super.getStateForPlacement( ctx );
        if ( state == null ) return state;

        Level world = ctx.getLevel();
        BlockState upNeighborState = world.getBlockState( ctx.getClickedPos().above() );
        // 如果上方的方块是同朝向的本类型方块，那就是 center，反之是 start
        if ( upNeighborState.getBlock() instanceof VerticalLadderBlock && upNeighborState.getValue( FACING ) == state.getValue( FACING ) ) {
            return state.setValue( SHAPE, PropLadderShape.NORMAL );
        }
        return state.setValue( SHAPE, PropLadderShape.START );
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
    public BlockState updateShape(
            BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos
    ) {
        if ( direction == Direction.UP ) {
            // 如果上方的方块变成了空气，那么当前方块的形状就是 start
            if ( neighborState.isAir() ) {
                // 此时不允许放置，转为空气
                if ( !canSurvive( state, world, pos ) ) {
                    return Blocks.AIR.defaultBlockState();
                }
                state = state.setValue( SHAPE, PropLadderShape.START );
            }
            // 如果上方的方块是本类型方块，那么如果朝向相同，形状就是 normal，反之是 start
            if ( neighborState.getBlock() instanceof VerticalLadderBlock ) {
                state = state.setValue(
                        SHAPE,
                        neighborState.getValue( FACING ) == state.getValue( FACING ) ? PropLadderShape.NORMAL : PropLadderShape.START
                );
            }
        }

        return super.updateShape( state, direction, neighborState, world, pos, neighborPos );
    }

    // 注册状态属性，让方块认识这些属性
    @Override
    protected void createBlockStateDefinition( StateDefinition.Builder<Block, BlockState> builder ) {
        builder.add( FACING, SHAPE, WATERLOGGED );
    }
}
