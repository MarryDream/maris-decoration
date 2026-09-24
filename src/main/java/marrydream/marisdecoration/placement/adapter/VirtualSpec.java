package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「virtual property」——<b>不在方块状态里、但玩家需要逐项决定的结构属性</b>。
 *
 * <p>本 mod 的两个自定义方块与 Copycats+ 的一部分结构信息住在方块实体里（护栏的四个方向与四个
 * 角柱、分层薄板的 12 层板与 6 个窗、以及每个交汇点选哪条边的材质）。这些都不可能让玩家手算位掩码，
 * 所以 adapter 把它们翻译成「一行一项 + 当前取值」，由 GUI 渲染成点击即切换的行。
 *
 * <h2>这一版为什么是「一项一行」而不是「一个组合枚举」</h2>
 * 上一版把整个结构压成一个大枚举：护栏是 16 个「朝向组合」、薄板是十几个「占用组合」。
 * 那一套有三个硬伤：
 * <ol>
 *   <li><b>说不清</b>：玩家想「北面留着、再把东面加上」时，得先在下拉里认出「北+东」是哪一项；
 *       加第三个面时又要换一项，前面选过的东西被打包进另一个编号里；</li>
 *   <li><b>长得不像结构</b>：组合项与它包含的几个部件之间没有稳定的对应关系，
 *       结构一复杂枚举就爆炸（12 层板的所有组合是 4096 种，不可能枚举）；</li>
 *   <li><b>动态性无处安放</b>：角柱只在与它相邻的两个面之一存在时才有意义、窗只在这个面有板时
 *       才有意义、交汇点只在候选 ≥ 2 条边时才有意义，这些「后面才出现的项」在枚举模型里没有位置。</li>
 * </ol>
 * 所以模型改成：<b>每个可独立决定的东西一项，取值就是开 / 关（或「选哪条边」），
 * 后续项按当前结构动态出现</b>。内部存储仍可以是位掩码（那是实现细节），
 * 但配置的<b>语义</b>是逐项的。
 *
 * <h2>「动态出现」在哪儿决定</h2>
 * 不在这一层，而在 {@link CopycatPlacementAdapter#virtualSpecs(PlacementConfig)}：
 * adapter 拿到当前配置，只返回<b>此刻真正存在</b>的那些项。所以
 * 「关闭北面之后就不再出现东北角柱」是 adapter 的事，GUI 只管把拿到的行画出来。
 * 这也让「隐藏—再出现不重置」自然成立：被隐藏的项只是没被返回，配置里那一位从没被动过。
 *
 * <h2>显示文本</h2>
 * 行标题与候选项都只给「可读文本」：{@link LabelPart} 是一对「翻译键 + 兜底文本」，
 * GUI 先查翻译、查不到才用兜底。{@link #key()} 与 {@link Option#id()} 是<b>内部标识</b>
 * （写进配置 / NBT），界面上永远不显示它们。
 */
public interface VirtualSpec {

    /** 内部键名。写进配置 / NBT，<b>不显示</b>。 */
    String key();

    /** 界面上的行标题；可以多段（例如交汇点行把候选边名都列出来）。 */
    List<LabelPart> label();

    /** 可选项；至少一项。 */
    List<Option> options();

    /**
     * 当前取值对应的候选项；配置里的值不在候选集合里时返回 {@code null}
     * （界面据此显示「配置值无效」，而不是把内部值画出来）。
     */
    @Nullable Option current(PlacementConfig config);

    /** 把选中的候选项写回配置，返回新配置。 */
    PlacementConfig with(PlacementConfig config, Option option);

    /**
     * 这个 virtual property <b>接管了哪些方块状态属性名</b>。
     *
     * <p>护栏的四个面既是方块状态的四个 {@code BooleanProperty}，又是这里的四个开关。
     * 两个入口都暴露给玩家就会出现「改了布尔属性又被开关覆盖掉」，所以 GUI 会把
     * {@link #managedProperties()} 里列出的属性从「普通属性」列表中隐藏。
     */
    default List<String> managedProperties() {
        return List.of();
    }

    /**
     * 这一项的候选项<b>本身就是给玩家看的信息</b>吗。
     *
     * <p>开关（关 / 开）不是：玩家看右边的值就知道当前状态，把「关 / 开」列一遍没有任何新信息。
     * 交汇点归属是：同一个物理位置可以显示几条不同边的材质，光看一行只看得到当前那一个，
     * 所以界面应当主动把这些候选来源列出来（悬停提示里）。
     */
    default boolean listsCandidates() {
        return false;
    }

    /** 当前候选项在 {@link #options()} 里的下标；值不在候选里（或缺失）时返回 {@code -1}。 */
    default int indexOfCurrent(PlacementConfig config) {
        Option current = current(config);
        if (current == null) {
            return -1;
        }
        List<Option> options = options();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(current.id())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 点一下之后切到的候选项。
     *
     * <p>配置里是个非法值时<b>直接归一化到第一个候选项</b>：从下标 0 再往后跳一格会让玩家
     * 点一下却看到既不是原值也不是相邻项的选项，像是随机跳的。
     */
    default Option next(PlacementConfig config) {
        List<Option> options = options();
        int index = indexOfCurrent(config);
        return options.get(index < 0 ? 0 : (index + 1) % options.size());
    }

    // ---------------------------------------------------------------- 显示文本

    /**
     * 一段显示文本：翻译键 + 兜底文本。
     *
     * @param labelKey  翻译键
     * @param labelText 翻译缺失时的文本。<b>必须是可读文字</b>，不能是内部键名 / 掩码 / 槽位码
     */
    record LabelPart(String labelKey, String labelText) {

        public static LabelPart of(String labelKey, String labelText) {
            return new LabelPart(labelKey, labelText);
        }
    }

    /**
     * 一个候选项。
     *
     * @param id    内部取值（掩码位 / 槽位名…）。写进配置，<b>不显示</b>
     * @param label 显示文本，可以多段
     */
    record Option(String id, List<LabelPart> label) {

        public Option {
            label = List.copyOf(label);
        }

        public static Option of(String id, String labelKey, String labelText) {
            return new Option(id, List.of(new LabelPart(labelKey, labelText)));
        }
    }

    // ---------------------------------------------------------------- 工厂

    /** 读一个开关。 */
    @FunctionalInterface
    interface FlagReader {
        boolean read(PlacementConfig config);
    }

    /** 写一个开关。 */
    @FunctionalInterface
    interface FlagWriter {
        PlacementConfig write(PlacementConfig config, boolean value);
    }

    /** 读一个标识（例如交汇点当前显示的槽名）。 */
    @FunctionalInterface
    interface IdReader {
        @Nullable String read(PlacementConfig config);
    }

    /** 写一个标识。 */
    @FunctionalInterface
    interface IdWriter {
        PlacementConfig write(PlacementConfig config, String id);
    }

    /**
     * 开关的候选项固定是这两个：关 / 开。
     *
     * <p>id 里带 {@code toggle.} 前缀是刻意的：<b>它不会被写进任何地方</b>
     * （开关的写回是按 boolean 落到掩码位上），所以它只是一个会话内的身份标识；
     * 加上前缀可以让「这是内部标识」在日志与断言里一眼可见，
     * 也避免它看起来像一个可以直接显示的单词。
     */
    String OFF_ID = "toggle.off";
    String ON_ID = "toggle.on";

    /**
     * 一个「开 / 关」项。
     *
     * <p>候选项顺序固定为「关、开」：非法值点一下归一化到第一项（关），
     * 而默认值本身始终是合法候选（例如护栏默认「只有北面开着」），不受这个顺序影响。
     */
    static VirtualSpec toggle(String key, List<LabelPart> label,
                              FlagReader reader, FlagWriter writer) {
        List<Option> options = List.of(
                Option.of(OFF_ID, "item.maris-decoration.copycat_placer.option.off", "关"),
                Option.of(ON_ID, "item.maris-decoration.copycat_placer.option.on", "开"));
        return new VirtualSpec() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public List<LabelPart> label() {
                return label;
            }

            @Override
            public List<Option> options() {
                return options;
            }

            @Override
            public Option current(PlacementConfig config) {
                return reader.read(config) ? options.get(1) : options.get(0);
            }

            @Override
            public PlacementConfig with(PlacementConfig config, Option option) {
                return writer.write(config, ON_ID.equals(option.id()));
            }

            @Override
            public String toString() {
                return "VirtualSpec(toggle:" + key + ")";
            }
        };
    }

    /**
     * 一个「从候选里选一个」的项。
     *
     * <p>本 mod 里只有交汇点材质归属用它：候选是几何上重合的那几条边，
     * 取值是<b>槽位名</b>（稳定的内部标识），显示是边的可读名。
     */
    static VirtualSpec choice(String key, List<LabelPart> label,
                              IdReader reader, IdWriter writer, List<Option> options) {
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
            public List<LabelPart> label() {
                return label;
            }

            @Override
            public List<Option> options() {
                return copy;
            }

            @Override
            public boolean listsCandidates() {
                return true;
            }

            @Override
            public @Nullable Option current(PlacementConfig config) {
                String id = reader.read(config);
                if (id == null) {
                    return null;
                }
                for (Option option : copy) {
                    if (option.id().equals(id)) {
                        return option;
                    }
                }
                return null;
            }

            @Override
            public PlacementConfig with(PlacementConfig config, Option option) {
                return writer.write(config, option.id());
            }

            @Override
            public String toString() {
                return "VirtualSpec(choice:" + key + ", count=" + copy.size() + ")";
            }
        };
    }

    /** 一个空实现：某些 adapter 没有任何 virtual property。 */
    static List<VirtualSpec> none() {
        return List.of();
    }

    /**
     * 给这一项附加「接管了哪些方块状态属性」，返回一个薄包装。
     *
     * <p>做成包装而不是给工厂再加参数：只有护栏的四个面开关需要它，其余项多想一次
     * 「要不要接管属性」没有意义。
     */
    default VirtualSpec managing(List<String> properties) {
        return new ManagedSpec(this, List.copyOf(properties));
    }

    /**
     * 附加 {@link #managedProperties()} 的包装。
     *
     * <p>只转发，不改任何语义：取值读写、候选项全部照旧。做成 delegate 而不是给三个具体实现
     * 各加一个字段，是为了让「额外的声明」与「项本身怎么算」彻底分开。
     */
    record ManagedSpec(VirtualSpec delegate, List<String> managedProperties) implements VirtualSpec {

        @Override
        public String key() {
            return delegate.key();
        }

        @Override
        public List<LabelPart> label() {
            return delegate.label();
        }

        @Override
        public List<Option> options() {
            return delegate.options();
        }

        @Override
        public Option current(PlacementConfig config) {
            return delegate.current(config);
        }

        @Override
        public PlacementConfig with(PlacementConfig config, Option option) {
            return delegate.with(config, option);
        }

        @Override
        public String toString() {
            return delegate + "+managed" + managedProperties;
        }
    }
}
