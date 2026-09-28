package marrydream.marisdecoration.worldgen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in integration harness; runTeakTest uses its own disposable world under build/. */
public final class TeakSelfTest {
    private static final List<String> REPORT = new ArrayList<>();
    private static int checks;
    private static final BlockPos ORIGIN = new BlockPos(0, 200, 0);

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
            try {
                run(server);
                REPORT.add("PASS: " + checks + " checks");
            } catch (Throwable failure) {
                REPORT.add("FAIL: " + failure);
                failure.printStackTrace();
            } finally {
                try { Files.write(Path.of("teak-selftest.txt"), REPORT); }
                catch (Exception failure) { failure.printStackTrace(); }
                REPORT.forEach(System.out::println);
                server.stop(false);
            }
        }));
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }

    private static void clear(ServerWorld world) {
        clear(world, ORIGIN);
    }

    private static void clear(ServerWorld world, BlockPos origin) {
        for (BlockPos pos : BlockPos.iterate(origin.add(-7, -1, -7), origin.add(7, 18, 7))) {
            world.setBlockState(pos, pos.getY() == origin.getY() - 1
                    ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
        }
    }

    private static void run(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        var registries = server.getRegistryManager();
        ConfiguredFeature<?, ?> tree = registries.get(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(ModWorldGeneration.TEAK);
        var expected = Set.of(BiomeKeys.SPARSE_JUNGLE, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU);
        for (var biome : registries.get(RegistryKeys.BIOME).streamEntries().toList()) {
            boolean hasTeak = biome.value().getGenerationSettings().getFeatures().stream()
                    .flatMap(list -> list.stream()).anyMatch(entry -> entry.matchesKey(ModWorldGeneration.TEAK_SPARSE_JUNGLE)
                            || entry.matchesKey(ModWorldGeneration.TEAK_SAVANNA));
            check(hasTeak == expected.contains(biome.registryKey()), "Biome injection: " + biome.registryKey());
        }
        for (Block log : List.of(ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG, ModBlock.STRIPPED_TEAK_WOOD)) {
            check(log.getDefaultState().isIn(BlockTags.LOGS), "Missing logs tag");
            check(new ItemStack(log).isIn(ItemTags.LOGS_THAT_BURN), "Missing burnable log item tag");
        }
        check(AxeItem.STRIPPED_BLOCKS.get(ModBlock.TEAK_LOG) == ModBlock.STRIPPED_TEAK_LOG, "Log stripping");
        check(AxeItem.STRIPPED_BLOCKS.get(ModBlock.TEAK_WOOD) == ModBlock.STRIPPED_TEAK_WOOD, "Wood stripping");
        for (String recipe : List.of("teak_planks", "teak_wood", "stripped_teak_wood")) {
            check(server.getRecipeManager().get(ModInfo.id(recipe)).isPresent(), "Missing recipe " + recipe);
        }
        REPORT.add("PASS: biome whitelist, block/item tags, stripping and recipes");

        int minHeight = 100, maxHeight = 0, minWidth = 100, maxWidth = 0;
        for (int seed = 0; seed < 64; seed++) {
            clear(world);
            check(tree.generate(world, world.getChunkManager().getChunkGenerator(), Random.create(seed * 0x9E3779B97F4A7C15L), ORIGIN), "Generation seed " + seed);
            Set<BlockPos> logs = new HashSet<>(), leaves = new HashSet<>();
            int top = 0, minX = 99, maxX = -99, minZ = 99, maxZ = -99;
            for (BlockPos pos : BlockPos.iterate(ORIGIN.add(-7, 0, -7), ORIGIN.add(7, 18, 7))) {
                BlockState state = world.getBlockState(pos);
                if (state.isOf(ModBlock.TEAK_LOG)) logs.add(pos.toImmutable());
                if (state.isOf(ModBlock.TEAK_LEAVES)) {
                    leaves.add(pos.toImmutable());
                    check(state.get(LeavesBlock.DISTANCE) < 7, "Unstable leaf seed " + seed + " at " + pos);
                    check(!state.get(LeavesBlock.PERSISTENT), "Generated persistent leaf");
                }
                if (state.isOf(ModBlock.TEAK_LOG) || state.isOf(ModBlock.TEAK_LEAVES)) {
                    top = Math.max(top, pos.getY() - ORIGIN.getY() + 1);
                    minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
                    minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
                }
            }
            check(top >= 11 && top <= 16, "Tree height " + top);
            check(logs.size() > 14 && leaves.size() > 50, "Incomplete tree");
            // Independent flood fill: all leaf blocks must really connect to wood, not just claim a distance.
            Set<BlockPos> reached = new HashSet<>(logs);
            Set<BlockPos> frontier = new HashSet<>(logs);
            for (int distance = 1; distance <= 6; distance++) {
                Set<BlockPos> next = new HashSet<>();
                for (BlockPos pos : frontier) for (Direction direction : Direction.values()) {
                    BlockPos neighbor = pos.offset(direction);
                    if (leaves.contains(neighbor) && reached.add(neighbor)) next.add(neighbor);
                }
                frontier = next;
            }
            check(reached.containsAll(leaves), "Disconnected foliage seed " + seed);
            minHeight = Math.min(minHeight, top); maxHeight = Math.max(maxHeight, top);
            int width = Math.max(maxX - minX + 1, maxZ - minZ + 1);
            minWidth = Math.min(minWidth, width); maxWidth = Math.max(maxWidth, width);
        }
        REPORT.add("PASS: 64 tree seeds, height " + minHeight + ".." + maxHeight + ", width " + minWidth + ".." + maxWidth + ", no unsupported leaves");

        clear(world);
        world.setBlockState(ORIGIN, ModBlock.TEAK_SAPLING.getDefaultState());
        ModBlock.TEAK_SAPLING.grow(world, Random.create(10), ORIGIN, world.getBlockState(ORIGIN));
        check(world.getBlockState(ORIGIN).get(SaplingBlock.STAGE) == 1, "Sapling stage");
        ModBlock.TEAK_SAPLING.grow(world, Random.create(11), ORIGIN, world.getBlockState(ORIGIN));
        check(world.getBlockState(ORIGIN).isOf(ModBlock.TEAK_LOG), "Bone meal growth");
        clear(world);
        world.setBlockState(ORIGIN, ModBlock.TEAK_SAPLING.getDefaultState().with(SaplingBlock.STAGE, 1));
        world.setBlockState(ORIGIN.up(5), Blocks.STONE.getDefaultState());
        ModBlock.TEAK_SAPLING.grow(world, Random.create(1), ORIGIN, world.getBlockState(ORIGIN));
        check(world.getBlockState(ORIGIN).isOf(ModBlock.TEAK_SAPLING), "Blocked growth must keep sapling");
        check(world.getBlockState(ORIGIN.up(5)).isOf(Blocks.STONE), "Blocked growth destroyed obstacle");
        // Use a separate, already-lit sky column. Repeatedly clearing the canopy above ORIGIN
        // queues lighting work that cannot finish inside this synchronous server-start callback.
        BlockPos naturalSapling = ORIGIN.add(48, 0, 0);
        world.getChunk(naturalSapling);
        world.setBlockState(naturalSapling.down(), Blocks.GRASS_BLOCK.getDefaultState());
        world.setBlockState(naturalSapling, ModBlock.TEAK_SAPLING.getDefaultState());
        world.setTimeOfDay(1000);
        check(world.getLightLevel(naturalSapling.up()) >= 9, "Natural growth fixture must have daylight");
        Random growthRandom = Random.create(37);
        for (int i = 0; i < 512 && world.getBlockState(naturalSapling).isOf(ModBlock.TEAK_SAPLING); i++) {
            ModBlock.TEAK_SAPLING.randomTick(world.getBlockState(naturalSapling), world, naturalSapling, growthRandom);
        }
        check(world.getBlockState(naturalSapling).isOf(ModBlock.TEAK_LOG), "Natural sapling growth");
        clear(world, naturalSapling);
        REPORT.add("PASS: bone meal, natural random ticks, obstruction protection");

        BlockState leaf = ModBlock.TEAK_LEAVES.getDefaultState();
        var sheared = Block.getDroppedStacks(leaf, world, ORIGIN, null, null, new ItemStack(Items.SHEARS));
        check(sheared.size() == 1 && sheared.get(0).isOf(ModBlock.TEAK_LEAVES.asItem()), "Shears loot");
        ItemStack silk = new ItemStack(Items.DIAMOND_HOE);
        silk.addEnchantment(net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1);
        check(Block.getDroppedStacks(leaf, world, ORIGIN, null, null, silk).stream()
                .anyMatch(stack -> stack.isOf(ModBlock.TEAK_LEAVES.asItem())), "Silk touch loot");
        int saplings = 0, sticks = 0;
        for (int i = 0; i < 1024; i++) for (ItemStack drop : Block.getDroppedStacks(leaf, world, ORIGIN, null)) {
            check(drop.isOf(ModBlock.TEAK_SAPLING.asItem()) || drop.isOf(Items.STICK), "Unexpected leaf drop");
            if (drop.isOf(ModBlock.TEAK_SAPLING.asItem())) saplings += drop.getCount();
            if (drop.isOf(Items.STICK)) sticks += drop.getCount();
        }
        check(saplings > 0 && sticks > 0, "Missing renewable drops");
        world.setBlockState(ORIGIN, leaf.with(LeavesBlock.DISTANCE, 7));
        ModBlock.TEAK_LEAVES.randomTick(world.getBlockState(ORIGIN), world, ORIGIN, Random.create(1));
        check(world.isAir(ORIGIN), "Unsupported leaf decay");
        world.setBlockState(ORIGIN, leaf.with(LeavesBlock.PERSISTENT, true));
        ModBlock.TEAK_LEAVES.randomTick(world.getBlockState(ORIGIN), world, ORIGIN, Random.create(1));
        check(world.getBlockState(ORIGIN).isOf(ModBlock.TEAK_LEAVES), "Player leaf persistence");
        REPORT.add("PASS: shears, silk touch, saplings/sticks, natural decay and persistent leaves");

        // Load actual new terrain, rather than calling the configured feature, to exercise biome placement.
        for (var biomeKey : List.of(BiomeKeys.SPARSE_JUNGLE, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU)) {
            var located = world.locateBiome(entry -> entry.matchesKey(biomeKey), new BlockPos(0, 80, 0), 10000, 32, 64);
            check(located != null, "Could not locate " + biomeKey);
            BlockPos center = located.getFirst();
            boolean found = false;
            for (int radius = 0; radius <= 8 && !found; radius++) {
                for (int cx = -radius; cx <= radius && !found; cx++) for (int cz = -radius; cz <= radius && !found; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != radius) continue;
                    var chunk = world.getChunk((center.getX() >> 4) + cx, (center.getZ() >> 4) + cz);
                    int bx = chunk.getPos().getStartX(), bz = chunk.getPos().getStartZ();
                    for (int x = bx; x < bx + 16 && !found; x++) for (int z = bz; z < bz + 16 && !found; z++) {
                        for (int y = 60; y < 160; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (world.getBlockState(pos).isOf(ModBlock.TEAK_LOG)
                                    && world.getBlockState(pos.down()).isIn(BlockTags.DIRT)
                                    && world.getBiome(pos).matchesKey(biomeKey)) { found = true; break; }
                        }
                    }
                }
            }
            check(found, "No natural teak near " + biomeKey + " " + center);
            REPORT.add("PASS: natural terrain teak in " + biomeKey.getValue() + " near " + center);
            System.out.println(REPORT.get(REPORT.size() - 1));
        }
    }
}
