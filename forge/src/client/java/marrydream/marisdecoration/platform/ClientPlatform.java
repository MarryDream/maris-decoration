package marrydream.marisdecoration.platform;
public final class ClientPlatform {
 public static void onEndTick(java.util.function.Consumer<net.minecraft.client.Minecraft> receiver) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent event)-> { if(event.phase==net.minecraftforge.event.TickEvent.Phase.END) receiver.accept(net.minecraft.client.Minecraft.getInstance()); }); }
}
