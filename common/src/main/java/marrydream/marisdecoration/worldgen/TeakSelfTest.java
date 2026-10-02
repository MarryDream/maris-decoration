package marrydream.marisdecoration.worldgen;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import marrydream.marisdecoration.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in integration harness; runTeakTest uses its own disposable world under build/. */
public final class TeakSelfTest {
    private static final List<String> REPORT = new ArrayList<>();
    private static int checks;
    private static final BlockPos ORIGIN = new BlockPos(0, 200, 0);

    public static void register() {
        Platform.onServerStarted(server -> server.execute(() -> {
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
                server.halt(false);
            }
        }));
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }

    /** 原版燃料表里的燃烧时间（tick），经 Fabric 的 FuelRegistry 查询。 */
    private static void checkFuel(ItemLike item, int expected) {
        Integer actual = Platform.fuel(item);
        check(actual != null && actual == expected, "Fuel time of " + item + " is " + actual + ", expected " + expected);
    }

    private static void clear(ServerLevel world) {
        clear(world, ORIGIN);
    }

    private static void clear(ServerLevel world, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, -1, -7), origin.offset(7, 18, 7))) {
            world.setBlock(pos, pos.getY() == origin.getY() - 1
                    ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
        }
    }

    private static void run(MinecraftServer server) {
        ServerLevel world = server.overworld();
        var registries = server.registryAccess();
        ConfiguredFeature<?, ?> tree = registries.registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(ModWorldGeneration.TEAK);
        var expected = Set.of(Biomes.SPARSE_JUNGLE, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU);
        for (var biome : registries.registryOrThrow(Registries.BIOME).holders().toList()) {
            boolean hasTeak = biome.value().getGenerationSettings().features().stream()
                    .flatMap(list -> list.stream()).anyMatch(entry -> entry.is(ModWorldGeneration.TEAK_SPARSE_JUNGLE)
                            || entry.is(ModWorldGeneration.TEAK_SAVANNA));
            check(hasTeak == expected.contains(biome.key()), "Biome injection: " + biome.key());
        }
        for (Block log : List.of(ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG, ModBlock.STRIPPED_TEAK_WOOD)) {
            check(log.defaultBlockState().is(BlockTags.LOGS), "Missing logs tag");
            check(new ItemStack(log).is(ItemTags.LOGS_THAT_BURN), "Missing burnable log item tag");
        }
        check(Platform.strippedBlock(ModBlock.TEAK_LOG) == ModBlock.STRIPPED_TEAK_LOG, "Log stripping");
        check(Platform.strippedBlock(ModBlock.TEAK_WOOD) == ModBlock.STRIPPED_TEAK_WOOD, "Wood stripping");
        for (String recipe : List.of("teak_planks", "teak_wood", "stripped_teak_wood")) {
            check(server.getRecipeManager().byKey(ModInfo.id(recipe)).isPresent(), "Missing recipe " + recipe);
        }
        REPORT.add("PASS: biome whitelist, block/item tags, stripping and recipes");

        // 木板家族：对照 1.20.1 原版的方块设置（Blocks 的 oak_*）、火焰蔓延表（FireBlock）与燃料表

        for (Block wooden : List.of(ModBlock.TEAK_PLANKS, ModBlock.WEATHERED_TEAK_PLANKS, ModBlock.TEAK_STAIRS,
                ModBlock.TEAK_FENCE, ModBlock.TEAK_FENCE_GATE)) {
            check(Platform.burnChance(wooden) == 5, "Burn chance " + wooden);
            check(Platform.spreadChance(wooden) == 20, "Spread chance " + wooden);
        }
        // 原版没有把活板门、压力板、按钮放进火焰蔓延表，它们只是燃料
        for (Block notFlammable : List.of(ModBlock.TEAK_TRAPDOOR, ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_BUTTON)) {
            check(Platform.burnChance(notFlammable) == 0, "Unexpected burn chance " + notFlammable);
        }
        check(Platform.burnChance(ModBlock.TEAK_LOG) == 5
                && Platform.spreadChance(ModBlock.TEAK_LOG) == 5, "Log flammability");
        check(Platform.burnChance(ModBlock.TEAK_LEAVES) == 30
                && Platform.spreadChance(ModBlock.TEAK_LEAVES) == 60, "Leaf flammability");
        checkFuel(ModBlock.TEAK_PLANKS, 300);
        checkFuel(ModBlock.WEATHERED_TEAK_PLANKS, 300);
        checkFuel(ModBlock.TEAK_STAIRS, 300);
        checkFuel(ModBlock.TEAK_SLABS, 150);
        checkFuel(ModBlock.TEAK_TRAPDOOR, 300);
        checkFuel(ModBlock.TEAK_FENCE, 300);
        checkFuel(ModBlock.TEAK_FENCE_GATE, 300);
        checkFuel(ModBlock.TEAK_PRESSURE_PLATE, 300);
        checkFuel(ModBlock.TEAK_BUTTON, 100);
        // 方块设置：硬度与原版对应方块一致，音效由柚木 BlockSetType 提供
        check(ModBlock.TEAK_PLANKS.defaultDestroyTime() == 2.0F && ModBlock.TEAK_PLANKS.defaultBlockState().ignitedByLava(), "Plank settings");
        check(ModBlock.TEAK_TRAPDOOR.defaultDestroyTime() == 3.0F, "Trapdoor hardness");
        check(ModBlock.TEAK_PRESSURE_PLATE.defaultDestroyTime() == 0.5F && ModBlock.TEAK_BUTTON.defaultDestroyTime() == 0.5F,
                "Redstone component hardness");
        check(ModBlock.TEAK_TRAPDOOR.defaultBlockState().getSoundType() == SoundType.WOOD, "Trapdoor sound group");
        check(ModBlock.TEAK_FENCE_GATE.defaultBlockState().getSoundType() == SoundType.WOOD, "Fence gate sound group");
        check(ModBlock.WEATHERED_TEAK_PLANKS.defaultDestroyTime() == ModBlock.TEAK_PLANKS.defaultDestroyTime()
                && ModBlock.WEATHERED_TEAK_PLANKS.defaultMapColor() == ModBlock.TEAK_PLANKS.defaultMapColor()
                && ModBlock.WEATHERED_TEAK_PLANKS.defaultBlockState().ignitedByLava(), "Weathered planks must copy the teak plank settings");
        // 标签：方块侧照原版木材登记（风化柚木木板只做方块，不进 #minecraft:planks）
        check(ModBlock.TEAK_PLANKS.defaultBlockState().is(BlockTags.PLANKS), "Missing planks block tag");
        check(ModBlock.TEAK_FENCE.defaultBlockState().is(BlockTags.WOODEN_FENCES), "Missing wooden_fences block tag");
        check(ModBlock.TEAK_FENCE_GATE.defaultBlockState().is(BlockTags.FENCE_GATES), "Missing fence_gates block tag");
        check(ModBlock.TEAK_PRESSURE_PLATE.defaultBlockState().is(BlockTags.WOODEN_PRESSURE_PLATES),
                "Missing wooden_pressure_plates block tag");
        check(ModBlock.TEAK_BUTTON.defaultBlockState().is(BlockTags.WOODEN_BUTTONS), "Missing wooden_buttons block tag");
        for (Block wooden : List.of(ModBlock.TEAK_PLANKS, ModBlock.WEATHERED_TEAK_PLANKS, ModBlock.TEAK_STAIRS,
                ModBlock.TEAK_SLABS, ModBlock.TEAK_TRAPDOOR, ModBlock.TEAK_FENCE, ModBlock.TEAK_FENCE_GATE,
                ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_BUTTON)) {
            check(wooden.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), "Missing axe mineable " + wooden);
        }
        // 物品侧标签：原版燃料表就是按这些标签取值的
        check(new ItemStack(ModBlock.TEAK_PLANKS).is(ItemTags.PLANKS), "Missing planks item tag");
        check(new ItemStack(ModBlock.TEAK_FENCE).is(ItemTags.WOODEN_FENCES), "Missing wooden_fences item tag");
        check(new ItemStack(ModBlock.TEAK_FENCE_GATE).is(ItemTags.FENCE_GATES), "Missing fence_gates item tag");
        check(new ItemStack(ModBlock.TEAK_PRESSURE_PLATE).is(ItemTags.WOODEN_PRESSURE_PLATES),
                "Missing wooden_pressure_plates item tag");
        check(new ItemStack(ModBlock.TEAK_BUTTON).is(ItemTags.WOODEN_BUTTONS), "Missing wooden_buttons item tag");
        for (String recipe : List.of("teak_fence", "teak_fence_gate", "teak_pressure_plate", "teak_button")) {
            check(server.getRecipeManager().byKey(ModInfo.id(recipe)).isPresent(), "Missing recipe " + recipe);
        }
        REPORT.add("PASS: plank family settings, flammability, fuel times, tags and recipes");

        int minHeight = 100, maxHeight = 0, minWidth = 100, maxWidth = 0;
        for (int seed = 0; seed < 64; seed++) {
            clear(world);
            check(tree.place(world, world.getChunkSource().getGenerator(), RandomSource.create(seed * 0x9E3779B97F4A7C15L), ORIGIN), "Generation seed " + seed);
            Set<BlockPos> logs = new HashSet<>(), leaves = new HashSet<>();
            int top = 0, minX = 99, maxX = -99, minZ = 99, maxZ = -99;
            for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-7, 0, -7), ORIGIN.offset(7, 18, 7))) {
                BlockState state = world.getBlockState(pos);
                if (state.is(ModBlock.TEAK_LOG)) logs.add(pos.immutable());
                if (state.is(ModBlock.TEAK_LEAVES)) {
                    leaves.add(pos.immutable());
                    check(state.getValue(LeavesBlock.DISTANCE) < 7, "Unstable leaf seed " + seed + " at " + pos);
                    check(!state.getValue(LeavesBlock.PERSISTENT), "Generated persistent leaf");
                }
                if (state.is(ModBlock.TEAK_LOG) || state.is(ModBlock.TEAK_LEAVES)) {
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
                    BlockPos neighbor = pos.relative(direction);
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
        world.setBlockAndUpdate(ORIGIN, ModBlock.TEAK_SAPLING.defaultBlockState());
        ModBlock.TEAK_SAPLING.performBonemeal(world, RandomSource.create(10), ORIGIN, world.getBlockState(ORIGIN));
        check(world.getBlockState(ORIGIN).getValue(SaplingBlock.STAGE) == 1, "Sapling stage");
        ModBlock.TEAK_SAPLING.performBonemeal(world, RandomSource.create(11), ORIGIN, world.getBlockState(ORIGIN));
        check(world.getBlockState(ORIGIN).is(ModBlock.TEAK_LOG), "Bone meal growth");
        clear(world);
        world.setBlockAndUpdate(ORIGIN, ModBlock.TEAK_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1));
        world.setBlockAndUpdate(ORIGIN.above(5), Blocks.STONE.defaultBlockState());
        ModBlock.TEAK_SAPLING.performBonemeal(world, RandomSource.create(1), ORIGIN, world.getBlockState(ORIGIN));
        check(world.getBlockState(ORIGIN).is(ModBlock.TEAK_SAPLING), "Blocked growth must keep sapling");
        check(world.getBlockState(ORIGIN.above(5)).is(Blocks.STONE), "Blocked growth destroyed obstacle");
        // Use a separate, already-lit sky column. Repeatedly clearing the canopy above ORIGIN
        // queues lighting work that cannot finish inside this synchronous server-start callback.
        BlockPos naturalSapling = ORIGIN.offset(48, 0, 0);
        // Forge's vanilla sapling patch checks the neighboring chunks before random
        // ticking. Prepare the same loaded fixture on both loaders; keep every assertion.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            world.getChunk((naturalSapling.getX() >> 4) + x, (naturalSapling.getZ() >> 4) + z);
        world.setBlockAndUpdate(naturalSapling.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        world.setBlockAndUpdate(naturalSapling, ModBlock.TEAK_SAPLING.defaultBlockState());
        world.setDayTime(1000);
        check(world.getMaxLocalRawBrightness(naturalSapling.above()) >= 9, "Natural growth fixture must have daylight");
        RandomSource growthRandom = RandomSource.create(37);
        for (int i = 0; i < 512 && world.getBlockState(naturalSapling).is(ModBlock.TEAK_SAPLING); i++) {
            //? if >=1.21 {
/*world.getBlockState(naturalSapling).randomTick(world, naturalSapling, growthRandom);
*///?} else {
ModBlock.TEAK_SAPLING.randomTick(world.getBlockState(naturalSapling), world, naturalSapling, growthRandom);
//?}
        }
        check(world.getBlockState(naturalSapling).is(ModBlock.TEAK_LOG), "Natural sapling growth");
        clear(world, naturalSapling);
        REPORT.add("PASS: bone meal, natural random ticks, obstruction protection");

        BlockState leaf = ModBlock.TEAK_LEAVES.defaultBlockState();
        var sheared = Block.getDrops(leaf, world, ORIGIN, null, null, new ItemStack(Items.SHEARS));
        check(sheared.size() == 1 && sheared.get(0).is(ModBlock.TEAK_LEAVES.asItem()), "Shears loot");
        ItemStack silk = new ItemStack(Items.DIAMOND_HOE);
        //? if >=1.21 {
/*silk.enchant(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH),1);
*///?} else {
silk.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
//?}
        check(Block.getDrops(leaf, world, ORIGIN, null, null, silk).stream()
                .anyMatch(stack -> stack.is(ModBlock.TEAK_LEAVES.asItem())), "Silk touch loot");
        int saplings = 0, sticks = 0;
        for (int i = 0; i < 1024; i++) for (ItemStack drop : Block.getDrops(leaf, world, ORIGIN, null)) {
            check(drop.is(ModBlock.TEAK_SAPLING.asItem()) || drop.is(Items.STICK), "Unexpected leaf drop");
            if (drop.is(ModBlock.TEAK_SAPLING.asItem())) saplings += drop.getCount();
            if (drop.is(Items.STICK)) sticks += drop.getCount();
        }
        check(saplings > 0 && sticks > 0, "Missing renewable drops");
        world.setBlockAndUpdate(ORIGIN, leaf.setValue(LeavesBlock.DISTANCE, 7));
        //? if >=1.21 {
/*world.getBlockState(ORIGIN).randomTick(world, ORIGIN, RandomSource.create(1));
*///?} else {
ModBlock.TEAK_LEAVES.randomTick(world.getBlockState(ORIGIN), world, ORIGIN, RandomSource.create(1));
//?}
        check(world.isEmptyBlock(ORIGIN), "Unsupported leaf decay");
        world.setBlockAndUpdate(ORIGIN, leaf.setValue(LeavesBlock.PERSISTENT, true));
        //? if >=1.21 {
/*world.getBlockState(ORIGIN).randomTick(world, ORIGIN, RandomSource.create(1));
*///?} else {
ModBlock.TEAK_LEAVES.randomTick(world.getBlockState(ORIGIN), world, ORIGIN, RandomSource.create(1));
//?}
        check(world.getBlockState(ORIGIN).is(ModBlock.TEAK_LEAVES), "Player leaf persistence");
        REPORT.add("PASS: shears, silk touch, saplings/sticks, natural decay and persistent leaves");

        // Load actual new terrain, rather than calling the configured feature, to exercise biome placement.
        for (var biomeKey : List.of(Biomes.SPARSE_JUNGLE, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU)) {
            var located = world.findClosestBiome3d(entry -> entry.is(biomeKey), new BlockPos(0, 80, 0), 10000, 32, 64);
            check(located != null, "Could not locate " + biomeKey);
            BlockPos center = located.getFirst();
            boolean found = false;
            for (int radius = 0; radius <= 8 && !found; radius++) {
                for (int cx = -radius; cx <= radius && !found; cx++) for (int cz = -radius; cz <= radius && !found; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != radius) continue;
                    var chunk = world.getChunk((center.getX() >> 4) + cx, (center.getZ() >> 4) + cz);
                    int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                    for (int x = bx; x < bx + 16 && !found; x++) for (int z = bz; z < bz + 16 && !found; z++) {
                        for (int y = 60; y < 160; y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (world.getBlockState(pos).is(ModBlock.TEAK_LOG)
                                    && world.getBlockState(pos.below()).is(BlockTags.DIRT)
                                    && world.getBiome(pos).is(biomeKey)) { found = true; break; }
                        }
                    }
                }
            }
            check(found, "No natural teak near " + biomeKey + " " + center);
            REPORT.add("PASS: natural terrain teak in " + biomeKey.location() + " near " + center);
            System.out.println(REPORT.get(REPORT.size() - 1));
        }
    }
}
