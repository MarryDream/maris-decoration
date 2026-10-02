package marrydream.marisdecoration.placement.harness;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.block.utils.*;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.platform.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.List;

/** Tests real BE serialization, including pre-component consumedItem compounds. */
final class BlockEntityPersistenceTest {
    static void run(ServerLevel world, PlacementHarness.Assertions assertions) {
        for (BlockState state : List.of(ModBlock.COPYCAT_GUARDRAIL.defaultBlockState(),
                ModBlock.LAYERED_COPYCAT_BOARD.defaultBlockState(),
                ModBlock.COPYCAT_STEEL_FIXED_LADDER.defaultBlockState(),
                ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.defaultBlockState(),
                ModBlock.STEEL_PLUG_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))) {
            var original = create(state);
            ItemStack paid = new ItemStack(Blocks.STONE);
            if (original instanceof CopycatGuardrailBlockEntity rail) {
                for (String key : GuardrailParts.allKeys()) rail.setMaterial(key, Blocks.STONE.defaultBlockState(), paid);
                rail.setHiddenColumns(List.of(GuardrailParts.columnKeys().get(0)));
            } else if (original instanceof LayeredCopycatBoardBlockEntity board) {
                board.setOccupancy(original instanceof SteelPlugDoorBlockEntity ? SteelPlugDoorBlockEntity.ROOF_OCCUPANCY : 4095);
                board.toggleWindow(LayeredBoardSlots.FaceDir.UP);
                for (String key : LayeredBoardSlots.allMaterialKeys()) board.setMaterial(key, Blocks.STONE.defaultBlockState(), paid);
            } else if (original instanceof CopycatLadderBlockEntity ladder) {
                var keys = state.is(ModBlock.COPYCAT_STEEL_FIXED_LADDER) ? CopycatLadderParts.fixedSlots() : CopycatLadderParts.verticalSlots();
                for (String key : keys) ladder.setMaterial(key, Blocks.STONE.defaultBlockState(), paid);
            }
            CompoundTag saved = save(original, world);
            var restored = create(state);
            load(restored, saved, world);
            assertions.equal("BE round trip: " + state.getBlock(), saved, save(restored, world));
            assertions.equal("BE render snapshot round trip: " + state.getBlock(), original.getRenderData(), restored.getRenderData());

            CompoundTag legacy = saved.copy();
            legacyStacks(legacy);
            var upgraded = create(state);
            load(upgraded, legacy, world);
            assertions.equal("Legacy consumedItem upgrade: " + state.getBlock(), saved, save(upgraded, world));
            assertions.equal("Legacy BE render snapshot: " + state.getBlock(), original.getRenderData(), upgraded.getRenderData());
        }
    }

    private static RenderDataBlockEntity create(BlockState state) {
        return (RenderDataBlockEntity) ((EntityBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
    }

    private static CompoundTag save(RenderDataBlockEntity entity, ServerLevel world) {
        //? if >=1.21 {
        /*return entity.saveWithoutMetadata(world.registryAccess());
        *///?} else {
        return entity.saveWithoutMetadata();
        //?}
    }

    private static void load(RenderDataBlockEntity entity, CompoundTag tag, ServerLevel world) {
        //? if >=1.21 {
        /*entity.loadWithComponents(tag, world.registryAccess());
        *///?} else {
        entity.load(tag);
        //?}
    }

    private static void legacyStacks(CompoundTag tag) {
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (!tag.contains(key, Tag.TAG_COMPOUND)) continue;
            CompoundTag child = tag.getCompound(key);
            if (child.contains("id", Tag.TAG_STRING) && child.contains("count", Tag.TAG_ANY_NUMERIC)) {
                var legacy = new CompoundTag();
                legacy.putString("id", child.getString("id"));
                legacy.putByte("Count", (byte) child.getInt("count"));
                tag.put(key, legacy);
            } else {
                legacyStacks(child);
            }
        }
    }
}
