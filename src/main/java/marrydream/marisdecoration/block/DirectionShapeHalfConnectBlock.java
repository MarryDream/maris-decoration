package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.enums.PropHalf;
import marrydream.marisdecoration.block.enums.PropShape;
import marrydream.marisdecoration.block.enums.PropTexture;
import marrydream.marisdecoration.block.property.Properties;
import marrydream.marisdecoration.block.utils.BlockShape;
import marrydream.marisdecoration.block.utils.DirectionConnectBlockGroup;
import marrydream.marisdecoration.init.ModItem;
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
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;

/* 有内左内右外左外右、东南西北、上下状态的连接方块，包含可改变的纹理，可含水 */
public class DirectionShapeHalfConnectBlock extends Block implements Waterloggable {
    public static final DirectionProperty FACING = Properties.BASE_ORIENTATION; // 北、东、南、西
    public static final EnumProperty<PropShape> SHAPE = Properties.PROP_SHAPE; // 直线、内左、内右、外左、外右
    public static final EnumProperty<PropHalf> HALF = Properties.PROP_HALF; // 上、下
    public static final EnumProperty<PropTexture> TEXTURE = Properties.PROP_TEXTURE; // 无纹理，左纹理，右纹理，两侧纹理
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED; // 是否含水
    // 符合上北下南左西右东的坐标系: x轴向右，z轴向下，y轴向上

    // 向下放置的连接方块组
    private final DirectionConnectBlockGroup bottomShapeGroup;
    // 向上放置的连接方块组
    private final DirectionConnectBlockGroup upShapeGroup;
    // 是否需要切换纹理
    private final boolean allowSwitchTexture;

    private final Block baseBlock;
    private final BlockState baseBlockState;

    /**
     * 带方位、形状、上下属性的方块
     * @param baseBlockState 基础方块状态
     * @param settings 方块设置
     * @param bottomShapeGroup 向下放置时的连接方块组
     * @param upShapeGroup 向上放置时的连接方块组
     * @param allowSwitchTexture 是否允许切换纹理
     */
    public DirectionShapeHalfConnectBlock(
            BlockState baseBlockState, AbstractBlock.Settings settings,
            DirectionConnectBlockGroup bottomShapeGroup, DirectionConnectBlockGroup upShapeGroup,
            boolean allowSwitchTexture
    ) {
        super( settings );
        this.bottomShapeGroup = bottomShapeGroup;
        this.upShapeGroup = upShapeGroup;
        this.allowSwitchTexture = allowSwitchTexture;

        this.setDefaultState(
                this.stateManager
                        .getDefaultState()
                        .with( FACING, Direction.NORTH )
                        .with( HALF, PropHalf.BOTTOM )
                        .with( SHAPE, PropShape.STRAIGHT )
                        .with( TEXTURE, PropTexture.NONE )
                        .with( WATERLOGGED, false ) // 默认不含水
        );
        this.baseBlock = baseBlockState.getBlock();
        this.baseBlockState = baseBlockState;
    }

    // 是否是单向方块（即只有底部）
    protected boolean isOneWayBlock( ) {
        return this.upShapeGroup == null;
    }

    // 具有侧面透明度
    @Override
    public boolean hasSidedTransparency( BlockState state ) {
        return true;
    }

    // 获取轮廓形状
    @Override
    public VoxelShape getOutlineShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        return getShapeByState( state ).getOutlineShape();
    }

    // 获取对于玩家和生物的碰撞体积
    @Override
    public VoxelShape getCollisionShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        return getShapeByState( state ).getCollisionShape();
    }

    // 根据形状和朝向状态获取碰撞体积
    protected BlockShape getShapeByState( BlockState state ) {
        DirectionConnectBlockGroup shapeGroup = ( isOneWayBlock() || state.get( HALF ) == PropHalf.BOTTOM ) ? this.bottomShapeGroup : this.upShapeGroup;
        return shapeGroup.getShapeByShapeAndFacing( state.get( SHAPE ).ordinal(), state.get( FACING ).getHorizontal() );
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

    // 当被爆炸破坏时
    @Override
    public void onDestroyedByExplosion( World world, BlockPos pos, Explosion explosion ) {
        this.baseBlock.onDestroyedByExplosion( world, pos, explosion );
    }

    // 获取放置状态
    @Override
    public BlockState getPlacementState( ItemPlacementContext ctx ) {
        Direction direction = ctx.getSide();
        BlockPos blockPos = ctx.getBlockPos();
        FluidState fluidState = ctx.getWorld().getFluidState( blockPos );

        PropHalf halfValue;
        if ( isOneWayBlock() ) {
            halfValue = PropHalf.BOTTOM;
        } else {
            halfValue = direction != Direction.DOWN && ( direction == Direction.UP || !( ctx.getHitPos().y - ( double ) blockPos.getY() > 0.5 ) ) ? PropHalf.BOTTOM : PropHalf.TOP;
        }
        // 要获取最新的 blockState 必须重新赋值，with 不会改变原有的 blockState（坑了我两个小时）
        BlockState blockState = this.getDefaultState()
                .with( FACING, ctx.getHorizontalPlayerFacing() )
                .with( HALF, halfValue )
                .with( TEXTURE, PropTexture.NONE ) // 放置时默认无纹理
                .with( WATERLOGGED, fluidState.getFluid() == Fluids.WATER );
        return blockState.with( SHAPE, getDirectionShapeHalfConnectShape( blockState, ctx.getWorld(), blockPos ) );
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
            PropShape shapeState = getDirectionShapeHalfConnectShape( state, world, pos );
            state = state.with( SHAPE, shapeState );

            // 处理过后若未非 inner 且 TEXTURE 为 left 或 right 则将 TEXTURE 设置为 side
            if ( shapeState != PropShape.INNER_LEFT && shapeState != PropShape.INNER_RIGHT ) {
                PropTexture textureState = state.get( TEXTURE );
                if ( textureState == PropTexture.LEFT || textureState == PropTexture.RIGHT ) {
                    return state.with( TEXTURE, PropTexture.SIDE );
                }
            }

            return state;
        }

        return super.getStateForNeighborUpdate( state, direction, neighborState, world, pos, neighborPos );
    }

    // 获取形状状态（直线、内左、内右、外左、外右）
    private PropShape getDirectionShapeHalfConnectShape( BlockState state, BlockView world, BlockPos pos ) {
        Direction direction = state.get( FACING );
        BlockState blockState = world.getBlockState( pos.offset( direction ) );
        if ( isDirectionShapeHalfConnectBlock( blockState, state ) && ( isOneWayBlock() || state.get( HALF ) == blockState.get( HALF ) ) ) {
            Direction direction2 = blockState.get( FACING );
            if ( direction2.getAxis() != ( ( Direction ) state.get( FACING ) ).getAxis() && isDifferentOrientation( state, world, pos, direction2.getOpposite() ) ) {
                if ( direction2 == direction.rotateYCounterclockwise() ) {
                    return PropShape.OUTER_LEFT;
                }

                return PropShape.OUTER_RIGHT;
            }
        }

        BlockState blockState2 = world.getBlockState( pos.offset( direction.getOpposite() ) );
        if ( isDirectionShapeHalfConnectBlock( blockState2, state ) && ( isOneWayBlock() || state.get( HALF ) == blockState2.get( HALF ) ) ) {
            Direction direction3 = blockState2.get( FACING );
            if ( direction3.getAxis() != ( ( Direction ) state.get( FACING ) ).getAxis() && isDifferentOrientation( state, world, pos, direction3 ) ) {
                if ( direction3 == direction.rotateYCounterclockwise() ) {
                    return PropShape.INNER_LEFT;
                }

                return PropShape.INNER_RIGHT;
            }
        }

        return PropShape.STRAIGHT;
    }

    private boolean isDifferentOrientation( BlockState state, BlockView world, BlockPos pos, Direction dir ) {
        BlockState blockState = world.getBlockState( pos.offset( dir ) );
        return !isDirectionShapeHalfConnectBlock( blockState, state ) || blockState.get( FACING ) != state.get( FACING ) || ( !isOneWayBlock() && blockState.get( HALF ) != state.get( HALF ) );
    }

    protected boolean isDirectionShapeHalfConnectBlock( BlockState neighborState, BlockState curState ) {
        return neighborState.getBlock() instanceof DirectionShapeHalfConnectBlock;
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
        PropShape propShape = state.get( SHAPE );
        switch ( mirror ) {
            case LEFT_RIGHT:
                if ( direction.getAxis() == Direction.Axis.Z ) {
                    switch ( propShape ) {
                        case INNER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.INNER_RIGHT );
                        case INNER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.INNER_LEFT );
                        case OUTER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.OUTER_RIGHT );
                        case OUTER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.OUTER_LEFT );
                        default:
                            return state.rotate( BlockRotation.CLOCKWISE_180 );
                    }
                }
                break;
            case FRONT_BACK:
                if ( direction.getAxis() == Direction.Axis.X ) {
                    switch ( propShape ) {
                        case INNER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.INNER_LEFT );
                        case INNER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.INNER_RIGHT );
                        case OUTER_LEFT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.OUTER_RIGHT );
                        case OUTER_RIGHT:
                            return state.rotate( BlockRotation.CLOCKWISE_180 ).with( SHAPE, PropShape.OUTER_LEFT );
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
        builder.add( FACING, HALF, SHAPE, TEXTURE, WATERLOGGED );
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

    // 右键时更换形态
    @Override
    public ActionResult onUse( BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit ) {
        // 只有手持钢铲刀且目标为上半部分方块时，才支持更换纹理
        if ( !allowSwitchTexture || state.get( HALF ) == PropHalf.BOTTOM || !player.getStackInHand( hand ).isOf( ModItem.STEEL_SPATULA ) ) {
            return this.baseBlockState.onUse( world, player, hand, hit );
        }
        PropTexture textureState = state.get( TEXTURE );
        PropShape shape = state.get( SHAPE );
        if ( textureState == PropTexture.NONE ) {
            // 只有 inner 才有 left 和 right 纹理
            if ( shape == PropShape.INNER_LEFT || shape == PropShape.INNER_RIGHT ) {
                state = state.with( TEXTURE, PropTexture.LEFT );
            } else {
                state = state.with( TEXTURE, PropTexture.SIDE );
            }
        } else if ( textureState == PropTexture.LEFT ) {
            state = state.with( TEXTURE, PropTexture.RIGHT );
        } else if ( textureState == PropTexture.RIGHT ) {
            state = state.with( TEXTURE, PropTexture.SIDE );
        } else {
            state = state.with( TEXTURE, PropTexture.NONE );
        }

        world.setBlockState( pos, state, Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD );
        world.emitGameEvent( player, GameEvent.BLOCK_CHANGE, pos );
        return ActionResult.success( world.isClient );
    }
}
