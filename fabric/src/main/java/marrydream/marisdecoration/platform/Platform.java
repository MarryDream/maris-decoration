package marrydream.marisdecoration.platform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.registry.*;
import net.fabricmc.fabric.api.biome.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import java.util.List;
import static marrydream.marisdecoration.init.ModBlock.*;
import static marrydream.marisdecoration.worldgen.ModWorldGeneration.*;
public final class Platform {
 public static <V,T extends V> T register(Registry<V> registry, ResourceLocation id, T value) { return Registry.register(registry,id,value); }
 public static boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
 public static boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }
 public static void initWood() {
        StrippableBlockRegistry.register(TEAK_LOG, STRIPPED_TEAK_LOG);
        StrippableBlockRegistry.register(TEAK_WOOD, STRIPPED_TEAK_WOOD);
        var flammable = FlammableBlockRegistry.getDefaultInstance();
        // 原木类：燃烧几率 5 / 蔓延几率 5，与原版原木一致
        for (Block wood : List.of(TEAK_LOG, TEAK_WOOD, STRIPPED_TEAK_LOG, STRIPPED_TEAK_WOOD)) {
            FuelRegistry.INSTANCE.add(wood, 300);
            flammable.add(wood, 5, 5);
        }
        // 木板与衍生品：燃烧几率 5 / 蔓延几率 20，与原版木板、楼梯、台阶、栅栏、栅栏门一致。
        // 原版没有把活板门、压力板、按钮放进火焰蔓延表（三者只是燃料），这里同样不登记。
        for (Block wooden : List.of(TEAK_PLANKS, WEATHERED_TEAK_PLANKS, TEAK_STAIRS, TEAK_SLABS,
                TEAK_FENCE, TEAK_FENCE_GATE)) {
            flammable.add(wooden, 5, 20);
        }
        flammable.add(TEAK_LEAVES, 30, 60);
        FuelRegistry.INSTANCE.add(TEAK_SAPLING, 100);
        CompostingChanceRegistry.INSTANCE.add(TEAK_LEAVES, 0.3F);
        CompostingChanceRegistry.INSTANCE.add(TEAK_SAPLING, 0.3F);
        // 燃烧时间（tick）与原版木材一致：木板 / 楼梯 / 活板门 / 栅栏 / 栅栏门 / 压力板 300、台阶 150、按钮 100。
        // 这些值同样能由物品标签（#minecraft:planks 等）带出来；这里显式登记是把整族配置集中在一处，
        // 并覆盖没有对应原版标签的风化柚木木板。
        FuelRegistry.INSTANCE.add( TEAK_PLANKS, 300 );
        FuelRegistry.INSTANCE.add( WEATHERED_TEAK_PLANKS, 300 );
        FuelRegistry.INSTANCE.add( TEAK_STAIRS, 300 );
        FuelRegistry.INSTANCE.add( TEAK_SLABS, 150 );
        FuelRegistry.INSTANCE.add( TEAK_TRAPDOOR, 300 );
        FuelRegistry.INSTANCE.add( TEAK_FENCE, 300 );
        FuelRegistry.INSTANCE.add( TEAK_FENCE_GATE, 300 );
        FuelRegistry.INSTANCE.add( TEAK_PRESSURE_PLATE, 300 );
        FuelRegistry.INSTANCE.add( TEAK_BUTTON, 100 );
 }
 public static void initBiomes() {
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.SPARSE_JUNGLE),
                GenerationStep.Decoration.VEGETAL_DECORATION, TEAK_SPARSE_JUNGLE);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU),
                GenerationStep.Decoration.VEGETAL_DECORATION, TEAK_SAVANNA);
 }
 public static void registerCommands(java.util.function.Consumer<com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>> receiver) {
  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> receiver.accept(dispatcher));
 }
 public static void registerReceiver(ResourceLocation id, java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer,net.minecraft.network.FriendlyByteBuf> receiver) {
  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(id,(server,player,handler,buffer,sender)->receiver.accept(player,buffer));
 }
 public static void onServerStarted(java.util.function.Consumer<net.minecraft.server.MinecraftServer> receiver) {
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(receiver::accept);
 }
 public static Integer fuel(net.minecraft.world.level.ItemLike item) { return net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.get(item); }
 public static net.minecraft.world.level.block.Block strippedBlock(net.minecraft.world.level.block.Block block) { return net.minecraft.world.item.AxeItem.STRIPPABLES.get(block); }
 public static int burnChance(net.minecraft.world.level.block.Block block) { return net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().get(block).getBurnChance(); }
 public static int spreadChance(net.minecraft.world.level.block.Block block) { return net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().get(block).getSpreadChance(); }
 public static void registerBlockItem(ResourceLocation id, java.util.function.Supplier<net.minecraft.world.item.BlockItem> factory, java.util.List<net.minecraft.world.item.Item> catalog) {
  catalog.add(register(net.minecraft.core.registries.BuiltInRegistries.ITEM,id,factory.get()));
 }
 public static net.minecraft.server.level.ServerPlayer createTestPlayer(net.minecraft.server.level.ServerLevel world, com.mojang.authlib.GameProfile profile) { return new net.minecraft.server.level.ServerPlayer(world.getServer(), world, profile); }
}
