package marrydream.marisdecoration.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public final class ModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void buildRegistry(net.minecraft.core.RegistrySetBuilder builder) {
        builder.add(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                marrydream.marisdecoration.worldgen.ModWorldGeneration::bootstrapConfigured);
        builder.add(net.minecraft.core.registries.Registries.PLACED_FEATURE,
                marrydream.marisdecoration.worldgen.ModWorldGeneration::bootstrapPlaced);
    }

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(ModWorldGenerationProvider::new);
        ModBlockTagProvider blockTags = pack.addProvider(ModBlockTagProvider::new);
        pack.addProvider((output, registries) -> new ModItemTagProvider(output, registries, blockTags));
        pack.addProvider(ModLootTableProvider::new);
        pack.addProvider(ModModelProvider::new);
        pack.addProvider(ModRecipeProvider::new);
        pack.addProvider((output, registries) -> new ModLanguageProvider(output, "en_us"));
        pack.addProvider((output, registries) -> new ModLanguageProvider(output, "zh_cn"));
    }
}
