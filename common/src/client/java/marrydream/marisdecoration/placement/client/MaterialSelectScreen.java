package marrydream.marisdecoration.placement.client;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.adapter.AdapterSlot;
import marrydream.marisdecoration.placement.adapter.CopycatPlacementAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 材质选择界面：给某一个材质槽挑一个伪装方块。
 *
 * <h2>候选怎么定</h2>
 * 与放置路径用的是<b>同一套准入判据</b>——直接问对应 adapter 的
 * {@link CopycatPlacementAdapter#acceptsMaterial}，也就是 Create 的
 * {@code CopycatBlock#getAcceptedBlockState} / Copycats+ 的
 * {@code ICopycatBlock#getAcceptedBlockState} / 本 mod 两个方块自己的 {@code getAcceptedMaterial}。
 * 不在这里复刻规则（复刻就会两边不一致），也不维护任何硬编码黑白名单：
 * 界面里列出来的就是真的能用的，选不到的就是服务端也会拒绝的。
 *
 * <p>判据里的「轮廓必须是完整立方体、碰撞箱非空」写在 {@code world != null} <b>里面</b>，
 * 所以这里必须传<b>真实世界</b>（{@code MinecraftClient.world}）与玩家所在位置。
 * 传 null 或一个「到处是空气」的视图会整段跳过形状检查，半砖、玻璃板、门就会混进候选
 * ——这是实测踩到的缺陷。位置本身不影响判定（判据只看候选方块自己的形状）。
 *
 * <p>伪装方块自己不出现在候选里，理由也一样——它们本来就不能当材质。
 *
 * <h2>为什么单独一个屏幕</h2>
 * 原版没有现成的「选一个方块」控件。用独立屏幕而不是下拉框，是为了能复用搜索框 + 滚动网格，
 * 几百个方块也找得到。
 */
public class MaterialSelectScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 190;
    private static final int CELL = 18;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 12;
    private static final int COLUMNS = (PANEL_WIDTH - PADDING * 2) / CELL;

    private static final int COLOR_PANEL = 0xF0101010;
    private static final int COLOR_BORDER = 0xFF5A5A5A;
    private static final int COLOR_SLOT = 0xFF202020;
    private static final int COLOR_SLOT_HOVER = 0xFF3A5A8A;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909090;

    private final PlacerScreen parent;
    private final PlacerEditState state;
    private final String slotKey;

    private final List<Block> candidates = new ArrayList<>();
    /** 通过准入判据的方块（未按搜索词过滤），按注册名排序；见 {@link #rebuildCandidates}。 */
    private final List<Block> accepted = new ArrayList<>();
    private CopycatPlacementAdapter cachedAdapter;
    private String cachedSlotKey;
    private EditBox searchField;
    private int scroll;
    private int left;
    private int top;
    private int gridTop;
    private int gridHeight;

    /** 鼠标悬停 / 键盘选中用于显示名字。 */
    private Block hovered;

    public MaterialSelectScreen(PlacerScreen parent, PlacerEditState state, String slotKey) {
        super(Component.translatable("item.maris-decoration.copycat_placer.material.title"));
        this.parent = parent;
        this.state = state;
        this.slotKey = slotKey;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;

        searchField = new EditBox(font, left + PADDING, top + PADDING + LINE_HEIGHT,
                PANEL_WIDTH - PADDING * 2, LINE_HEIGHT + 4, Component.literal("search"));
        searchField.setMaxLength(64);
        searchField.setHint(Component.translatable("item.maris-decoration.copycat_placer.screen.search"));
        // 输入即时刷新：重建候选并让滚动回到顶部。
        // 候选只在「搜索词变化」时扫一遍注册表，不是每帧扫。
        searchField.setResponder(value -> {
            rebuildCandidates(value);
            scroll = 0;
        });
        addRenderableWidget(searchField);

        gridTop = top + PADDING + LINE_HEIGHT + searchField.getHeight() + PADDING;
        gridHeight = top + PANEL_HEIGHT - PADDING - LINE_HEIGHT - gridTop;

        rebuildCandidates("");
    }

    /**
     * 重建候选列表。
     *
     * <p>两步：
     * <ol>
     *   <li>只留下「当前 adapter 认为能当材质」的方块——判据由 adapter 提供
     *       （Create / Copycats+ 复用它们各自的 {@code getAcceptedBlockState}，本 mod 两个方块复用
     *       现有的 {@code getAcceptedMaterial}）。这样界面里列出来的就是真的用得上的，
     *       不需要在本类里维护任何 hardcoded 黑白名单；</li>
     *   <li>按搜索词过滤：显示名 / 完整注册名 / 注册名路径，大小写不敏感子串匹配。</li>
     * </ol>
     *
     * <p><b>第 1 步要跑一遍注册表并逐个查方块形状（轮廓 / 碰撞箱），所以按
     * (adapter, slotKey) 缓存</b>：准入结论只取决于这两个东西，与搜索词无关；
     * 不缓存的话每敲一个字符就要把一千多个方块重算一遍。
     *
     * <p>缓存的生命周期就是本屏幕实例（每次打开材质界面都是新的一个）。位置用玩家所在处，
     * 而判据只关心候选方块自身的形状，因此玩家在界面打开期间走动不会让结论失效。
     */
    private void rebuildCandidates(String filter) {
        candidates.clear();
        CopycatPlacementAdapter adapter = state.adapter();
        PlacementConfig config = state.config();
        if (adapter == null) {
            return;
        }
        if (adapter != cachedAdapter || !slotKey.equals(cachedSlotKey)) {
            cachedAdapter = adapter;
            cachedSlotKey = slotKey;
            accepted.clear();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block == net.minecraft.world.level.block.Blocks.AIR) {
                    continue;
                }
                Item item = block.asItem();
                if (item == null || item == Items.AIR || !(item instanceof BlockItem)) {
                    continue;
                }
                // 传真实世界：Create / Copycats+ 的判据都把「轮廓必须是完整立方体」放在
                // world != null 里面，传 null 或空视图会整段跳过形状检查，半砖、玻璃板就会混进来。
                // 位置用玩家所在处，判据只关心「这个方块自己的形状」，与具体坐标无关。
                if (!adapter.acceptsMaterial(block.defaultBlockState(), slotKey, config,
                        materialFilterView(), materialFilterPos())) {
                    continue;
                }
                accepted.add(block);
            }
            accepted.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        }
        for (Block block : accepted) {
            if (PlacerEditState.matches(block, filter)) {
                candidates.add(block);
            }
        }
    }

    /** 候选过滤用的世界视图：真实客户端世界；世界还没加载时才退回无上下文视图。 */
    private static net.minecraft.world.level.BlockGetter materialFilterView() {
        net.minecraft.client.multiplayer.ClientLevel world = Minecraft.getInstance().level;
        return world != null ? world : CopycatPlacementAdapter.NO_MATERIAL_CONTEXT;
    }

    /** 候选过滤用的位置：玩家所在处（判据只关心方块自身形状，坐标本身不影响结果）。 */
    private static net.minecraft.core.BlockPos materialFilterPos() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.blockPosition() : net.minecraft.core.BlockPos.ZERO;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 半透明压暗：不调用 super.renderBackground 是为了避开默认的模糊背景
        context.fill(0, 0, width, height, 0x80000000);

        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOR_BORDER);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL);

        context.drawString(font, title, left + PADDING, top + PADDING, COLOR_TEXT);
        // 当前正在编辑哪个槽：玩家关掉界面之前应该知道自己在改什么
        context.drawString(font, Component.literal(labelOfSlot()),
                left + PADDING + font.width(title) + 8, top + PADDING + 1, COLOR_DIM);

        hovered = null;
        context.enableScissor(left + PADDING, gridTop, left + PANEL_WIDTH - PADDING, gridTop + gridHeight);
        for (int i = 0; i < candidates.size(); i++) {
            int cellX = left + PADDING + (i % COLUMNS) * CELL;
            int cellY = gridTop + (i / COLUMNS) * CELL - scroll;
            if (cellY + CELL < gridTop || cellY > gridTop + gridHeight) {
                continue;
            }
            // 悬停必须落在网格里：被裁掉的那半格不该因为 y 落在行里就亮起来
            boolean isHovered = isInsideGrid(mouseX, mouseY)
                    && mouseX >= cellX && mouseX < cellX + CELL
                    && mouseY >= cellY && mouseY < cellY + CELL;
            context.fill(cellX, cellY, cellX + CELL - 1, cellY + CELL - 1,
                    isHovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
            context.renderItem(new ItemStack(candidates.get(i)), cellX + 1, cellY + 1);
            if (isHovered) {
                hovered = candidates.get(i);
            }
        }
        context.disableScissor();

        // 底部一行：悬停时显示完整注册名，否则显示提示与当前材质
        String hint;
        if (hovered != null) {
            hint = BuiltInRegistries.BLOCK.getKey(hovered) + " — " + hovered.getName().getString();
        } else {
            hint = Component.translatable("item.maris-decoration.copycat_placer.material.hint").getString()
                    + "  |  " + currentMaterialName();
        }
        context.drawString(font, hint, left + PADDING,
                top + PANEL_HEIGHT - PADDING - LINE_HEIGHT + 2, hovered != null ? COLOR_TEXT : COLOR_DIM);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 右键 = 清掉这个槽的材质
        if (button == 1 && isInsideGrid(mouseX, mouseY)) {
            Block block = blockAt(mouseX, mouseY);
            if (block != null) {
                state.update(state.config().withoutSlot(slotKey));
                onClose();
                return true;
            }
        }
        if (button != 0 || !isInsideGrid(mouseX, mouseY)) {
            return false;
        }
        Block block = blockAt(mouseX, mouseY);
        if (block == null) {
            return false;
        }
        // 材质存的是完整方块状态：伪装需要朝向 / 轴向等属性，默认状态对绝大多数方块就是对的，
        // 精细朝向留给「以后再做一个属性微调」的入口
        BlockState material = block.defaultBlockState();
        PlacementConfig config = state.config().withSlot(slotKey, material);
        state.update(config);
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int step = (int) (-amount * CELL * 2);
        if (step == 0) {
            step = amount > 0 ? CELL : -CELL;
        }
        scroll = Mth.clamp(scroll + step, 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        // 直接切回配置屏，而不是交给 super（super 会 setScreen(null) 退回游戏，
        // 那样玩家从材质选择回来就得重新开一遍界面）。
        // 先调父类完成「松开鼠标抓取」等收尾动作，再切屏幕。
        super.onClose();
        minecraft.setScreen(parent);
    }

    /**
     * Esc 走的是 {@code Screen#shouldCloseOnEsc} + {@code close()}，所以这里不用额外处理。
     * 显式写出来是为了说明「Esc 也会回到配置屏而不是退出整个界面」是有意的。
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private boolean isInsideGrid(double mouseX, double mouseY) {
        return mouseX >= left + PADDING && mouseX < left + PANEL_WIDTH - PADDING
                && mouseY >= gridTop && mouseY < gridTop + gridHeight;
    }

    private Block blockAt(double mouseX, double mouseY) {
        int column = (int) ((mouseX - left - PADDING) / CELL);
        int row = (int) ((mouseY - gridTop + scroll) / CELL);
        if (column < 0 || column >= COLUMNS || row < 0) {
            return null;
        }
        int index = row * COLUMNS + column;
        return index >= 0 && index < candidates.size() ? candidates.get(index) : null;
    }

    private int maxScroll() {
        int rows = (candidates.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows * CELL - gridHeight);
    }

    /**
     * 槽位键名对应的显示文本。
     *
     * <p>优先用翻译，翻译缺失时用 adapter 给的兜底文本；<b>两者都没有时退回小节的通用名，
     * 而不是把 slot key 本身画出来</b>——{@code copycats.multistate.east} 这类内部键名
     * 只应该出现在配置与 NBT 里。
     */
    private String labelOfSlot() {
        CopycatPlacementAdapter adapter = state.adapter();
        BlockState display = state.displayState();
        if (adapter != null && display != null) {
            for (AdapterSlot slot : adapter.slots(display, state.config())) {
                if (slot.key().equals(slotKey)) {
                    Component translated = Component.translatable(slot.labelKey());
                    String rendered = translated.getString();
                    return rendered.equals(slot.labelKey()) ? slot.labelText() : rendered;
                }
            }
        }
        return Component.translatable("item.maris-decoration.copycat_placer.screen.materials").getString();
    }

    /** 当前槽已经配了什么材质（显示在底部）。 */
    private String currentMaterialName() {
        CopycatPlacementAdapter adapter = state.adapter();
        BlockState display = state.displayState();
        if (adapter == null || display == null) {
            return "-";
        }
        for (AdapterSlot slot : adapter.slots(display, state.config())) {
            if (slot.key().equals(slotKey)) {
                return slot.hasMaterial() ? slot.material().getName().getString() : "-";
            }
        }
        return "-";
    }
}
