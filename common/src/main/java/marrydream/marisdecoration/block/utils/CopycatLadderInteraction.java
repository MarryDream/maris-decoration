package marrydream.marisdecoration.block.utils;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity;
import marrydream.marisdecoration.item.CopycatPlacerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CopycatLadderInteraction {
    private CopycatLadderInteraction() {
    }

    /** Applies a valid offhand material to the requested slot after the ladder is placed. */
    public static void applyPlacedMaterial(Level world, BlockPos pos, @Nullable LivingEntity placer, String slot) {
        if (world.isClientSide || !(placer instanceof Player player)) return;
        if (player.getMainHandItem().getItem() instanceof CopycatPlacerItem
                || player.getOffhandItem().getItem() instanceof CopycatPlacerItem) return;

        ItemStack offhand = player.getOffhandItem();
        BlockState material = CopycatGuardrailBlock.getAcceptedMaterial(
                world, pos, offhand, Direction.orderedByNearest(placer)[0]);
        if (material == null || !(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)
                || be.hasMaterial(slot)) return;

        boolean pay = !be.hasMaterialBlock(material.getBlock());
        be.setMaterial(slot, material, pay ? offhand : null);
        if (!player.isCreative() && pay) {
            offhand.shrink(1);
            if (offhand.isEmpty()) player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    public static InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit, String slot) {
        if (slot == null) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        BlockState material = CopycatGuardrailBlock.getAcceptedMaterial(world, pos, stack, hit.getDirection());
        if (material == null || !(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (be.material(slot).is(material.getBlock())) {
            if (!be.cycleMaterial(slot)) return InteractionResult.PASS;
            world.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .75F, .95F);
            return InteractionResult.SUCCESS;
        }
        if (be.hasMaterial(slot)) return InteractionResult.PASS;
        if (!world.isClientSide) {
            boolean pay = !be.hasMaterialBlock(material.getBlock());
            be.setMaterial(slot, material, pay ? stack : null);
            world.playSound(null, pos, material.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1F, .75F);
            if (!player.isCreative() && pay) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult wrench(UseOnContext context, String slot) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (slot == null || !(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)
                || !be.hasMaterial(slot)) return InteractionResult.PASS;
        if (!world.isClientSide) {
            BlockState material = be.material(slot);
            ItemStack returned = be.takeConsumedItemForRemoval(slot);
            if (player != null && !player.isCreative() && !returned.isEmpty()) {
                player.getInventory().placeItemBackInInventory(returned);
            }
            world.levelEvent(2001, pos, Block.getId(material));
            IWrenchable.playRemoveSound(world, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Settles every material before the caller invokes IWrenchable's normal structure removal. */
    public static void settleForSneakWrench(UseOnContext context, List<String> slots) {
        Level world = context.getLevel();
        if (world.isClientSide) return;
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be)) return;
        for (String slot : slots) {
            if (!be.hasMaterial(slot)) continue;
            BlockState material = be.material(slot);
            ItemStack returned = be.takeConsumedItemForRemoval(slot);
            if (player != null && !player.isCreative() && !returned.isEmpty()) {
                player.getInventory().placeItemBackInInventory(returned);
            }
            world.levelEvent(2001, pos, Block.getId(material));
        }
    }

    public static void onBreak(Level world, BlockPos pos, Player player) {
        if (player.isCreative() && world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be) {
            be.clearConsumedItems();
        }
    }

    public static void onStateReplaced(BlockState state, Level world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !world.isClientSide && !moved
                && world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be) {
            be.dropAllMaterials(world, pos);
        }
    }
}
