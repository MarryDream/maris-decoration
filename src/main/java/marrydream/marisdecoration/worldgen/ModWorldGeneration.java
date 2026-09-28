package marrydream.marisdecoration.worldgen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.feature.size.TwoLayersFeatureSize;
import net.minecraft.world.gen.foliage.FoliagePlacerType;
import net.minecraft.world.gen.placementmodifier.*;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import net.minecraft.world.gen.trunk.TrunkPlacerType;

public final class ModWorldGeneration {
    public static final TagKey<Block> TEAK_LOGS = TagKey.of(RegistryKeys.BLOCK, ModInfo.id("teak_logs"));
    public static final TagKey<Item> TEAK_LOG_ITEMS = TagKey.of(RegistryKeys.ITEM, ModInfo.id("teak_logs"));
    public static final RegistryKey<ConfiguredFeature<?, ?>> TEAK = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, ModInfo.id("teak"));
    public static final RegistryKey<PlacedFeature> TEAK_SPARSE_JUNGLE = RegistryKey.of(RegistryKeys.PLACED_FEATURE, ModInfo.id("teak_sparse_jungle"));
    public static final RegistryKey<PlacedFeature> TEAK_SAVANNA = RegistryKey.of(RegistryKeys.PLACED_FEATURE, ModInfo.id("teak_savanna"));
    public static final TrunkPlacerType<TeakTrunkPlacer> TEAK_TRUNK = Registry.register(
            Registries.TRUNK_PLACER_TYPE, ModInfo.id("teak_trunk"), new TrunkPlacerType<>(TeakTrunkPlacer.CODEC));
    public static final FoliagePlacerType<TeakFoliagePlacer> TEAK_FOLIAGE = Registry.register(
            Registries.FOLIAGE_PLACER_TYPE, ModInfo.id("teak_foliage"), new FoliagePlacerType<>(TeakFoliagePlacer.CODEC));

    private ModWorldGeneration() {}

    public static void init() {
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.SPARSE_JUNGLE),
                GenerationStep.Feature.VEGETAL_DECORATION, TEAK_SPARSE_JUNGLE);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU),
                GenerationStep.Feature.VEGETAL_DECORATION, TEAK_SAVANNA);
    }

    public static void bootstrapConfigured(Registerable<ConfiguredFeature<?, ?>> registry) {
        registry.register(TEAK, new ConfiguredFeature<>(Feature.TREE, new TreeFeatureConfig.Builder(
                BlockStateProvider.of(ModBlock.TEAK_LOG), new TeakTrunkPlacer(9, 3, 2),
                BlockStateProvider.of(ModBlock.TEAK_LEAVES),
                new TeakFoliagePlacer(UniformIntProvider.create(2, 3), ConstantIntProvider.create(0)),
                // Reserve the crown envelope before placing anything: no partially overwritten buildings.
                new TwoLayersFeatureSize(5, 0, 6)).ignoreVines().build()));
    }

    public static void bootstrapPlaced(Registerable<PlacedFeature> registry) {
        var tree = registry.getRegistryLookup(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(TEAK);
        PlacedFeatures.register(registry, TEAK_SPARSE_JUNGLE, tree, CountPlacementModifier.of(1),
                SquarePlacementModifier.of(), PlacedFeatures.OCEAN_FLOOR_HEIGHTMAP,
                PlacedFeatures.wouldSurvive(ModBlock.TEAK_SAPLING), BiomePlacementModifier.of());
        PlacedFeatures.register(registry, TEAK_SAVANNA, tree, RarityFilterPlacementModifier.of(4),
                SquarePlacementModifier.of(), PlacedFeatures.OCEAN_FLOOR_HEIGHTMAP,
                PlacedFeatures.wouldSurvive(ModBlock.TEAK_SAPLING), BiomePlacementModifier.of());
    }
}
