package marrydream.marisdecoration.client;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.worldgen.ModWorldGeneration;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Development-only model check and repeatable in-game screenshot in an isolated world copy. */
public final class TeakClientCheck {
    private static int ticks;
    private static boolean requested;
    private static volatile boolean ready;
    private static volatile String failure;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null || client.getServer() == null) return;
            try {
                if (!requested) {
                    requested = true;
                    verifyModels(client);
                    client.options.hudHidden = true;
                    client.options.getViewDistance().setValue(4);
                    prepareWorld(client);
                }
                if (failure != null) throw new IllegalStateException(failure);
                if (!ready) return;
                ticks++;
                if (ticks == 160) {
                    ScreenshotRecorder.saveScreenshot(client.runDirectory, "teak-tree.png", client.getFramebuffer(), text -> {});
                }
                if (ticks == 180) {
                    client.getServer().execute(() -> {
                        var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                        if (player != null) player.teleport(player.getServerWorld(), 1, 203, 14, 180, 22);
                    });
                }
                if (ticks == 240) {
                    ScreenshotRecorder.saveScreenshot(client.runDirectory, "teak-blocks.png", client.getFramebuffer(), text -> {});
                    Files.writeString(Path.of("teak-client-check.txt"), "PASS: all teak block states and item models baked without missing textures; screenshots captured.\n");
                }
                if (ticks >= 260) client.scheduleStop();
            } catch (Throwable error) {
                try { Files.writeString(Path.of("teak-client-check.txt"), "FAIL: " + error); }
                catch (Exception ignored) {}
                error.printStackTrace();
                client.scheduleStop();
            }
        });
    }

    private static void verifyModels(MinecraftClient client) {
        for (Block block : List.of(ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG,
                ModBlock.STRIPPED_TEAK_WOOD, ModBlock.TEAK_LEAVES, ModBlock.TEAK_SAPLING)) {
            for (BlockState state : block.getStateManager().getStates()) {
                var model = client.getBlockRenderManager().getModel(state);
                if (model == client.getBakedModelManager().getMissingModel()) throw new AssertionError("Missing model " + state);
                var quads = new ArrayList<>(model.getQuads(state, null, Random.create(1)));
                for (Direction face : Direction.values()) quads.addAll(model.getQuads(state, face, Random.create(1)));
                if (quads.isEmpty()) throw new AssertionError("Empty model " + state);
                for (var quad : quads) {
                    if (quad.getSprite().getContents().getId().getPath().equals("missingno"))
                        throw new AssertionError("Missing texture " + state);
                    if (block == ModBlock.TEAK_LEAVES && quad.getColorIndex() != 0)
                        throw new AssertionError("Missing foliage tint " + state);
                }
            }
            var itemModel = client.getItemRenderer().getModel(new net.minecraft.item.ItemStack(block), client.world, client.player, 0);
            if (itemModel == client.getBakedModelManager().getMissingModel()) throw new AssertionError("Missing item model " + block);
        }
    }

    private static void prepareWorld(MinecraftClient client) {
        var server = client.getServer();
        var playerId = client.player.getUuid();
        server.execute(() -> {
            try {
                var world = server.getOverworld();
                BlockPos origin = new BlockPos(0, 200, 0);
                world.setTimeOfDay(1000);
                world.setWeather(0, 100000, false, false);
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
                for (BlockPos pos : BlockPos.iterate(origin.add(-10, -1, -10), origin.add(10, 19, 10))) {
                    world.setBlockState(pos, pos.getY() == 199 ? Blocks.GRASS_BLOCK.getDefaultState()
                            : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
                var tree = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(ModWorldGeneration.TEAK);
                if (!tree.generate(world, world.getChunkManager().getChunkGenerator(), Random.create(20260928), origin))
                    throw new AssertionError("Preview tree failed to generate");
                Block[] blocks = {ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG,
                        ModBlock.STRIPPED_TEAK_WOOD, ModBlock.TEAK_PLANKS, ModBlock.TEAK_LEAVES, ModBlock.TEAK_SAPLING};
                for (int i = 0; i < blocks.length; i++) {
                    BlockState state = blocks[i].getDefaultState();
                    if (state.contains(LeavesBlock.PERSISTENT)) state = state.with(LeavesBlock.PERSISTENT, true);
                    world.setBlockState(new BlockPos(-6 + i * 2, 200, 7), state);
                }
                var player = server.getPlayerManager().getPlayer(playerId);
                player.changeGameMode(GameMode.SPECTATOR);
                player.teleport(world, 17, 211, 24, 145, 12);
                ready = true;
            } catch (Throwable error) { failure = error.toString(); }
        });
    }
}
