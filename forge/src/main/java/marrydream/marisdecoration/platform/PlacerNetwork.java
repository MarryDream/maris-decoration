package marrydream.marisdecoration.platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.BiConsumer;
public final class PlacerNetwork {
 private static SimpleChannel channel;
 private record Payload(byte[] bytes) {}
 public static void register(ResourceLocation id, BiConsumer<ServerPlayer,FriendlyByteBuf> handler) {
  channel=NetworkRegistry.ChannelBuilder.named(id).networkProtocolVersion(()->"1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
  channel.messageBuilder(Payload.class,0,NetworkDirection.PLAY_TO_SERVER)
   .encoder((p,b)->b.writeByteArray(p.bytes()))
   .decoder(b->new Payload(b.readByteArray(b.readableBytes())))
   .consumerMainThread((p,ctx)-> {
    ServerPlayer player=ctx.get().getSender();
    if(player!=null) { FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.wrappedBuffer(p.bytes())); try { handler.accept(player,b); } finally { b.release(); } }
    ctx.get().setPacketHandled(true);
   }).add();
 }
 public static void send(FriendlyByteBuf buffer) { byte[] bytes=new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(),bytes); channel.sendToServer(new Payload(bytes)); }
}
