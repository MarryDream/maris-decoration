package marrydream.marisdecoration;
import marrydream.marisdecoration.init.*;
import marrydream.marisdecoration.platform.Platform;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import marrydream.marisdecoration.placement.harness.CopycatPlacerCommand;
import marrydream.marisdecoration.placement.network.ServerPlacerConfigHandler;
import marrydream.marisdecoration.worldgen.ModWorldGeneration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Mod(ModInfo.MOD_ID)
public final class MarisDecoration {
 public static final Logger LOGGER=LoggerFactory.getLogger(ModInfo.NAMESPACE);
 public MarisDecoration() {
  var bus=FMLJavaModLoadingContext.get().getModEventBus();
  Platform.attach(bus);
  bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(()-> {
   marrydream.marisdecoration.platform.ForgeWoodHooks.init();
   PlacementAdapters.init();
   LOGGER.info("Initialized Maris' Decoration (Forge)");
  }));
  ServerPlacerConfigHandler.register(); CopycatPlacerCommand.register();
  if(Boolean.getBoolean("maris.teak.selftest") && Platform.isDevelopmentEnvironment()) marrydream.marisdecoration.worldgen.TeakSelfTest.register();
 }
}
