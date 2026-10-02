package marrydream.marisdecoration.platform;
public final class ClientPlatform {
 public static void onEndTick(java.util.function.Consumer<net.minecraft.client.Minecraft> receiver) { net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(receiver::accept); }
}
