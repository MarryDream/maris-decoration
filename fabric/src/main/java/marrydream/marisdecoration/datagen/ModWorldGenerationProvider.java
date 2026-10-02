package marrydream.marisdecoration.datagen;

import marrydream.marisdecoration.worldgen.ModWorldGeneration;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import java.util.concurrent.CompletableFuture;

public final class ModWorldGenerationProvider extends FabricDynamicRegistryProvider {
    public ModWorldGenerationProvider(FabricDataOutput output,
            CompletableFuture<HolderLookup.Provider> registries) { super(output, registries); }

    @Override
    protected void configure(HolderLookup.Provider registries, Entries entries) {
        entries.add(registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE), ModWorldGeneration.TEAK);
        entries.add(registries.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE), ModWorldGeneration.TEAK_SPARSE_JUNGLE);
        entries.add(registries.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE), ModWorldGeneration.TEAK_SAVANNA);
    }

    @Override
    public String getName() { return "Teak world generation"; }
}
