package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.network.PlacerConfigPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端侧的接线：把界面挂上桥、把配置发出去。
 *
 * <h2>为什么需要「注册接收器」这一步</h2>
 * 本包只往服务端发，不需要处理收到的包。但 Fabric 的
 * {@code ClientPlayNetworking.send(Identifier, PacketByteBuf)} 会先检查这个频道是否在
 * 「可以发送」的集合里，而那个集合来自「已注册的接收器」。所以这里注册一个<b>什么都不做</b>的
 * 接收器，纯粹是为了让发送合法。服务端那边才是真正的接收方。
 *
 * <p>接收器体写成空的是有意的：如果以后真的要加 S2C（比如服务端拒绝配置时回一个提示），
 * 那时候再往这里填。现在填任何东西都只是「猜需求」。
 */
@Environment(EnvType.CLIENT)
public final class PlacerClientHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("maris-decoration/copycat-placer");

    private PlacerClientHooks() {
    }

    /** 由 {@code MarisDecorationClient#onInitializeClient} 调用。 */
    public static void init() {
        // ① 装上桥的实现：打开界面 / 发送配置
        PlacerClientBridge.install(PlacerClientHooks::openScreen, PlacerClientHooks::sendConfig);

        // ② 注册一个空接收器，让这个频道进入「可发送」集合
        ClientPlayNetworking.registerGlobalReceiver(PlacerConfigPacket.ID,
                (client, handler, buffer, sender) -> {
                    // 目前没有 S2C 消息；消费掉缓冲避免 Fabric 报「只读了一半」的警告
                    buffer.skipBytes(buffer.readableBytes());
                });
    }

    /**
     * 打开配置界面。
     *
     * <p>从这把工具的 ItemStack 造一份编辑状态。每次打开都重新读 NBT，所以服务端刚刚同步过来
     * 的修正会立刻体现出来（不会拿着上一次打开时缓存的旧配置）。
     */
    private static void openScreen(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlacerEditState state = new PlacerEditState(stack);
        client.setScreen(new PlacerScreen(state));
    }

    /**
     * 把配置发给服务端。
     *
     * <p>{@code null} 表示清空。发送本身不等待服务端确认——服务端校验后会改物品 NBT，
     * 那个改动会随背包同步回到这里。
     *
     * <p>用 {@code canSend} 先探一下而不是直接 try/catch：单人世界里服务端线程就在同一个进程，
     * 频道一定注册过；但在「连接还没完成 / 已经在断开」的瞬间发送会抛异常。
     * 那种情况下安静地跳过即可，配置已经在本地 NBT 里了。
     */
    private static void sendConfig(@Nullable PlacementConfig config) {
        if (!ClientPlayNetworking.canSend(PlacerConfigPacket.ID)) {
            LOGGER.debug("伪装放置器：网络频道当前不可发送，跳过同步（配置已写入本地物品 NBT）");
            return;
        }
        ClientPlayNetworking.send(PlacerConfigPacket.ID, PlacerConfigPacket.encode(config));
    }
}
