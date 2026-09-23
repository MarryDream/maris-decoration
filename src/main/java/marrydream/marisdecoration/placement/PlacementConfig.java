package marrydream.marisdecoration.placement;

import marrydream.marisdecoration.placement.nbt.PlacementConfigNbt;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 「伪装放置器」的一份放置预设。
 *
 * <p>这一份数据就是 GUI（下一阶段）要做的东西的<b>全部状态</b>，也是网络包要传的东西、
 * 服务端执行放置时唯一的事实来源。所以它是不可变的、与 {@code World} 无关的纯数据：
 * 构造一次之后只能通过 {@link #withSlot} / {@link #withStructure} 派生新副本。
 *
 * <h2>它描述什么</h2>
 * <ul>
 *   <li>{@link #state()} —— 要放的那个伪装方块的<b>方块与方块状态属性</b>。
 *       属性值由玩家在 GUI 里逐项指定，不走「照点击面定向」那套默认逻辑。
 *       <b>不含 {@code WATERLOGGED}</b>：含水由实际放置位置的水体决定，
 *       见 {@link #stateForPlacement}。</li>
 *   <li>{@link #structures()} —— <b>特殊结构属性</b>（键值对）。键由各 adapter 定义，
 *       例如分层伪装薄板的 12 位占用掩码、护栏的四面掩码。见 {@link Key}。</li>
 *   <li>{@link #slots()} —— 各 material slot 的伪装材质。键由各 adapter 定义，
 *       值是要伪装成的 {@link BlockState}（含属性，例如朝向 / 轴向 / 半砖上下）。</li>
 * </ul>
 *
 * <h2>为什么 slot 值是 BlockState 而不是 Item</h2>
 * 伪装材质需要的不只是「哪个方块」，还有它的属性（原木沿哪个轴、楼梯朝哪边）。Create 与
 * Copycats+ 的伪装存的就是完整 {@link BlockState}，直接用状态才能与它们对齐。
 *
 * <h2>线程与所有权</h2>
 * 全部字段都是不可变或已复制的映射，构造完即可安全跨线程读（客户端渲染线程 / 服务端逻辑线程）。
 */
public final class PlacementConfig {

    /** 没有任何伪装材质 / 没有任何特殊结构的空配置。 */
    public static final PlacementConfig EMPTY = new PlacementConfig();

    /**
     * 特殊结构属性的键名。
     *
     * <p>集中放在这里，是为了让「配置的键名」「adapter 读的键名」「GUI（下一阶段）写的键名」
     * 不可能各写各的。键名只用小写字母与下划线，直接充当 NBT 的子标签名。
     */
    public static final class Key {
        /** 分层伪装薄板：12 个 Face/Layer 的占用掩码（{@code 0..4095}，位序见 LayeredBoardSlots#slotBit）。 */
        public static final String LAYERED_BOARD_OCCUPANCY = "occupancy";
        /** 分层伪装薄板：6 个面的窗开关掩码（{@code 0..63}，位序见 LayeredBoardSlots#windowBit）。 */
        public static final String LAYERED_BOARD_WINDOWS = "windows";
        /** 伪装护栏：四个方向的存在掩码（{@code 0..15}，位序见 CopycatGuardrailBlock#bit）。 */
        public static final String GUARDRAIL_FACES = "guardrail_faces";

        private Key() {
        }
    }

    private final BlockState state;
    private final Map<String, String> structures;
    private final Map<String, BlockState> slots;

    private PlacementConfig() {
        this(null, Map.of(), Map.of());
    }

    public PlacementConfig(@Nullable BlockState state,
                           Map<String, String> structures,
                           Map<String, BlockState> slots) {
        this.state = state;
        // 复制成 LinkedHashMap：保留写入顺序，NBT 与调试输出因此可复现；
        // 同时挡住调用方在构造之后继续改自己那份 Map。
        this.structures = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(structures));
        this.slots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }

    /** 只带方块状态的极简配置（没有任何特殊结构、没有任何材质）。 */
    public static PlacementConfig of(@Nullable BlockState state) {
        return new PlacementConfig(state, Map.of(), Map.of());
    }

    // ---------------------------------------------------------------- 读取

    /** 预设的方块状态；未指定方块时为 {@code null}。 */
    public @Nullable BlockState state() {
        return state;
    }

    /**
     * 预设方块状态所属的方块；未指定时为 {@code null}。
     *
     * <p>这是「有没有结构方块可以放」的唯一判据，见
     * {@link PlacementService}：拿不到方块 = 这次什么都不做。
     */
    public @Nullable Block block() {
        return state == null ? null : state.getBlock();
    }

    /** 结构方块对应的物品（生存模式要消耗的那一个）；未指定或该方块没有物品时为 {@code null}。 */
    public @Nullable Item structureItem() {
        Block block = block();
        if (block == null) {
            return null;
        }
        Item item = block.asItem();
        return item == null || item == net.minecraft.item.Items.AIR ? null : item;
    }

    /** 特殊结构属性（只读，保序）。 */
    public Map<String, String> structures() {
        return structures;
    }

    /** 各 material slot 的伪装材质（只读，保序）。 */
    public Map<String, BlockState> slots() {
        return slots;
    }

    /**
     * 只给日志用的方块名：注册名 + 属性，或者 {@code <none>}。
     *
     * <p>与 {@link #toString()} 的区别是它<b>不会</b>把几十个 material slot 一起打出来，
     * 适合放进「每次放置都记一条」的调试日志里。
     */
    public String blockIdSafe() {
        return state == null ? "<none>" : Registries.BLOCK.getId(state.getBlock()).toString();
    }

    /** 某个特殊结构属性的原始字符串值；没有则返回 {@code null}。 */
    public @Nullable String structure(String key) {
        return structures.get(key);
    }

    // ---------------------------------------------------------------- 派生

    /** 换一个方块状态，其余不变。 */
    public PlacementConfig withState(@Nullable BlockState state) {
        return new PlacementConfig(state, structures, slots);
    }

    /** 写一个特殊结构属性，其余不变。 */
    public PlacementConfig withStructure(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(structures);
        next.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return new PlacementConfig(state, next, slots);
    }

    /** 写一个 material slot 的伪装材质，其余不变。 */
    public PlacementConfig withSlot(String key, BlockState material) {
        Map<String, BlockState> next = new LinkedHashMap<>(slots);
        next.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(material, "material"));
        return new PlacementConfig(state, structures, next);
    }

    /** 批量写 material slot 的伪装材质（后写的覆盖先写的），其余不变。 */
    public PlacementConfig withSlots(Map<String, BlockState> materials) {
        Map<String, BlockState> next = new LinkedHashMap<>(slots);
        next.putAll(materials);
        return new PlacementConfig(state, structures, next);
    }

    /** 去掉一个 material slot，其余不变。 */
    public PlacementConfig withoutSlot(String key) {
        if (!slots.containsKey(key)) {
            return this;
        }
        Map<String, BlockState> next = new LinkedHashMap<>(slots);
        next.remove(key);
        return new PlacementConfig(state, structures, next);
    }

    /** 去掉一个特殊结构属性，其余不变。 */
    public PlacementConfig withoutStructure(String key) {
        if (!structures.containsKey(key)) {
            return this;
        }
        Map<String, String> next = new LinkedHashMap<>(structures);
        next.remove(key);
        return new PlacementConfig(state, next, slots);
    }

    /**
     * 把配置的方块状态调成「真正要写进世界的那个状态」——<b>即从水体推出含水</b>。
     *
     * <p>注意 {@code PlacementService} <b>不用</b>这个方法：它要在 adapter 写完特殊结构属性
     * <b>之后</b>才加含水（见 {@code PlacementService#stateWithWater}），
     * 直接拿这里的原始预设状态去算会把 adapter 写进去的结构属性丢掉。
     * 保留这个方法是为了让「只看一份配置」的调用方（例如下一阶段的 GUI 预览）也能拿到
     * 一个合理的展示状态。放水里的实际结果以 {@code PlacementService} 为准。
     */
    public @Nullable BlockState stateForPlacement(boolean waterlogged) {
        if (state == null) {
            return null;
        }
        if (!state.contains(net.minecraft.state.property.Properties.WATERLOGGED)) {
            return state;
        }
        return state.with(net.minecraft.state.property.Properties.WATERLOGGED, waterlogged);
    }

    // ---------------------------------------------------------------- NBT

    /** 写进 NBT 的复合标签（物品 NBT / 自定义网络包都用这一份格式）。 */
    public NbtCompound toNbt() {
        return PlacementConfigNbt.write(this);
    }

    /** 从 NBT 读回；结构不完整时返回 {@link #EMPTY}。 */
    public static PlacementConfig fromNbt(@Nullable NbtCompound nbt) {
        return PlacementConfigNbt.read(nbt);
    }

    // ---------------------------------------------------------------- 值语义

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlacementConfig config)) {
            return false;
        }
        return Objects.equals(state, config.state)
                && structures.equals(config.structures)
                && slots.equals(config.slots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, structures, slots);
    }

    /**
     * 调试用文本。方块用注册名（含属性）而不是 {@code BlockState.toString()}，
     * 后者在不同映射下不稳定，写日志时不好对比。
     */
    @Override
    public String toString() {
        return "PlacementConfig{" + blockName() + ", structures=" + structures + ", slots=" + describeSlots() + "}";
    }

    private String blockName() {
        if (state == null) {
            return "block=<none>";
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        Set<Map.Entry<net.minecraft.state.property.Property<?>, Comparable<?>>> entries = state.getEntries().entrySet();
        if (entries.isEmpty()) {
            return "block=" + id;
        }
        StringBuilder builder = new StringBuilder("block=").append(id).append('[');
        boolean first = true;
        for (Map.Entry<net.minecraft.state.property.Property<?>, Comparable<?>> entry : entries) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(entry.getKey().getName()).append('=').append(entry.getValue());
        }
        return builder.append(']').toString();
    }

    private String describeSlots() {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, BlockState> entry : slots.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append('=')
                    .append(Registries.BLOCK.getId(entry.getValue().getBlock()));
        }
        return builder.append('}').toString();
    }
}
