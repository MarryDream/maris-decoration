package marrydream.marisdecoration.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TestableWorld;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.foliage.FoliagePlacer;
import net.minecraft.world.gen.foliage.FoliagePlacerType;

/** Overlapping rounded lobes; every lobe stays within six leaf steps of its branch. */
public final class TeakFoliagePlacer extends FoliagePlacer {
    public static final Codec<TeakFoliagePlacer> CODEC = RecordCodecBuilder.create(instance ->
            fillFoliagePlacerFields(instance).apply(instance, TeakFoliagePlacer::new));

    public TeakFoliagePlacer(IntProvider radius, IntProvider offset) { super(radius, offset); }

    @Override
    protected FoliagePlacerType<?> getType() { return ModWorldGeneration.TEAK_FOLIAGE; }

    @Override
    protected void generate(TestableWorld world, BlockPlacer placer, Random random, TreeFeatureConfig config,
            int trunkHeight, TreeNode node, int foliageHeight, int radius, int offset) {
        for (int y = -2; y <= 2; y++) {
            int layerRadius = Math.max(1, radius - Math.abs(y));
            generateSquare(world, placer, random, config, node.getCenter().up(offset), layerRadius, y, false);
        }
    }

    @Override
    public int getRandomHeight(Random random, int trunkHeight, TreeFeatureConfig config) { return 5; }

    @Override
    protected boolean isInvalidForLeaves(Random random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        if (dx + dz > radius + 1) return true;
        // Remove only outer corners so the remaining leaves retain a connected path to wood.
        return dx != 0 && dz != 0 && dx + dz == radius + 1 && random.nextInt(4) == 0;
    }
}
