package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 「主代码要调客户端界面」的那一道桥。
 *
 * <h2>为什么要它</h2>
 * 本项目用 {@code splitEnvironmentSourceSets()} 把主代码与客户端代码分成两个源集，
 * <b>主源集在编译期看不见客户端源集</b>，而 {@code CopycatPlacerItem#use} 恰恰需要
 * 「在客户端打开一个配置界面」。
 *
 * <p>直接在物品里 new PlacerScreen(...) 编译不过；把物品整个搬到客户端源集又会让它
 * 在专用服务端上消失。所以这里放一个只持有 {@link Consumer} 的静态钩子：
 * <ul>
 *   <li>本类<b>没有任何 Screen / MinecraftClient 类型</b>，两端都能安全加载；</li>
 *   <li>客户端源集在 onInitializeClient 里注册真正的实现（见 PlacerClientHooks）；</li>
 *   <li>专用服务端永远不会注册它，于是 {@link #openConfigScreen} 是一个安全的空操作。</li>
 * </ul>
 *
 * <p><b>刻意不加 {@code @Environment(EnvType.CLIENT)}</b>：那会让专用服务端在加载这个类时直接抛错，
 * 而物品的 use 在两端都会被调用、必然碰到它。这里要的正是「两端都能加载，只是服务端上钩子是空的」。
 *
 * <p>注意这个类虽然叫 placement.client，但它属于<b>主源集</b>：
 * 包名表达的是「用途与客户端相关」，不是源集归属。
 */
public final class PlacerClientBridge {

    /** 打开配置界面：接收那把工具的 ItemStack（界面从它的 NBT 读当前预设）。 */
    private static volatile Consumer<ItemStack> opener;

    /** 把编辑好的预设发给服务端；null 表示清空配置。 */
    private static volatile Consumer<PlacementConfig> sender;

    private PlacerClientBridge() {
    }

    /** 客户端初始化时调用，装上真正的实现。 */
    public static void install(Consumer<ItemStack> screenOpener,
                               Consumer<PlacementConfig> packetSender) {
        opener = screenOpener;
        sender = packetSender;
    }

    /**
     * 打开配置界面。
     *
     * <p>没装实现时（专用服务端、或客户端还没初始化完）安静地什么都不做——比抛异常安全，
     * 因为物品的 use 在两端都会被调用。
     */
    public static void openConfigScreen(ItemStack stack) {
        Consumer<ItemStack> hook = opener;
        if (hook != null) {
            hook.accept(stack);
        }
    }

    /** 把配置发给服务端。null 表示清空。没装实现时不做任何事。 */
    public static void sendConfig(@Nullable PlacementConfig config) {
        Consumer<PlacementConfig> hook = sender;
        if (hook != null) {
            hook.accept(config);
        }
    }
}
