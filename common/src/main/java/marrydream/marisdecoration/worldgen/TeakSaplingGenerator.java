//? if >=1.21 {
/*package marrydream.marisdecoration.worldgen;
public final class TeakSaplingGenerator {
 public static net.minecraft.world.level.block.grower.TreeGrower grower() {
  return GrowerHolder.TEAK;
 }
 private static final class GrowerHolder {
  static final net.minecraft.world.level.block.grower.TreeGrower TEAK = new net.minecraft.world.level.block.grower.TreeGrower(
   "maris-decoration:teak", java.util.Optional.empty(), java.util.Optional.of(ModWorldGeneration.TEAK), java.util.Optional.empty());
 }
}

*///?} else {
package marrydream.marisdecoration.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public final class TeakSaplingGenerator extends AbstractTreeGrower {
    public static TeakSaplingGenerator grower() { return new TeakSaplingGenerator(); }
    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean bees) {
        return ModWorldGeneration.TEAK;
    }
}

//?}
