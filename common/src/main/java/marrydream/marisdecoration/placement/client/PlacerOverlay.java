package marrydream.marisdecoration.placement.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面绘制流程里的「overlay 队列」：内容阶段排队，帧末统一绘制。
 *
 * <h2>为什么要有它</h2>
 * 悬停提示（tooltip）必须画在<b>整帧内容全部画完之后</b>，否则后画的那些内容会盖在它上面——
 * 原版 {@code DrawContext} 把 GUI 几何攒在一个 immediate 缓冲里，谁后写谁在上面，
 * 而提示框是在「画文字」那一步顺手把缓冲刷出去的。上一版把提示框放在
 * <b>结构区行循环里</b>画，于是后面紧接着画的材质区行、滚动条、搜索框都会压到提示框上
 * （实测表现就是「框出来了但文字只露一部分 / 被面板盖住」）。
 *
 * <p>光靠「记得最后画」是一条口头约定，下次有人往 {@code render} 末尾加一行内容又会复发。
 * 所以这里把<b>阶段</b>变成显式的、可检查的状态：
 * <ul>
 *   <li>{@link #beginContent()}：一帧开始，清掉上一帧残留；</li>
 *   <li>{@link #queue(Object)}：<b>只有内容阶段</b>能排队，别处调用直接返回 {@code false}；</li>
 *   <li>{@link #beginOverlay()}：内容画完，进入 overlay 阶段；</li>
 *   <li>{@link #takeForOverlay()}：<b>只有 overlay 阶段</b>能取走，取走即清空（不会重复画）；</li>
 *   <li>{@link #endFrame()}：帧结束。</li>
 * </ul>
 * 这些规则都能在无头自检里直接跑（见 {@code PlacementSelfTest} 第 20 段的
 * {@code runOverlayPhaseRules}），
 * 所以「提示框是不是真的在 overlay 阶段画」不再是靠肉眼看代码，而是有断言兜着的。
 *
 * @param <T> 排队的东西。客户端排的是「已经解析好文字的提示行」，
 *            所以这个类本身不依赖任何绘制类型，能放在 main 源集里。
 */
public final class PlacerOverlay<T> {

    /** 当前处于哪个绘制阶段。 */
    public enum Phase {
        /** 不在绘制中。 */
        IDLE,
        /** 内容阶段：往面板里画东西，只能排队。 */
        CONTENT,
        /** overlay 阶段：内容已经画完，只能取走并绘制。 */
        OVERLAY
    }

    private final List<T> queued = new ArrayList<>(2);
    private Phase phase = Phase.IDLE;

    public Phase phase() {
        return phase;
    }

    /** 一帧开始：进入内容阶段，并清掉上一帧的残留（异常中断也不会把旧提示带到下一帧）。 */
    public void beginContent() {
        queued.clear();
        phase = Phase.CONTENT;
    }

    /**
     * 内容阶段排队一个 overlay 元素。
     *
     * @return 是否真的排上了；不在内容阶段时返回 {@code false}，调用方据此可以断定
     *         「这行代码被放到了错误的阶段」
     */
    public boolean queue(T item) {
        if (phase != Phase.CONTENT || item == null) {
            return false;
        }
        queued.add(item);
        return true;
    }

    /** 内容画完，进入 overlay 阶段。 */
    public void beginOverlay() {
        phase = Phase.OVERLAY;
    }

    /**
     * 取走这一帧排队的全部元素；<b>只有 overlay 阶段能取</b>，取走即清空。
     *
     * <p>返回空列表表示「这一帧什么都不用画」——包括阶段不对、以及内容阶段没排任何东西。
     */
    public List<T> takeForOverlay() {
        if (phase != Phase.OVERLAY || queued.isEmpty()) {
            return List.of();
        }
        List<T> taken = List.copyOf(queued);
        queued.clear();
        return taken;
    }

    public boolean isEmpty() {
        return queued.isEmpty();
    }

    /** 帧结束：回到空闲，丢弃残留。 */
    public void endFrame() {
        queued.clear();
        phase = Phase.IDLE;
    }
}
