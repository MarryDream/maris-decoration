package marrydream.marisdecoration.placement.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 「这个方块状态属性该怎么在界面上编辑、怎么显示给玩家」的描述。
 *
 * <p>GUI <b>不</b>自己判断哪种方块该给什么控件——它只拿到一串 {@link PropertySpec}，
 * 按 {@link Kind} 渲染即可。属性本身的取值语义完全由原版 {@link Property} 决定，
 * 这里只做两件事：「类型 → 控件」的翻译，以及「属性名 / 取值 → 玩家看得懂的文字」的翻译。
 *
 * <h2>三种控件</h2>
 * <ul>
 *   <li>{@link BooleanProperty} → 开关按钮；</li>
 *   <li>{@link EnumProperty}（含原版 {@code DirectionProperty}）→ 循环按钮，点一次下一个取值；</li>
 *   <li>{@link IntegerProperty} → 步进按钮，取值个数不多时也用循环。</li>
 * </ul>
 * 其它 {@link Property} 子类（例如模组自定义的）统一按「循环取值」处理——
 * 原版 {@code State#cycle} 对任意属性都成立，所以不会出现点不动的控件。
 *
 * <h2>本地化：绝不把裸属性名 / 裸取值丢给玩家</h2>
 * 原版方块状态里的 {@code facing}、{@code bottom_left}、{@code true} 这些是<b>数据</b>，
 * 不是给玩家看的文字。{@link #displayLabel()} / {@link #displayValue(BlockState)} 走同一个
 * 三级回退，保证界面上不会出现裸 key、也不会出现裸英文标识符：
 * <ol>
 *   <li><b>本 mod 的翻译</b> {@code maris-decoration.property.<属性名>}
 *       —— 覆盖所有常见的原版属性（facing / axis / half / shape / 各方向…）；</li>
 *   <li><b>原版自带翻译</b> {@code block.minecraft.<属性名>}
 *       —— 原版对 {@code facing}、{@code half}、{@code axis} 这些确实提供了词条，
 *       拿它兜一道可以覆盖我们没列到的原版属性；</li>
 *   <li><b>人读化处理</b>：把 {@code bottom_left} 变成 {@code bottom left} 再首字母大写
 *       —— 第三方模组的自定义属性走到这里为止，显示出来仍然是人话。</li>
 * </ol>
 * 取值（{@code north} / {@code true} / {@code 3}）走同一套三级回退，键名分别是
 * {@code maris-decoration.property_value.<值>} 与 {@code block.minecraft.<值>}。
 *
 * <h2>WATERLOGGED</h2>
 * 含水由实际放置位置的水体决定，不是玩家可配置项，所以不在这里过滤——过滤放在 GUI 侧
 * （它才是「该不该显示」的决定方）。
 */
public record PropertySpec(Property<?> property, Kind kind, List<String> values) {

    /** 用哪种控件编辑。 */
    public enum Kind {
        /** 布尔开关。 */
        TOGGLE,
        /** 循环取值（枚举 / 整数 / 其它）。 */
        CYCLE
    }

    /** 本 mod 的属性名翻译键前缀。 */
    public static final String LABEL_PREFIX = "maris-decoration.property.";
    /** 本 mod 的属性取值翻译键前缀。 */
    public static final String VALUE_PREFIX = "maris-decoration.property_value.";
    /** 原版对属性名与取值也有词条，用它兜一道。 */
    public static final String VANILLA_PREFIX = "block.minecraft.";

    /** 属性名（数据用，日志与测试会读它；<b>不要</b>直接拿它当界面文本）。 */
    public String name() {
        return property.getName();
    }

    /** 当前状态下的取值（数据用，小写字符串）。 */
    public String current(BlockState state) {
        return state.hasProperty(property) ? rawToText(state.getValue(property)) : "";
    }

    /** 当前取值在 {@link #values()} 里的下标；取不到时返回 0。 */
    public int currentIndex(BlockState state) {
        if (!state.hasProperty(property)) {
            return 0;
        }
        int index = values.indexOf(current(state));
        return index < 0 ? 0 : index;
    }

    // ---------------------------------------------------------------- 本地化

    /** 属性名给玩家看的文本（三级回退）。 */
    public Component displayLabel() {
        return localized(LABEL_PREFIX, name(), name());
    }

    /** 当前取值给玩家看的文本（三级回退）。 */
    public Component displayValue(BlockState state) {
        String raw = current(state);
        return raw.isEmpty() ? Component.empty() : localized(VALUE_PREFIX, raw, raw);
    }

    /** 界面上显示的一整行，例如「朝向 = 北」。 */
    public Component displayLine(BlockState state) {
        return Component.translatable("maris-decoration.property_line", displayLabel(), displayValue(state));
    }

    /**
     * 三级回退的本地化。
     *
     * <p>判定「有没有翻译」的办法与原版一致：找不到时 {@code Text#getString()} 会把键原样返回。
     * 这个办法不需要访问 Language 实例，两端都能用（自检是在服务端跑的）。
     *
     * @param prefix   本 mod 的翻译键前缀
     * @param raw      原始标识符（属性名或取值，全小写）
     * @param fallback 连人读化都不做时的兜底原文
     */
    private static Component localized(String prefix, String raw, String fallback) {
        Component own = Component.translatable(prefix + raw);
        if (!own.getString().equals(prefix + raw)) {
            return own;
        }
        Component vanilla = Component.translatable(VANILLA_PREFIX + raw);
        if (!vanilla.getString().equals(VANILLA_PREFIX + raw)) {
            return vanilla;
        }
        return Component.literal(humanize(fallback));
    }

    /**
     * 把标识符变成人读文本：下划线变空格、首字母大写。
     *
     * <p>这是给「我们和原版都没有词条」的第三方属性用的最后一档。它不翻译，但至少
     * {@code bottom_left} 会显示成 {@code Bottom left} 而不是一坨连着下划线的机器名。
     */
    public static String humanize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String spaced = raw.replace('_', ' ').trim();
        return spaced.isEmpty()
                ? raw
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** 属性全部取值（原始类型，保序）。 */
    public List<? extends Comparable<?>> rawValues() {
        return new ArrayList<>(property.getPossibleValues());
    }

    /**
     * 下一个取值要写进去的原始值。
     *
     * <p>用「先找到当前下标，再取下一个」而不是 {@code state.cycle(property)}：
     * 循环必须按 {@link #values()} 的顺序走，而 {@code cycle} 用的是属性自己的注册顺序；
     * 两者对原版属性一致，但统一走一条路更不容易出岔子。
     */
    public Object nextValue(BlockState state) {
        List<? extends Comparable<?>> raw = rawValues();
        if (raw.isEmpty()) {
            return null;
        }
        return raw.get((currentIndex(state) + 1) % raw.size());
    }

    /** 取值转成小写字符串：枚举用 {@code name()}，其余用 {@code toString()}。 */
    private static String rawToText(Comparable<?> value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase(Locale.ROOT);
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    /**
     * 把一个原版属性包装成 spec。
     *
     * <p>{@code values} 的字符串必须与 {@link #current} 的输出口径一致，否则下标会对不上，
     * 所以两者共用同一个 {@link #rawToText}。
     */
    public static PropertySpec of(Property<?> property) {
        List<String> values = new ArrayList<>();
        for (Comparable<?> value : property.getPossibleValues()) {
            values.add(rawToText(value));
        }
        Kind kind = property instanceof BooleanProperty ? Kind.TOGGLE : Kind.CYCLE;
        return new PropertySpec(property, kind, List.copyOf(values));
    }

    /** 这个是布尔属性吗（GUI 用它决定点击时直接取反还是切下一个值）。 */
    public boolean isBoolean() {
        return kind == Kind.TOGGLE && property instanceof BooleanProperty;
    }
}
