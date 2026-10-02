package marrydream.marisdecoration.client.tooltip;

import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 本 mod 通用的方块说明文案。
 *
 * <p><b>特性是显式枚举</b>（{@link MarisCharacteristic}），方块注册时列出自己拥有哪几条，
 * 顺序即显示顺序。
 *
 * <p><b>说明文字默认全部固定</b>：标题与说明都放在 mod 级的共用语言键里
 * （{@code maris-decoration.characteristic.<特性名>.title} / {@code .description}），
 * 一个方块声明了某条特性，显示的就是那条特性的标准文案——不随方块变化，
 * 所以不会出现同一个概念在不同方块上各写一套、久了就对不上的情况。
 *
 * <p><b>唯一的例外是「可调整状态」</b>：这条特性天然要说明「普通右键对这个方块做什么」，
 * 所以它的文案由三段拼成——共用前缀（{@code .prefix}）、方块自己的那一句、
 * 共用后缀（{@code .suffix}）。方块那一句的键由方块翻译键自动推导为
 * {@code <方块翻译键>.tooltip.<特性名>}，只有需要这一句的特性才会去读它。
 *
 * <p><b>与 Create 的关系</b>：挂载点、排版、提示行全部沿用 Create——
 * 写进 {@link TooltipModifier#REGISTRY} 由 Create 的客户端事件统一应用（不用自己挂回调）、
 * 用 {@link TooltipHelper#cutStringTextComponent} 做硬换行与「下划线交替高亮」、
 * 「按住 Shift」提示直接取 Create 自己的 {@code create.tooltip.*} 语言键。
 *
 * <p>本 mod 的说明不输出 summary，只保留特性条目。
 */
public final class MarisTooltip extends marrydream.marisdecoration.platform.TooltipAdapter {

    /** 条目前缀。 */
    private static final String BULLET = "- ";

    // ---------------------------------------------------------------- 配色

    /**
     * 条目标题：压到灰色，只起索引作用。按住 Shift 展开后<b>不变色</b>——
     * 展开的是说明文字，标题保持稳定，读者才不会以为换了条目。
     */
    private static final Style TITLE = Style.EMPTY.applyFormat(ChatFormatting.GRAY);
    /** 说明正文：柔和的浅粉白。 */
    private static final Style PRIMARY = TooltipHelper.styleFromColor(0xFFE6F0);
    /** 说明里的关键词 / 操作强调：低饱和玫瑰粉。 */
    private static final Style HIGHLIGHT = TooltipHelper.styleFromColor(0xFF9CC2);
    /** 「按住 Shift」提示的框架文字：沿用 Create 的深灰。 */
    private static final Style HINT_FRAME = Style.EMPTY.applyFormat(ChatFormatting.DARK_GRAY);

    private final Item item;
    private final List<MarisCharacteristic> characteristics;

    private String cachedLanguage;
    private List<Component> collapsed = List.of();
    private List<Component> expanded = List.of();

    private MarisTooltip(Item item, List<MarisCharacteristic> characteristics) {
        this.item = item;
        this.characteristics = characteristics;
    }

    /**
     * 给一个方块注册说明，并按给定顺序列出特性。
     *
     * <pre>{@code
     * MarisTooltip.register(ModBlock.XXX.asItem(),
     *         MarisCharacteristic.CAMOUFLAGE,
     *         MarisCharacteristic.ADJUSTABLE_STATE);
     * }</pre>
     */
    public static void register(Item item, MarisCharacteristic... characteristics) {
        TooltipModifier.REGISTRY.register(item, new MarisTooltip(item, List.of(characteristics)));
    }

    // ---------------------------------------------------------------- TooltipModifier

    @Override
    public void appendTooltip(List<Component> tooltip) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!language.equals(cachedLanguage)) {
            cachedLanguage = language;
            rebuild();
        }
        // 插在物品名后面，与 Create 的 ItemDescription 一致
        tooltip.addAll(1, Screen.hasShiftDown() ? expanded : collapsed);
    }

    private void rebuild() {
        String base = item.getDescriptionId() + ".tooltip";

        List<Component> brief = new ArrayList<>();
        List<Component> full = new ArrayList<>();

        brief.add(holdShiftHint(false));
        full.add(holdShiftHint(true));
        // 提示行与条目之间留一行，与 Create / Copycats+ 的观感一致
        brief.add(Component.empty());
        full.add(Component.empty());

        for (MarisCharacteristic characteristic : characteristics) {
            String title = BULLET + I18n.get(characteristic.titleKey());
            brief.add(Component.literal(title).setStyle(TITLE));
            full.add(Component.literal(title).setStyle(TITLE));
            // 说明缩进 1 格、自动硬换行，与 Create 的 behaviour 排版一致
            full.addAll(TooltipHelper.cutStringTextComponent(
                    describe(characteristic, base), PRIMARY, HIGHLIGHT, 1));
        }

        collapsed = List.copyOf(brief);
        expanded = List.copyOf(full);
    }

    /**
     * 一条特性的说明。
     *
     * <p>默认取该特性的共用文案；只有 {@link MarisCharacteristic#needsBlockClause()} 为真的特性
     * 才需要拼上方块自己那一句。
     */
    private static String describe(MarisCharacteristic characteristic, String base) {
        if (!characteristic.needsBlockClause()) {
            return I18n.get(characteristic.descriptionKey());
        }
        return I18n.get(characteristic.prefixKey())
                + I18n.get(base + "." + characteristic.key())
                + I18n.get(characteristic.suffixKey());
    }

    /**
     * 「按住 Shift 可查看概要」。文案直接取 Create 自己的语言键，措辞、按键名大小写与配色
     * 都跟 Create 方块保持一致，也能跟着 Create 一起被翻译。
     */
    private static MutableComponent holdShiftHint(boolean highlighted) {
        MutableComponent keyShift = Component.translatable("create.tooltip.keyShift")
                .setStyle(Style.EMPTY.applyFormat(highlighted ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        String[] parts = I18n.get("create.tooltip.holdForDescription", "$").split("\\$");
        if (parts.length != 2) {
            // Create 的语言键没加载出来（例如被资源包改坏）时的兜底，宁可显示得朴素一点
            return Component.translatable("create.tooltip.holdForDescription").setStyle(HINT_FRAME);
        }
        return Component.literal(parts[0]).setStyle(HINT_FRAME)
                .append(keyShift)
                .append(Component.literal(parts[1]).setStyle(HINT_FRAME));
    }

    // ---------------------------------------------------------------- 特性

    /**
     * 伪装方块共有的特性清单。
     *
     * <p>枚举定义「这是什么概念」以及<b>它的说明由哪几段构成</b>：
     * 绝大多数特性是整段固定文案；{@link #ADJUSTABLE_STATE} 例外，它由
     * 「共用前缀 + 方块那一句 + 共用后缀」拼成，因为「普通右键做什么」必然因方块而异。
     *
     * <p>新增特性：加一个常量、补 {@code maris-decoration.characteristic.<特性名>.title} 与
     * {@code .description} 两条共用语言键即可。只有确实需要方块各自措辞的特性，
     * 才把构造参数设成 {@code true} 并补 {@code .prefix} / {@code .suffix}。
     */
    public enum MarisCharacteristic {

        /** 伪装方块：用材质方块赋予 / 移除伪装。 */
        CAMOUFLAGE(false),
        /** 复合状态：同一方块空间可容纳多个部分。 */
        COMPOSITE_STATE(false),
        /** 分段伪装：同一结构的不同部分可分别设置材质。 */
        SEGMENT_CAMOUFLAGE(false),
        /**
         * 可调整状态：细工凿调整该方块的状态。
         *
         * <p>三段式——共用前缀里点亮工具名，方块自己那句写「普通右键做什么」，
         * 共用后缀里的「按住 Shift 右键还原状态」是所有可调整方块都必然具备的语义，
         * 写死在共用后缀里，方块作者碰不到、也就漏不掉。
         */
        ADJUSTABLE_STATE(true);

        private static final String LANG_PREFIX = "maris-decoration.characteristic.";

        /** 说明是否需要由方块补一句（拼接位置在 {@code .prefix} 与 {@code .suffix} 之间）。 */
        private final boolean needsBlockClause;

        MarisCharacteristic(boolean needsBlockClause) {
            this.needsBlockClause = needsBlockClause;
        }

        /** 语言键里用的短名，与枚举名小写一致。 */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 共用标题的语言键。 */
        public String titleKey() {
            return LANG_PREFIX + key() + ".title";
        }

        /** 整段固定说明的语言键。 */
        public String descriptionKey() {
            return LANG_PREFIX + key() + ".description";
        }

        /** 三段式说明的共用前缀。 */
        public String prefixKey() {
            return LANG_PREFIX + key() + ".prefix";
        }

        /** 三段式说明的共用后缀。 */
        public String suffixKey() {
            return LANG_PREFIX + key() + ".suffix";
        }

        public boolean needsBlockClause() {
            return needsBlockClause;
        }
    }
}
