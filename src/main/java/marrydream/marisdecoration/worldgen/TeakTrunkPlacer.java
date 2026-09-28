package marrydream.marisdecoration.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TestableWorld;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.foliage.FoliagePlacer;
import net.minecraft.world.gen.trunk.TrunkPlacer;
import net.minecraft.world.gen.trunk.TrunkPlacerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Tall clear bole, with short, face-connected branches supporting a broad crown. */
public final class TeakTrunkPlacer extends TrunkPlacer {
    public static final Codec<TeakTrunkPlacer> CODEC = RecordCodecBuilder.create(instance ->
            fillTrunkPlacerFields(instance).apply(instance, TeakTrunkPlacer::new));

    public TeakTrunkPlacer(int baseHeight, int firstRandomHeight, int secondRandomHeight) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
    }

    @Override
    protected TrunkPlacerType<?> getType() { return ModWorldGeneration.TEAK_TRUNK; }

    @Override
    public List<FoliagePlacer.TreeNode> generate(TestableWorld world, BiConsumer<BlockPos, BlockState> replacer,
            Random random, int height, BlockPos start, TreeFeatureConfig config) {
        setToDirt(world, replacer, random, start.down(), config);
        for (int y = 0; y < height; y++) {
            getAndSetState(world, replacer, random, start.up(y), config);
        }
        List<FoliagePlacer.TreeNode> nodes = new ArrayList<>();
        nodes.add(new FoliagePlacer.TreeNode(start.up(height - 1), 0, false));
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int rotation = random.nextInt(4);
        int branchCount = 3 + random.nextInt(3);
        for (int i = 0; i < branchCount; i++) {
            Direction direction = directions[(i + rotation) % 4];
            BlockPos tip = start.up(height - 3);
            int length = 2 + random.nextInt(2);
            // The optional fifth branch forks sideways from an upper branch.
            if (i == 4) tip = tip.up();
            for (int step = 0; step < length; step++) {
                Direction segment = i == 4 && step > 0 ? direction.rotateYClockwise() : direction;
                BlockPos next = tip.offset(segment);
                if (!getAndSetState(world, replacer, random, next, config,
                        state -> state.with(PillarBlock.AXIS, segment.getAxis()))) break;
                tip = next;
                if (step == length - 2 && i != 4) {
                    BlockPos raised = tip.up();
                    if (getAndSetState(world, replacer, random, raised, config)) tip = raised;
                }
            }
            nodes.add(new FoliagePlacer.TreeNode(tip, 0, false));
        }
        return nodes;
    }
}
