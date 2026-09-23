package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;

import java.util.List;

/**
 * 「virtual property」——<b>不在方块状态里、但玩家需要配置的结构属性</b>。
 *
 * <p>本 mod 的两个自定义方块与 Copycats+ 的一部分结构信息住在方块实体里（12 位占用掩码、窗开关、
 * 四向掩码…）。玩家当然不可能去手算位掩码，所以 adapter 把它们翻译成「名字 + 一组可选项」，
 * 由 GUI 渲染成下拉式循环按钮。
 *
 * <h2>为什么必须由 adapter 提供</h2>
 * 「哪些 virtual property 存在、各自有几个取值、改动后怎么写回配置」全部是方块类型相关的知识。
 * 如果 GUI 自己去认「这是分层薄板所以要给 occupancy/windows 两个按钮」，那么每加一种伪装方块
 * 都要改 GUI。做成 spec 之后 GUI 只认识 {@link VirtualSpec} 一种数据。
 *
 * <h2>取值怎么表达</h2>
 * 一律用整数。位掩码天然是整数；「四面全开」「只有北」这些语义由 adapter 在
 * {@link #options} 里给出可读标签，GUI 不解释它们的含义。
 */
public interface VirtualSpec {

    /** 写进 {@link PlacementConfig#structures()} 的键名。 */
    String key();

    /** 界面上的行标题翻译键。 */
    String labelKey();

    /**
     * 行标题的兜底文本（翻译缺失时显示它）。
     *
     * <p><b>必须有值</b>，而且<b>不能</b>是 {@link #key()} 本身：{@code key()} 是写进
     * {@link PlacementConfig#structures()} / NBT 的内部键名（{@code guardrail_faces}、
     * {@code occupancy}），只应该出现在配置与存档里。界面上显示它是「把内部标识漏给玩家看」。
     */
    String labelText();

    /** 读当前取值。 */
    int current(PlacementConfig config);

    /** 把新取值写回配置。返回值必须是新的配置对象（{@code PlacementConfig} 不可变）。 */
    PlacementConfig with(PlacementConfig config, int value);

    /** 可选项：显示名 + 值，顺序由 adapter 定。至少要有一项，否则 GUI 无从渲染。 */
    List<Option> options();

    /**
     * 这个 virtual property <b>接管了哪些方块状态属性名</b>。
     *
     * <p>存在的意义是消除「同一个结构两套编辑入口」：护栏的四向结构既在方块状态的四个
     * {@code BooleanProperty} 里，又在这个 virtual property 里。如果两个入口都暴露给玩家，
     * 就会出现「改了布尔属性、又被 virtual 覆盖掉」这种互相打架的状态。所以 GUI 会把
     * {@link #managedProperties()} 里列出的属性从「普通属性」列表中<b>隐藏</b>，
     * 只保留 virtual property 这一个入口。
     *
     * <p>默认空：结构完全由方块实体掩码表达的 adapter（分层薄板、multistate）本来就没有
     * 重叠的属性，返回空表示「不隐藏任何东西」。
     */
    default List<String> managedProperties() {
        return List.of();
    }

    /** 当前取值在 {@link #options()} 里的下标；找不到时返回 0。 */
    default int currentIndex(PlacementConfig config) {
        int index = indexOfCurrent(config);
        return index < 0 ? 0 : index;
    }

    /**
     * 当前取值在候选里的下标；<b>找不到时返回 -1</b>。
     *
     * <p>用于区分「正好是第一个候选项」与「配置里是个当前候选集合里没有的值」
     * （旧版本的工具 NBT、被改过的配置）。前者照常显示，后者要显示「配置无效」。
     */
    default int indexOfCurrent(PlacementConfig config) {
        int current = current(config);
        List<Option> options = options();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value() == current) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 当前候选项；配置里的值不在候选里时返回第一个候选项（归一化）。
     *
     * <p>「归一化」只发生在玩家下一次点击时（{@link #nextValue}）与内部取值上——
     * 界面上的显示仍然是「配置无效」，不会把一个乱值假装成某个正常选项；
     * 服务端的 {@code validateStructure} 继续做最终兜底。
     */
    default Option currentOption(PlacementConfig config) {
        List<Option> options = options();
        int index = indexOfCurrent(config);
        return options.get(index < 0 ? 0 : index);
    }

    /**
     * 下一个可选项的取值（循环）。
     *
     * <p>配置里是个非法值时<b>直接归一化到第一个候选项</b>，而不是「从 0 再往后跳一格」——
     * 后者会让玩家点一下却看到一个既不是原值也不是相邻项的选项，像是随机跳的。
     */
    default int nextValue(PlacementConfig config) {
        List<Option> options = options();
        int index = indexOfCurrent(config);
        if (index < 0) {
            return options.get(0).value();
        }
        return options.get((index + 1) % options.size()).value();
    }

    /**
     * 一个可选项。
     *
     * @param labelKey  翻译键，GUI 优先用它
     * @param labelText 翻译缺失时的兜底文本；<b>不能是原始数值或十六进制掩码</b>
     *                  （{@code 0x3}、{@code 16} 这类只属于配置/NBT）
     * @param value     写进配置的整数值
     */
    record Option(String labelKey, String labelText, int value) {

        /** 只有翻译键的简写：兜底直接用键名（只适合「键名本身就是可读英文」的场景）。 */
        public static Option of(String labelKey, int value) {
            return new Option(labelKey, labelKey, value);
        }
    }

    /**
     * 「一个开关」型 virtual property 的便捷实现：0 = 关、1 = 开。
     *
     * <p>给「这个面开不开窗」这类语义用，省掉每个 adapter 各写一遍两个 Option。
     */
    static VirtualSpec toggle(String key, String labelKey, String labelText,
                              Reader reader, Writer writer) {
        return new VirtualSpec() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String labelKey() {
                return labelKey;
            }

            @Override
            public String labelText() {
                return labelText;
            }

            @Override
            public int current(PlacementConfig config) {
                return reader.read(config) ? 1 : 0;
            }

            @Override
            public PlacementConfig with(PlacementConfig config, int value) {
                return writer.write(config, value != 0);
            }

            @Override
            public List<Option> options() {
                return List.of(
                        new Option("maris-decoration.copycat_placer.option.off", "off", 0),
                        new Option("maris-decoration.copycat_placer.option.on", "on", 1));
            }

            @Override
            public String toString() {
                return "VirtualSpec(toggle:" + key + ")";
            }
        };
    }

    /** {@link #current} 用的读取器。 */
    @FunctionalInterface
    interface Reader {
        boolean read(PlacementConfig config);
    }

    /** {@link #with} 用的写入器。 */
    @FunctionalInterface
    interface Writer {
        PlacementConfig write(PlacementConfig config, boolean value);
    }

    /**
     * 一个「从候选列表里选一个整数」的 virtual property。
     *
     * <p>给位掩码用：候选由 adapter 给（例如「四个方向」的 16 种组合、或「占哪几层」的几种常用组合）。
     * 这样界面上出现的是可读标签而不是裸数字，玩家也不需要理解位。
     */
    static VirtualSpec options(String key, String labelKey, String labelText,
                               IntReader reader, IntWriter writer, List<Option> options) {
        List<Option> copy = List.copyOf(options);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("virtual property " + key + " 必须至少有一个候选项");
        }
        return new VirtualSpec() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String labelKey() {
                return labelKey;
            }

            @Override
            public String labelText() {
                return labelText;
            }

            @Override
            public int current(PlacementConfig config) {
                return reader.read(config);
            }

            @Override
            public PlacementConfig with(PlacementConfig config, int value) {
                return writer.write(config, value);
            }

            @Override
            public List<Option> options() {
                return copy;
            }

            @Override
            public String toString() {
                return "VirtualSpec(options:" + key + ", count=" + copy.size() + ")";
            }
        };
    }

    /** {@link #options} 版本的读取器。 */
    @FunctionalInterface
    interface IntReader {
        int read(PlacementConfig config);
    }

    /** {@link #options} 版本的写入器。 */
    @FunctionalInterface
    interface IntWriter {
        PlacementConfig write(PlacementConfig config, int value);
    }

    /** 一个空实现：某些 adapter 没有任何 virtual property。 */
    static List<VirtualSpec> none() {
        return List.of();
    }
}
