package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;

import java.util.concurrent.CompletableFuture;

public final class ModItemTagProvider extends FabricTagProvider.ItemTagProvider {
    public ModItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registries,
                              FabricTagProvider.BlockTagProvider blockTags) {
        super(output, registries, blockTags);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup lookup) {
        copy(marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOGS,
                marrydream.marisdecoration.worldgen.ModWorldGeneration.TEAK_LOG_ITEMS);
        copy(BlockTags.LOGS_THAT_BURN, ItemTags.LOGS_THAT_BURN);
        copy(BlockTags.LEAVES, ItemTags.LEAVES);
        copy(BlockTags.SAPLINGS, ItemTags.SAPLINGS);
        copy(BlockTags.PLANKS, ItemTags.PLANKS);
        copy(BlockTags.WOODEN_STAIRS, ItemTags.WOODEN_STAIRS);
        copy(BlockTags.WOODEN_SLABS, ItemTags.WOODEN_SLABS);
        copy(BlockTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_TRAPDOORS);
        // 栅栏、栅栏门、压力板、按钮：原版锅炉燃料表就是按这几个物品标签取值的
        copy(BlockTags.WOODEN_FENCES, ItemTags.WOODEN_FENCES);
        copy(BlockTags.FENCE_GATES, ItemTags.FENCE_GATES);
        copy(BlockTags.WOODEN_PRESSURE_PLATES, ItemTags.WOODEN_PRESSURE_PLATES);
        copy(BlockTags.WOODEN_BUTTONS, ItemTags.WOODEN_BUTTONS);
    }
}
