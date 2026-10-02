package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.PlacementConfigs;
import marrydream.marisdecoration.placement.adapter.CopycatPlacementAdapter;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 配置界面的编辑状态。
 *
 * <p>屏幕只负责画，所有「当前选了哪个方块、配置被改成什么样、可选方块有哪些」都住在这里。
 * 分成独立一层有两个理由：{@code PlacerScreen} 与 {@code MaterialSelectScreen} 是两个屏幕，
 * 材质选择屏关掉之后要回到配置屏并保留编辑进度，状态必须活得比屏幕久；而且这一层<b>不依赖任何
 * 客户端类型</b>，所以能放在主源集里、被服务端的自检直接测到（GUI 的画法测不了，但列表怎么生成、
 * 属性怎么枚举、virtual property 怎么循环这些逻辑都能测）。
 *
 * <h2>配置从哪来、到哪去</h2>
 * <ul>
 *   <li>打开界面时从<b>那把工具的 ItemStack</b> 读（{@link PlacementConfigs#read}）；</li>
 *   <li>任何一次修改都同时写回那把 ItemStack（让客户端立刻看到 tooltip / 放置结果变化）
 *       并往服务端发一次包（{@link PlacerClientBridge#sendConfig}）；</li>
 *   <li>关界面不发特殊包——每次改动已经实时同步过了，服务端那边的副本永远是最新的。</li>
 * </ul>
 * 「实时发」而不是「关界面时发一次」是有意的：中途退出、崩溃、被踢都不会丢配置，
 * 而且服务端的校验结果能立刻反映到 tooltip 上。
 *
 * <h2>方块列表怎么来</h2>
 * 见 {@code buildBlockList()}：遍历方块注册表，留下「有 BlockItem」且「被某个 adapter 认领」的。
 * 判断全部交给 {@link PlacementAdapters}，这里不认识任何具体方块类型。
 */
public final class PlacerEditState {

    /** 列表里的一项：方块 + 注册名（用于排序与显示）。 */
    public record Entry(Block block, ResourceLocation id, @Nullable String namespace) {
    }

    private final ItemStack tool;
    private PlacementConfig config;

    private List<Entry> blockList;
    private String filter = "";

    public PlacerEditState(ItemStack tool) {
        this.tool = tool;
        this.config = PlacementConfigs.read(tool);
    }

    // ---------------------------------------------------------------- 配置

    public ItemStack tool() {
        return tool;
    }

    public PlacementConfig config() {
        return config;
    }

    /** 当前选中的方块状态；没选方块时为 {@code null}。 */
    public @Nullable BlockState state() {
        return config.state();
    }

    public @Nullable Block selectedBlock() {
        return config.block();
    }

    /** 当前方块的 adapter；没选方块或没适配时为 {@code null}。 */
    public @Nullable CopycatPlacementAdapter adapter() {
        Block block = selectedBlock();
        return block == null ? null : PlacementAdapters.resolve(block).orElse(null);
    }

    /**
     * config 自己的方块状态——GUI 显示属性、点击修改、NBT 保存、最终放置<b>全部</b>以它为准。
     *
     * <p>{@link #config()} 是唯一事实来源，这个方法返回的只是它对「方块状态」这一面的忠实投影
     * （adapter 用它把结构掩码同步成方块状态属性）。它<b>不是</b>「为了方便展示而临时拼出来的状态」
     * ——曾经在这里返回 previewState 导致 GUI 与 config 分裂，见
     * {@link CopycatPlacementAdapter#displayState} 的注释。
     */
    public @Nullable BlockState displayState() {
        BlockState state = state();
        CopycatPlacementAdapter adapter = adapter();
        if (state == null || adapter == null) {
            return state;
        }
        return adapter.displayState(state, config);
    }

    /** 替换整份配置（列表里选方块、或者整体重置时用）。 */
    public void setConfig(PlacementConfig next) {
        this.config = next;
        commit();
    }

    /**
     * 换一个方块：配置重置成那个方块的<b>最小有效默认状态</b>。
     *
     * <p>两件事都不能省：
     * <ul>
     *   <li>不能直接用 {@code block.getDefaultState()}：有些伪装方块的默认状态虽然合法，
     *       但代表「零个部件」（Copycats+ 的 half_layer 就是两层都是 0），放下去就是一个
     *       看不见的幽灵方块。交给 {@link CopycatPlacementAdapter#defaultConfig} 去求最小有效形态；</li>
     *   <li>不能保留上一份配置里的结构与材质：不同方块的材质槽键名基本不重叠
     *       （{@code north_row} vs {@code up.outer.body} vs {@code copycats.multistate.up}），
     *       强行保留只会留下一堆永远不会被用到的孤儿数据，tooltip 上还显示「已配 12 个材质」误导玩家。</li>
     * </ul>
     */
    public void selectBlock(Block block) {
        CopycatPlacementAdapter adapter = PlacementAdapters.resolve(block).orElse(null);
        setConfig(adapter == null ? PlacementConfig.of(block.defaultBlockState()) : adapter.defaultConfig(block));
    }

    /** 把配置改成一个新的派生版本。所有编辑动作都走这里，保证「写回 + 同步」不会被漏掉。 */
    public void update(PlacementConfig next) {
        setConfig(next);
    }

    /** 清空选择（工具栏的「无」按钮）。 */
    public void clearSelection() {
        setConfig(PlacementConfig.EMPTY);
    }

    // ---------------------------------------------------------------- 方块列表

    /** 可选的伪装方块列表，按命名空间 + 路径排序，结果缓存。 */
    public List<Entry> blockList() {
        if (blockList == null) {
            blockList = buildBlockList();
        }
        return blockList;
    }

    /**
     * 扫描注册表，挑出「放置器能放」的方块。
     *
     * <p>三个条件，缺一不可：
     * <ol>
     *   <li>有对应的 {@link BlockItem}——没有物品就没法在生存模式付账，列表里出现它只会让玩家困惑；</li>
     *   <li>被某个 adapter 认领（{@link PlacementAdapters#resolve}）——这条直接复用了放置路径的判据，
     *       所以「列表里能选」与「右键能放」永远一致，不会出现选了却放不出来的方块；</li>
     *   <li>不是空气。</li>
     * </ol>
     * 按注册名排序，保证列表顺序稳定、可复现（不依赖注册顺序，那样会随加载顺序变化）。
     */
    private static List<Entry> buildBlockList() {
        List<Entry> entries = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            Item item = block.asItem();
            if (item == null || item == net.minecraft.world.item.Items.AIR || !(item instanceof BlockItem)) {
                continue;
            }
            if (!PlacementAdapters.isPlaceable(block)) {
                continue;
            }
            entries.add(new Entry(block, id, id.getNamespace()));
        }
        entries.sort(Comparator.comparing((Entry entry) -> entry.id().getNamespace())
                .thenComparing(entry -> entry.id().getPath()));
        return List.copyOf(entries);
    }

    /**
     * 当前过滤词。
     *
     * <p>匹配三个字段（大小写不敏感的子串匹配，不做编辑距离那套）：
     * 当前语言下的显示名、完整注册名、注册名路径。所以中文环境下输入「薄板」能同时命中
     * 「伪装薄板」和「分层伪装薄板」，输入 {@code copycat_board} 也能按 id 命中。
     */
    public String filter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter;
    }

    /** 应用过滤之后的列表。 */
    public List<Entry> filteredBlocks() {
        return filterEntries(blockList(), filter);
    }

    /**
     * 按搜索词过滤一组方块条目。
     *
     * <p>抽成静态方法是为了让它可测：自检直接调它验证「薄板」「copycat_board」这些查询，
     * 不需要真的开界面。
     */
    public static List<Entry> filterEntries(List<Entry> entries, String filter) {
        if (filter == null || filter.isBlank()) {
            return entries;
        }
        String needle = filter.toLowerCase(java.util.Locale.ROOT).trim();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (matches(entry, needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** 一个条目是否匹配搜索词：显示名 / 完整 id / id 路径，任一命中即可。 */
    public static boolean matches(Entry entry, String lowerCaseNeedle) {
        if (entry.id().toString().toLowerCase(java.util.Locale.ROOT).contains(lowerCaseNeedle)) {
            return true;
        }
        if (entry.id().getPath().toLowerCase(java.util.Locale.ROOT).contains(lowerCaseNeedle)) {
            return true;
        }
        // getName() 会走当前语言；语言文件没加载时它返回的是 translation key，
        // 那种情况下按名字搜不到，但按 id 搜仍然可用，不会整体失效。
        String name = entry.block().getName().getString();
        return name.toLowerCase(java.util.Locale.ROOT).contains(lowerCaseNeedle);
    }

    /** 判断一个方块是否匹配搜索词（材质选择界面用同一套规则）。 */
    public static boolean matches(Block block, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return matches(new Entry(block, BuiltInRegistries.BLOCK.getKey(block), null),
                filter.toLowerCase(java.util.Locale.ROOT).trim());
    }

    // ---------------------------------------------------------------- 提交

    /**
     * 写回物品 NBT 并发给服务端。
     *
     * <p>顺序是先本地后远端：本地写入让客户端立刻能用（tooltip、下一次右键的乐观预测），
     * 远端写入负责让服务端持有权威副本。服务端校验失败时不会回滚本地——它只是不更新自己的副本，
     * 下一次背包同步就会把客户端的乐观写入纠正掉。
     */
    private void commit() {
        PlacementConfigs.write(tool, config);
        PlacerClientBridge.sendConfig(config.equals(PlacementConfig.EMPTY) ? null : config);
    }
}
