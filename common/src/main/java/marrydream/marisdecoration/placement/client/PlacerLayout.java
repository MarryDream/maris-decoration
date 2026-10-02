package marrydream.marisdecoration.placement.client;

import java.util.List;

/**
 * 伪装放置器界面的<b>纯几何计算</b>。
 *
 * <h2>为什么它不在 {@code PlacerScreen} 里</h2>
 * 上一版的溢出缺陷（材质区的行画到了面板外、盖住物品栏）根源是
 * 「同一个区域的坐标被算了三遍」：渲染一套、点击命中一套、滚动条一套，
 * 三者的下边界差 12px。<b>只有把几何收敛到唯一来源，才谈得上验证</b>——
 * 而放在 {@code PlacerScreen}（客户端源集）里的几何是没法被无头服务端的自检覆盖的：
 * 专用服务端上没有 {@code Screen}，那部分代码永远跑不到。
 *
 * <p>所以这里把「一个区域占哪块屏幕」抽成一个<b>只依赖整数</b>的类，放在 main 源集：
 * 界面用它，自检也用它。自检断言的就是界面真正在用的那份计算，而不是复刻一份。
 *
 * <h2>三个区域</h2>
 * <ul>
 *   <li>{@code list} —— 左栏方块列表；</li>
 *   <li>{@code property} —— 右栏上半「结构 / 属性」；</li>
 *   <li>{@code material} —— 右栏下半「材质」。</li>
 * </ul>
 *
 * <p>三个视口的下边界全部由 {@link Geometry#contentBottom()} 派生，而它等于
 * {@code top + PANEL_HEIGHT - PADDING}。也就是说<b>结构上就不可能越过面板下边界</b>——
 * 这比「渲染时记得裁剪」强得多：越界不是被裁掉，而是根本不存在。
 */
public final class PlacerLayout {

    // ---------------------------------------------------------------- 尺寸常量

    /** 面板宽（基准分辨率 320x240 下留出边距）。 */
    public static final int PANEL_WIDTH = 330;
    /** 面板高。 */
    public static final int PANEL_HEIGHT = 200;
    /** 左栏（方块列表）的列宽，含内边距。 */
    public static final int LIST_WIDTH = 140;
    /** 面板内边距。 */
    public static final int PADDING = 6;
    /** 单行文字行高（属性行 / 材质行）。 */
    public static final int LINE_HEIGHT = 12;
    /** 方块列表条目高（两行文字 + 图标）。 */
    public static final int ENTRY_HEIGHT = 20;
    /** 搜索框高度。 */
    public static final int SEARCH_HEIGHT = 16;
    /** 右栏小节标题占的行高。 */
    public static final int SECTION_LINE = 10;
    /** 列表条目左侧留给物品图标的宽度。 */
    public static final int ICON_WIDTH = 22;
    /** 滚动条宽度。 */
    public static final int SCROLLBAR_WIDTH = 4;
    /** thumb 的最小可抓长度，太短就没法拖。 */
    public static final int MIN_THUMB_HEIGHT = 8;
    /** 注册名的显示缩放：原样太大，列表里连一半都放不下。 */
    public static final float ID_SCALE = 0.5F;
    /** 原版字体高度：小节标题的文字必须整行落在内容区上面，靠它来判定。 */
    public static final int TITLE_TEXT_HEIGHT = 8;
    /** 小节标题的文字相对标题行顶边的偏移。 */
    public static final int TITLE_TEXT_OFFSET = 1;

    private PlacerLayout() {
    }

    // ---------------------------------------------------------------- 内容描述

    /**
     * 布局需要的「内容规模」。几何只关心有多少行，不关心行里画什么。
     *
     * @param listRows     方块列表条目数
     * @param propertyRows 属性行 + virtual property 行的总数
     * @param materialRows 材质槽行数
     */
    public record Content(int listRows, int propertyRows, int materialRows) {

        public static Content empty() {
            return new Content(0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- 视口

    /**
     * 一个可滚动区域的几何：内容画在 {@code [x, x+w) × [y, y+h)} 内，
     * 内容整体按 {@code scroll} 上移，可绘制区域与可点区域<b>都是这个矩形</b>。
     *
     * <p>把它做成 record（不可变）是为了让「改滚动量」必须走 {@link #withScroll}：
     * 不可能出现「视口里的 scroll 已经变了、外面的裁剪框还是旧的」这种半更新状态。
     */
    public record Viewport(int x, int y, int w, int h, int scroll, int contentHeight) {

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public int maxScroll() {
            return Math.max(0, contentHeight - h);
        }

        public boolean hasScrollbar() {
            return contentHeight > h && h > 0;
        }

        /** 内容顶部的 y（第 0 行画在这里）。 */
        public int contentY() {
            return y - scroll;
        }

        /** 第 {@code index} 行在屏幕上的顶边。 */
        public int rowY(int index, int rowHeight) {
            return contentY() + index * rowHeight;
        }

        /**
         * 第 {@code index} 行是否与视口相交。
         *
         * <p>只有相交的行才允许绘制、才允许响应点击。用「相交」而不是「完全包含」，
         * 是为了让半露在边缘的那一行也正常显示（越界部分由 scissor 裁掉）。
         */
        public boolean rowVisible(int index, int rowHeight) {
            int rowTop = rowY(index, rowHeight);
            return rowTop + rowHeight > y && rowTop < bottom();
        }

        /** 点是否落在这个视口内。 */
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }

        /** 点落在视口内时命中的行下标；否则 {@code -1}。 */
        public int rowIndexAt(double mouseX, double mouseY, int rowHeight) {
            if (!contains(mouseX, mouseY) || rowHeight <= 0) {
                return -1;
            }
            return (int) Math.floor((mouseY - contentY()) / rowHeight);
        }

        /** 鼠标是否正好在这一行的可见部分上（用于 hover）。 */
        public boolean rowHovered(double mouseY, int index, int rowHeight) {
            int rowTop = rowY(index, rowHeight);
            return mouseY >= rowTop && mouseY < rowTop + rowHeight - 1;
        }

        /** 同一个视口、换一个滚动量（已夹紧到合法范围）。 */
        public Viewport withScroll(int newScroll) {
            return new Viewport(x, y, w, h, clamp(newScroll, 0, maxScroll()), contentHeight);
        }
    }

    // ---------------------------------------------------------------- 整体几何

    /**
     * 一整个界面的几何快照。
     *
     * @param contentBottom  所有内容区的统一下边界（= 面板下边界 - 内边距）
     * @param searchTop      搜索框顶边
     * @param listX          左栏内容左缘
     * @param listWidth      左栏内容宽
     * @param rightX         右栏左缘
     * @param rightWidth     右栏宽
     * @param propertyTitleY 右栏上小节标题所在行
     * @param materialTitleY 右栏下小节标题所在行
     */
    public record Geometry(int contentBottom, int searchTop, int listX, int listWidth,
                           int rightX, int rightWidth, int propertyTitleY, int materialTitleY,
                           Viewport list, Viewport property, Viewport material) {

        /** 三个视口，顺序固定：列表、属性、材质。 */
        public List<Viewport> all() {
            return List.of(list, property, material);
        }
    }

    /**
     * 算出一整个界面的几何。
     *
     * <p>纵向结构（右栏）：属性小节标题 → 属性内容 → 材质小节标题 → 材质内容。
     * 两段内容按可用高度均分，且材质内容的下边界<b>恰好</b>是
     * {@code contentBottom}——它由「可用高度 - 两段标题预留」反推，不再各算一遍。
     *
     * @param screenWidth  当前窗口宽
     * @param screenHeight 当前窗口高
     * @param content      内容规模
     * @param listScroll   左栏滚动量
     * @param propertyScroll 属性区滚动量
     * @param materialScroll 材质区滚动量
     */
    public static Geometry compute(int screenWidth, int screenHeight, Content content,
                                   int listScroll, int propertyScroll, int materialScroll) {
        int left = (screenWidth - PANEL_WIDTH) / 2;
        int top = (screenHeight - PANEL_HEIGHT) / 2;

        // 左栏：主标题 → 搜索框 → 列表（主标题下面直接是搜索框，不再有灰色小节标题）
        int searchTop = top + PADDING + LINE_HEIGHT + 3;
        int listX = left + PADDING;
        int listWidth = LIST_WIDTH - PADDING * 2;
        int contentTop = searchTop + SEARCH_HEIGHT + 4;

        // 面板内容的统一下边界。三个视口全部由它派生。
        int contentBottom = top + PANEL_HEIGHT - PADDING;

        int rightX = left + LIST_WIDTH + PADDING;
        int rightWidth = PANEL_WIDTH - LIST_WIDTH - PADDING * 2;

        Viewport list = new Viewport(listX, contentTop, listWidth,
                contentBottom - contentTop, listScroll, content.listRows() * ENTRY_HEIGHT)
                .withScroll(listScroll);

        // 右栏纵向预算：属性标题带 + 属性内容 + 空隙 + 材质标题带 + 材质内容 = 可用高度。
        // 两个标题带各占 SECTION_LINE，中间留 PADDING 的空隙——这三段是「固定开销」，
        // 剩下的才是两段内容。**材质内容的高度是「反推出来的余数」**，所以它的下边界
        // 恰好等于 contentBottom；上一版把固定开销算漏了一个 SECTION_LINE，
        // 于是材质区比面板下边界多出 2×SECTION_LINE（20px），行就画到面板外去了。
        int available = contentBottom - contentTop;
        int fixed = SECTION_LINE * 2 + PADDING;
        int propertyHeight = Math.max(0, (available - fixed) / 2);
        int materialTop = Math.min(contentTop + SECTION_LINE + propertyHeight + PADDING + SECTION_LINE,
                contentBottom);
        int materialHeight = Math.max(0, contentBottom - materialTop);

        int propertyTop = contentTop + SECTION_LINE;

        Viewport property = new Viewport(rightX, propertyTop, rightWidth,
                propertyHeight, propertyScroll, content.propertyRows() * LINE_HEIGHT)
                .withScroll(propertyScroll);
        Viewport material = new Viewport(rightX, materialTop, rightWidth,
                materialHeight, materialScroll, content.materialRows() * LINE_HEIGHT)
                .withScroll(materialScroll);

        return new Geometry(contentBottom, searchTop, listX, listWidth, rightX, rightWidth,
                propertyTop - SECTION_LINE + 1, materialTop - SECTION_LINE + 1,
                list, property, material);
    }

    // ---------------------------------------------------------------- 滚动条 / 滚动量

    /** 滚动条左缘：贴视口右缘内侧。 */
    public static int scrollbarX(Viewport viewport) {
        return viewport.right() - SCROLLBAR_WIDTH;
    }

    /** thumb 长度：视口高 × (视口高 / 内容高)，并保证一个最小可抓长度。 */
    public static int thumbHeightFor(int viewportHeight, int contentHeight) {
        if (contentHeight <= viewportHeight || viewportHeight <= 0) {
            return viewportHeight;
        }
        return Math.max(MIN_THUMB_HEIGHT, viewportHeight * viewportHeight / contentHeight);
    }

    /** thumb 顶边。滚动量为 0 时贴顶，为 maxScroll 时恰好贴底。 */
    public static int thumbY(Viewport viewport) {
        int thumbHeight = thumbHeightFor(viewport.h(), viewport.contentHeight());
        int travel = viewport.h() - thumbHeight;
        int max = viewport.maxScroll();
        return viewport.y() + (max <= 0 ? 0 : travel * viewport.scroll() / max);
    }

    /** 点是否落在该视口的滚动条上（内容不足一屏时恒为 false）。 */
    public static boolean inScrollbar(double mouseX, double mouseY, Viewport viewport) {
        if (viewport == null || !viewport.hasScrollbar()) {
            return false;
        }
        int x = scrollbarX(viewport);
        return mouseX >= x - 1 && mouseX < x + SCROLLBAR_WIDTH + 1
                && mouseY >= viewport.y() && mouseY < viewport.bottom();
    }

    /**
     * 拖动滚动条后的新滚动量。
     *
     * <p>按「鼠标位移 / thumb 行程」换算，而不是绝对定位——绝对定位会让人一按就把内容跳到别处。
     */
    public static int scrollForDrag(Viewport viewport, int startScroll, int startMouseY, int mouseY) {
        int max = viewport.maxScroll();
        int travel = Math.max(1, viewport.h() - thumbHeightFor(viewport.h(), viewport.contentHeight()));
        int moved = mouseY - startMouseY;
        return clamp(startScroll + moved * max / travel, 0, max);
    }

    /**
     * 让某一行的整行都在视野里的滚动量（打开界面时把已选中的方块滚进来）。
     *
     * @param scroll    当前滚动量
     * @param viewport  区域
     * @param rowTop    目标行在「内容坐标系」里的顶边（{@code index * rowHeight}）
     * @param rowHeight 行高
     */
    public static int scrollToShowRow(int scroll, Viewport viewport, int rowTop, int rowHeight) {
        int rowBottom = rowTop + rowHeight;
        int next = scroll;
        if (rowTop < scroll) {
            next = rowTop;
        } else if (rowBottom > scroll + viewport.h()) {
            next = rowBottom - viewport.h();
        }
        return clamp(next, 0, viewport.maxScroll());
    }

    /** 把一个滚动量夹进 {@code [0, maxScroll]}。 */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
