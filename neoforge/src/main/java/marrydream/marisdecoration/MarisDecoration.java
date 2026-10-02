package marrydream.marisdecoration;
import marrydream.marisdecoration.init.*;
import marrydream.marisdecoration.platform.Platform;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import marrydream.marisdecoration.placement.harness.CopycatPlacerCommand;
import marrydream.marisdecoration.placement.network.ServerPlacerConfigHandler;
import marrydream.marisdecoration.worldgen.ModWorldGeneration;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Mod(ModInfo.MOD_ID)
public final class MarisDecoration {
 public static final Logger LOGGER=LoggerFactory.getLogger(ModInfo.NAMESPACE);
 public MarisDecoration(net.neoforged.bus.api.IEventBus bus) {
  Platform.attach(bus);
  bus.addListener(marrydream.marisdecoration.platform.PlacerNetwork::registerPayloads);
  bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(()-> {
   marrydream.marisdecoration.platform.NeoForgeWoodHooks.init();
   PlacementAdapters.init();
   LOGGER.info("Initialized Maris' Decoration (NeoForge)");
  }));
  ServerPlacerConfigHandler.register(); CopycatPlacerCommand.register();
  if(Boolean.getBoolean("maris.teak.selftest") && Platform.isDevelopmentEnvironment()) marrydream.marisdecoration.worldgen.TeakSelfTest.register();
 }
}
