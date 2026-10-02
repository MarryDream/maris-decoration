package marrydream.marisdecoration.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/** Overlapping rounded lobes; every lobe stays within six leaf steps of its branch. */
public final class TeakFoliagePlacer extends FoliagePlacer {
    //? if >=1.21 {
/*public static final com.mojang.serialization.MapCodec<TeakFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(instance ->
*///?} else {
public static final Codec<TeakFoliagePlacer> CODEC = RecordCodecBuilder.create(instance ->
//?}
            foliagePlacerParts(instance).apply(instance, TeakFoliagePlacer::new));

    public TeakFoliagePlacer(IntProvider radius, IntProvider offset) { super(radius, offset); }

    @Override
    protected FoliagePlacerType<?> type() { return ModWorldGeneration.TEAK_FOLIAGE; }

    @Override
    protected void createFoliage(LevelSimulatedReader world, FoliageSetter placer, RandomSource random, TreeConfiguration config,
            int trunkHeight, FoliageAttachment node, int foliageHeight, int radius, int offset) {
        for (int y = -2; y <= 2; y++) {
            int layerRadius = Math.max(1, radius - Math.abs(y));
            placeLeavesRow(world, placer, random, config, node.pos().above(offset), layerRadius, y, false);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) { return 5; }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        if (dx + dz > radius + 1) return true;
        // Remove only outer corners so the remaining leaves retain a connected path to wood.
        return dx != 0 && dz != 0 && dx + dz == radius + 1 && random.nextInt(4) == 0;
    }
}
