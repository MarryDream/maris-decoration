package marrydream.marisdecoration.placement.adapter;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 内置 adapter 的清单与优先级。
 *
 * <h2>解析优先级</h2>
 * 数字小者先被问，第一个 {@link CopycatPlacementAdapter#supports} 成立的胜出：
 * <ol>
 *   <li>{@link #PRIORITY_OWN_CUSTOM} —— 本 mod 的两个自定义方块（护栏、分层薄板）。
 *       它们的判据是具体类，与其它 adapter 天然互斥；排在前面只是为了让「自己的东西优先」这件事
 *       在表里一眼可见，也让以后万一出现判据重叠时有确定的先后。</li>
 *   <li>{@link #PRIORITY_COPYCATS_MULTISTATE} —— Copycats+ 的 multistate。<b>必须</b>排在
 *       Copycats+ 普通与 Create 之前：见下面关于「Create 的伪装方块也被 mixin 成了
 *       {@code ICopycatBlock}」的说明，两者判据真的重叠，顺序在这里是语义而不是风格。</li>
 *   <li>{@link #PRIORITY_CREATE} —— Create 原生伪装方块（{@code CopycatBlock} 家族）。
 *       <b>必须早于</b> Copycats+ 普通：装了 Copycats+ 之后，Create 的
 *       {@code copycat_step} / {@code copycat_panel} / {@code copycat_bars} 会被它的 mixin
 *       额外实现 {@code ICopycatBlock}，于是「是不是 Copycats+ 普通方块」这条判据也会成立。
 *       但那些方块的方块实体仍然是 Create 的 {@code CopycatBlockEntity}，
 *       所以必须用 {@link CreateCopycatAdapter} 去写材质。</li>
 *   <li>{@link #PRIORITY_COPYCATS_ORDINARY} —— Copycats+ 自己的单材质方块
 *       （它们的方块实体是 {@code CCCopycatBlockEntity}）。</li>
 *   <li>{@link #PRIORITY_GENERIC} —— 兜底。</li>
 * </ol>
 *
 * <h2>可选依赖的处理</h2>
 * Copycats+ 是 {@code modCompileOnly}（可选兼容，不是依赖）。构造 adapter 表时会先查
 * {@link FabricLoader#isModLoaded(String)}，<b>没装就一个 Copycats+ 的 adapter 都不装</b>；
 * 装了才构造。这样没装 Copycats+ 的环境里既不会出现「类找不到」，也不会出现
 * 「一个永远不成立的 adapter」。
 *
 * <p>本 mod 的 {@code fabric.mod.json} 已经把 Create 声明为硬依赖，所以 Create 相关的 adapter
 * 不需要这层判断。
 */
public final class BuiltinAdapters {

    /** 本 mod 自己的两个自定义方块。 */
    public static final int PRIORITY_OWN_CUSTOM = 0;
    /** Copycats+ multistate：必须在所有单材质 adapter 之前。 */
    public static final int PRIORITY_COPYCATS_MULTISTATE = 10;
    /**
     * Create 原生伪装方块。
     *
     * <p>必须早于 {@link #PRIORITY_COPYCATS_ORDINARY}：装了 Copycats+ 之后 Create 的伪装方块
     * 会被它的 mixin 加上 {@code ICopycatBlock}，但方块实体仍是 Create 的
     * {@code CopycatBlockEntity}，只能由 {@code CreateCopycatAdapter} 写材质。
     */
    public static final int PRIORITY_CREATE = 15;
    /** Copycats+ 自己的单材质方块（方块实体是 {@code CCCopycatBlockEntity}）。 */
    public static final int PRIORITY_COPYCATS_ORDINARY = 20;
    /** 兜底。 */
    public static final int PRIORITY_GENERIC = 100;

    /** Copycats+ 的 mod id。 */
    public static final String COPYCATS_MOD_ID = "copycats";

    private static final boolean COPYCATS_LOADED = FabricLoader.getInstance().isModLoaded(COPYCATS_MOD_ID);

    private BuiltinAdapters() {
    }

    /** Copycats+ 在不在场。判断结果在类初始化时定下来，之后是常量。 */
    public static boolean copycatsLoaded() {
        return COPYCATS_LOADED;
    }

    /**
     * 全部内置 adapter，已按 {@link CopycatPlacementAdapter#priority()} 升序排好。
     *
     * <p>{@code List.sort} 是稳定排序，所以同优先级的相对顺序就是下面 {@code add} 的顺序，
     * 可复现、可断言。
     */
    public static List<CopycatPlacementAdapter> all() {
        List<CopycatPlacementAdapter> adapters = new ArrayList<>();
        adapters.add(new LayeredBoardCopycatAdapter());
        adapters.add(new GuardrailCopycatAdapter());
        if (COPYCATS_LOADED) {
            adapters.add(new CopycatsMultistateAdapter());
            adapters.add(new CopycatsOrdinaryAdapter());
        }
        adapters.add(new CreateCopycatAdapter());
        adapters.add(new GenericCopycatAdapter());
        adapters.sort(Comparator.comparingInt(CopycatPlacementAdapter::priority));
        return List.copyOf(adapters);
    }
}
