package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.block.Block;

import java.util.Set;

public final class ModLootTableProvider extends FabricBlockLootTableProvider {
    public ModLootTableProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate() {
        Set<Block> specialDrops = Set.of(
                ModBlock.TEAK_SLABS, ModBlock.STEEL_SLABS, ModBlock.CYAN_STEEL_SLABS, ModBlock.BLACK_STEEL_SLABS,
                ModBlock.STEEL_PLUG_DOOR, ModBlock.STEEL_PLUG_DOOR_WITH_ROOF,
                ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF, ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF,
                ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF
        );
        for (Block block : ModBlock.registeredBlocks()) {
            if (!specialDrops.contains(block)) {
                addDrop(block);
            }
        }
        addDrop(ModBlock.TEAK_SLABS, slabDrops(ModBlock.TEAK_SLABS));
        addDrop(ModBlock.STEEL_SLABS, slabDrops(ModBlock.STEEL_SLABS));
        addDrop(ModBlock.CYAN_STEEL_SLABS, slabDrops(ModBlock.CYAN_STEEL_SLABS));
        addDrop(ModBlock.BLACK_STEEL_SLABS, slabDrops(ModBlock.BLACK_STEEL_SLABS));
        addDrop(ModBlock.STEEL_PLUG_DOOR, doorDrops(ModBlock.STEEL_PLUG_DOOR));
        addDrop(ModBlock.STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.STEEL_PLUG_DOOR_WITH_ROOF));
        addDrop(ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF));
        addDrop(ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF));
        addDrop(ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF, doorDrops(ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF));
    }
}
