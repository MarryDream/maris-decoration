package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;

import java.util.concurrent.CompletableFuture;

public final class ModBlockTagProvider extends FabricTagProvider.BlockTagProvider {
    private static final Block[] WOODEN = {
            ModBlock.TEAK_PLANKS, ModBlock.WEATHERED_TEAK_PLANKS, ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS,
            ModBlock.TEAK_TRAPDOOR, ModBlock.TEAK_FENCE, ModBlock.TEAK_FENCE_GATE,
            ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_BUTTON
    };
    private static final Block[] METAL = {
            ModBlock.STEEL_BLOCK, ModBlock.CYAN_STEEL_BLOCK, ModBlock.BLACK_STEEL_BLOCK,
            ModBlock.COPYCAT_GUARDRAIL,
            ModBlock.COPYCAT_STEEL_FIXED_LADDER, ModBlock.COPYCAT_STEEL_VERTICAL_LADDER,
            ModBlock.STEEL_PLUG_DOOR
    };

    public ModBlockTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries) {
        super(output, registries);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup lookup) {
        getOrCreateTagBuilder(marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOGS)
                .add(ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG, ModBlock.STRIPPED_TEAK_WOOD);
        getOrCreateTagBuilder(BlockTags.LOGS_THAT_BURN).addTag(marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOGS);
        getOrCreateTagBuilder(BlockTags.AXE_MINEABLE).addTag(marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOGS);
        getOrCreateTagBuilder(BlockTags.LEAVES).add(ModBlock.TEAK_LEAVES);
        getOrCreateTagBuilder(BlockTags.HOE_MINEABLE).add(ModBlock.TEAK_LEAVES);
        getOrCreateTagBuilder(BlockTags.SAPLINGS).add(ModBlock.TEAK_SAPLING);
        getOrCreateTagBuilder(BlockTags.AXE_MINEABLE).add(WOODEN);
        getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(METAL);
        getOrCreateTagBuilder(BlockTags.NEEDS_IRON_TOOL).add(METAL);
        getOrCreateTagBuilder(BlockTags.PLANKS).add(ModBlock.TEAK_PLANKS);
        getOrCreateTagBuilder(BlockTags.WOODEN_STAIRS).add(ModBlock.TEAK_STAIRS);
        getOrCreateTagBuilder(BlockTags.WOODEN_SLABS).add(ModBlock.TEAK_SLABS);
        getOrCreateTagBuilder(BlockTags.WOODEN_TRAPDOORS).add(ModBlock.TEAK_TRAPDOOR);
        getOrCreateTagBuilder(BlockTags.WOODEN_FENCES).add(ModBlock.TEAK_FENCE);
        getOrCreateTagBuilder(BlockTags.FENCE_GATES).add(ModBlock.TEAK_FENCE_GATE);
        getOrCreateTagBuilder(BlockTags.WOODEN_PRESSURE_PLATES).add(ModBlock.TEAK_PRESSURE_PLATE);
        getOrCreateTagBuilder(BlockTags.WOODEN_BUTTONS).add(ModBlock.TEAK_BUTTON);
        getOrCreateTagBuilder(BlockTags.CLIMBABLE).add(
                ModBlock.COPYCAT_STEEL_FIXED_LADDER, ModBlock.COPYCAT_STEEL_VERTICAL_LADDER);
    }
}
