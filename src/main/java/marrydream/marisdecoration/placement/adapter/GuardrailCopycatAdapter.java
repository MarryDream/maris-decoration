package marrydream.marisdecoration.placement.adapter;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 伪装护栏（{@code maris-decoration:copycat_guardrail}）的 adapter。
 *
 * <h2>结构属性</h2>
 * 护栏的「形态」是四个方向位的集合，住在方块状态的四个 {@code BooleanProperty}
 * （{@code north/east/south/west}）里，所以 {@link #stateFrom} 是真正在干活的：
 * 它按 {@link PlacementConfig.Key#GUARDRAIL_FACES} 这个 4 位掩码把四个方向置位。
 *
 * <p>掩码的位定义<b>直接复用方块自己的</b> {@link CopycatGuardrailBlock#bit(Direction)}，
 * 不另立一套编号——两套编号迟早会分叉。为了让 GUI 侧写预设的人不必记方向位，
 * {@link #facesMask} 也提供了反解。
 *
 * <h2>材质槽</h2>
 * 键名全部来自 {@link GuardrailParts}（横梁 {@code north_row} 等四个、柱子 {@code 0_0} 等四个），
 * 与方块实体、渲染层用的是同一份常量，不可能写错。
 *
 * <p>「这个槽当前存不存在」用 {@link GuardrailParts#visibleKeys(BlockState)} 回答：
 * 那是<b>真实几何</b>（共享柱只在归属方向缺席时才由相邻方向补画），所以预设里给一个
 * 当前看不见的柱槽写了材质也不会白扣钱——{@link #apply} 只处理可见的槽。
 *
 * <h2>付账语义：同一 BlockPos 同一种材质只付一次</h2>
 * 与分层伪装薄板一致，也是放置器的统一规则。注意这与「右键逐部件独立付账」<b>不是</b>同一套
 * 语义，那是护栏原来那套交互路径，本轮完全没有改动它——放置器只是另一个入口。
 * 去重靠 {@link PlacementContext#isPaid}，本次放置里同一种方块只会在第一处付账，
 * 而账本由方块实体自己的 {@code consumedItems} 记录，拆除时照旧按槽返还。
 */
public final class GuardrailCopycatAdapter implements CopycatPlacementAdapter {

    /** 结构属性：四个方向的 4 位掩码。 */
    public static final String STRUCTURE_FACES = PlacementConfig.Key.GUARDRAIL_FACES;

    /** 没配结构属性时的默认朝向：正北（掩码就是 NORTH 的位）。 */
    public static final int DEFAULT_FACES = CopycatGuardrailBlock.bit(Direction.NORTH);

    private static final String LABEL_PREFIX = "maris-decoration.copycat_placer.slot.guardrail.";
    private static final String GROUP_PREFIX = "maris-decoration.copycat_placer.group.guardrail.";
    /** 横梁一组、柱子一组，GUI 折叠成两个节点。 */
    private static final String GROUP_ROW = GROUP_PREFIX + "row";
    private static final String GROUP_COLUMN = GROUP_PREFIX + "column";

    @Override
    public boolean supports(Block block) {
        return block instanceof CopycatGuardrailBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_OWN_CUSTOM;
    }

    @Override
    public String name() {
        return "maris-decoration:copycat_guardrail";
    }

    // ---------------------------------------------------------------- 结构

    /** 预设里的方向掩码（0..15）。没配或配坏时是 {@link #DEFAULT_FACES}。 */
    public static int facesMask(PlacementConfig config) {
        return StructureMasks.read(config, STRUCTURE_FACES, 4, DEFAULT_FACES);
    }

    /** 写回方向掩码。 */
    public static PlacementConfig withFaces(PlacementConfig config, int mask) {
        return config.withStructure(STRUCTURE_FACES, StructureMasks.write(mask & 0xF));
    }

    /** 掩码里选中的方向，顺序固定为北、东、南、西（与 {@link CopycatGuardrailBlock#FACES} 一致）。 */
    public static List<Direction> faces(int mask) {
        List<Direction> out = new ArrayList<>(4);
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            if (CopycatGuardrailBlock.maskHas(mask, dir)) {
                out.add(dir);
            }
        }
        return out;
    }

    @Override
    public BlockState stateFrom(BlockState state, PlacementConfig config) {
        if (!(state.getBlock() instanceof CopycatGuardrailBlock)) {
            return state;
        }
        int mask = facesMask(config);
        // 从「四面全 false」出发，再把掩码选中的置位——与方块自己的 getPlacementState 同一套做法，
        // 避免默认状态里预置的方向混进来。
        BlockState result = state;
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            result = result.with(CopycatGuardrailBlock.PROPERTY_BY_DIRECTION.get(dir),
                    CopycatGuardrailBlock.maskHas(mask, dir));
        }
        return result;
    }

    // ---------------------------------------------------------------- 槽位

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        BlockState shaped = stateFrom(state, config);
        // 真实可见的槽（横梁按方向、柱子按角点）。柱子按角点意味着它可能被两个方向共用，
        // 所以「哪些柱子存在」不能靠方向推，只能按几何算。
        Set<String> visible = new LinkedHashSet<>(GuardrailParts.visibleKeys(shaped));

        List<AdapterSlot> slots = new ArrayList<>(GuardrailParts.allKeys().size());
        int index = 0;
        for (String key : GuardrailParts.allKeys()) {
            // 前四个是横梁，后四个是柱子（见 GuardrailParts#allKeys 的实现）
            boolean isRow = index < CopycatGuardrailBlock.FACES.size();
            index++;
            String group = isRow ? GROUP_ROW : GROUP_COLUMN;
            slots.add(AdapterSlot.of(key, LABEL_PREFIX + key, group, visible.contains(key),
                    config.slots().get(key)));
        }
        return slots;
    }

    /**
     * 护栏有一个 virtual property：四个方向的组合。
     *
     * <p>候选项给的是 16 种组合的可读标签（「只有北」「北+东」…），而不是裸数字——
     * 玩家不需要理解位掩码。方向位定义直接复用 {@link CopycatGuardrailBlock#bit}。
     *
     * <p><b>它同时声明接管了 north/east/south/west 四个方块状态属性</b>：这四个属性与这个
     * virtual property 表达的是同一个结构，GUI 只保留后者一个入口，避免两边互相覆盖。
     */
    @Override
    public List<VirtualSpec> virtualSpecs() {
        List<VirtualSpec.Option> options = new ArrayList<>(16);
        for (int mask = 0; mask < 16; mask++) {
            options.add(new VirtualSpec.Option(
                    "maris-decoration.copycat_placer.guardrail_faces." + Integer.toHexString(mask),
                    facesFallbackText(mask), mask));
        }
        return List.of(new ManagedOptions(STRUCTURE_FACES,
                "maris-decoration.copycat_placer.virtual.guardrail_faces",
                "Faces",
                GuardrailCopycatAdapter::facesMask,
                (config, value) -> StructureMasks.with(config, STRUCTURE_FACES, value, 4),
                options));
    }

    /**
     * 候选项的兜底文本：把位掩码翻成方向名（{@code north+east}），<b>绝不出现十六进制</b>。
     *
     * <p>只在翻译缺失时用到。用「方向名拼接」而不是「掩码值」是因为掩码是内部表示：
     * 玩家看到 {@code 0x3} 不可能知道那是「南+西」。取值顺序与
     * {@link CopycatGuardrailBlock#FACES} 一致，所以文本与翻译文件里的条目一一对应。
     */
    private static String facesFallbackText(int mask) {
        if (mask == 0) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (Direction face : CopycatGuardrailBlock.FACES) {
            if ((mask & CopycatGuardrailBlock.bit(face)) == 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append('+');
            }
            text.append(face.getName());
        }
        return text.toString();
    }

    /**
     * {@link VirtualSpec#options} 的薄包装，额外声明「本 spec 接管了四个方向属性」。
     *
     * <p>做成包装而不是给 {@code VirtualSpec.options} 再加一个参数：那个工厂方法被多处复用，
     * 加参数只会让所有调用点都要多想一次「要不要接管属性」。这里只有护栏需要，就地包一层更清楚。
     */
    private record ManagedOptions(String key, String labelKey, String labelText,
                                  VirtualSpec.IntReader reader,
                                  VirtualSpec.IntWriter writer,
                                  List<VirtualSpec.Option> options) implements VirtualSpec {

        @Override
        public int current(PlacementConfig config) {
            return reader.read(config);
        }

        @Override
        public PlacementConfig with(PlacementConfig config, int value) {
            return writer.write(config, value);
        }

        @Override
        public List<String> managedProperties() {
            return List.of(Direction.NORTH.getName(), Direction.EAST.getName(),
                    Direction.SOUTH.getName(), Direction.WEST.getName());
        }
    }

    /**
     * 默认配置：<b>只要正北一面</b>。
     *
     * <p>不能用方块默认状态——那样四个方向全 false，是一个<b>零部件的护栏</b>：放下去占着格子
     * 却什么都看不见，再想放别的东西还会提示「这里放不下」。所以默认给一个真正有几何的形态。
     */
    @Override
    public PlacementConfig defaultConfig(Block block) {
        return withFaces(PlacementConfig.of(block.getDefaultState()), DEFAULT_FACES);
    }

    /** 至少一个方向存在，否则就是那个看不见的幽灵方块。 */
    @Override
    public String validateStructure(BlockState state, PlacementConfig config) {
        if (facesMask(config) == 0) {
            return "guardrail_no_face";
        }
        return null;
    }

    /** 护栏的材质准入直接复用方块自己那份判据（Create 的 copycat_allow/deny + 完整立方体检查）。 */
    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config, BlockView world, BlockPos pos) {
        // world/pos 传 null：那两个参数只用于「读方块轮廓形状」这一步，而判据里的
        // 「必须是不透明完整立方体」在无世界上下文时按方块自身属性判断即可。
        // 服务端放置时会带真实 world/pos 再验一次，所以这里放宽不会漏掉不合格材质。
        return CopycatGuardrailBlock.getAcceptedMaterial(world, pos,
                new ItemStack(material.getBlock()), null) != null;
    }

    @Override
    public void apply(PlacementContext context) {
        if (!(context.world().getBlockEntity(context.pos()) instanceof CopycatGuardrailBlockEntity blockEntity)) {
            return;
        }
        for (String key : GuardrailParts.visibleKeys(context.state())) {
            BlockState material = context.material(key);
            if (material == null) {
                continue;
            }
            // 已经有材质的槽不动它（右键 / 上一次放置留下的都算）
            if (blockEntity.hasMaterial(key)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
                continue;
            }
            // 服务端准入校验：不合法的材质留空，不阻止放置（客户端过滤只是 UX）
            if (!context.acceptsMaterial(material, key)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
                continue;
            }
            // 同一个 BlockPos 上同一种材质只付一次：已经付过就只写材质、不记账。
            // 拿不出这个材质就留空，不阻止放置。
            boolean alreadyPaid = context.paid().contains(material);
            if (!context.claim(material)) {
                context.markSkipped(key, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
                continue;
            }
            blockEntity.setMaterial(key, material,
                    alreadyPaid ? null : new ItemStack(material.getBlock()));
            context.markApplied(key);
        }
    }
}
