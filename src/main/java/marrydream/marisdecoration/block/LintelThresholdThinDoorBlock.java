package marrydream.marisdecoration.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.block.utils.SteelPlugDoorRoof;
import marrydream.marisdecoration.block.utils.ThinDoor.LintelThresholdDoorShape;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.item.DetailChisel;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/* 带门楣门槛细门；基础钢内嵌门还可在上半格插入一层伪装屋顶。 */
public class LintelThresholdThinDoorBlock extends DoorBlock implements BlockEntityProvider, IWrenchable {
    protected static final float field_31083 = 2.0F;

    protected static final LintelThresholdDoorShape NORTH_SHAPE = new LintelThresholdDoorShape(0.0, 0.0, 16.0, 2);
    protected static final LintelThresholdDoorShape SOUTH_SHAPE = new LintelThresholdDoorShape(0.0, 14.0, 16.0, 16.0);
    protected static final LintelThresholdDoorShape EAST_SHAPE = new LintelThresholdDoorShape(14.0, 0.0, 16.0, 16.0);
    protected static final LintelThresholdDoorShape WEST_SHAPE = new LintelThresholdDoorShape(0.0, 0.0, 2.0, 16.0);
    private static final VoxelShape ROOF_SHAPE = Block.createCuboidShape(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);

    static {
        NORTH_SHAPE.setOpenShape(EAST_SHAPE, WEST_SHAPE);
        SOUTH_SHAPE.setOpenShape(WEST_SHAPE, EAST_SHAPE);
        EAST_SHAPE.setOpenShape(SOUTH_SHAPE, NORTH_SHAPE);
        WEST_SHAPE.setOpenShape(NORTH_SHAPE, SOUTH_SHAPE);
    }

    public LintelThresholdThinDoorBlock(AbstractBlock.Settings settings, BlockSetType blockSetType) {
        super(settings, blockSetType);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.UPPER ? new SteelPlugDoorBlockEntity(pos, state) : null;
    }

    protected VoxelShape getShape(LintelThresholdDoorShape shape, boolean isOpen, boolean isLower, boolean isRight) {
        if (!isOpen) return shape.base;
        if (isRight) return isLower ? shape.open.right.bottom : shape.open.right.top;
        return isLower ? shape.open.left.bottom : shape.open.left.top;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction direction = state.get(FACING);
        boolean isOpen = state.get(OPEN);
        boolean isLower = state.get(HALF) == DoubleBlockHalf.LOWER;
        boolean isRight = state.get(HINGE) == DoorHinge.RIGHT;
        VoxelShape base = switch (direction) {
            case SOUTH -> getShape(NORTH_SHAPE, isOpen, isLower, isRight);
            case WEST -> getShape(EAST_SHAPE, isOpen, isLower, isRight);
            case NORTH -> getShape(SOUTH_SHAPE, isOpen, isLower, isRight);
            default -> getShape(WEST_SHAPE, isOpen, isLower, isRight);
        };
        return !isLower && roofAt(world, pos) != null ? VoxelShapes.union(base, ROOF_SHAPE) : base;
    }

    private static Vec3d localHit(Vec3d hitPos, BlockPos pos) {
        return hitPos.subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    private static @Nullable SteelPlugDoorBlockEntity roofAt(BlockView world, BlockPos upperPos) {
        return world.getBlockEntity(upperPos) instanceof SteelPlugDoorBlockEntity roof && roof.hasRoof() ? roof : null;
    }

    private static @Nullable SteelPlugDoorBlockEntity ownerAt(BlockView world, BlockPos pos, BlockState state) {
        BlockPos upperPos = state.get(HALF) == DoubleBlockHalf.UPPER ? pos : pos.up();
        return world.getBlockEntity(upperPos) instanceof SteelPlugDoorBlockEntity roof ? roof : null;
    }

    private static boolean isRoofHit(BlockState state, BlockPos pos, Vec3d hitPos) {
        return state.get(HALF) == DoubleBlockHalf.UPPER && SteelPlugDoorRoof.isRoofHit(localHit(hitPos, pos));
    }

    private static Vec3d canonicalHit(BlockState state, BlockPos pos, Vec3d hitPos) {
        return SteelPlugDoorRoof.toCanonical(localHit(hitPos, pos), state.get(FACING));
    }

    /** Item-side entry point used when sneaking bypasses Block#onUse. */
    public static @Nullable ActionResult tryInsertRoof(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof LintelThresholdThinDoorBlock)
                || state.get(HALF) != DoubleBlockHalf.UPPER
                || !context.getStack().isOf(ModBlock.LAYERED_COPYCAT_BOARD.asItem())) {
            return null;
        }
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null) return ActionResult.PASS;
        if (roof.hasRoof()) return ActionResult.success(world.isClient);
        if (!world.isClient) {
            roof.insertRoof();
            PlayerEntity player = context.getPlayer();
            if (player == null || !player.isCreative()) context.getStack().decrement(1);
            if (player != null) {
                LayeredCopycatBoardBlock.autoCamoNewLayers(world, pos, roof, player,
                        Direction.getEntityFacingOrder(player)[0]);
            }
            world.playSound(null, pos, ModBlock.LAYERED_COPYCAT_BOARD.getSoundGroup(
                    ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState()).getPlaceSound(),
                    SoundCategory.BLOCKS, 1.0F, 0.75F);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (state.get(HALF) == DoubleBlockHalf.UPPER && stack.isOf(ModBlock.LAYERED_COPYCAT_BOARD.asItem())) {
            ActionResult inserted = tryInsertRoof(new ItemUsageContext(player, hand, hit));
            return inserted == null ? ActionResult.PASS : inserted;
        }
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null || !roof.hasRoof() || !isRoofHit(state, pos, hit.getPos())) {
            return super.onUse(state, world, pos, player, hand, hit);
        }
        Vec3d canonical = canonicalHit(state, pos, hit.getPos());
        if (stack.getItem() instanceof DetailChisel) {
            return LayeredCopycatBoardBlock.onChisel(world, pos, player, roof, canonical);
        }
        if (stack.getItem() instanceof WrenchItem) {
            return onWrenched(state, new ItemUsageContext(player, hand, hit));
        }
        BlockState material = LayeredCopycatBoardBlock.getAcceptedMaterial(world, pos, stack, hit.getSide());
        return material == null ? ActionResult.PASS
                : LayeredCopycatBoardBlock.applyMaterial(world, pos, roof, material, stack, player, canonical);
    }

    public static ActionResult onRoofChisel(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null || !roof.hasRoof() || !isRoofHit(state, pos, context.getHitPos())) return ActionResult.PASS;
        return LayeredCopycatBoardBlock.onChisel(world, pos, context.getPlayer(), roof,
                canonicalHit(state, pos, context.getHitPos()));
    }

    @Override
    public ActionResult onWrenched(BlockState state, ItemUsageContext context) {
        SteelPlugDoorBlockEntity roof = ownerAt(context.getWorld(), context.getBlockPos(), state);
        if (roof == null || !roof.hasRoof()
                || !isRoofHit(state, context.getBlockPos(), context.getHitPos())) return ActionResult.PASS;
        return LayeredCopycatBoardBlock.removeMaterialAt(context.getWorld(), context.getBlockPos(),
                context.getPlayer(), roof, canonicalHit(state, context.getBlockPos(), context.getHitPos()));
    }

    @Override
    public ActionResult onSneakWrenched(BlockState state, ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (roof == null || !roof.hasRoof() || !isRoofHit(state, pos, context.getHitPos())) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;

        LayeredCopycatBoardBlock.LayerRemovalResult removal =
                LayeredCopycatBoardBlock.settleLayerForPlayer(world, pos, roof,
                        FaceDir.UP, BoardLayer.OUTER, context.getPlayer(), canonicalHit(state, pos, context.getHitPos()));
        roof.clearRoof();
        PlayerEntity player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            player.getInventory().offerOrDrop(new ItemStack(ModBlock.LAYERED_COPYCAT_BOARD));
        }
        if (removal.feedbackMaterial() != null) {
            world.syncWorldEvent(2001, pos, Block.getRawIdFromState(removal.feedbackMaterial()));
        }
        IWrenchable.playRemoveSound(world, pos);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        SteelPlugDoorBlockEntity roof = ownerAt(world, pos, state);
        if (!world.isClient && player.isCreative() && roof != null && roof.hasRoof()) roof.suppressRoofDrops();
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && state.get(HALF) == DoubleBlockHalf.UPPER && !world.isClient
                && world.getBlockEntity(pos) instanceof SteelPlugDoorBlockEntity roof && roof.hasRoof()) {
            boolean suppressed = roof.consumeDropSuppression();
            if (!suppressed) {
                Block.dropStack(world, pos, new ItemStack(ModBlock.LAYERED_COPYCAT_BOARD));
                roof.dropAllMaterials(world, pos);
            }
            roof.clearRoof();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
