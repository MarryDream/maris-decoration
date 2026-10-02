package marrydream.marisdecoration.platform;
import marrydream.marisdecoration.init.ModInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import java.util.LinkedHashMap;
import java.util.Map;
public final class Platform {
 private static net.minecraftforge.registries.RegisterEvent currentEvent;
 private static final java.util.List<java.util.function.Consumer<net.minecraftforge.registries.RegisterEvent>> pending = new java.util.ArrayList<>();
 private static final java.util.List<Runnable> blockItems = new java.util.ArrayList<>();
 public static <V,T extends V> T register(Registry<V> registry, ResourceLocation id, T value) {
  if (!id.getNamespace().equals(ModInfo.NAMESPACE)) throw new IllegalArgumentException(id.toString());
  java.util.function.Consumer<net.minecraftforge.registries.RegisterEvent> registration=event -> event.register(registry.key(), helper -> helper.register(id,value));
  if(currentEvent!=null && currentEvent.getRegistryKey().equals(registry.key())) registration.accept(currentEvent);
  else pending.add(registration);
  return value;
 }
 public static void registerBlockItem(ResourceLocation id, java.util.function.Supplier<net.minecraft.world.item.BlockItem> factory, java.util.List<net.minecraft.world.item.Item> catalog) {
  blockItems.add(()->catalog.add(register(net.minecraft.core.registries.BuiltInRegistries.ITEM,id,factory.get())));
 }
 public static void attach(IEventBus bus) {
  bus.addListener((net.minecraftforge.registries.RegisterEvent event)-> {
   currentEvent=event;
   try {
    if(event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) {
     marrydream.marisdecoration.init.ModBlock.init();
     marrydream.marisdecoration.worldgen.ModWorldGeneration.init();
     marrydream.marisdecoration.init.ModBlockEntity.init();
     marrydream.marisdecoration.init.ModItemGroup.init();
    }
    if(event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) {
     blockItems.forEach(Runnable::run);
     marrydream.marisdecoration.init.ModItem.init();
    }
    pending.forEach(r -> r.accept(event));
   } finally { currentEvent=null; }
  });
 }
 public static boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
 public static boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }
 // Vanilla wood tables need registered items; Forge installs them during common setup.
 public static void initWood() { }
 // Forge injects shared placed features with loader-local biome modifier metadata.
 public static void initBiomes() { }
 public static void registerCommands(java.util.function.Consumer<com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>> receiver) {
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) -> receiver.accept(event.getDispatcher()));
 }
 public static void registerReceiver(ResourceLocation id, java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer,net.minecraft.network.FriendlyByteBuf> receiver) {
  PlacerNetwork.register(id,receiver);
 }
 public static void onServerStarted(java.util.function.Consumer<net.minecraft.server.MinecraftServer> receiver) {
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent event)->receiver.accept(event.getServer()));
 }
 public static Integer fuel(net.minecraft.world.level.ItemLike item) { return net.minecraftforge.common.ForgeHooks.getBurnTime(new net.minecraft.world.item.ItemStack(item),net.minecraft.world.item.crafting.RecipeType.SMELTING); }
 public static net.minecraft.world.level.block.Block strippedBlock(net.minecraft.world.level.block.Block block) { return net.minecraft.world.item.AxeItem.STRIPPABLES.get(block); }
 public static int burnChance(net.minecraft.world.level.block.Block block) { return ((net.minecraft.world.level.block.FireBlock)net.minecraft.world.level.block.Blocks.FIRE).getIgniteOdds(block.defaultBlockState()); }
 public static int spreadChance(net.minecraft.world.level.block.Block block) { return ((net.minecraft.world.level.block.FireBlock)net.minecraft.world.level.block.Blocks.FIRE).getBurnOdds(block.defaultBlockState()); }
 public static net.minecraft.server.level.ServerPlayer createTestPlayer(net.minecraft.server.level.ServerLevel world, com.mojang.authlib.GameProfile profile) { return new net.minecraft.server.level.ServerPlayer(world.getServer(), world, profile); }
}
