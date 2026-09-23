package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import org.jetbrains.annotations.Nullable;

/**
 * 掩码类「特殊结构属性」的解析与格式化。
 *
 * <p>结构属性的值在 {@link PlacementConfig#structures()} 里一律是字符串（这样才与图形界面、
 * 网络包、NBT 三种载体同时兼容）。掩码是其中最常见的一种，所以统一在这里处理：
 * <ul>
 *   <li>写出去用 <b>十六进制</b>、带 {@code 0x} 前缀（例如 {@code 0xfff}）——
 *       位与位的对应关系一眼能看出来，十进制 {@code 4095} 看不出来；</li>
 *   <li>读回来接受 {@code 0x} 前缀、不带前缀的十六进制、以及十进制；
 *       解析失败或越界一律退化成 {@code fallback}，<b>绝不抛异常</b>。</li>
 * </ul>
 *
 * <p>为什么「读失败要退化」而不是报错：结构属性是玩家/GUI 填的，一份坏预设应当只表现为
 * 「这次放置用默认结构」，而不是让整个物品或网络包处理崩掉。
 */
public final class StructureMasks {

    private StructureMasks() {
    }

    /**
     * 读一个掩码。
     *
     * @param config   预设
     * @param key      键名（见 {@link PlacementConfig.Key}）
     * @param bits     这个掩码占多少位；超出的位会被清掉
     * @param fallback 缺失或解析失败时的返回值
     * @return 落在 {@code [0, (1 << bits) - 1]} 内的掩码
     */
    public static int read(PlacementConfig config, String key, int bits, int fallback) {
        String raw = config.structure(key);
        if (raw == null || raw.isBlank()) {
            return clamp(fallback, bits);
        }
        Integer parsed = parse(raw);
        return parsed == null ? clamp(fallback, bits) : clamp(parsed, bits);
    }

    /** 把掩码写成规范字符串（十六进制、带 {@code 0x} 前缀）。 */
    public static String write(int mask) {
        return "0x" + Integer.toHexString(mask);
    }

    /**
     * 把掩码写回配置（先按 {@code bits} 截断）。
     *
     * <p>给 GUI 用：界面上的「virtual property」以整数暴露取值，回写时统一走这里，
     * 免得每个 adapter 各写一遍 {@code config.withStructure(key, StructureMasks.write(...))}。
     */
    public static PlacementConfig with(PlacementConfig config, String key, int mask, int bits) {
        return config.withStructure(key, write(clamp(mask, bits)));
    }

    /**
     * 解析掩码：先按 {@code 0x}/{@code 0X} 前缀走十六进制，再按裸十六进制试一次，
     * 最后按十进制试一次。全部失败返回 {@code null}。
     *
     * <p>顺序上先试十六进制是刻意的：{@link #write} 的输出永远是 {@code 0x...}，
     * 而玩家手写的 {@code 10} 更可能想表达「第 4 位」（16 = 0x10）而不是「第 1 位 + 0」。
     * 这个歧义无法消除，选择与写出格式一致的那一侧。
     */
    private static @Nullable Integer parse(String raw) {
        String value = raw.trim();
        boolean hex = value.startsWith("0x") || value.startsWith("0X") || value.startsWith("#");
        if (hex) {
            value = value.substring(value.startsWith("#") ? 1 : 2);
        }
        try {
            return hex ? Integer.parseUnsignedInt(value, 16) : Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            // 落到十进制再试一次
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 清掉多余的位，并保证非负。 */
    private static int clamp(int mask, int bits) {
        int limit = bits >= 32 ? -1 : (1 << bits) - 1;
        return mask & limit;
    }

    /**
     * 把 {@code 0..(2^bits - 1)} 的掩码解析成布尔数组，下标即位数。
     *
     * <p>给「按位枚举 slot」的 adapter 用，省掉每个 adapter 各写一遍位移。
     */
    public static boolean[] bits(int mask, int bits) {
        boolean[] out = new boolean[bits];
        for (int i = 0; i < bits; i++) {
            out[i] = (mask & (1 << i)) != 0;
        }
        return out;
    }
}
