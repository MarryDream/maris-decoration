package marrydream.marisdecoration.worldgen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

public final class ModWorldGeneration {
    public static final TagKey<Block> TEAK_LOGS = TagKey.create(Registries.BLOCK, ModInfo.id("teak_logs"));
    public static final TagKey<Item> TEAK_LOG_ITEMS = TagKey.create(Registries.ITEM, ModInfo.id("teak_logs"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> TEAK = ResourceKey.create(Registries.CONFIGURED_FEATURE, ModInfo.id("teak"));
    public static final ResourceKey<PlacedFeature> TEAK_SPARSE_JUNGLE = ResourceKey.create(Registries.PLACED_FEATURE, ModInfo.id("teak_sparse_jungle"));
    public static final ResourceKey<PlacedFeature> TEAK_SAVANNA = ResourceKey.create(Registries.PLACED_FEATURE, ModInfo.id("teak_savanna"));
    public static final TrunkPlacerType<TeakTrunkPlacer> TEAK_TRUNK = Registry.register(
            BuiltInRegistries.TRUNK_PLACER_TYPE, ModInfo.id("teak_trunk"), new TrunkPlacerType<>(TeakTrunkPlacer.CODEC));
    public static final FoliagePlacerType<TeakFoliagePlacer> TEAK_FOLIAGE = Registry.register(
            BuiltInRegistries.FOLIAGE_PLACER_TYPE, ModInfo.id("teak_foliage"), new FoliagePlacerType<>(TeakFoliagePlacer.CODEC));

    private ModWorldGeneration() {}

    public static void init() {
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.SPARSE_JUNGLE),
                GenerationStep.Decoration.VEGETAL_DECORATION, TEAK_SPARSE_JUNGLE);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU),
                GenerationStep.Decoration.VEGETAL_DECORATION, TEAK_SAVANNA);
    }

    public static void bootstrapConfigured(BootstapContext<ConfiguredFeature<?, ?>> registry) {
        registry.register(TEAK, new ConfiguredFeature<>(Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(ModBlock.TEAK_LOG), new TeakTrunkPlacer(9, 3, 2),
                BlockStateProvider.simple(ModBlock.TEAK_LEAVES),
                new TeakFoliagePlacer(UniformInt.of(2, 3), ConstantInt.of(0)),
                // Reserve the crown envelope before placing anything: no partially overwritten buildings.
                new TwoLayersFeatureSize(5, 0, 6)).ignoreVines().build()));
    }

    public static void bootstrapPlaced(BootstapContext<PlacedFeature> registry) {
        var tree = registry.lookup(Registries.CONFIGURED_FEATURE).getOrThrow(TEAK);
        PlacementUtils.register(registry, TEAK_SPARSE_JUNGLE, tree, CountPlacement.of(1),
                InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                PlacementUtils.filteredByBlockSurvival(ModBlock.TEAK_SAPLING), BiomeFilter.biome());
        PlacementUtils.register(registry, TEAK_SAVANNA, tree, RarityFilter.onAverageOnceEvery(4),
                InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                PlacementUtils.filteredByBlockSurvival(ModBlock.TEAK_SAPLING), BiomeFilter.biome());
    }
}
