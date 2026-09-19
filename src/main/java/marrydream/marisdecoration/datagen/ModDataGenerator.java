package marrydream.marisdecoration.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public final class ModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        ModBlockTagProvider blockTags = pack.addProvider(ModBlockTagProvider::new);
        pack.addProvider((output, registries) -> new ModItemTagProvider(output, registries, blockTags));
        pack.addProvider(ModLootTableProvider::new);
        pack.addProvider(ModModelProvider::new);
        pack.addProvider(ModRecipeProvider::new);
        pack.addProvider((output, registries) -> new ModLanguageProvider(output, "en_us"));
        pack.addProvider((output, registries) -> new ModLanguageProvider(output, "zh_cn"));
    }
}
