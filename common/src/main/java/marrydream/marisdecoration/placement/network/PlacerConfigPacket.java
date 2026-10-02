package marrydream.marisdecoration.placement.network;

import io.netty.buffer.Unpooled;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 配置同步的负载编码。
 *
 * <h2>为什么不用 Fabric 的 PacketType / CustomPayload</h2>
 * 本项目用的 Fabric API 是 0.92.12，那个版本既没有 {@code PayloadTypeRegistry}，
 * 1.20.1 的原版也没有 {@code CustomPayload}。所以走 Fabric 最经典的那条路：
 * <b>一个频道 {@link #ID} + 一个 {@link FriendlyByteBuf}</b>，两端各自用
 * {@code ClientPlayNetworking.send} / {@code ServerPlayNetworking.registerGlobalReceiver} 处理。
 * 好处是不需要任何类型注册，坏处是负载格式要自己保证两端一致——所以编解码只写在这里一处。
 *
 * <h2>负载格式</h2>
 * <pre>
 * writeNbt( config.toNbt() )        // 有配置
 * writeNbt( new NbtCompound() )     // 无配置（玩家清空了选择）；空标签是哨兵值
 * </pre>
 * 只有一个 NBT：预设本来就是自包含的（见 {@code PlacementConfigNbt}），协议层不必认识它的内部结构，
 * 原样搬运、原样校验即可。空 NBT 与「配过但什么都没填」在原版序列化下天然可区分——
 * 只要选了方块，{@code block} 子标签就必然存在。
 *
 * <h2>方向</h2>
 * 只有 <b>客户端 → 服务端</b>。服务端的回应是「把合法配置写进物品 NBT」，而物品 NBT 本来就会同步
 * 回客户端，所以不需要回执包。
 */
public final class PlacerConfigPacket {

    /** 频道名。改它等于改协议版本。 */
    public static final ResourceLocation ID = new ResourceLocation("maris-decoration", "placer_config");

    private PlacerConfigPacket() {
    }

    /** 编码一份配置；{@code config} 为空表示「清空配置」。 */
    public static FriendlyByteBuf encode(@Nullable PlacementConfig config) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        // 读端用 readNbt 反序列化，这里写空标签即哨兵
        buffer.writeNbt(config == null ? new CompoundTag() : config.toNbt());
        return buffer;
    }

    /** 从收到的负载里解码配置；空标签返回 {@code null}，表示「清空配置」。 */
    public static @Nullable PlacementConfig decode(FriendlyByteBuf buffer) {
        CompoundTag nbt = buffer.readNbt();
        if (nbt == null || nbt.isEmpty()) {
            return null;
        }
        return PlacementConfig.fromNbt(nbt);
    }
}
