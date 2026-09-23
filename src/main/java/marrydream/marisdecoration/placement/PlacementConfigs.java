package marrydream.marisdecoration.placement;

import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/**
 * 「工具物品 ↔ 放置预设」的读写。
 *
 * <p>工具携带的预设<b>只存在这一把物品自己的 NBT 里</b>（需求 9：关界面 / 换快捷栏都要还在）。
 * 这里把读写规则集中成一处，服务端写回、客户端读取、物品 tooltip 三边共用，
 * 不会出现「某处写进去、另一处读不出来」。
 *
 * <h2>NBT 结构</h2>
 * <pre>
 * { maris_placer: &lt;PlacementConfigNbt 的那一份复合标签&gt; }   // 键缺失 = 没配过
 * </pre>
 * 用 {@link PlacementConfigNbt} 的格式原样嵌套，不再包一层自己的字段——工具就这一份数据，
 * 多包一层只会让以后做「复制预设」时多一次拆包。
 */
public final class PlacementConfigs {

    /** ItemStack NBT 里的键名。 */
    public static final String NBT_KEY = "maris_placer";

    private PlacementConfigs() {
    }

    /** 读这把工具携带的预设；没配过时返回 {@link PlacementConfig#EMPTY}。 */
    public static PlacementConfig read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return PlacementConfig.EMPTY;
        }
        NbtCompound nbt = stack.getSubNbt(NBT_KEY);
        if (nbt == null) {
            return PlacementConfig.EMPTY;
        }
        return PlacementConfig.fromNbt(nbt);
    }

    /** 这把工具配过东西吗。 */
    public static boolean hasConfig(ItemStack stack) {
        return read(stack).state() != null;
    }

    /**
     * 把预设写进这把工具。
     *
     * <p>空预设就<b>删掉整个子标签</b>，而不是写一个空复合进去——物品 NBT 里留一堆空标签
     * 会让「配过 / 没配过」的判断变复杂，也让 tooltip 逻辑要多想一层。
     */
    public static void write(ItemStack stack, PlacementConfig config) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (config == null || config.equals(PlacementConfig.EMPTY)) {
            stack.removeSubNbt(NBT_KEY);
            return;
        }
        stack.setSubNbt(NBT_KEY, config.toNbt());
    }

    /** 清掉这把工具的预设。 */
    public static void clear(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stack.removeSubNbt(NBT_KEY);
        }
    }

    /**
     * 这把工具当前选中的方块状态（用于 tooltip 与「请先选择伪装方块」的判断）。
     *
     * <p>返回 {@code null} 就代表「还没选方块」——需求 11 的 actionbar 提示由它触发。
     */
    public static @Nullable BlockState selectedState(ItemStack stack) {
        return read(stack).state();
    }

    /**
     * 服务端校验用：这份预设能不能被信任地拿去放置。
     *
     * <p>只做「结构上是否自洽」的检查，不做玩法判断（那些在 {@link PlacementService} 里）：
     * <ol>
     *   <li>必须指定了方块；</li>
     *   <li>那个方块必须被某个 adapter 认领；</li>
     *   <li>预设里的方块状态必须真的属于那个方块（防止客户端塞一个拼错的属性进去）。</li>
     * </ol>
     * 属性值本身由原版 {@code Property} 保证合法（反序列化时就校验过了）。
     */
    public static boolean isWellFormed(PlacementConfig config) {
        BlockState state = config.state();
        if (state == null || state.isAir()) {
            return false;
        }
        if (PlacementAdapters.resolve(state.getBlock()).isEmpty()) {
            return false;
        }
        // 属性名与取值在 NBT 反序列化阶段已经校验过；这里再确认一遍方块自己的属性集包含它们，
        // 挡住「用别的方块的属性拼出来的状态」。
        for (var entry : state.getEntries().entrySet()) {
            if (state.getBlock().getStateManager().getProperty(entry.getKey().getName()) == null) {
                return false;
            }
        }
        return true;
    }
}
