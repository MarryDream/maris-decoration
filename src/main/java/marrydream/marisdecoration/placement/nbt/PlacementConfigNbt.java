package marrydream.marisdecoration.placement.nbt;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link PlacementConfig} 的 NBT 形式。
 *
 * <p>格式（全部子键名都是稳定的常量字符串，改了就等于改存档格式）：
 * <pre>
 * {
 *   block:        &lt;方块状态复合标签，NbtHelper.fromBlockState 的原生形式&gt;   // 可缺省 = 没有方块
 *   structures:   { &lt;键&gt;: &lt;字符串&gt; ... }                                // 可缺省 = 空
 *   slots:        { &lt;键&gt;: &lt;方块状态复合标签&gt; ... }                       // 可缺省 = 空
 * }
 * </pre>
 *
 * <p><b>为什么 slots 的值是方块状态复合标签而不是物品 id</b>：伪装材质需要属性（朝向 / 轴向 /
 * 半砖上下…），只存物品 id 会在读回来的时候丢掉这些信息。用
 * {@link NbtHelper#fromBlockState} 的形式还天然兼容原版的调试工具（{@code /data get} 能直接看懂）。
 *
 * <p><b>为什么不用 {@code NbtHelper.toBlockState} 的反查做版本迁移</b>：不需要。属性名与值都是
 * 原版自带的字符串，方块 id 用的是注册名；唯一会失效的情况是「那个方块被卸载了」，
 * 此时整条 slot 直接丢弃（见 {@link #readSlots}），不会让整份配置读失败。
 */
public final class PlacementConfigNbt {

    /** 预设的方块状态。 */
    public static final String KEY_BLOCK = "block";
    /**
     * 方块状态复合标签里的属性表键名。
     *
     * <p>这是<b>原版自己的格式</b>（{@code NbtHelper.fromBlockState} 写出来就是
     * {@code {Name: ..., Properties: {...}}}），这里只是把它提成常量，好在读取时按名字校验。
     */
    public static final String KEY_PROPERTIES = "Properties";
    /** 特殊结构属性表。 */
    public static final String KEY_STRUCTURES = "structures";
    /** material slot → 伪装材质。 */
    public static final String KEY_SLOTS = "slots";

    private PlacementConfigNbt() {
    }

    /** 写。空表整个不写，读端按「键缺失 = 空」处理。 */
    public static NbtCompound write(PlacementConfig config) {
        NbtCompound nbt = new NbtCompound();
        BlockState state = config.state();
        if (state != null) {
            nbt.put(KEY_BLOCK, NbtHelper.fromBlockState(state));
        }

        if (!config.structures().isEmpty()) {
            NbtCompound structures = new NbtCompound();
            config.structures().forEach(structures::putString);
            nbt.put(KEY_STRUCTURES, structures);
        }

        if (!config.slots().isEmpty()) {
            NbtCompound slots = new NbtCompound();
            config.slots().forEach((key, material) -> slots.put(key, NbtHelper.fromBlockState(material)));
            nbt.put(KEY_SLOTS, slots);
        }
        return nbt;
    }

    /** 读。任何一层缺失 / 类型不对都退化成空，不抛异常。 */
    public static PlacementConfig read(@Nullable NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return PlacementConfig.EMPTY;
        }
        return new PlacementConfig(readBlock(nbt), readStructures(nbt), readSlots(nbt));
    }

    /**
     * 读预设方块状态。
     *
     * <p>原版 {@link NbtHelper#toBlockState} 对<b>不存在的属性名</b>是「静默忽略」而不是报错，
     * 于是一份「属性名拼错」或「拿别的方块的属性拼出来的」配置会被它悄悄读成一个看似正常的
     * 状态——这种东西不能信。所以这里<b>在反序列化之前</b>逐项校验原始 NBT 里的属性名属于该方块，
     * 任何一项对不上就整体退化成「没有方块」。
     *
     * <p><b>必须查原始 NBT 的键，不能查反序列化之后的状态</b>：未知属性已经被原版丢掉了，
     * 事后从 {@code state.getEntries()} 里根本查不出来（这是实测踩到的坑）。
     *
     * <p>按需求，这种坏配置的表现是「这次不放置、不扣任何东西」，而不是让物品或网络包处理崩掉，
     * 所以全部失败路径都返回 {@code null}，不抛异常。
     */
    private static @Nullable BlockState readBlock(NbtCompound nbt) {
        if (!nbt.contains(KEY_BLOCK, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        NbtCompound blockNbt = nbt.getCompound(KEY_BLOCK);
        BlockState state;
        try {
            state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), blockNbt);
        } catch (RuntimeException exception) {
            return null;
        }
        if (state == null || state.isAir()) {
            return null;
        }
        StateManager<Block, BlockState> stateManager = state.getBlock().getStateManager();
        NbtCompound properties = blockNbt.getCompound(KEY_PROPERTIES);
        for (String name : properties.getKeys()) {
            if (stateManager.getProperty(name) == null) {
                return null;
            }
        }
        return state;
    }

    private static Map<String, String> readStructures(NbtCompound nbt) {
        Map<String, String> structures = new LinkedHashMap<>();
        NbtCompound compound = nbt.getCompound(KEY_STRUCTURES);
        for (String key : compound.getKeys()) {
            if (compound.contains(key, NbtElement.STRING_TYPE)) {
                structures.put(key, compound.getString(key));
            }
        }
        return structures;
    }

    private static Map<String, BlockState> readSlots(NbtCompound nbt) {
        Map<String, BlockState> slots = new LinkedHashMap<>();
        NbtCompound compound = nbt.getCompound(KEY_SLOTS);
        for (String key : compound.getKeys()) {
            if (!compound.contains(key, NbtElement.COMPOUND_TYPE)) {
                continue;
            }
            BlockState material;
            try {
                material = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), compound.getCompound(key));
            } catch (RuntimeException exception) {
                // 这个方块可能已经被卸载 / 属性改了：只丢这一条 slot
                continue;
            }
            if (material != null && !material.isAir()) {
                slots.put(key, material);
            }
        }
        return slots;
    }

    /**
     * 这一份 NBT 是不是「空配置」。
     *
     * <p>给物品 tooltip 与网络包（下一阶段）用：没必要为一份什么都没配的预设发一个包。
     */
    public static boolean isEmpty(@Nullable NbtCompound nbt) {
        return read(nbt).equals(PlacementConfig.EMPTY);
    }
}

