package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.enums.GuardrailShape;
import marrydream.marisdecoration.block.property.Properties;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
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

public class GuardrailBlock extends Block {
    public static final DirectionProperty FACING = Properties.BASE_ORIENTATION; // 北、东、南、西
    public static final EnumProperty<GuardrailShape> SHAPE = Properties.GUARDRAIL_SHAPE; // 直线、内左、内右、外左、外右
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED; // 是否含水
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
                        .with( WATERLOGGED, false ) // 默认不含水
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
        FluidState fluidState = ctx.getWorld().getFluidState( blockPos );
        BlockState blockState = this.getDefaultState().with( FACING, ctx.getHorizontalPlayerFacing() );
        return blockState
                .with( SHAPE, getGuardrailShape( blockState, ctx.getWorld(), blockPos ) )
                .with( WATERLOGGED, fluidState.getFluid() == Fluids.WATER );
    }

    // 获取旁边方块的更新状态
    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
    ) {
        // 正确处理水流
        if ( state.get( WATERLOGGED ) ) {
            world.scheduleFluidTick( pos, Fluids.WATER, Fluids.WATER.getTickRate( world ) );
        }

        boolean isHorizontal = direction.getAxis().isHorizontal();
        if ( isHorizontal ) {
            return state.with( SHAPE, getGuardrailShape( state, world, pos ) );
        }
        return super.getStateForNeighborUpdate( state, direction, neighborState, world, pos, neighborPos );
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
    public VoxelShape getCameraCollisionShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        return this.getOutlineShape( state, world, pos, context );
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

    // 注册状态属性，让方块认识这些属性
    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( FACING, SHAPE, WATERLOGGED );
    }

    // 覆盖 getFluidState，这样方块含水后就会显示水
    @Override
    public FluidState getFluidState( BlockState state ) {
        return state.get( WATERLOGGED ) ? Fluids.WATER.getStill( false ) : super.getFluidState( state );
    }

    // 实体不能尝试穿过
    @Override
    public boolean canPathfindThrough( BlockState state, BlockView world, BlockPos pos, NavigationType type ) {
        return false;
    }
}
