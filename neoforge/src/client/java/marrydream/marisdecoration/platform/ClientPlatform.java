package marrydream.marisdecoration.platform;
public final class ClientPlatform {
 public static void onEndTick(java.util.function.Consumer<net.minecraft.client.Minecraft> receiver) {
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> receiver.accept(net.minecraft.client.Minecraft.getInstance()));
 }
}
