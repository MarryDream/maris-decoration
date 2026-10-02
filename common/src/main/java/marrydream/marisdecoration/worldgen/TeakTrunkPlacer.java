package marrydream.marisdecoration.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/** Tall clear bole, with short, face-connected branches supporting a broad crown. */
public final class TeakTrunkPlacer extends TrunkPlacer {
    //? if >=1.21 {
/*public static final com.mojang.serialization.MapCodec<TeakTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(instance ->
*///?} else {
public static final Codec<TeakTrunkPlacer> CODEC = RecordCodecBuilder.create(instance ->
//?}
            trunkPlacerParts(instance).apply(instance, TeakTrunkPlacer::new));

    public TeakTrunkPlacer(int baseHeight, int firstRandomHeight, int secondRandomHeight) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
    }

    @Override
    protected TrunkPlacerType<?> type() { return ModWorldGeneration.TEAK_TRUNK; }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer,
            RandomSource random, int height, BlockPos start, TreeConfiguration config) {
        setDirtAt(world, replacer, random, start.below(), config);
        for (int y = 0; y < height; y++) {
            placeLog(world, replacer, random, start.above(y), config);
        }
        List<FoliagePlacer.FoliageAttachment> nodes = new ArrayList<>();
        nodes.add(new FoliagePlacer.FoliageAttachment(start.above(height - 1), 0, false));
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int rotation = random.nextInt(4);
        int branchCount = 3 + random.nextInt(3);
        for (int i = 0; i < branchCount; i++) {
            Direction direction = directions[(i + rotation) % 4];
            BlockPos tip = start.above(height - 3);
            int length = 2 + random.nextInt(2);
            // The optional fifth branch forks sideways from an upper branch.
            if (i == 4) tip = tip.above();
            for (int step = 0; step < length; step++) {
                Direction segment = i == 4 && step > 0 ? direction.getClockWise() : direction;
                BlockPos next = tip.relative(segment);
                if (!placeLog(world, replacer, random, next, config,
                        state -> state.setValue(RotatedPillarBlock.AXIS, segment.getAxis()))) break;
                tip = next;
                if (step == length - 2 && i != 4) {
                    BlockPos raised = tip.above();
                    if (placeLog(world, replacer, random, raised, config)) tip = raised;
                }
            }
            nodes.add(new FoliagePlacer.FoliageAttachment(tip, 0, false));
        }
        return nodes;
    }
}
