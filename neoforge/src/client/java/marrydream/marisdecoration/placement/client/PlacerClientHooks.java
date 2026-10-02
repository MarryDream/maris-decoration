package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.network.PlacerConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Forge client hooks: shared editor screens and the registered C2S NeoForge payload. */
public final class PlacerClientHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("maris-decoration/copycat-placer");

    private PlacerClientHooks() {
    }

    /** Installed by Forge client setup. */
    public static void init() {
        PlacerClientBridge.install(PlacerClientHooks::openScreen, PlacerClientHooks::sendConfig);
    }

    /**
     * 打开配置界面。
     *
     * <p>从这把工具的 ItemStack 造一份编辑状态。每次打开都重新读 NBT，所以服务端刚刚同步过来
     * 的修正会立刻体现出来（不会拿着上一次打开时缓存的旧配置）。
     */
    private static void openScreen(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        PlacerEditState state = new PlacerEditState(stack);
        client.setScreen(new PlacerScreen(state));
    }

    /**
     * 把配置发给服务端。
     *
     * <p>{@code null} 表示清空。发送本身不等待服务端确认——服务端校验后会改物品 NBT，
     * 那个改动会随背包同步回到这里。
     *
     * <p>连接建立后使用已注册的 Forge NeoForge payload；物品 NBT 的回传由原版背包同步负责。
     */
    private static void sendConfig(@Nullable PlacementConfig config) {
        if (Minecraft.getInstance().getConnection() == null) {
            LOGGER.debug("伪装放置器：网络频道当前不可发送，跳过同步（配置已写入本地物品 NBT）");
            return;
        }
        var buffer = PlacerConfigPacket.encode(config);
        try { marrydream.marisdecoration.platform.PlacerNetwork.send(buffer); } finally { buffer.release(); }
    }
}
