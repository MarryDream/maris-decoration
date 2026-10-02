package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.adapter.VirtualSpec;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 「结构 / 属性」区一行的悬停提示内容。
 *
 * <h2>为什么它单独一个类</h2>
 * 提示的<b>内容规则</b>（什么时候该有提示、提示里放什么）是纯数据，和「怎么画」无关。
 * 放在 main 源集里，无头服务端的自检就能直接断言它——而这件事必须在代码级钉死，
 * 因为上一版的缺陷正是「只要鼠标落在结构行上就无条件 {@code drawTooltip}」：
 * 普通布尔项（北面 / 下面外层 / 下面开窗…）明明一眼能看全，却也弹一个紫黑色空框出来。
 *
 * <h2>什么时候才该有提示</h2>
 * <ol>
 *   <li><b>文本真的被截断</b>（绘制时发生 ellipsis）→ 提示里给出完整原文。
 *       截断与否由渲染阶段实测（{@code fit} 前后是否变化）后传进来，不靠猜；</li>
 *   <li><b>顶点材质归属</b>（同一处顶点可以显示几条不同边的材质）→ 提示里先说明这一项是干什么的，
 *       再列出全部候选与当前归属。这是玩家光看一行看不到的信息；</li>
 *   <li>其它情况 → <b>空列表</b>：调用方不得画任何提示框。
 *       普通开关项（下面外层 / 东面内层 / 下面开窗…）走的就是这一支。</li>
 * </ol>
 *
 * <p>还有一道兜底：即使返回了行，{@link #shouldDraw} 也会逐行解析文本，
 * 全是空白就当没有。调用方必须同时过这两关才允许调用 {@code drawTooltip}——
 * 空列表、空白文本一律不画（{@code Text.empty()} 不算「没有提示」）。
 *
 * <h2>什么时候画</h2>
 * 提示框必须在<b>整帧内容画完之后</b>画，否则会被后面的内容盖住。
 * 这个次序由 {@link PlacerOverlay} 显式约束：这里只负责「有什么内容」，
 * 画在哪个阶段是那边的事。
 */
public final class StructureTooltip {

    /** 一行提示：若干段文本 + 段间分隔符（分隔符由调用方拼接，不是文本的一部分）。 */
    public record Line(List<VirtualSpec.LabelPart> parts, String separator) {

        public Line {
            parts = List.copyOf(parts);
        }

        /** 行内解析后的纯文本。 */
        public String resolve() {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    out.append(separator);
                }
                out.append(StructureTooltip.resolve(parts.get(i)));
            }
            return out.toString();
        }
    }

    /** 交汇点提示的标题行：「候选材质来源：」。 */
    public static final VirtualSpec.LabelPart CANDIDATES_TITLE = new VirtualSpec.LabelPart(
            "item.maris-decoration.copycat_placer.tooltip.candidates", "候选材质来源：");
    /** 交汇点提示的当前行：「当前：」。 */
    public static final VirtualSpec.LabelPart CURRENT_TITLE = new VirtualSpec.LabelPart(
            "item.maris-decoration.copycat_placer.tooltip.current", "当前：");
    /** 交汇点提示的第一行：说明这一项是干什么的（玩家一眼就知道该不该动它）。 */
    public static final VirtualSpec.LabelPart VERTEX_HINT = new VirtualSpec.LabelPart(
            "item.maris-decoration.copycat_placer.tooltip.vertex", "切换这个顶点使用哪条边的材质");
    /** 候选 / 当前列表的行首符号。 */
    public static final VirtualSpec.LabelPart BULLET = new VirtualSpec.LabelPart(
            "item.maris-decoration.copycat_placer.tooltip.bullet", "- ");

    private StructureTooltip() {
    }

    /**
     * 一段显示文本的最终文字：翻译优先，缺翻译时用兜底文本，<b>绝不返回裸翻译键</b>。
     *
     * <p>与 GUI 里给槽位用的那套是同一个口径（{@code PlacerScreen#labelOf}），
     * 但这里放在 main 源集，所以自检能直接验「显示出来的到底是什么字」。
     */
    public static String resolve(VirtualSpec.LabelPart part) {
        Component translated = Component.translatable(part.labelKey());
        String rendered = translated.getString();
        // 原版在找不到翻译时会把 key 原样返回，据此判断
        if (rendered.equals(part.labelKey()) && part.labelText() != null && !part.labelText().isBlank()) {
            return part.labelText();
        }
        return rendered;
    }

    /**
     * 这一行在当前渲染状态下应该给什么提示。
     *
     * @param spec            结构项
     * @param config          当前配置（取当前归属用）
     * @param labelTruncated  标题在绘制时是否真的被 {@code fit} 截断过
     * @param valueTruncated  取值在绘制时是否真的被 {@code fit} 截断过
     * @return 提示行；<b>空列表表示不该画提示</b>
     */
    public static List<Line> lines(VirtualSpec spec, PlacementConfig config,
                                   boolean labelTruncated, boolean valueTruncated) {
        List<Line> lines = new ArrayList<>(6);
        if (labelTruncated) {
            // 标题被截断了：把完整标题给出来（分隔符与行标题一致）
            lines.add(new Line(spec.label(), "·"));
        }
        if (spec.listsCandidates()) {
            // 第一行先说清「这一项是干什么的」，再列候选与当前归属：
            // 光看一行标题玩家不知道这是「切换顶点材质归属」，悬停却只看到一串边名。
            lines.add(new Line(List.of(VERTEX_HINT), ""));
            lines.add(new Line(List.of(CANDIDATES_TITLE), ""));
            for (VirtualSpec.Option option : spec.options()) {
                lines.add(new Line(withBullet(option.label()), ""));
            }
            VirtualSpec.Option current = spec.current(config);
            if (current != null) {
                lines.add(new Line(List.of(CURRENT_TITLE), ""));
                lines.add(new Line(withBullet(current.label()), ""));
            }
        } else if (valueTruncated) {
            // 取值被截断了：单独给一行完整取值
            VirtualSpec.Option current = spec.current(config);
            if (current != null) {
                lines.add(new Line(current.label(), ""));
            }
        }
        return lines;
    }

    /** 给一行文本加上行首符号（候选 / 当前列表用）。 */
    private static List<VirtualSpec.LabelPart> withBullet(List<VirtualSpec.LabelPart> parts) {
        List<VirtualSpec.LabelPart> out = new ArrayList<>(parts.size() + 1);
        out.add(BULLET);
        out.addAll(parts);
        return List.copyOf(out);
    }

    /**
     * 这些行里有没有真正要显示的文字。
     *
     * <p>空列表、以及「所有行都是空白」都返回 {@code false}。调用方必须据此决定
     * <b>不调用</b> {@code drawTooltip}——原版只要被调用就会画出背景框。
     */
    public static boolean shouldDraw(List<Line> lines) {
        for (Line line : lines) {
            if (!line.resolve().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
