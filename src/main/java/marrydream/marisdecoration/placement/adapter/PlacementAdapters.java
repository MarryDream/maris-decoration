package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * adapter 解析表。
 *
 * <p>解析规则简单到不需要任何聪明东西：<b>按 {@link CopycatPlacementAdapter#priority()} 升序问一遍，
 * 谁先说自己能处理就用谁</b>。刻意不做「注册表 + 覆盖」那套：本 mod 的 adapter 数量是个位数，
 * 而且互相之间判据几乎不重叠，一张有序列表比一个可变注册表更难出错、也更容易在测试里断言。
 *
 * <p>最后一位必须是兜底的 {@link GenericCopycatAdapter}：它处理所有「是伪装方块、但前面几个
 * adapter 都不认识」的情况（最典型的是第三方 mod 新增的 Create {@code CopycatBlock} 子类）。
 * 没有它的话，一个没适配的伪装方块会静默地「放不出来」，而不是至少把结构放对、只缺材质。
 *
 * <p>{@link #resolve} 返回 {@link Optional#empty()} 只代表「这不是伪装方块」——
 * 也就是「放置器管不着」，调用方应当干脆地什么都不做。
 */
public final class PlacementAdapters {

    private static volatile List<CopycatPlacementAdapter> adapters = List.of();

    private PlacementAdapters() {
    }

    /**
     * 装上全部内置 adapter 并排好序。
     *
     * <p>由 {@code MarisDecoration#onInitialize} 调用。放在初始化里而不是静态常量里，是为了让
     * 「依赖的 mod 到底在不在」这件事在类初始化时就已经确定（见
     * {@link BuiltinAdapters#all()}）。
     */
    public static void init() {
        adapters = BuiltinAdapters.all();
    }

    /** 当前已装上的 adapter（按优先级升序，只读）。 */
    public static List<CopycatPlacementAdapter> all() {
        return adapters;
    }

    /**
     * 找出处理这个方块的 adapter。
     *
     * @param block 要放的方块
     * @return 处理它的 adapter；不是伪装方块时为空
     */
    public static Optional<CopycatPlacementAdapter> resolve(Block block) {
        for (CopycatPlacementAdapter adapter : adapters) {
            if (adapter.supports(block)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    /**
     * 「这个方块能不能用放置器放」。
     *
     * <p>给工具物品（下一阶段）与测试用：判断能不能放只看方块类型，与预设里配了什么无关。
     */
    public static boolean isPlaceable(Block block) {
        return resolve(block).isPresent();
    }

    /** 全部 adapter 的名字，调试 / 日志用。 */
    public static List<String> names() {
        List<String> names = new ArrayList<>(adapters.size());
        for (CopycatPlacementAdapter adapter : adapters) {
            names.add(adapter.name());
        }
        return names;
    }

    /**
     * 枚举某个状态的 material slot。
     *
     * <p>找不到 adapter 时返回空列表，而不是抛异常——「不是伪装方块」是一种正常输入。
     */
    public static List<AdapterSlot> slotsOf(BlockState state, PlacementConfig config) {
        return resolve(state.getBlock())
                .map(adapter -> adapter.slots(state, config))
                .orElse(List.of());
    }
}
