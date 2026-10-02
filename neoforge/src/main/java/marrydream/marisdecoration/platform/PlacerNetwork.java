package marrydream.marisdecoration.platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.BiConsumer;
public final class PlacerNetwork {
 private static BiConsumer<ServerPlayer,FriendlyByteBuf> handler;
 private static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("maris-decoration","placer_config"));
 private record Payload(byte[] bytes) implements CustomPacketPayload {
  public Type<Payload> type() { return TYPE; }
 }
 private static final StreamCodec<RegistryFriendlyByteBuf,Payload> CODEC = StreamCodec.of((buffer,payload)->buffer.writeByteArray(payload.bytes()),buffer->new Payload(buffer.readByteArray(32767)));
 public static void register(ResourceLocation id, BiConsumer<ServerPlayer,FriendlyByteBuf> receiver) { if (!TYPE.id().equals(id)) throw new IllegalArgumentException(id.toString()); handler=receiver; }
 public static void registerPayloads(RegisterPayloadHandlersEvent event) {
  event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()-> {
   if(context.player() instanceof ServerPlayer player) {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes()));
    try { handler.accept(player,buffer); } finally { buffer.release(); }
   }
  }));
 }
 public static void send(FriendlyByteBuf buffer) { byte[] bytes=new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(),bytes); PacketDistributor.sendToServer(new Payload(bytes)); }
}
