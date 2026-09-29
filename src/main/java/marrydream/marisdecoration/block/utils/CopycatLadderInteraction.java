package marrydream.marisdecoration.block.utils;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CopycatLadderInteraction {
    private CopycatLadderInteraction() {
    }

    /** Applies a valid offhand material to the requested slot after the ladder is placed. */
    public static void applyPlacedMaterial(World world, BlockPos pos, @Nullable LivingEntity placer, String slot) {
        if (world.isClient || !(placer instanceof PlayerEntity player)) return;

        ItemStack offhand = player.getOffHandStack();
        BlockState material = CopycatGuardrailBlock.getAcceptedMaterial(
                world, pos, offhand, Direction.getEntityFacingOrder(placer)[0]);
        if (material == null || !(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)
                || be.hasMaterial(slot)) return;

        boolean pay = !be.hasMaterialBlock(material.getBlock());
        be.setMaterial(slot, material, pay ? offhand : null);
        if (!player.isCreative() && pay) {
            offhand.decrement(1);
            if (offhand.isEmpty()) player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    public static ActionResult use(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                   Hand hand, BlockHitResult hit, String slot) {
        if (slot == null) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        BlockState material = CopycatGuardrailBlock.getAcceptedMaterial(world, pos, stack, hit.getSide());
        if (material == null || !(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)) {
            return ActionResult.PASS;
        }
        if (be.material(slot).isOf(material.getBlock())) {
            if (!be.cycleMaterial(slot)) return ActionResult.PASS;
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, .75F, .95F);
            return ActionResult.SUCCESS;
        }
        if (be.hasMaterial(slot)) return ActionResult.PASS;
        if (!world.isClient) {
            boolean pay = !be.hasMaterialBlock(material.getBlock());
            be.setMaterial(slot, material, pay ? stack : null);
            world.playSound(null, pos, material.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1F, .75F);
            if (!player.isCreative() && pay) stack.decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    public static ActionResult wrench(ItemUsageContext context, String slot) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        if (slot == null || !(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)
                || !be.hasMaterial(slot)) return ActionResult.PASS;
        if (!world.isClient) {
            BlockState material = be.material(slot);
            ItemStack returned = be.takeConsumedItemForRemoval(slot);
            if (player != null && !player.isCreative() && !returned.isEmpty()) {
                player.getInventory().offerOrDrop(returned);
            }
            world.syncWorldEvent(2001, pos, Block.getRawIdFromState(material));
            IWrenchable.playRemoveSound(world, pos);
        }
        return ActionResult.SUCCESS;
    }

    /** Settles every material before the caller invokes IWrenchable's normal structure removal. */
    public static void settleForSneakWrench(ItemUsageContext context, List<String> slots) {
        World world = context.getWorld();
        if (world.isClient) return;
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        if (!(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)) return;
        for (String slot : slots) {
            if (!be.hasMaterial(slot)) continue;
            BlockState material = be.material(slot);
            ItemStack returned = be.takeConsumedItemForRemoval(slot);
            if (player != null && !player.isCreative() && !returned.isEmpty()) {
                player.getInventory().offerOrDrop(returned);
            }
            world.syncWorldEvent(2001, pos, Block.getRawIdFromState(material));
        }
    }

    public static void onBreak(World world, BlockPos pos, PlayerEntity player) {
        if (player.isCreative() && world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be) {
            be.clearConsumedItems();
        }
    }

    public static void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.isOf(next.getBlock()) && !world.isClient && !moved
                && world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be) {
            be.dropAllMaterials(world, pos);
        }
    }
}
