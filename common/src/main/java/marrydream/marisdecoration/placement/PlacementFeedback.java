package marrydream.marisdecoration.placement;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 放置结果的玩家可见反馈（动作栏文本）。
 *
 * <p>分成两步是为了让「怎么算失败」和「怎么说给玩家听」各归各位：
 * {@link #messageFor} 只做分类，具体的措辞放在语言文件里（翻译键在下面列着），
 * 所以以后改文案不需要动代码，加语言也不需要动代码。
 *
 * <h2>哪些情况必须说话、哪些必须闭嘴</h2>
 * 按需求逐条定：
 * <ul>
 *   <li>没选方块 → <b>必须说</b>「请先选择伪装方块」；</li>
 *   <li>生存模式缺少结构伪装板 → <b>必须说</b>「伪装板不足」；</li>
 *   <li>材质不足 → <b>必须不说</b>。放置照常发生，缺的槽留空即可；
 *       这里连「已放置」都不播报，避免玩家以为出了什么问题；</li>
 *   <li>放置成功且什么都没缺 → 也不播报，保持手感干净；</li>
 *   <li>其它失败（目标被占、位置放不下）→ 说一句，否则玩家会以为工具坏了。</li>
 * </ul>
 */
public final class PlacementFeedback {

    /** 没选方块。 */
    public static final String NO_BLOCK_KEY = "item.maris-decoration.copycat_placer.no_block";
    /** 生存模式缺少结构伪装板。 */
    public static final String NO_STRUCTURE_KEY = "item.maris-decoration.copycat_placer.no_structure";
    /** 目标位置放不下 / 被占。 */
    public static final String BLOCKED_KEY = "item.maris-decoration.copycat_placer.blocked";
    /** 配置形不成有效结构（零部件的幽灵方块）。 */
    public static final String INVALID_STRUCTURE_KEY =
            "item.maris-decoration.copycat_placer.invalid_structure";

    private PlacementFeedback() {
    }

    /**
     * 这次放置要不要对玩家说话；要说什么。
     *
     * @param result 放置结果
     * @return 要显示的文本；{@code null} 表示什么都不说
     */
    public static @Nullable Component messageFor(PlacementResult result) {
        if (result.success()) {
            // 成功不播报：材质不足也是成功（缺的槽留空），所以这里一律安静
            return null;
        }
        return switch (result.failure()) {
            case NO_BLOCK, NOT_COPYCAT -> Component.translatable(NO_BLOCK_KEY);
            case NO_STRUCTURE_ITEM, NO_STRUCTURE_BLOCK_ITEM -> Component.translatable(NO_STRUCTURE_KEY);
            case BLOCKED -> Component.translatable(BLOCKED_KEY);
            // 结构形不成：告诉玩家去检查属性，而不是让他以为工具坏了
            case INVALID_STRUCTURE -> Component.translatable(INVALID_STRUCTURE_KEY);
            // 这两条属于「配置本身坏了」，对玩家来说与「没选方块」等价
            case NO_ADAPTER, INVALID_STATE -> Component.translatable(NO_BLOCK_KEY);
        };
    }

    /** 把消息发到动作栏。{@code message} 为 {@code null} 时什么都不做。 */
    public static void sendActionBar(Player player, @Nullable Component message) {
        if (player != null && message != null) {
            // 第二个参数 true = 动作栏（不走聊天框）
            player.displayClientMessage(message, true);
        }
    }

    /** 便捷入口：分类 + 发送。 */
    public static void report(Player player, PlacementResult result) {
        sendActionBar(player, messageFor(result));
    }
}
