package marrydream.marisdecoration.placement.network;

import java.util.Map.Entry;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.item.CopycatPlacerItem;
import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.PlacementConfigs;
import marrydream.marisdecoration.platform.Platform;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端侧的配置接收与校验。
 *
 * <h2>为什么服务端必须重新校验</h2>
 * 这个包的内容最终会决定「放什么方块、贴什么材质」，等于让客户端提交一份会影响世界的指令。
 * 所以服务端<b>不信任</b>包里的任何东西，只把它当成「玩家的意图」，逐层验证后才落盘：
 * <ol>
 *   <li><b>手里确实是伪装放置器吗</b> —— 否则直接丢弃。没有这一条，任何改过包的客户端都能
 *       把任意预设塞进任意物品；</li>
 *   <li><b>预设结构自洽吗</b>（{@link PlacementConfigs#isWellFormed}）—— 方块存在、被某个 adapter
 *       认领、方块状态里的属性确实属于那个方块。这一层挡的是「拼错的属性名 / 别的方块的方块状态」；</li>
 *   <li><b>材质槽的方块存在吗</b> —— NBT 反序列化时已经验过（放不存在的方块会被原版拒绝），
 *       所以这里只需要确认每个槽都不是空气。</li>
 * </ol>
 * 通过之后才写进主手物品的 NBT（见下面关于「为什么是主手」的注释）。
 *
 * <h2>为什么写主手</h2>
 * 服务端收到包时并不知道客户端当时用的是哪只手，而 {@link CopycatPlacerItem} 是
 * {@code maxCount(1)} 的工具、正常只会拿在手上。这里按「主手优先」处理：
 * 主手是放置器就写主手，否则副手是放置器就写副手，都不是就丢弃。
 */
public final class ServerPlacerConfigHandler {

    private ServerPlacerConfigHandler() {
    }

    /** 注册全局接收器。由 {@code MarisDecoration#onInitialize} 调用（两端都会执行）。 */
    public static void register() {
        Platform.registerReceiver(PlacerConfigPacket.ID,
                (player, buffer) -> {
                    PlacementConfig received;
                    try {
                        received = PlacerConfigPacket.decode(buffer);
                    } catch (RuntimeException exception) {
                        // 负载坏了（改过的客户端 / 版本不一致）：丢弃，不让异常顺着网络线程往上冒
                        MarisDecoration.LOGGER.warn("收到无法解析的伪装放置器配置包，已丢弃", exception);
                        return;
                    }

                    // ① 手里真的是这个工具吗
                    InteractionHand hand = findHand(player);
                    if (hand == null) {
                        return;
                    }
                    ItemStack stack = player.getItemInHand(hand);

                    // ② 空配置 = 玩家清空了选择，直接擦掉旧配置
                    if (received == null) {
                        PlacementConfigs.clear(stack);
                        return;
                    }

                    // ③ 结构自洽性
                    if (!PlacementConfigs.isWellFormed(received)) {
                        MarisDecoration.LOGGER.warn("丢弃不合法的伪装放置器配置：{}", received);
                        return;
                    }

                    // ④ 材质槽不能是空气（空气槽没有意义，写进去只会让 tooltip 多一行废话）
                    PlacementConfig sanitized = sanitize(received);

                    PlacementConfigs.write(stack, sanitized);
                    // 物品 NBT 变了：让服务端把这个槽重新同步给客户端，
                    // 否则客户端会一直拿着自己那份乐观写入的副本，直到下次背包同步才纠正
                    player.containerMenu.sendAllDataToRemote();
                });
    }

    /** 找出玩家哪只手上拿着伪装放置器；没有则返回 {@code null}。 */
    private static @Nullable InteractionHand findHand(net.minecraft.server.level.ServerPlayer player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof CopycatPlacerItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof CopycatPlacerItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * 清掉没有意义的材质槽（空气）。
     *
     * <p>不做「这个槽在当前结构下存不存在」的过滤：那样会让配置随结构变化而丢数据
     * （玩家先配好材质、再回头调结构，材质就没了）。结构不存在的槽在放置时自然会被跳过。
     */
    private static PlacementConfig sanitize(PlacementConfig config) {
        PlacementConfig result = config;
        for (var entry : config.slots().entrySet()) {
            if (entry.getValue().isAir()) {
                result = result.withoutSlot(entry.getKey());
            }
        }
        return result;
    }
}
