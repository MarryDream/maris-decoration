package marrydream.marisdecoration.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.block.utils.SteelPlugDoorRoof;
import marrydream.marisdecoration.block.utils.ThinDoor.LintelThresholdDoorShape;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModBlockEntity;
import marrydream.marisdecoration.item.DetailChisel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/* 带门楣门槛细门；基础钢内嵌门还可在上半格插入一层伪装屋顶。 */
public class LintelThresholdThinDoorBlock extends DoorBlock implements EntityBlock, IWrenchable {
    protected static final float field_31083 = 2.0F;

    protected static final LintelThresholdDoorShape NORTH_SHAPE = new LintelThresholdDoorShape(0.0, 0.0, 16.0, 2);
    protected static final LintelThresholdDoorShape SOUTH_SHAPE = new LintelThresholdDoorShape(0.0, 14.0, 16.0, 16.0);
    protected static final LintelThresholdDoorShape EAST_SHAPE = new LintelThresholdDoorShape(14.0, 0.0, 16.0, 16.0);
    protected static final LintelThresholdDoorShape WEST_SHAPE = new LintelThresholdDoorShape(0.0, 0.0, 2.0, 16.0);
    private static final VoxelShape ROOF_SHAPE = Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);

    static {
        NORTH_SHAPE.setOpenShape(EAST_SHAPE, WEST_SHAPE);
        SOUTH_SHAPE.setOpenShape(WEST_SHAPE, EAST_SHAPE);
        EAST_SHAPE.setOpenShape(SOUTH_SHAPE, NORTH_SHAPE);
        WEST_SHAPE.setOpenShape(NORTH_SHAPE, SOUTH_SHAPE);
    }

    public LintelThresholdThinDoorBlock(BlockBehaviour.Properties settings, BlockSetType blockSetType) {
        super(settings, blockSetType);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? new SteelPlugDoorBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClientSide || type != ModBlockEntity.STEEL_PLUG_DOOR) return null;
        return (tickerWorld, tickerPos, tickerState, blockEntity) ->
                ((SteelPlugDoorBlockEntity) blockEntity).tickDoorAnimation();
    }

    protected VoxelShape getShape(LintelThresholdDoorShape shape, boolean isOpen, boolean isLower, boolean isRight) {
        if (!isOpen) return shape.base;
        if (isRight) return isLower ? shape.open.right.bottom : shape.open.right.top;
        return isLower ? shape.open.left.bottom : shape.open.left.top;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        boolean isOpen = state.getValue(OPEN);
        boolean isLower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean isRight = state.getValue(HINGE) == DoorHingeSide.RIGHT;
        VoxelShape base = switch (direction) {
            case SOUTH -> getShape(NORTH_SHAPE, isOpen, isLower, isRight);
            case WEST -> getShape(EAST_SHAPE, isOpen, isLower, isRight);
            case NORTH -> getShape(SOUTH_SHAPE, isOpen, isLower, isRight);
            default -> getShape(WEST_SHAPE, isOpen, isLower, isRight);
        };
        return !isLower && roofAt(world, pos) != null ? Shapes.or(base, ROOF_SHAPE) : base;
    }

    private static Vec3 localHit(Vec3 hitPos, BlockPos pos) {
        return hitPos.subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    private static @Nullable SteelPlugDoorBlockEntity roofAt(BlockGetter world, BlockPos upperPos) {
        return world.getBlockEntity(upperPos) instanceof SteelPlugDoorBlockEntity roof && roof.hasRoof() ? roof : null;
    }

    private static @Nullable SteelPlugDoorBlockEntity ownerAt(BlockGetter world, BlockPos pos, BlockState state) {
        BlockPos upperPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos : pos.above();
        return world.getBlockEntity(upperPos) instanceof SteelPlugDoorBlockEntity roof ? roof : null;
    }

    private static boolean isRoofHit(BlockState state, BlockPos pos, Vec3 hitPos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER && SteelPlugDoorRoof.isRoofHit(localHit(hitPos, pos));
    }

    private static Vec3 canonicalHit(BlockState state, BlockPos pos, Vec3 hitPos) {
        return SteelPlugDoorRoof.toCanonical(localHit(hitPos, pos), state.getValue(FACING));
    }

    /** Item-side entry point used when sneaking bypasses Block#onUse. */
    public static @Nullable InteractionResult tryInsertRoof(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof LintelThresholdThinDoorBlock)
                || state.getValue(HALF) != DoubleBlockHalf.UPPER
                || !context.getItemInHand().is(ModBlock.LAYERED_COPYCAT_BOARD.asItem())) {
            return null;
        }
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null) return InteractionResult.PASS;
        if (roof.hasRoof()) return InteractionResult.sidedSuccess(world.isClientSide);
        if (!world.isClientSide) {
            roof.insertRoof();
            Player player = context.getPlayer();
            if (player == null || !player.isCreative()) context.getItemInHand().shrink(1);
            if (player != null) {
                LayeredCopycatBoardBlock.autoCamoNewLayers(world, pos, roof, player,
                        Direction.orderedByNearest(player)[0]);
            }
            world.playSound(null, pos, ModBlock.LAYERED_COPYCAT_BOARD.getSoundType(
                    ModBlock.LAYERED_COPYCAT_BOARD.defaultBlockState()).getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 0.75F);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                              InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && stack.is(ModBlock.LAYERED_COPYCAT_BOARD.asItem())) {
            InteractionResult inserted = tryInsertRoof(new UseOnContext(player, hand, hit));
            return inserted == null ? InteractionResult.PASS : inserted;
        }
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null || !roof.hasRoof() || !isRoofHit(state, pos, hit.getLocation())) {
            return super.use(state, world, pos, player, hand, hit);
        }
        Vec3 canonical = canonicalHit(state, pos, hit.getLocation());
        if (stack.getItem() instanceof DetailChisel) {
            return LayeredCopycatBoardBlock.onChisel(world, pos, player, roof, canonical);
        }
        if (stack.getItem() instanceof WrenchItem) {
            return onWrenched(state, new UseOnContext(player, hand, hit));
        }
        BlockState material = LayeredCopycatBoardBlock.getAcceptedMaterial(world, pos, stack, hit.getDirection());
        return material == null ? InteractionResult.PASS
                : LayeredCopycatBoardBlock.applyMaterial(world, pos, roof, material, stack, player, canonical);
    }

    public static InteractionResult onRoofChisel(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null || !roof.hasRoof() || !isRoofHit(state, pos, context.getClickLocation())) return InteractionResult.PASS;
        return LayeredCopycatBoardBlock.onChisel(world, pos, context.getPlayer(), roof,
                canonicalHit(state, pos, context.getClickLocation()));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        SteelPlugDoorBlockEntity roof = ownerAt(context.getLevel(), context.getClickedPos(), state);
        if (roof == null || !roof.hasRoof()
                || !isRoofHit(state, context.getClickedPos(), context.getClickLocation())) return InteractionResult.PASS;
        return LayeredCopycatBoardBlock.removeMaterialAt(context.getLevel(), context.getClickedPos(),
                context.getPlayer(), roof, canonicalHit(state, context.getClickedPos(), context.getClickLocation()));
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null || !roof.hasRoof() || !isRoofHit(state, pos, context.getClickLocation())) return InteractionResult.PASS;
        if (world.isClientSide) return InteractionResult.SUCCESS;

        LayeredCopycatBoardBlock.LayerRemovalResult removal =
                LayeredCopycatBoardBlock.settleLayerForPlayer(world, pos, roof,
                        FaceDir.UP, BoardLayer.OUTER, context.getPlayer(), canonicalHit(state, pos, context.getClickLocation()));
        roof.clearRoof();
        Player player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(ModBlock.LAYERED_COPYCAT_BOARD));
        }
        if (removal.feedbackMaterial() != null) {
            world.levelEvent(2001, pos, Block.getId(removal.feedbackMaterial()));
        }
        IWrenchable.playRemoveSound(world, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (!world.isClientSide && player.isCreative() && roof != null && roof.hasRoof()) roof.suppressRoofDrops();
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && state.getValue(HALF) == DoubleBlockHalf.UPPER && !world.isClientSide
                && world.getBlockEntity(pos) instanceof SteelPlugDoorBlockEntity roof && roof.hasRoof()) {
            boolean suppressed = roof.consumeDropSuppression();
            if (!suppressed) {
                Block.popResource(world, pos, new ItemStack(ModBlock.LAYERED_COPYCAT_BOARD));
                roof.dropAllMaterials(world, pos);
            }
            roof.clearRoof();
        }
        super.onRemove(state, world, pos, newState, moved);
    }
}
