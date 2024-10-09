package marrydream.marisdecoration.block;

import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.enums.GuardrailColumn;
import marrydream.marisdecoration.block.enums.GuardrailShape;
import marrydream.marisdecoration.block.property.Properties;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.explosion.Explosion;

/**
 * 目的为实现一条围栏，围栏每间隔两个方块插一条柱子，起始点和终点必有柱子
 *
 * straight 为一条面朝东的直线，left 为北侧有单柱，right 为南侧有单柱，side 为两侧都有柱
 * inner 为面朝东南角的内角，由东侧和南侧两条直线组成，left 为北侧和中心角有柱，right 为西侧和中心角有柱，center 为中心有柱但两头无柱，side 为两侧都有柱但中心无柱
 * outer 为面朝东南角的外角，俯视 1px，none 为无柱（仅从上到下三个像素点线条），left、right 为一根 1px 柱子（之所以写两个属性是因为它用来继承相邻的柱子朝向）
 *
 * 放置时左右侧同时有围栏时只看左侧
 *
 * 是 straight
 * 1. 两无: side
 * 2. 左/右侧有 side-straight: right（左）left（右），并把它变成 left-straight（左）right-straight（右）
 * 3. 左有 right-straight/右有 left-straight: side
 * 4. 左有 right-inner: right, 并把它变成 center-inner
 * 5. 右有 left-inner: left, 并把它变成 center-inner
 * 6. 左有 left-outer: right，并把它变成 none-outer
 * 7. 右有 right-outer: left，并把它变成 none-outer
 * 8. 左有 right-outer/右有 left-outer: side
 *
 * 是 inner
 * 1. 左/右侧有 side-straight: center，并把它变成 left-straight（左）right-straight（右）
 * 2. 左有 right-straight/右有 left-straight: side
 * 3. 左右有 side-inner: side
 * 4. 左有 left-inner: right，并把它变成 center-inner
 * 5. 右有 right-inner: left，并把它变成 center-inner
 * 6. 左有 right-outer: right，并把它变成 none-outer
 * 7. 右有 left-outer: left，并把它变成 none-outer
 * 8. 左有 left-outer/右有 right-outer: side
 *
 * outer 可以继承上一个人的状态（left、right）
 * 1. 左有 side-straight: left，并把它变成 left-straight
 * 2. 右有 side-straight: right，并把它变成 right-straight
 * 3. 左有 left-straight: left
 * 4. 右有 right-straight: right
 * 5. 左有 right-inner: left
 * 6. 左有 left-inner: right
 * 7. 左/右有 outer: 复制其属性
 */

public class GuardrailBlock extends Block {
    public static final DirectionProperty FACING = Properties.BASE_ORIENTATION; // 北、东、南、西
    public static final EnumProperty<GuardrailShape> SHAPE = Properties.GUARDRAIL_SHAPE; // 直线、内左、内右、外左、外右
    public static final EnumProperty<GuardrailColumn> COLUMN = Properties.GUARDRAIL_COLUMN; // 无柱、左柱、右柱、中柱、两侧柱
    // 符合上北下南左西右东的坐标系: x轴向右，z轴向下，y轴向上

    // 直线
    protected static final VoxelShape NORTH_STRAIGHT = Block.createCuboidShape( 0.0, 0.0, 0.0, 16.0, 16.0, 1.0 );
    protected static final VoxelShape SOUTH_STRAIGHT = Block.createCuboidShape( 0.0, 0.0, 15.0, 16.0, 16.0, 16.0 );
    protected static final VoxelShape WEST_STRAIGHT = Block.createCuboidShape( 0.0, 0.0, 0.0, 1.0, 16.0, 16.0 );
    protected static final VoxelShape EAST_STRAIGHT = Block.createCuboidShape( 15.0, 0.0, 0.0, 16.0, 16.0, 16.0 );

    // 外角柱
    protected static final VoxelShape NORTH_WEST_OUTER = Block.createCuboidShape( 0.0, 0.0, 0.0, 1.0, 16.0, 1.0 );
    protected static final VoxelShape SOUTH_WEST_OUTER = Block.createCuboidShape( 0.0, 0.0, 15.0, 1.0, 16.0, 16.0 );
    protected static final VoxelShape NORTH_EAST_OUTER = Block.createCuboidShape( 15.0, 0.0, 0.0, 16.0, 16.0, 1.0 );
    protected static final VoxelShape SOUTH_EAST_OUTER = Block.createCuboidShape( 15.0, 0.0, 15.0, 16.0, 16.0, 16.0 );

    // 内角
    protected static final VoxelShape NORTH_WEST_INNER = VoxelShapes.union( NORTH_STRAIGHT, WEST_STRAIGHT );
    protected static final VoxelShape SOUTH_WEST_INNER = VoxelShapes.union( SOUTH_STRAIGHT, WEST_STRAIGHT );
    protected static final VoxelShape NORTH_EAST_INNER = VoxelShapes.union( NORTH_STRAIGHT, EAST_STRAIGHT );
    protected static final VoxelShape SOUTH_EAST_INNER = VoxelShapes.union( SOUTH_STRAIGHT, EAST_STRAIGHT );

    // 绘制围栏三条横线
    private static VoxelShape composeStraightShape( double minX, double minZ, double maxX, double maxZ ) {
        VoxelShape voxelShape = null;
        double currentY = 5.0; // 当前划线高度
        do {
            VoxelShape line = Block.createCuboidShape( minX, currentY, minZ, maxX, currentY + 1.0, maxZ );
            if ( voxelShape == null ) {
                voxelShape = line;
            } else {
                voxelShape = VoxelShapes.union( voxelShape, line );
            }
            currentY += 5.0; // 每次划线高度增加 5
        } while ( currentY <= 15.0 );
        return voxelShape;
    }

    // 12 种形状
    protected static final VoxelShape[] ALL_SHAPES = new VoxelShape[]{
            null,
            NORTH_WEST_OUTER,
            NORTH_EAST_OUTER,
            NORTH_STRAIGHT,
            SOUTH_WEST_OUTER,
            WEST_STRAIGHT,
            null,
            NORTH_WEST_INNER,
            SOUTH_EAST_OUTER,
            null,
            EAST_STRAIGHT,
            NORTH_EAST_INNER,
            SOUTH_STRAIGHT,
            SOUTH_WEST_INNER,
            SOUTH_EAST_INNER,
            null
    };
    // 映射到 ALL_SHAPES 的索引
    private static final int[] SHAPE_INDICES = new int[]{ 12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8 };
    private final Block baseBlock;
    private final BlockState baseBlockState;

    public GuardrailBlock( BlockState baseBlockState, AbstractBlock.Settings settings ) {
        super( settings );
        this.setDefaultState(
                this.stateManager
                        .getDefaultState()
                        .with( FACING, Direction.NORTH )
                        .with( SHAPE, GuardrailShape.STRAIGHT )
                        .with( COLUMN, GuardrailColumn.SIDE )
        );
        this.baseBlock = baseBlockState.getBlock();
        this.baseBlockState = baseBlockState;
    }

    // 具有侧面透明度
    @Override
    public boolean hasSidedTransparency( BlockState state ) {
        return true;
    }

    // 获取轮廓形状
    @Override
    public VoxelShape getOutlineShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        return ALL_SHAPES[SHAPE_INDICES[this.getShapeIndexIndex( state )]];
    }

    private int getShapeIndexIndex( BlockState state ) {
        return state.get( SHAPE ).ordinal() * 4 + state.get( FACING ).getHorizontal();
    }

    // 随机显示刻度
    @Override
    public void randomDisplayTick( BlockState state, World world, BlockPos pos, Random random ) {
        this.baseBlock.randomDisplayTick( state, world, pos, random );
    }

    @Override
    public void onBlockBreakStart( BlockState state, World world, BlockPos pos, PlayerEntity player ) {
        this.baseBlockState.onBlockBreakStart( world, pos, player );
    }

    // 当被破坏时
    @Override
    public void onBroken( WorldAccess world, BlockPos pos, BlockState state ) {
        this.baseBlock.onBroken( world, pos, state );
    }

    @Override
    public float getBlastResistance( ) {
        return this.baseBlock.getBlastResistance();
    }

    @Override
    public void onBlockAdded( BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify ) {
        if ( !state.isOf( state.getBlock() ) ) {
            world.updateNeighbor( this.baseBlockState, pos, Blocks.AIR, pos, false );
            this.baseBlock.onBlockAdded( this.baseBlockState, world, pos, oldState, false );
        }
    }

    @Override
    public void onStateReplaced( BlockState state, World world, BlockPos pos, BlockState newState, boolean moved ) {
        if ( !state.isOf( newState.getBlock() ) ) {
            this.baseBlockState.onStateReplaced( world, pos, newState, moved );
        }
    }

    @Override
    public void onSteppedOn( World world, BlockPos pos, BlockState state, Entity entity ) {
        this.baseBlock.onSteppedOn( world, pos, state, entity );
    }

    @Override
    public boolean hasRandomTicks( BlockState state ) {
        return this.baseBlock.hasRandomTicks( state );
    }

    @Override
    public void randomTick( BlockState state, ServerWorld world, BlockPos pos, Random random ) {
        this.baseBlock.randomTick( state, world, pos, random );
    }

    @Override
    public void scheduledTick( BlockState state, ServerWorld world, BlockPos pos, Random random ) {
        this.baseBlock.scheduledTick( state, world, pos, random );
    }

    // 当右键点击时
    @Override
    public ActionResult onUse( BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit ) {
        return this.baseBlockState.onUse( world, player, hand, hit );
    }

    // 当被爆炸破坏时
    @Override
    public void onDestroyedByExplosion( World world, BlockPos pos, Explosion explosion ) {
        this.baseBlock.onDestroyedByExplosion( world, pos, explosion );
    }

    // 获取放置状态
    @Override
    public BlockState getPlacementState( ItemPlacementContext ctx ) {
        BlockPos blockPos = ctx.getBlockPos();
        BlockState blockState = this.getDefaultState().with( FACING, ctx.getHorizontalPlayerFacing() );
        GuardrailShape shapeState = getGuardrailShape( blockState, ctx.getWorld(), blockPos );
        return blockState.with( SHAPE, shapeState ).with( COLUMN, getGuardrailColumn( shapeState, blockState, ctx.getWorld(), blockPos, "放置，" ) );
    }

    // 获取旁边方块的更新状态
    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
    ) {
        boolean isHorizontal = direction.getAxis().isHorizontal();
        if ( isHorizontal ) {
            GuardrailShape shapeState = getGuardrailShape( state, world, pos );
            return state.with( SHAPE, shapeState ).with( COLUMN, getGuardrailColumn( shapeState, state, world, pos, "邻居更新，" ) );
        }
        return super.getStateForNeighborUpdate( state, direction, neighborState, world, pos, neighborPos );
    }

    // 获取柱子状态（无柱、左柱、右柱、中柱、两侧柱）
    private static GuardrailColumn getGuardrailColumn( GuardrailShape shape, BlockState state, BlockView world, BlockPos pos, String type ) {
        GuardrailColumn a;
        if ( shape == GuardrailShape.STRAIGHT ) {
            a = getGuardrailColumnStraight( state, world, pos );
            MarisDecoration.LOGGER.info( type + "直线柱子状态: " + a );
        } else if ( shape == GuardrailShape.INNER_LEFT || shape == GuardrailShape.INNER_RIGHT ) {
            a = getGuardrailColumnInner( state, world, pos );
            MarisDecoration.LOGGER.info( type + "内角柱子状态: " + a );
        } else {
            a = getGuardrailColumnOuter( state, world, pos );
            MarisDecoration.LOGGER.info( type + "外角柱子状态: " + a );
        }
        return a;
    }

    // 获取直线柱子状态
    private static GuardrailColumn getGuardrailColumnStraight( BlockState state, BlockView world, BlockPos pos ) {
        BlockState blockState = world.getBlockState( pos.offset( state.get( FACING ).rotateYCounterclockwise() ) );
        BlockState blockState2 = world.getBlockState( pos.offset( state.get( FACING ).rotateYClockwise() ) );
        if ( isGuardrails( blockState ) && blockState.get( SHAPE ) == GuardrailShape.STRAIGHT ) {
            return GuardrailColumn.RIGHT;
        } else if ( isGuardrails( blockState2 ) && blockState2.get( SHAPE ) == GuardrailShape.STRAIGHT ) {
            return GuardrailColumn.LEFT;
        } else {
            return GuardrailColumn.SIDE;
        }
    }

    // 获取内角柱子状态
    private static GuardrailColumn getGuardrailColumnInner( BlockState state, BlockView world, BlockPos pos ) {
        BlockState blockState = world.getBlockState( pos.offset( state.get( FACING ).rotateYCounterclockwise() ) );
        BlockState blockState2 = world.getBlockState( pos.offset( state.get( FACING ).rotateYClockwise() ) );
        if ( isGuardrails( blockState ) && blockState.get( SHAPE ) == GuardrailShape.STRAIGHT ) {
            return GuardrailColumn.RIGHT;
        } else if ( isGuardrails( blockState2 ) && blockState2.get( SHAPE ) == GuardrailShape.STRAIGHT ) {
            return GuardrailColumn.LEFT;
        } else {
            return GuardrailColumn.SIDE;
        }
    }

    // 获取外角柱子状态
    private static GuardrailColumn getGuardrailColumnOuter( BlockState state, BlockView world, BlockPos pos ) {
        BlockState blockState = world.getBlockState( pos.offset( state.get( FACING ).rotateYCounterclockwise() ) );
        BlockState blockState2 = world.getBlockState( pos.offset( state.get( FACING ).rotateYClockwise() ) );
        if ( isGuardrails( blockState ) ) {
            if ( blockState.get( SHAPE ) == GuardrailShape.STRAIGHT ) {
                return GuardrailColumn.LEFT;
            } else {
                return blockState.get( COLUMN );
            }
        } else if ( isGuardrails( blockState2 ) ) {
            if ( blockState2.get( SHAPE ) == GuardrailShape.STRAIGHT ) {
                return GuardrailColumn.RIGHT;
            } else {
                return blockState2.get( COLUMN );
            }
        } else {
            return GuardrailColumn.SIDE;
        }
    }

    // 获取形状状态（直线、内左、内右、外左、外右）
    private static GuardrailShape getGuardrailShape( BlockState state, BlockView world, BlockPos pos ) {
        Direction direction = state.get( FACING );
        BlockState blockState = world.getBlockState( pos.offset( direction ) );
        if ( isGuardrails( blockState ) ) {
            Direction direction2 = blockState.get( FACING );
            if ( direction2.getAxis() != ( ( Direction ) state.get( FACING ) ).getAxis() && isDifferentOrientation( state, world, pos, direction2.getOpposite() ) ) {
                if ( direction2 == direction.rotateYCounterclockwise() ) {
                    return GuardrailShape.OUTER_LEFT;
                }

                return GuardrailShape.OUTER_RIGHT;
            }
        }

        BlockState blockState2 = world.getBlockState( pos.offset( direction.getOpposite() ) );
        if ( isGuardrails( blockState2 ) ) {
            Direction direction3 = blockState2.get( FACING );
            if ( direction3.getAxis() != ( ( Direction ) state.get( FACING ) ).getAxis() && isDifferentOrientation( state, world, pos, direction3 ) ) {
                if ( direction3 == direction.rotateYCounterclockwise() ) {
                    return GuardrailShape.INNER_LEFT;
                }

                return GuardrailShape.INNER_RIGHT;
            }
        }

        return GuardrailShape.STRAIGHT;
    }

    private static boolean isDifferentOrientation( BlockState state, BlockView world, BlockPos pos, Direction dir ) {
        BlockState blockState = world.getBlockState( pos.offset( dir ) );
        return !isGuardrails( blockState ) || blockState.get( FACING ) != state.get( FACING );
    }

    public static boolean isGuardrails( BlockState state ) {
        return state.getBlock() instanceof GuardrailBlock;
    }

    @Override
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return this.getOutlineShape(state, world, pos, context);
    }

    // 旋转
    @Override
    public BlockState rotate( BlockState state, BlockRotation rotation ) {
        return state.with( FACING, rotation.rotate( state.get( FACING ) ) );
    }

    // 镜像
    @Override
    public BlockState mirror( BlockState state, BlockMirror mirror ) {
        Direction direction = state.get( FACING );
        GuardrailShape guardrailShape = state.get( SHAPE );
        switch ( mirror ) {
            case LEFT_RIGHT:
                if ( direction.getAxis() == Direction.Axis.Z ) {
                    switch ( guardrailShape ) {
                        case INNER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.INNER_RIGHT );
                        case INNER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.INNER_LEFT );
                        case OUTER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.OUTER_RIGHT );
                        case OUTER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.OUTER_LEFT );
                        default:
                            return state.rotate( BlockRotation.CLOCKWISE_180 );
                    }
                }
                break;
            case FRONT_BACK:
                if ( direction.getAxis() == Direction.Axis.X ) {
                    switch ( guardrailShape ) {
                        case INNER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.INNER_LEFT );
                        case INNER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.INNER_RIGHT );
                        case OUTER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.OUTER_RIGHT );
                        case OUTER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, GuardrailShape.OUTER_LEFT );
                        case STRAIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 );
                    }
                }
        }

        return super.mirror( state, mirror );
    }

    // 注册状态属性
    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( FACING, SHAPE, COLUMN );
    }

    // 实体不能尝试穿过
    @Override
    public boolean canPathfindThrough( BlockState state, BlockView world, BlockPos pos, NavigationType type ) {
        return false;
    }
}
