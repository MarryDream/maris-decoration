package marrydream.marisdecoration.client;

import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.worldgen.ModWorldGeneration;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Development-only model check and repeatable in-game screenshot in an isolated world copy. */
public final class TeakClientCheck {
    private static int ticks;
    private static boolean requested;
    private static volatile boolean ready;
    private static volatile String failure;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            try {
                if (!requested) {
                    requested = true;
                    verifyModels(client);
                    client.options.hideGui = true;
                    client.options.renderDistance().set(4);
                    prepareWorld(client);
                }
                if (failure != null) throw new IllegalStateException(failure);
                if (!ready) return;
                ticks++;
                if (ticks == 160) {
                    Screenshot.grab(client.gameDirectory, "teak-tree.png", client.getMainRenderTarget(), text -> {});
                }
                if (ticks == 180) {
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                        if (player != null) player.teleportTo(player.serverLevel(), 1, 203, 14, 180, 22);
                    });
                }
                if (ticks == 240) {
                    Screenshot.grab(client.gameDirectory, "teak-blocks.png", client.getMainRenderTarget(), text -> {});
                    Files.writeString(Path.of("teak-client-check.txt"), "PASS: all teak block states and item models baked without missing textures; screenshots captured.\n");
                }
                if (ticks >= 260) client.stop();
            } catch (Throwable error) {
                try { Files.writeString(Path.of("teak-client-check.txt"), "FAIL: " + error); }
                catch (Exception ignored) {}
                error.printStackTrace();
                client.stop();
            }
        });
    }

    private static void verifyModels(Minecraft client) {
        for (Block block : List.of(ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG,
                ModBlock.STRIPPED_TEAK_WOOD, ModBlock.TEAK_LEAVES, ModBlock.TEAK_SAPLING,
                // 木板家族：方块状态与物品模型都必须烤出来（栅栏门 / 压力板的物品模型来自数据生成器）
                ModBlock.TEAK_PLANKS, ModBlock.WEATHERED_TEAK_PLANKS, ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS,
                ModBlock.TEAK_TRAPDOOR, ModBlock.TEAK_FENCE, ModBlock.TEAK_FENCE_GATE,
                ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_BUTTON)) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                var model = client.getBlockRenderer().getBlockModel(state);
                if (model == client.getModelManager().getMissingModel()) throw new AssertionError("Missing model " + state);
                var quads = new ArrayList<>(model.getQuads(state, null, RandomSource.create(1)));
                for (Direction face : Direction.values()) quads.addAll(model.getQuads(state, face, RandomSource.create(1)));
                if (quads.isEmpty()) throw new AssertionError("Empty model " + state);
                for (var quad : quads) {
                    if (quad.getSprite().contents().name().getPath().equals("missingno"))
                        throw new AssertionError("Missing texture " + state);
                    if (block == ModBlock.TEAK_LEAVES && quad.getTintIndex() != 0)
                        throw new AssertionError("Missing foliage tint " + state);
                }
            }
            var itemModel = client.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(block), client.level, client.player, 0);
            if (itemModel == client.getModelManager().getMissingModel()) throw new AssertionError("Missing item model " + block);
        }
    }

    private static void prepareWorld(Minecraft client) {
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        server.execute(() -> {
            try {
                var world = server.overworld();
                BlockPos origin = new BlockPos(0, 200, 0);
                world.setDayTime(1000);
                world.setWeatherParameters(0, 100000, false, false);
                world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-10, -1, -10), origin.offset(10, 19, 10))) {
                    world.setBlock(pos, pos.getY() == 199 ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                var tree = world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(ModWorldGeneration.TEAK);
                if (!tree.place(world, world.getChunkSource().getGenerator(), RandomSource.create(20260928), origin))
                    throw new AssertionError("Preview tree failed to generate");
                Block[] blocks = {ModBlock.TEAK_LOG, ModBlock.TEAK_WOOD, ModBlock.STRIPPED_TEAK_LOG,
                        ModBlock.STRIPPED_TEAK_WOOD, ModBlock.TEAK_PLANKS, ModBlock.TEAK_LEAVES, ModBlock.TEAK_SAPLING};
                for (int i = 0; i < blocks.length; i++) {
                    BlockState state = blocks[i].defaultBlockState();
                    if (state.hasProperty(LeavesBlock.PERSISTENT)) state = state.setValue(LeavesBlock.PERSISTENT, true);
                    world.setBlockAndUpdate(new BlockPos(-6 + i * 2, 200, 7), state);
                }
                // 木板家族摞在上面一排，位置与上排对齐，保证截图里每个方块都完整可见
                Block[] wooden = {ModBlock.WEATHERED_TEAK_PLANKS, ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS,
                        ModBlock.TEAK_FENCE, ModBlock.TEAK_FENCE_GATE, ModBlock.TEAK_PRESSURE_PLATE, ModBlock.TEAK_BUTTON};
                for (int i = 0; i < wooden.length; i++) {
                    world.setBlockAndUpdate(new BlockPos(-6 + i * 2, 201, 7), wooden[i].defaultBlockState());
                }
                var player = server.getPlayerList().getPlayer(playerId);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(world, 17, 211, 24, 145, 12);
                ready = true;
            } catch (Throwable error) { failure = error.toString(); }
        });
    }
}
