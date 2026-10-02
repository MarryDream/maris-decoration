package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.adapter.AdapterSlot;
import marrydream.marisdecoration.placement.adapter.CopycatPlacementAdapter;
import marrydream.marisdecoration.placement.adapter.PropertySpec;
import marrydream.marisdecoration.placement.adapter.VirtualSpec;
import marrydream.marisdecoration.placement.client.PlacerLayout.Content;
import marrydream.marisdecoration.placement.client.PlacerLayout.Geometry;
import marrydream.marisdecoration.placement.client.PlacerLayout.Viewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.ArrayList;
import java.util.List;

import static marrydream.marisdecoration.placement.client.PlacerLayout.ENTRY_HEIGHT;
import static marrydream.marisdecoration.placement.client.PlacerLayout.ICON_WIDTH;
import static marrydream.marisdecoration.placement.client.PlacerLayout.ID_SCALE;
import static marrydream.marisdecoration.placement.client.PlacerLayout.LINE_HEIGHT;
import static marrydream.marisdecoration.placement.client.PlacerLayout.PANEL_HEIGHT;
import static marrydream.marisdecoration.placement.client.PlacerLayout.PANEL_WIDTH;
import static marrydream.marisdecoration.placement.client.PlacerLayout.PADDING;
import static marrydream.marisdecoration.placement.client.PlacerLayout.SCROLLBAR_WIDTH;
import static marrydream.marisdecoration.placement.client.PlacerLayout.SEARCH_HEIGHT;
import static marrydream.marisdecoration.placement.client.PlacerLayout.clamp;
import static marrydream.marisdecoration.placement.client.PlacerLayout.thumbHeightFor;
import static marrydream.marisdecoration.placement.client.PlacerLayout.thumbY;


/**
 * 伪装放置器的配置界面。
 *
 * <h2>布局</h2>
 * 一块面板分成左右两栏（见 {@link #PANEL_WIDTH} 等常量）：
 * <pre>
 * ┌──────────────┬────────────────────────────┐
 * │ 搜索框        │  结构 / 属性                │
 * │ ┌──────────┐ │  ┌──────────────────────┐  │
 * │ │ 方块列表  │ │  │ 方块属性（可滚动）      │  │
 * │ │ 图标+名称 │ │  │ virtual property      │  │
 * │ │ 当前高亮  │ │  └──────────────────────┘  │
 * │ └──────────┘ │  材质                       │
 * │              │  ┌──────────────────────┐  │
 * │              │  │ 槽位名 + 当前材质      │  │
 * │              │  └──────────────────────┘  │
 * └──────────────┴────────────────────────────┘
 * </pre>
 *
 * <h2>这一层刻意不知道的东西</h2>
 * <ul>
 *   <li><b>不知道有哪些方块类型</b>：左栏列表直接来自 {@link PlacerEditState#blockList()}，
 *       它是「有 BlockItem + 被某个 adapter 认领」的注册表扫描结果；</li>
 *   <li><b>不知道某个方块有哪些属性</b>：属性行由方块自己的 {@code StateManager} 枚举出来，
 *       按 {@link PropertySpec} 的控件类型渲染；</li>
 *   <li><b>不知道什么是「分层薄板的占用」或「护栏的四向」</b>：那些是
 *       {@link VirtualSpec}，由 adapter 提供，这里只负责画一个循环按钮；</li>
 *   <li><b>不显示 WATERLOGGED</b>：它是放置时按实际水体决定的，不是玩家可配置项，
 *       在 {@link #visibleProperties} 里被过滤掉。</li>
 * </ul>
 *
 * <h2>滚动</h2>
 * 三个区域各自独立滚动（方块列表、属性区、材质区），鼠标悬停在哪个区域就滚哪个。
 * 内容用 {@code enableScissor} 裁切，条目坐标由 {@code scroll} 偏移。
 */
public class PlacerScreen extends Screen {

    // 布局尺寸全部来自 {@link PlacerLayout}：那是界面与自检共用的唯一一份几何来源。
    // 这里刻意不再定义任何尺寸常量——「同一个数字写两遍」正是上一版溢出的根因。

    private static final int COLOR_PANEL = 0xF0101010;
    private static final int COLOR_BORDER = 0xFF5A5A5A;
    private static final int COLOR_SECTION = 0xFF2A2A2A;
    private static final int COLOR_SELECTED = 0xFF3A5A8A;
    private static final int COLOR_HOVER = 0xFF303030;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909090;
    private static final int COLOR_VALUE = 0xFFFFD080;
    private static final int COLOR_SCROLL_TRACK = 0xFF1A1A1A;
    private static final int COLOR_SCROLL_THUMB = 0xFF7A7A7A;

    private final PlacerEditState state;

    private int left;
    private int top;

    /**
     * 三个可滚动区域的几何，以及整体布局快照。
     *
     * <p>它们全部来自 {@link PlacerLayout#compute}，「画在哪」「裁到哪」「点到哪」用的是
     * <b>同一份坐标</b>。之前三处各算一遍（render 一套、mouseClicked 一套、scrollbar 一套），
     * 结果材质区的下边界算错 12px、行画到了面板外，而裁剪框又是另一个值，谁都发现不了。
     */
    private Geometry geometry;
    private Viewport listViewport;
    private Viewport propertyViewport;
    private Viewport materialViewport;

    /** 三个区域各自的滚动偏移。 */
    private int listScroll;
    private int propertyScroll;
    private int materialScroll;

    private EditBox searchField;

    /** 每帧重建的可点击区域：属性行、virtual property 行、材质行。 */
    private final List<PropertyRow> propertyRows = new ArrayList<>();
    private final List<VirtualRow> virtualRows = new ArrayList<>();
    private final List<MaterialRow> materialRows = new ArrayList<>();

    public PlacerScreen(PlacerEditState state) {
        super(Component.translatable("item.maris-decoration.copycat_placer.screen.title"));
        this.state = state;
    }

    // ---------------------------------------------------------------- 视口

    // 视口类型与它的全部算法都在 {@link PlacerLayout.Viewport}（main 源集），
    // 这样无头服务端的自检能验证界面真正在用的那份几何。

    // ---------------------------------------------------------------- 行模型

    /** 行模型只存内容，坐标由 {@link Viewport} 现算——见 {@link #rebuildRows()} 的说明。 */
    private record PropertyRow(PropertySpec spec) {
    }

    private record VirtualRow(VirtualSpec spec) {
    }

    private record MaterialRow(String key, String labelKey, String labelText, boolean structure,
                               boolean hasMaterial, net.minecraft.world.level.block.Block material) {
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void init() {
        this.left = (width - PANEL_WIDTH) / 2;
        this.top = (height - PANEL_HEIGHT) / 2;

        // 布局先算一遍：搜索框的位置也来自同一份几何（左栏内容左缘、搜索框顶边）
        layoutViewports();

        // 左栏：主标题 → 搜索框 → 列表；搜索框是原版控件，其余自绘
        searchField = new EditBox(font, geometry.listX(), geometry.searchTop(),
                geometry.listWidth(), SEARCH_HEIGHT, Component.literal("search"));
        searchField.setMaxLength(64);
        searchField.setHint(Component.translatable("item.maris-decoration.copycat_placer.screen.search"));
        searchField.setValue(state.filter());
        // 输入即时刷新：改过滤词后重算过滤结果并让滚动回到顶部。
        // 过滤结果必须缓存——列表每帧都拿它渲染，不能每帧重扫注册表再逐条比名字。
        searchField.setResponder(value -> {
            state.setFilter(value);
            filteredCache = null;
            listScroll = 0;
            layoutViewports();
        });
        addRenderableWidget(searchField);

        filteredCache = null;
        layoutViewports();
        // 打开界面时把已选中的方块滚进视野（只在 init 与真正换方块时做，render 里不做）
        scrollToSelected();
    }

    // ---------------------------------------------------------------- 布局

    /**
     * 重算一整套几何。
     *
     * <p><b>这里是「一个区域占哪块屏幕」的唯一来源</b>：渲染、裁剪、点击命中、滚动条
     * 全部读同一组 {@link Viewport}。真正的算式在 {@link PlacerLayout#compute}（main 源集），
     * 自检验的就是那一份。
     */
    private void layoutViewports() {
        geometry = PlacerLayout.compute(width, height,
                new Content(filtered().size(), propertyRows.size() + virtualRows.size(),
                        materialRows.size()),
                listScroll, propertyScroll, materialScroll);
        // 回写夹紧后的滚动量：compute 会把 scroll 夹进 [0, maxScroll]，
        // 不写回来的话下一帧又拿着越界的旧值去算。
        listScroll = geometry.list().scroll();
        propertyScroll = geometry.property().scroll();
        materialScroll = geometry.material().scroll();
        listViewport = geometry.list();
        propertyViewport = geometry.property();
        materialViewport = geometry.material();
    }

    /** 材质小节标题所在行。 */
    private int materialTitleY() {
        return geometry == null ? 0 : geometry.materialTitleY();
    }

    /** 属性小节标题所在行。 */
    private int propertyTitleY() {
        return geometry == null ? 0 : geometry.propertyTitleY();
    }
    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 一帧开始：进入内容阶段。悬停提示只能在这一阶段「排队」，不会立刻画出来——
        // 见 render 末尾的 overlay 阶段，以及 PlacerOverlay 里那套可自检的阶段规则。
        overlay.beginContent();

        // 行表每帧重建：内容会随「选了哪个方块」「配置怎么改」随时变化，重建成本只有几十个
        // record 对象，比维护一套失效标记便宜得多，也不会出现忘记刷新
        rebuildRows();
        // 半透明压暗。不调用 super.renderBackground 是为了避开默认的模糊背景，
        // 这里需要看清背后的世界才知道自己在往哪放。
        context.fill(0, 0, width, height, 0x80000000);

        // 面板
        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOR_BORDER);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL);

        context.drawString(font, title, left + PADDING, top + PADDING, COLOR_TEXT);
        context.drawString(font,
                Component.translatable("item.maris-decoration.copycat_placer.screen.hint"),
                left + PADDING + font.width(title) + 8, top + PADDING + 1, COLOR_DIM);

        // 左栏没有小节标题：主标题下面直接是搜索框，再下面是列表
        renderBlockList(context, mouseX, mouseY);

        // 右栏两段的小节标题
        boolean hasAdapter = state.adapter() != null;
        context.fill(geometry.rightX(), propertyTitleY() - 1, geometry.rightX() + geometry.rightWidth(),
                propertyTitleY() - 1 + LINE_HEIGHT - 1, COLOR_SECTION);
        context.drawString(font,
                Component.translatable("item.maris-decoration.copycat_placer.screen.structure"),
                geometry.rightX() + 2, propertyTitleY() + 1, COLOR_TEXT);

        context.fill(geometry.rightX(), materialTitleY() - 1, geometry.rightX() + geometry.rightWidth(),
                materialTitleY() - 1 + LINE_HEIGHT - 1, COLOR_SECTION);
        context.drawString(font,
                Component.translatable("item.maris-decoration.copycat_placer.screen.materials"),
                geometry.rightX() + 2, materialTitleY() + 1, COLOR_TEXT);

        if (hasAdapter) {
            renderRows(context, mouseX, mouseY, propertyRows, virtualRows, propertyViewport);
            renderMaterialRows(context, mouseX, mouseY);
        } else {
            context.drawString(font,
                    Component.translatable("item.maris-decoration.copycat_placer.screen.no_selection"),
                    geometry.rightX() + 4, propertyViewport.y() + 4, COLOR_DIM);
        }

        // 滚动条画在内容之外（内容已经 disableScissor 了）；内容不足一屏时 drawScrollbar 自己跳过
        drawScrollbar(context, listViewport);
        if (hasAdapter) {
            drawScrollbar(context, materialViewport);
        }

        // 搜索框交给原版控件系统画
        super.render(context, mouseX, mouseY, delta);

        // ---- overlay 阶段：内容全部画完之后，才画悬停提示。
        //
        // 这里是整个 render 的最后一步，并且此时所有 scissor 都已经关闭
        // （每个内容方法自己成对 enable / disable，overlay 之前没有任何一个还开着）。
        // 提示框因此是整帧最上层：不会被材质区、滚动条、搜索框盖住，也不受左右栏视口裁剪。
        // 位置避让屏幕边缘由原版的 HoveredTooltipPositioner 负责。
        overlay.beginOverlay();
        for (TooltipRequest request : overlay.takeForOverlay()) {
            context.renderComponentTooltip(font, request.lines(), mouseX, mouseY);
        }
        overlay.endFrame();
    }

    /**
     * 一条排队等待 overlay 阶段绘制的提示。
     *
     * <p>文字在内容阶段就已经解析好（{@link StructureTooltip#resolve} + 过滤空白行），
     * 排队时只带「已经能画的东西」，排空了的请求根本不会入队。
     */
    private record TooltipRequest(List<Component> lines) {
    }

    /** 本帧的 overlay 队列。规则见 {@link PlacerOverlay}，自检里逐条验过。 */
    private final PlacerOverlay<TooltipRequest> overlay = new PlacerOverlay<>();

    // ---- 左栏：方块列表
    private void renderBlockList(GuiGraphics context, int mouseX, int mouseY) {
        List<PlacerEditState.Entry> entries = filtered();
        Viewport viewport = listViewport;
        int x = viewport.x();
        int w = viewport.w();

        context.fill(x - 1, viewport.y() - 1, x + w + 1, viewport.bottom() + 1, COLOR_SECTION);
        // 裁剪框 = 视口矩形本身（横向也不放宽）。三个区域用的是同一个规矩：
        // 「画出来的东西」与「点得到的东西」是同一个矩形，不存在差 1px 的第三份坐标。
        context.enableScissor(x, viewport.y(), viewport.right(), viewport.bottom());

        for (int i = 0; i < entries.size(); i++) {
            if (!viewport.rowVisible(i, ENTRY_HEIGHT)) {
                continue;
            }
            PlacerEditState.Entry entry = entries.get(i);
            int y = viewport.rowY(i, ENTRY_HEIGHT);
            boolean selected = state.selectedBlock() == entry.block();
            boolean hovered = viewport.contains(mouseX, mouseY)
                    && viewport.rowHovered(mouseY, i, ENTRY_HEIGHT);
            if (selected) {
                context.fill(x, y, x + w, y + ENTRY_HEIGHT - 1, COLOR_SELECTED);
            } else if (hovered) {
                context.fill(x, y, x + w, y + ENTRY_HEIGHT - 1, COLOR_HOVER);
            }

            context.renderItem(new ItemStack(entry.block()), x + 1, y + 1);
            // 第一行：当前语言的显示名（正常字号）
            context.drawString(font,
                    fit(entry.block().getName().getString(), w - ICON_WIDTH),
                    x + ICON_WIDTH, y + 2, COLOR_TEXT);
            // 第二行：注册名，0.5 倍字号。
            // 原样的注册名（maris-decoration:layered_copycat_board）在 140px 宽的列表里
            // 连一半都放不下，缩小之后才有一半左右能看全，放不下的仍按宽度省略。
            drawScaledText(context, entry.id().toString(),
                    x + ICON_WIDTH, y + 11, w - ICON_WIDTH, COLOR_DIM, ID_SCALE);
        }
        if (entries.isEmpty()) {
            context.drawString(font,
                    Component.translatable("item.maris-decoration.copycat_placer.screen.no_match"),
                    x + 2, viewport.y() + 2, COLOR_DIM);
        }
        context.disableScissor();
        // 悬停时把完整注册名显示出来——列表里那行是省略过的。
        // 只是「排队」：真正的绘制统一发生在整帧末尾的 overlay 阶段（见 render 的注释），
        // 否则后画的右栏内容会盖住提示框。
        PlacerEditState.Entry hoveredEntry = hoveredEntry(mouseX, mouseY);
        if (hoveredEntry != null) {
            queueTooltip(List.of(Component.literal(hoveredEntry.id().toString())));
        }
    }

    // ---- virtual property 的显示映射

    /** 结构项标题里各段之间的分隔符（与材质槽的写法一致，例如「底面·外层·左下角」）。 */
    private static final String LABEL_SEPARATOR = "·";

    /**
     * 一行结构项的标题：把它的若干段文本逐个解析（翻译优先、兜底文本其次）再拼起来。
     *
     * <p>分隔符是 {@link #LABEL_SEPARATOR}：标题描述的是「这一项是什么」
     * （面 / 层 / 角 / 交点），不是「有哪些候选」——候选来源在悬停提示里，见
     * {@link StructureTooltip}。
     *
     * <p><b>绝不</b>使用 {@link VirtualSpec#key()} 或 {@link VirtualSpec.Option#id()}：
     * 那是写进配置 / NBT 的内部标识（{@code occupancy}、{@code junction.2:0+…}），
     * 界面上永远不显示。
     */
    private String virtualLabelText(VirtualSpec spec) {
        return joinParts(spec.label(), LABEL_SEPARATOR);
    }

    /**
     * 一行结构项的当前取值（纯文本）。
     *
     * <p>取值同样只画候选项自己的文本。<b>这里是「配置里的内部值 → 可读文本」的唯一映射点。</b>
     * 找不到匹配的候选项（旧配置里存了一个当前候选集合里没有的值）时显示「配置值无效」，
     * 而不是把内部值 / 掩码 / 槽位码画出来；玩家下一次点击时
     * {@link VirtualSpec#next} 会把它归一化到第一个候选项，服务端的 {@code validateStructure}
     * 继续兜底。
     */
    private String virtualValueText(VirtualSpec spec, PlacementConfig config) {
        VirtualSpec.Option current = spec.current(config);
        if (current == null) {
            return Component.translatable("item.maris-decoration.copycat_placer.screen.value_unknown").getString();
        }
        return joinParts(current.label(), LABEL_SEPARATOR);
    }

    /**
     * 把若干段「翻译键 + 兜底文本」解析成一段纯文本。
     *
     * <p>刻意返回 {@code String} 而不是拼一个 {@code MutableText}：界面上的宽度测量与绘制必须
     * 落在<b>同一份内容</b>上。原版里 {@code MutableText.asOrderedText()} 会把重排结果缓存起来，
     * 而 {@code append} 不会让缓存失效——「先量宽度、后追加内容」就会得到「框按新宽度画、
     * 字形还是旧的」这种东西。直接给字符串，这条路根本不存在。
     */
    private String joinParts(List<VirtualSpec.LabelPart> parts, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            out.append(StructureTooltip.resolve(parts.get(i)));
        }
        return out.toString();
    }

    /**
     * 按缩放画一行文字。
     *
     * <p>缩放靠矩阵变换实现：先把原点平移到文字左上角，再缩放，之后在局部坐标 (0,0) 画字。
     * 这样文字的起点不受缩放影响，不会出现「缩小之后整行往左上角跑」。
     */
    private void drawScaledText(GuiGraphics context, String text, int x, int y, int maxWidth,
                                int color, float scale) {
        String shown = fit(text, (int) (maxWidth / scale));
        context.pose().pushPose();
        context.pose().translate(x, y, 0);
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(font, shown, 0, 0, color);
        context.pose().popPose();
    }

    /** 当前鼠标悬停的列表条目；没有则返回 {@code null}。 */
    private PlacerEditState.Entry hoveredEntry(int mouseX, int mouseY) {
        if (listViewport == null) {
            return null;
        }
        int index = listViewport.rowIndexAt(mouseX, mouseY, ENTRY_HEIGHT);
        List<PlacerEditState.Entry> entries = filtered();
        return index >= 0 && index < entries.size() && listViewport.rowVisible(index, ENTRY_HEIGHT)
                ? entries.get(index)
                : null;
    }

    /**
     * 过滤结果缓存。
     *
     * <p>列表每帧都要拿它渲染，而过滤要逐条比「显示名 + 注册名 + 路径」，直接每帧重算会在
     * 方块多的时候明显拖慢。只在「搜索词变了」「换方块了」时置空重算。
     */
    private List<PlacerEditState.Entry> filteredCache;

    private List<PlacerEditState.Entry> filtered() {
        if (filteredCache == null) {
            filteredCache = state.filteredBlocks();
        }
        return filteredCache;
    }

    // ---- 右栏上：结构 / 属性

    /**
     * 画「结构 / 属性」区的所有行（方块自己的属性 + adapter 的 virtual property）。
     *
     * <p>关键点：行的 y 全部由 {@link Viewport#rowY} 派生，可见性由
     * {@link Viewport#rowVisible} 判断，裁剪框由视口给出——三者同源，不可能各算一套。
     *
     * <p>virtual property 的<b>取值显示</b>走 {@link #virtualValueLabel}：它把「配置里的原始
     * 整数」映射成当前 Option 的可读标签。界面上任何时候都不允许出现原始的 structure key 或十六进制掩码。
     */
    private void renderRows(GuiGraphics context, int mouseX, int mouseY,
                            List<PropertyRow> rows, List<VirtualRow> virtuals, Viewport viewport) {
        BlockState currentState = this.state.state();
        BlockState display = this.state.displayState();
        if (currentState == null || display == null) {
            return;
        }
        int x = viewport.x();
        int width = viewport.w();

        context.enableScissor(x, viewport.y(), viewport.right(), viewport.bottom());

        for (int i = 0; i < rows.size(); i++) {
            PropertyRow row = rows.get(i);
            if (!viewport.rowVisible(i, LINE_HEIGHT)) {
                continue;
            }
            int y = viewport.rowY(i, LINE_HEIGHT);
            boolean hovered = viewport.contains(mouseX, mouseY) && viewport.rowHovered(mouseY, i, LINE_HEIGHT);
            context.fill(x, y, x + width, y + LINE_HEIGHT - 1, hovered ? COLOR_HOVER : 0x00000000);
            context.drawString(font, row.spec().displayLabel(), x + 2, y + 2, COLOR_TEXT);
            Component value = row.spec().displayValue(display);
            context.drawString(font, value,
                    x + width - 4 - font.width(value), y + 2, COLOR_VALUE);
        }

        int offset = rows.size();
        VirtualSpec hoveredSpec = null;
        boolean hoveredLabelTruncated = false;
        boolean hoveredValueTruncated = false;
        for (int i = 0; i < virtuals.size(); i++) {
            VirtualRow row = virtuals.get(i);
            int index = offset + i;
            if (!viewport.rowVisible(index, LINE_HEIGHT)) {
                continue;
            }
            int y = viewport.rowY(index, LINE_HEIGHT);
            boolean hovered = viewport.contains(mouseX, mouseY)
                    && viewport.rowHovered(mouseY, index, LINE_HEIGHT);
            context.fill(x, y, x + width, y + LINE_HEIGHT - 1, hovered ? COLOR_HOVER : 0x00000000);

            // 取值靠右对齐，标题占剩下的宽度。两者都可能放不下，所以两边都过 fit，
            // 并且**明确记下有没有真的发生截断**——悬停提示只在这两个标志为真、
            // 或者这一项自己有候选来源时才出现（见 StructureTooltip）。
            String fullValue = virtualValueText(row.spec(), this.state.config());
            String shownValue = fit(fullValue, Math.max(8, width - 8 - 12));
            int valueWidth = font.width(shownValue);
            boolean valueTruncated = !shownValue.equals(fullValue);

            String fullLabel = virtualLabelText(row.spec());
            String shownLabel = fit(fullLabel, Math.max(8, width - 8 - valueWidth));
            boolean labelTruncated = !shownLabel.equals(fullLabel);

            context.drawString(font, Component.literal(shownLabel), x + 2, y + 2, COLOR_TEXT);
            context.drawString(font, Component.literal(shownValue),
                    x + width - 4 - valueWidth, y + 2, COLOR_VALUE);
            if (hovered) {
                hoveredSpec = row.spec();
                hoveredLabelTruncated = labelTruncated;
                hoveredValueTruncated = valueTruncated;
            }
        }

        context.disableScissor();

        // 悬停提示放在裁剪之外：它要能盖住面板边缘，而且里面给的是完整信息。
        // 只有「真的被截断」或「这一项自己有多条候选来源（顶点材质归属）」才排队；
        // 没有可说的东西就一个框都不画——普通开关项（下面外层 / 东面内层 / 下面开窗…）走的就是这一支。
        // 排队而不是立刻画：绘制在整帧末尾的 overlay 阶段，见 render 的注释。
        if (hoveredSpec != null) {
            queueStructureTooltip(hoveredSpec, hoveredLabelTruncated, hoveredValueTruncated);
        }
    }

    /**
     * 结构项的悬停提示：内容规则在 {@link StructureTooltip}，这里只负责把「能画的东西」排进 overlay 队列。
     *
     * <p>两道闸都必须过：{@link StructureTooltip#lines} 给出「这一项有什么可说的」，
     * {@link StructureTooltip#shouldDraw} 再逐行解析一次文本；<b>空列表或全空白一律不入队</b>——
     * 原版只要调用 {@code drawTooltip} 就会把紫黑色的提示框画出来，
     * 于是「没什么可说的行」也会弹一个空框。
     *
     * <p>提示文本一律由解析好的字符串重新构造（{@code Text.literal}），不用
     * {@code Text.empty()} 拼——理由见 {@link #joinParts} 的注释。
     */
    private void queueStructureTooltip(VirtualSpec spec, boolean labelTruncated, boolean valueTruncated) {
        List<StructureTooltip.Line> lines = StructureTooltip.lines(
                spec, this.state.config(), labelTruncated, valueTruncated);
        if (!StructureTooltip.shouldDraw(lines)) {
            return;
        }
        List<Component> rendered = new ArrayList<>(lines.size());
        for (StructureTooltip.Line line : lines) {
            String text = line.resolve();
            if (!text.isBlank()) {
                rendered.add(Component.literal(text));
            }
        }
        if (rendered.isEmpty()) {
            return;
        }
        queueTooltip(rendered);
    }

    /**
     * 把一段已经解析好的提示排进 overlay 队列。
     *
     * <p>内容阶段之外调用会失败（返回 false）——那是代码放错阶段的信号，
     * 由 {@link PlacerOverlay} 的阶段规则挡住，自检里也验了这条。
     */
    private void queueTooltip(List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        overlay.queue(new TooltipRequest(List.copyOf(lines)));
    }

    // ---- 右栏下：材质

    private void renderMaterialRows(GuiGraphics context, int mouseX, int mouseY) {
        Viewport viewport = materialViewport;
        int x = viewport.x();
        int width = viewport.w();

        context.enableScissor(x, viewport.y(), viewport.right(), viewport.bottom());

        for (int i = 0; i < materialRows.size(); i++) {
            MaterialRow row = materialRows.get(i);
            if (!viewport.rowVisible(i, LINE_HEIGHT)) {
                continue;
            }
            int y = viewport.rowY(i, LINE_HEIGHT);
            boolean hovered = viewport.contains(mouseX, mouseY) && viewport.rowHovered(mouseY, i, LINE_HEIGHT);
            context.fill(x, y, x + width, y + LINE_HEIGHT - 1, hovered ? COLOR_HOVER : 0x00000000);
            // 结构不存在的槽压暗显示：仍然可以预先配材质，但要让玩家知道现在放出来看不见
            int labelColor = row.structure() ? COLOR_TEXT : COLOR_DIM;
            context.drawString(font, labelOf(row.labelKey(), row.labelText()),
                    x + 2, y + 2, labelColor);
            Component value = row.hasMaterial()
                    ? row.material().getName()
                    : Component.translatable("item.maris-decoration.copycat_placer.screen.material_unset");
            context.drawString(font, value,
                    x + width - 4 - font.width(value), y + 2,
                    row.hasMaterial() ? COLOR_VALUE : COLOR_DIM);
        }
        if (materialRows.isEmpty()) {
            context.drawString(font,
                    Component.translatable("item.maris-decoration.copycat_placer.screen.no_slots"),
                    x + 2, viewport.y() + 2, COLOR_DIM);
        }

        context.disableScissor();
    }

    // ---------------------------------------------------------------- 每帧重建行表 + 事件

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        // 滚动条优先：thumb 与列表内容可能重叠，必须抢在选方块之前处理
        if (beginScrollbarDrag(mouseX, mouseY)) {
            return true;
        }

        // 左栏：选方块。命中判定完全交给视口——滚出可见范围的行不可能被点到。
        if (listViewport.contains(mouseX, mouseY)) {
            List<PlacerEditState.Entry> entries = filtered();
            int index = listViewport.rowIndexAt(mouseX, mouseY, ENTRY_HEIGHT);
            if (index >= 0 && index < entries.size() && listViewport.rowVisible(index, ENTRY_HEIGHT)) {
                state.selectBlock(entries.get(index).block());
                propertyScroll = 0;
                materialScroll = 0;
                layoutViewports();
                // 换方块之后过滤结果不变，但槽位/属性全变了，行表下次 render 会重建
                scrollToSelected();
            }
            return true;
        }

        // 右栏上：属性
        if (propertyViewport.contains(mouseX, mouseY)) {
            return clickProperty(mouseX, mouseY);
        }

        // 右栏下：材质
        if (materialViewport.contains(mouseX, mouseY)) {
            return clickMaterial(mouseX, mouseY);
        }
        return false;
    }

    /** 点属性行：布尔直接取反，其余切下一个取值。 */
    private boolean clickProperty(double mouseX, double mouseY) {
        Viewport viewport = propertyViewport;
        int index = viewport.rowIndexAt(mouseX, mouseY, LINE_HEIGHT);
        if (index < 0 || !viewport.rowVisible(index, LINE_HEIGHT)) {
            return false;
        }
        BlockState currentState = this.state.state();
        BlockState display = this.state.displayState();
        if (currentState == null || display == null) {
            return false;
        }

        if (index < propertyRows.size()) {
            PropertySpec spec = propertyRows.get(index).spec();
            Property<?> property = spec.property();
            if (property instanceof BooleanProperty booleanProperty) {
                boolean current = display.getValue(booleanProperty);
                this.state.update(this.state.config().withState(currentState.setValue(booleanProperty, !current)));
            } else {
                Object next = spec.nextValue(display);
                if (next != null) {
                    this.state.update(this.state.config()
                            .withState(withValue(currentState, property, next)));
                }
            }
            return true;
        }

        int virtualIndex = index - propertyRows.size();
        if (virtualIndex >= 0 && virtualIndex < virtualRows.size()) {
            VirtualSpec spec = virtualRows.get(virtualIndex).spec();
            // 取值切换：当前值不在候选里时 next() 会归一化到第一个候选，
            // 所以一份坏配置点一下就能回到合法状态。
            this.state.update(spec.with(this.state.config(), spec.next(this.state.config())));
            return true;
        }
        return false;
    }

    /** 把属性的原始取值写进状态。泛型由调用方保证（{@code next} 来自同一个属性）。 */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property<T>) property, (T) value);
    }

    private boolean hit(double mouseY, int rowY) {
        return mouseY >= rowY && mouseY < rowY + LINE_HEIGHT - 1;
    }

    /** 点材质行：打开材质选择界面。 */
    private boolean clickMaterial(double mouseX, double mouseY) {
        Viewport viewport = materialViewport;
        int index = viewport.rowIndexAt(mouseX, mouseY, LINE_HEIGHT);
        if (index < 0 || index >= materialRows.size() || !viewport.rowVisible(index, LINE_HEIGHT)) {
            return false;
        }
        Minecraft.getInstance().setScreen(
                new MaterialSelectScreen(this, state, materialRows.get(index).key()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int step = (int) (-amount * LINE_HEIGHT * 2);
        if (step == 0) {
            step = amount > 0 ? LINE_HEIGHT : -LINE_HEIGHT;
        }

        if (listViewport.contains(mouseX, mouseY)) {
            listScroll = clamp(listScroll + step, 0, listViewport.maxScroll());
            layoutViewports();
            return true;
        }
        if (propertyViewport.contains(mouseX, mouseY)) {
            propertyScroll = clamp(propertyScroll + step, 0, propertyViewport.maxScroll());
            layoutViewports();
            return true;
        }
        if (materialViewport.contains(mouseX, mouseY)) {
            materialScroll = clamp(materialScroll + step, 0, materialViewport.maxScroll());
            layoutViewports();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 行表构建

    /**
     * 每帧重建右栏行表。
     *
     * <p>放在 render 开头而不是 init：行内容会随「选了哪个方块」「配置怎么改」随时变化，
     * 重建成本是几十个 record 对象，比维护一套失效标记便宜得多，也不会出现忘记刷新。
     */
    private void rebuildRows() {
        propertyRows.clear();
        virtualRows.clear();
        materialRows.clear();

        CopycatPlacementAdapter adapter = state.adapter();
        BlockState display = state.displayState();
        if (adapter == null || display == null) {
            layoutViewports();
            return;
        }

        for (PropertySpec spec : visibleProperties(display)) {
            propertyRows.add(new PropertyRow(spec));
        }
        PlacementConfig config = state.config();
        // 结构项由 adapter 按当前配置算出来：它只返回此刻真正存在的那几项
        // （角柱 / 开窗 / 交汇点的动态展开全在那一层，GUI 不认识这些规则）。
        for (VirtualSpec spec : adapter.virtualSpecs(config)) {
            virtualRows.add(new VirtualRow(spec));
        }

        for (AdapterSlot slot : adapter.slots(display, config)) {
            // 材质区一律「全量显示 + 无效项置灰」，与本 GUI 里 Copycats+ 伪装薄板（copycat_board）
            // 的展示方式一致：{@code structure} 为真的槽正常显示，为假的槽仍然列出来、只是压暗，
            // 并且照样能点进去预先配材质（点击逻辑不区分有效 / 无效）。
            // 放置语义不受影响：真正生效的仍然只有有效槽（见 PlacementService 的预演与 adapter.apply）。
            materialRows.add(new MaterialRow(slot.key(), slot.labelKey(), slot.labelText(),
                    slot.structure(), slot.hasMaterial(), slot.material()));
        }

        // 内容变了，视口的内容高度与裁剪框跟着重算
        layoutViewports();
    }

    /**
     * 一个槽位/分组的显示名：有翻译就用翻译，没有就用适配器给的兜底文本。
     *
     * <p>为什么需要兜底：Copycats+ 的 multistate 槽位由方块自己在运行时声明，属性名
     * 不可能在语言文件里穷举。没有兜底的话界面会显示一串 {@code copycats.multistate.up}，
     * 玩家完全看不懂。
     */
    private Component labelOf(String labelKey, String labelText) {
        Component translated = Component.translatable(labelKey);
        String rendered = translated.getString();
        // 原版在找不到翻译时会把 key 原样返回，据此判断
        return rendered.equals(labelKey) && labelText != null && !labelText.isBlank()
                ? Component.literal(labelText)
                : translated;
    }

    /**
     * 界面上要显示的属性：方块自己的属性减去两类不该让玩家配的东西。
     *
     * <ol>
     *   <li><b>WATERLOGGED</b>——含水由实际放置位置的水体决定（见 {@code PlacementService}），
     *       是运行时结果不是预设项；</li>
     *   <li><b>POWERED</b>——红石充能由世界里的红石信号决定。允许预设它只会造出
     *       「配置里写着已充能、放下去立刻被红石改掉」这种自相矛盾的状态。
     *       注意只过滤「预设入口」：世界里红石照常能改这个属性。</li>
     *   <li><b>已经被 virtual property 接管的属性</b>——例如护栏的 north/east/south/west。
     *       同一个结构绝不能有两套编辑入口，否则会出现「改了布尔属性又被 virtual 覆盖掉」。
     *       由 adapter 通过 {@link VirtualSpec#managedProperties()} 声明。</li>
     * </ol>
     */
    private List<PropertySpec> visibleProperties(BlockState display) {
        CopycatPlacementAdapter adapter = state.adapter();
        java.util.Set<String> managed = new java.util.HashSet<>();
        if (adapter != null) {
            managed.addAll(adapter.hiddenProperties(state.config()));
            for (VirtualSpec spec : adapter.virtualSpecs(state.config())) {
                managed.addAll(spec.managedProperties());
            }
        }

        List<PropertySpec> specs = new ArrayList<>();
        for (Property<?> property : display.getProperties()) {
            if (property == net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
                    || property == net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) {
                continue;
            }
            if (managed.contains(property.getName())) {
                continue;
            }
            specs.add(PropertySpec.of(property));
        }
        return specs;
    }

    // ---------------------------------------------------------------- 自动滚动

    /**
     * 把当前选中的方块滚进视野。
     *
     * <p>只在 {@code init()} 与「玩家真的换了方块」时调用，<b>不在 render 里调用</b>——
     * 每帧定位会让玩家根本没法自由滚动列表。
     */
    private void scrollToSelected() {
        Block selected = state.selectedBlock();
        if (selected == null || listViewport == null || listViewport.h() <= 0) {
            return;
        }
        List<PlacerEditState.Entry> entries = filtered();
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).block() == selected) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int entryTop = index * ENTRY_HEIGHT;
        // 滚动量与夹紧都在 PlacerLayout 里（自检验的就是那一份）
        listScroll = PlacerLayout.scrollToShowRow(listScroll, listViewport, entryTop, ENTRY_HEIGHT);
        layoutViewports();
    }

    // ---------------------------------------------------------------- 滚动条

    private boolean draggingScrollbar;
    /** 拖动起点：鼠标 Y 与当时的 scroll 值，用来算增量而不是绝对定位。 */
    private int dragStartY;
    private int dragStartScroll;

    /** 画一根滚动条。内容不足一屏时整根不画（视口自己知道）。 */
    private void drawScrollbar(GuiGraphics context, Viewport viewport) {
        if (!viewport.hasScrollbar()) {
            return;
        }
        int x = PlacerLayout.scrollbarX(viewport);
        int thumbHeight = thumbHeightFor(viewport.h(), viewport.contentHeight());
        int thumbTop = thumbY(viewport);

        context.fill(x, viewport.y(), x + SCROLLBAR_WIDTH, viewport.bottom(), COLOR_SCROLL_TRACK);
        context.fill(x, thumbTop, x + SCROLLBAR_WIDTH, thumbTop + thumbHeight, COLOR_SCROLL_THUMB);
    }

    /**
     * 鼠标按下时判断是不是点在某个滚动条上，是就开始拖动。
     *
     * <p>点在轨道（而不只是 thumb）上也接受，仍然按「拖动增量」换算滚动量——
     * 绝对定位会让人一按就把内容跳到别处，手感很差。
     *
     * @return 是否吃掉了这次点击
     */
    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        if (PlacerLayout.inScrollbar(mouseX, mouseY, listViewport)) {
            draggingScrollbar = true;
            dragTarget = DragTarget.LIST;
            dragStartY = (int) mouseY;
            dragStartScroll = listScroll;
            return true;
        }
        if (PlacerLayout.inScrollbar(mouseX, mouseY, materialViewport)) {
            draggingScrollbar = true;
            dragTarget = DragTarget.MATERIAL;
            dragStartY = (int) mouseY;
            dragStartScroll = materialScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0 && dragTarget != DragTarget.NONE) {
            Viewport viewport = dragTarget == DragTarget.LIST ? listViewport : materialViewport;
            // thumb 行程 → 滚动量的换算在 PlacerLayout 里（自检验的就是那一份）
            int next = PlacerLayout.scrollForDrag(viewport, dragStartScroll, dragStartY, (int) mouseY);
            if (dragTarget == DragTarget.LIST) {
                listScroll = next;
            } else {
                materialScroll = next;
            }
            layoutViewports();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /** 正在拖哪一根滚动条。 */
    private enum DragTarget {
        NONE, LIST, MATERIAL
    }

    private DragTarget dragTarget = DragTarget.NONE;

    // ---------------------------------------------------------------- 文本

    /** 文本按像素宽度截断，超长补省略号——列表里方块名可能很长。 */
    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int limit = maxWidth - font.width(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(builder.toString() + c) > limit) {
                break;
            }
            builder.append(c);
        }
        return builder.append(ellipsis).toString();
    }
}
