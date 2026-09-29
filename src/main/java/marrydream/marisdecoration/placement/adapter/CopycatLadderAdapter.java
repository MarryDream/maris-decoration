package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity;
import marrydream.marisdecoration.block.CopycatSteelFixedLadderBlock;
import marrydream.marisdecoration.block.CopycatSteelVerticalLadderBlock;
import marrydream.marisdecoration.block.VerticalLadderBlock;
import marrydream.marisdecoration.block.enums.PropLadderShape;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Copycat Placer support for this mod's fixed and vertical steel ladders. */
public final class CopycatLadderAdapter implements CopycatPlacementAdapter {
    private static final String LABEL_PREFIX = "maris-decoration.copycat_placer.slot.copycat_ladder.";
    private static final String GROUP = "maris-decoration.copycat_placer.group.main";

    @Override
    public boolean supports(Block block) {
        return block instanceof CopycatSteelFixedLadderBlock
                || block instanceof CopycatSteelVerticalLadderBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_OWN_CUSTOM;
    }

    @Override
    public String name() {
        return "maris-decoration:copycat_ladder";
    }

    @Override
    public @Nullable BlockState stateFrom(BlockState state, PlacementConfig config) {
        if (state.getBlock() instanceof CopycatSteelVerticalLadderBlock) {
            return state.with(VerticalLadderBlock.SHAPE, PropLadderShape.START);
        }
        return state;
    }

    @Override
    public @Nullable BlockState stateForPlacement(BlockState state, PlacementConfig config,
                                                  World world, BlockPos pos) {
        BlockState shaped = stateFrom(state, config);
        if (shaped == null || !(shaped.getBlock() instanceof CopycatSteelVerticalLadderBlock)) {
            return shaped;
        }
        BlockState above = world.getBlockState(pos.up());
        boolean hanging = above.getBlock() instanceof VerticalLadderBlock
                && above.get(LadderBlock.FACING) == shaped.get(LadderBlock.FACING);
        return shaped.with(VerticalLadderBlock.SHAPE,
                hanging ? PropLadderShape.NORMAL : PropLadderShape.START);
    }

    @Override
    public Set<String> hiddenProperties(PlacementConfig config) {
        return config.block() instanceof CopycatSteelVerticalLadderBlock
                ? Set.of(VerticalLadderBlock.SHAPE.getName()) : Set.of();
    }

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        return slotKeys(state.getBlock()).stream()
                .map(key -> AdapterSlot.of(key, LABEL_PREFIX + key, GROUP, true, config.slots().get(key)))
                .toList();
    }

    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config,
                                   @Nullable BlockView world, @Nullable BlockPos pos) {
        return CopycatGuardrailBlock.getAcceptedMaterial(world, pos,
                new ItemStack(material.getBlock()), null) != null;
    }

    @Override
    public void apply(PlacementContext context) {
        List<String> slots = slotKeys(context.state().getBlock());
        if (!(context.world().getBlockEntity(context.pos()) instanceof CopycatLadderBlockEntity blockEntity)) {
            for (String slot : slots) {
                BlockState material = context.material(slot);
                if (material != null) {
                    context.markSkipped(slot, material, PlacementContext.SkippedSlot.Reason.NO_BLOCK_ENTITY);
                }
            }
            return;
        }

        for (String slot : slots) {
            BlockState material = context.material(slot);
            if (material == null) continue;
            if (blockEntity.hasMaterial(slot)) {
                context.markSkipped(slot, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
                continue;
            }
            if (!context.acceptsMaterial(material, slot)) {
                context.markSkipped(slot, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
                continue;
            }
            boolean alreadyPaid = context.paid().contains(material);
            if (!context.claim(material)) {
                context.markSkipped(slot, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
                continue;
            }
            blockEntity.setMaterial(slot, material,
                    alreadyPaid ? null : new ItemStack(material.getBlock()));
            context.markApplied(slot);
        }
    }

    private static List<String> slotKeys(Block block) {
        return block instanceof CopycatSteelFixedLadderBlock
                ? CopycatLadderParts.fixedSlots() : CopycatLadderParts.verticalSlots();
    }
}
