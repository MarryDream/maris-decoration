package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.worldgen.ModWorldGeneration;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.RegistryWrapper;
import java.util.concurrent.CompletableFuture;

public final class ModWorldGenerationProvider extends FabricDynamicRegistryProvider {
    public ModWorldGenerationProvider(FabricDataOutput output,
            CompletableFuture<RegistryWrapper.WrapperLookup> registries) { super(output, registries); }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        entries.add(registries.getWrapperOrThrow(net.minecraft.registry.RegistryKeys.CONFIGURED_FEATURE), ModWorldGeneration.TEAK);
        entries.add(registries.getWrapperOrThrow(net.minecraft.registry.RegistryKeys.PLACED_FEATURE), ModWorldGeneration.TEAK_SPARSE_JUNGLE);
        entries.add(registries.getWrapperOrThrow(net.minecraft.registry.RegistryKeys.PLACED_FEATURE), ModWorldGeneration.TEAK_SAVANNA);
    }

    @Override
    public String getName() { return "Teak world generation"; }
}
