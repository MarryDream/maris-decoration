package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 「一个 material slot 长什么样」的通用描述。
 *
 * <p>存在的意义只有一个：<b>把方块类型的差异挡在 GUI 之外</b>。GUI 永远只看到一份
 * {@link AdapterSlot} 列表，它不需要知道「这是 Create 的伪装、还是 Copycats+ 的 multistate、
 * 还是本 mod 的分层薄板」——那些判断全在各自的 {@link CopycatPlacementAdapter} 里。
 *
 * @param key        material slot 的键名。写进 {@link PlacementConfig#slots()} 的就是它，
 *                   各 adapter 与对应方块实体的材质键<b>逐字符一致</b>（例如 {@code north.outer.body}）。
 * @param labelKey   GUI 上显示的名字，用翻译键。
 * @param labelText  翻译缺失时的兜底文本。
 *                   <p>为什么需要它：像 Copycats+ 的 multistate 那样「槽位由方块自己声明」的适配器，
 *                   属性名是<b>运行时才知道</b>的（{@code up}、{@code north}…，第三方还可能加新的），
 *                   不可能在语言文件里穷举翻译键。有了兜底文本，界面上至少显示得出东西，
 *                   而不是一串裸翻译键。已经写了翻译的槽位优先用翻译。
 * @param groupKey   所属分组（一个「面」、一个「部件」之类）。GUI 用它把 slot 折叠成一棵树；
 *                   扁平结构的适配器（Create、Copycats+ 普通）给一个固定值就够。
 * @param structure  这个 slot 所在的部件在当前结构下是否真的存在，也就是这个材质槽<b>当前有效还是无效</b>。
 *                   <p>它有两个用途：
 *                   <ol>
 *                   <li><b>显示</b>：无效槽照样列在材质区（全量显示），但界面把它<b>置灰</b>，
 *                       让玩家一眼看出「这个槽现在配了也看不见」。是否允许点它去改，
 *                       各 adapter 一致（与 Copycats+ 的伪装薄板行为对齐：允许点）；</li>
 *                   <li><b>放置</b>：{@code PlacementService} 只预演 / 处理有效的槽——
 *                       {@code false} 的槽写进去也不会显示。例如分层薄板里没有被 occupancy 选中的
 *                       面 / 层，或者没开窗的那个面的窗槽。</li>
 *                   </ol>
 * @param material   当前预设给这个 slot 的伪装方块；没有预设时为 {@code null}。
 */
public record AdapterSlot(String key,
                          String labelKey,
                          String labelText,
                          String groupKey,
                          boolean structure,
                          @Nullable Block material) {

    /** 当前预设里这个 slot 有没有材质。 */
    public boolean hasMaterial() {
        return material != null;
    }

    /** 兜底构造：翻译键就是 labelText（适合「键名本身就是可读英文」的简单场景）。 */
    public static AdapterSlot of(String key, String labelKey, String groupKey, boolean structure,
                                 @Nullable BlockState material) {
        return new AdapterSlot(key, labelKey, labelKey, groupKey, structure,
                material == null ? null : material.getBlock());
    }

    /** 带兜底文本的构造。 */
    public static AdapterSlot of(String key, String labelKey, String labelText, String groupKey,
                                 boolean structure, @Nullable BlockState material) {
        return new AdapterSlot(key, labelKey, labelText, groupKey, structure,
                material == null ? null : material.getBlock());
    }

    /** 只改材质，其余照抄。让适配器在「有材质 / 无材质」两种写法间切换时不必重复长参数表。 */
    public AdapterSlot withMaterial(@Nullable BlockState material) {
        return new AdapterSlot(key, labelKey, labelText, groupKey, structure,
                material == null ? null : material.getBlock());
    }
}
