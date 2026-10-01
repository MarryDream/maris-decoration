package marrydream.marisdecoration.placement.harness;

import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.copycat.CopycatSpecialCases;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity;
import marrydream.marisdecoration.block.LintelThresholdThinDoorBlock;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import marrydream.marisdecoration.block.VerticalLadderBlock;
import marrydream.marisdecoration.block.enums.PropLadderShape;
import marrydream.marisdecoration.block.utils.BoardFaceCulling;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import marrydream.marisdecoration.block.utils.LayeredBoardParts;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardArea;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.block.utils.SteelPlugDoorRoof;
import marrydream.marisdecoration.block.utils.SteelPlugDoorAnimation;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModItem;
import marrydream.marisdecoration.item.CopycatPlacerItem;
import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.PlacementConfigs;
import marrydream.marisdecoration.placement.PlacementFailure;
import marrydream.marisdecoration.placement.PlacementFeedback;
import marrydream.marisdecoration.placement.PlacementResult;
import marrydream.marisdecoration.placement.PlacementService;
import marrydream.marisdecoration.placement.adapter.AdapterSlot;
import marrydream.marisdecoration.placement.adapter.BuiltinAdapters;
import marrydream.marisdecoration.placement.adapter.CopycatPlacementAdapter;
import marrydream.marisdecoration.placement.adapter.CopycatsMultistateAdapter;
import marrydream.marisdecoration.placement.adapter.CopycatsOrdinaryAdapter;
import marrydream.marisdecoration.placement.adapter.CreateCopycatAdapter;
import marrydream.marisdecoration.placement.adapter.GuardrailCopycatAdapter;
import marrydream.marisdecoration.placement.adapter.LayeredBoardCopycatAdapter;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import marrydream.marisdecoration.placement.adapter.PlacementContext;
import marrydream.marisdecoration.placement.adapter.PropertySpec;
import marrydream.marisdecoration.placement.adapter.StructureMasks;
import marrydream.marisdecoration.placement.adapter.VirtualSpec;
import marrydream.marisdecoration.placement.client.PlacerEditState;
import marrydream.marisdecoration.placement.client.PlacerLayout;
import marrydream.marisdecoration.placement.client.PlacerOverlay;
import marrydream.marisdecoration.placement.client.StructureTooltip;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static marrydream.marisdecoration.placement.harness.PlacementHarness.Assertions;
import static marrydream.marisdecoration.placement.harness.PlacementHarness.clearInventory;
import static marrydream.marisdecoration.placement.harness.PlacementHarness.countOf;
import static marrydream.marisdecoration.placement.harness.PlacementHarness.creative;
import static marrydream.marisdecoration.placement.harness.PlacementHarness.give;

/**
 * 第一阶段的代码级验证：手工构造配置 → 调放置服务 → 检查世界里真的出现了什么。
 *
 * <p>覆盖的问题清单就是本阶段的验收点：
 * <ul>
 *   <li>Config / NBT 能否无损往返；</li>
 *   <li>adapter 解析优先级对不对，方块类型判断有没有泄漏到调用方；</li>
 *   <li>分层薄板暴露的是不是只有该暴露的东西（12 槽 / 窗 / 5 个区域 / 窗材质）；</li>
 *   <li>护栏的结构属性与 material slot 是不是按<b>当前代码</b>的真实几何算的；</li>
 *   <li>创造模式不要求库存、也不消耗；</li>
 *   <li>生存模式成功放置消耗 1 个结构方块；</li>
 *   <li>没有结构方块时不放置、也不扣任何材质；</li>
 *   <li>材质不足不阻止放置，缺的槽留空；</li>
 *   <li>{@code WATERLOGGED} 不是用户可配置属性，但放进水里照常含水；</li>
 *   <li>护栏与分层薄板保持「同一 BlockPos 同一种材质只付一次」；</li>
 *   <li>Create 与 Copycats+ 各自原本的 consumption 语义；</li>
 *   <li>原来的 BlockItem 放置路径没有被这次改动波及。</li>
 * </ul>
 *
 * <p>入口：{@code /marisplacer self-test}。
 */
public final class PlacementSelfTest {

    private PlacementSelfTest() {
    }

    /** 跑全部检查，返回可读报告（同时按结果写进日志）。 */
    public static String run(ServerWorld world, BlockPos origin) {
        PlacementHarness harness = new PlacementHarness(world, origin).reset();
        Assertions assertions = harness.assertions();
        List<String> sections = new ArrayList<>();

        // 每一段单独兜异常：某一段炸了也要把「炸在哪一段」写进报告文件，
        // 否则无头服务端上只能看到原版那句「An unexpected error occurred」。
        section(sections, "0. 过时注册已清除", () -> runRemovedRegistrations(assertions, sections));
        section(sections, "1. PlacementConfig / NBT", () -> runConfigNbt(assertions, sections));
        section(sections, "2. adapter resolver", () -> runAdapterResolution(assertions, sections));
        section(sections, "3. 分层薄板槽位", () -> runLayeredBoardSlots(assertions, sections));
        section(sections, "3b. 钢内嵌门可插拔屋顶", () -> runSteelDoorRoof(harness, assertions, sections));
        section(sections, "3c. 钢内嵌门铰链动画", () -> runSteelDoorAnimation(assertions, sections));
        section(sections, "4. 伪装护栏槽位", () -> runGuardrailSlots(assertions, sections));
        section(sections, "4b. 伪装梯子放置器", () -> runCopycatLadderAdapter(harness, assertions, sections));
        section(sections, "4c. 伪装垂直爬梯附着", () -> runCopycatVerticalAttachment(harness, assertions, sections));
        section(sections, "5. 创造模式放置", () -> runCreativePlacements(harness, assertions, sections));
        section(sections, "6. 生存模式放置", () -> runSurvivalPlacements(harness, assertions, sections));
        section(sections, "7. 含水", () -> runWaterlogged(harness, assertions, sections));
        section(sections, "8. 付账语义", () -> runPaymentSemantics(harness, assertions, sections));
        section(sections, "9. 第三方 adapter", () -> runThirdPartyAdapters(harness, assertions, sections));
        section(sections, "10. 工具物品 / 配置 NBT", () -> runItemAndConfigNbt(assertions, sections));
        section(sections, "11. GUI 逻辑（广告位/属性/材质）", () -> runGuiLogic(assertions, sections));
        section(sections, "12. 服务端校验 + 右键放置", () -> runToolPlacement(harness, assertions, sections));
        section(sections, "12b. 竖直面附着（原版梯子）", () -> runFaceAttachments(harness, assertions, sections));
        section(sections, "12c. 梯子放置对比", () -> runLadderComparison(harness, assertions, sections));
        section(sections, "13. 落点解析（点击面只决定格子）", () -> runTargetPos(harness, assertions, sections));
        section(sections, "14. GUI/config 一致性（唯一事实来源）", () -> runConfigConsistency(assertions, sections));
        section(sections, "15. 零结构校验（幽灵方块）", () -> runStructureValidation(harness, assertions, sections));
        section(sections, "16. 搜索（名称 / id / path）", () -> runSearch(assertions, sections));
        section(sections, "17. 材质准入过滤", () -> runMaterialFilter(harness, assertions, sections));
        section(sections, "18. 翻译资源", () -> runTranslations(world, assertions, sections));
        section(sections, "19. 界面几何", () -> runLayoutGeometry(assertions, sections));
        section(sections, "20. 结构配置模型", () -> runStructureModel(assertions, sections));
        section(sections, "20d. 护栏渲染 cullFace", () -> runGuardrailCullFaceModel(assertions, sections));

        return report(assertions, sections);
    }

    private static void runRemovedRegistrations(Assertions a, List<String> log) {
        String[] removedBlocks = {
                "steel_slab", "cyan_steel_slab", "black_steel_slab",
                "steel_stairs", "cyan_steel_stairs", "black_steel_stairs",
                "teak_wall", "steel_wall", "steel_roof_teak_wall", "cyan_steel_roof_teak_wall",
                "black_steel_roof_teak_wall", "cyan_roof_steel_wall", "black_roof_steel_wall",
                "cyan_roof_steel_trim_cyan_window_wall", "steel_teak_component_wall",
                "cyan_roof_steel_teak_component_wall", "cyan_glass_steel_teak_component_wall",
                "cyan_glass_cyan_roof_steel_teak_component_wall",
                "teak_roof", "steel_roof", "cyan_steel_roof", "black_steel_roof",
                "steel_teak_trim_roof", "steel_trim_cyan_steel_roof",
                "steel_fixed_ladder", "steel_vertical_ladder",
                "steel_guardrail", "black_steel_guardrail",
                "steel_plug_door_with_roof", "teak_steel_plug_door_with_roof",
                "cyan_steel_plug_door_with_roof", "black_steel_plug_door_with_roof"
        };
        for (String path : removedBlocks) {
            Identifier id = new Identifier("maris-decoration", path);
            a.isFalse("旧方块 ID 已移除：" + path, Registries.BLOCK.containsId(id));
            a.isFalse("旧方块物品 ID 已移除：" + path, Registries.ITEM.containsId(id));
        }
        for (String path : List.of("steel_hammer", "rebar")) {
            a.isFalse("旧物品 ID 已移除：" + path,
                    Registries.ITEM.containsId(new Identifier("maris-decoration", path)));
        }
        log.add("   32 个旧方块 ID 与 2 个旧物品 ID 均未注册");
    }

    /** 跑一个测试段；它抛异常时记成一条失败，并继续跑后面的段。 */
    private static void section(List<String> sections, String name, Runnable body) {
        try {
            body.run();
        } catch (Throwable throwable) {
            sections.add("!! 段 [" + name + "] 抛出异常：" + throwable);
            java.io.StringWriter writer = new java.io.StringWriter();
            throwable.printStackTrace(new java.io.PrintWriter(writer));
            sections.add(writer.toString());
            MarisDecoration.LOGGER.error("Copycat Placer 自检段 [" + name + "] 抛出异常", throwable);
        }
    }

    // ================================================================ 1. Config / NBT

    private static void runConfigNbt(Assertions a, List<String> log) {
        log.add("== 1. PlacementConfig / NBT");

        PlacementConfig config = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x3")
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_WINDOWS, "0x1")
                .withSlot(LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.BODY),
                        Blocks.OAK_PLANKS.getDefaultState())
                .withSlot(LayeredBoardSlots.windowKey(FaceDir.UP), Blocks.GLASS.getDefaultState());

        PlacementConfig roundTrip = PlacementConfig.fromNbt(config.toNbt());
        a.equal("NBT 往返：整份配置相等", config, roundTrip);
        a.equal("NBT 往返：槽位数不变", config.slots().size(), roundTrip.slots().size());
        a.equal("NBT 往返：结构属性读回", "0x3",
                roundTrip.structure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY));

        // 掩码解析：带前缀十六进制、裸十六进制、十进制、坏值
        a.equal("掩码解析 0x3", 3, LayeredBoardCopycatAdapter.occupancy(config));
        a.equal("掩码解析十进制 3", 3, LayeredBoardCopycatAdapter.occupancy(
                config.withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "3")));
        a.equal("掩码解析坏值退回默认（下面外层）", LayeredBoardCopycatAdapter.DEFAULT_OCCUPANCY,
                LayeredBoardCopycatAdapter.occupancy(config.withStructure(
                        LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "not-a-number")));
        a.equal("占用掩码默认只有下面外层", LayeredBoardCopycatAdapter.DEFAULT_OCCUPANCY,
                LayeredBoardCopycatAdapter.occupancy(
                        PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())));
        a.equal("窗掩码默认全关", 0, LayeredBoardCopycatAdapter.windows(
                PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())));
        a.equal("掩码写回是 0x 前缀的十六进制", "0xf", StructureMasks.write(15));

        // 旧配置的名字形式必须换算成等价的层集合，而不是整份作废
        a.equal("旧名字 all → 12 层全占", LayeredBoardSlots.FULL_OCCUPANCY,
                LayeredBoardCopycatAdapter.occupancy(config.withStructure(
                        LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "all")));
        a.equal("旧名字 outer_only → 六个面的外层",
                LayeredBoardCopycatAdapter.outerOnly(),
                LayeredBoardCopycatAdapter.occupancy(config.withStructure(
                        LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "outer_only")));
        a.equal("旧名字 north_outer → 北面外层一个 bit",
                LayeredBoardSlots.slotBitMask(FaceDir.NORTH, BoardLayer.OUTER),
                LayeredBoardCopycatAdapter.occupancy(config.withStructure(
                        LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "north_outer")));
        a.equal("旧名字窗 north → 北面的窗",
                LayeredBoardSlots.windowBit(FaceDir.NORTH),
                LayeredBoardCopycatAdapter.windows(config.withStructure(
                        LayeredBoardCopycatAdapter.STRUCTURE_WINDOWS, "north")));
        a.equal("旧名字组合 north+east → 两个方向",
                CopycatGuardrailBlock.bit(Direction.NORTH) | CopycatGuardrailBlock.bit(Direction.EAST),
                GuardrailCopycatAdapter.facesMask(config.withStructure(
                        GuardrailCopycatAdapter.STRUCTURE_FACES, "north+east")));
        a.equal("角柱掩码缺失时按「四个都开」处理", GuardrailCopycatAdapter.DEFAULT_CORNERS,
                GuardrailCopycatAdapter.cornersMask(config));

        // 空配置
        a.isTrue("空 NBT = 空配置", PlacementConfig.fromNbt(new NbtCompound()).equals(PlacementConfig.EMPTY));
        a.equal("空配置没有方块", null, PlacementConfig.EMPTY.block());
        a.equal("没有方块时没有结构物品", null, PlacementConfig.EMPTY.structureItem());
        a.equal("结构方块物品解析", ModBlock.LAYERED_COPYCAT_BOARD.asItem(), config.structureItem());

        // WATERLOGGED 不是可配置项：就算预设里被人硬写进去，stateForPlacement 也必须按实际水体覆盖
        PlacementConfig polluted = PlacementConfig.of(
                ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState().with(Properties.WATERLOGGED, true));
        a.isFalse("stateForPlacement(false) 会清掉预设里的 WATERLOGGED",
                polluted.stateForPlacement(false).get(Properties.WATERLOGGED));
        a.isTrue("stateForPlacement(true) 会置上 WATERLOGGED",
                polluted.stateForPlacement(true).get(Properties.WATERLOGGED));

        // 不可变
        PlacementConfig base = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState());
        PlacementConfig derived = base.withSlot("x", Blocks.STONE.getDefaultState());
        a.equal("派生配置不改原配置", 0, base.slots().size());
        a.equal("派生配置自己有新槽", 1, derived.slots().size());
    }

    // ================================================================ 2. adapter 解析

    private static void runAdapterResolution(Assertions a, List<String> log) {
        log.add("== 2. adapter resolver / 优先级");

        a.equal("分层薄板 → 自己的 adapter", "maris-decoration:layered_copycat_board",
                PlacementAdapters.resolve(ModBlock.LAYERED_COPYCAT_BOARD).orElseThrow().name());
        a.equal("伪装护栏 → 自己的 adapter", "maris-decoration:copycat_guardrail",
                PlacementAdapters.resolve(ModBlock.COPYCAT_GUARDRAIL).orElseThrow().name());
        a.isTrue("石头没有 adapter", PlacementAdapters.resolve(Blocks.STONE).isEmpty());
        a.isFalse("石头不是可放置目标", PlacementAdapters.isPlaceable(Blocks.STONE));
        a.isTrue("分层薄板是可放置目标", PlacementAdapters.isPlaceable(ModBlock.LAYERED_COPYCAT_BOARD));

        List<String> names = PlacementAdapters.names();
        a.isTrue("adapter 表非空", !names.isEmpty());
        a.equal("兜底 adapter 排在最后", "generic:copycat", names.get(names.size() - 1));
        a.equal("本 mod 自定义 adapter 优先级最小", BuiltinAdapters.PRIORITY_OWN_CUSTOM,
                PlacementAdapters.all().get(0).priority());

        int previous = Integer.MIN_VALUE;
        boolean ascending = true;
        for (var adapter : PlacementAdapters.all()) {
            if (adapter.priority() < previous) {
                ascending = false;
            }
            previous = adapter.priority();
        }
        a.isTrue("优先级升序排列", ascending);

        log.add("   adapter 顺序：" + names);
        if (BuiltinAdapters.copycatsLoaded()) {
            a.isTrue("装了 Copycats+ 时有 multistate adapter", names.contains("copycats:multistate"));
            a.isTrue("装了 Copycats+ 时有 ordinary adapter", names.contains("copycats:ordinary"));
            // 这两条判据真重叠：multistate 的 block 也是 ICopycatBlock，顺序在这里是语义
            a.isTrue("multistate 排在 ordinary 之前",
                    names.indexOf("copycats:multistate") < names.indexOf("copycats:ordinary"));
        } else {
            log.add("   （没有装 Copycats+，跳过 Copycats+ 相关断言）");
        }
    }

    // ================================================================ 3. 分层薄板槽位

    private static void runLayeredBoardSlots(Assertions a, List<String> log) {
        log.add("== 3. 分层薄板 adapter 暴露的槽位");

        BlockState state = ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState();
        PlacementConfig config = PlacementConfig.of(state)
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x3")
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_WINDOWS,
                        StructureMasks.write(LayeredBoardSlots.windowBit(FaceDir.DOWN)));

        var adapter = new LayeredBoardCopycatAdapter();
        var slots = adapter.slots(state, config);

        // 66 = 6 面 × (2 层 × 5 区域 + 1 份窗)
        a.equal("槽位总数 66（adapter 仍然给全量，由 GUI 按结构过滤）", 66, slots.size());
        // 结构存在的：DOWN 两层各 5 个区域 = 10，加 DOWN 的窗（面级、只在循环外出现一次）= 11
        a.equal("结构存在的槽 = 10 + 1 个窗", 11,
                (int) slots.stream().filter(s -> s.structure()).count());

        a.equal("占用掩码读回", 3, LayeredBoardCopycatAdapter.occupancy(config));
        // 逐项开关：DOWN 的两层都开着，其它面一层都没有
        a.isTrue("DOWN.OUTER 开着", LayeredBoardCopycatAdapter.slotEnabled(config, FaceDir.DOWN, BoardLayer.OUTER));
        a.isTrue("DOWN.INNER 开着", LayeredBoardCopycatAdapter.slotEnabled(config, FaceDir.DOWN, BoardLayer.INNER));
        a.isFalse("UP.OUTER 关着", LayeredBoardCopycatAdapter.slotEnabled(config, FaceDir.UP, BoardLayer.OUTER));
        a.isTrue("DOWN 这一面有板", LayeredBoardCopycatAdapter.faceHasLayer(config, FaceDir.DOWN));
        a.isFalse("UP 这一面没有板", LayeredBoardCopycatAdapter.faceHasLayer(config, FaceDir.UP));

        // 五个区域 + 窗：键名必须与方块实体逐字符一致
        for (BoardArea area : LayeredBoardSlots.MATERIAL_AREAS) {
            String key = LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, area);
            a.isTrue("存在区域槽 " + key, slots.stream().anyMatch(s -> s.key().equals(key)));
        }
        a.isTrue("存在窗槽 north.window",
                slots.stream().anyMatch(s -> s.key().equals(LayeredBoardSlots.windowKey(FaceDir.NORTH))));

        // 刻意不暴露的内部状态
        List<String> keys = slots.stream().map(s -> s.key()).toList();
        for (String forbidden : List.of("junction", "paid_materials", "topology", "occupancy",
                "windows", "physical_run", "cache")) {
            a.isTrue("没有把内部字段暴露成槽：" + forbidden,
                    keys.stream().noneMatch(k -> k.contains(forbidden)));
        }
        a.equal("只有开窗的那个面有窗槽存在（掩码只开 DOWN）", 1,
                (int) slots.stream().filter(s -> s.key().endsWith(".window"))
                        .filter(s -> s.structure()).count());
        a.equal("存在窗槽的就是 DOWN", LayeredBoardSlots.windowKey(FaceDir.DOWN),
                slots.stream().filter(s -> s.key().endsWith(".window"))
                        .filter(s -> s.structure()).findFirst().orElseThrow().key());

        // 预设材质会被填进描述里
        String bodyKey = LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.BODY);
        var described = adapter.slots(state, config.withSlot(bodyKey, Blocks.OAK_PLANKS.getDefaultState()))
                .stream().filter(s -> s.key().equals(bodyKey)).findFirst().orElseThrow();
        a.equal("槽位描述里带着预设方块", Blocks.OAK_PLANKS, described.material());
        a.isTrue("槽位描述报告「预设里有材质」", described.hasMaterial());
    }

    // ================================================================ 4. 护栏槽位

    private static void runSteelDoorRoof(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 3b. steel plug door removable Layered Board roof");
        ServerWorld world = harness.world();
        BlockPos lowerPos = harness.next();
        BlockPos upperPos = lowerPos.up();
        BlockState lower = ModBlock.STEEL_PLUG_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.EAST)
                .with(net.minecraft.block.DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = lower.with(net.minecraft.block.DoorBlock.HALF, DoubleBlockHalf.UPPER);
        world.setBlockState(lowerPos, lower, Block.NOTIFY_LISTENERS);
        world.setBlockState(upperPos, upper, Block.NOTIFY_LISTENERS);

        a.isFalse("lower door half does not own roof data",
                world.getBlockEntity(lowerPos) instanceof SteelPlugDoorBlockEntity);
        a.isTrue("upper door half owns roof data",
                world.getBlockEntity(upperPos) instanceof SteelPlugDoorBlockEntity);
        if (!(world.getBlockEntity(upperPos) instanceof SteelPlugDoorBlockEntity roof)) return;

        ServerPlayerEntity player = harness.fakePlayer(lowerPos.getX() + 0.5, lowerPos.getY(), lowerPos.getZ() + 2.0);
        creative(player, false);

        BlockHitResult doorHit = new BlockHitResult(
                Vec3d.of(lowerPos).add(0.5, 0.5, 0.5), Direction.NORTH, lowerPos, false);
        ModBlock.STEEL_PLUG_DOOR.onUse(lower, world, lowerPos, player, Hand.MAIN_HAND, doorHit);
        a.isTrue("door body toggles normally without a roof", world.getBlockState(lowerPos).get(
                net.minecraft.block.DoorBlock.OPEN));
        BlockState openLower = world.getBlockState(lowerPos);
        ModBlock.STEEL_PLUG_DOOR.onUse(openLower, world, lowerPos, player, Hand.MAIN_HAND, doorHit);
        a.isFalse("door can be returned to closed before roof insertion", world.getBlockState(lowerPos).get(
                net.minecraft.block.DoorBlock.OPEN));
        lower = world.getBlockState(lowerPos);
        upper = world.getBlockState(upperPos);

        a.isTrue("door lintel quad in y=15..16 is covered by roof",
                SteelPlugDoorRoof.isCoveredDoorQuad(15.0 / 16.0, 1.0, 1.0, 15.0 / 16.0));
        a.isTrue("door panel cap at y=15 is covered by roof",
                SteelPlugDoorRoof.isCoveredDoorQuad(15.0 / 16.0, 15.0 / 16.0,
                        15.0 / 16.0, 15.0 / 16.0));
        a.isFalse("door geometry extending below roof remains visible",
                SteelPlugDoorRoof.isCoveredDoorQuad(0.0, 0.0, 15.0 / 16.0, 15.0 / 16.0));

        give(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem(), 2);
        BlockHitResult centerHit = new BlockHitResult(
                Vec3d.of(upperPos).add(0.5, 31.0 / 32.0, 0.5), Direction.UP, upperPos, false);
        ActionResult inserted = ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player,
                Hand.MAIN_HAND, centerHit);
        a.isTrue("survival insert succeeds", inserted.isAccepted());
        a.isTrue("insert creates exactly the roof occupancy", roof.hasRoof()
                && roof.occupancy() == SteelPlugDoorBlockEntity.ROOF_OCCUPANCY);
        a.equal("survival insert consumes one board", 1,
                countOf(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem()));

        ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player, Hand.MAIN_HAND, centerHit);
        a.equal("existing roof refuses a second structure without consuming it", 1,
                countOf(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem()));

        clearInventory(player);
        BlockHitResult upperDoorHit = new BlockHitResult(
                Vec3d.of(upperPos).add(0.5, 0.5, 0.5), Direction.NORTH, upperPos, false);
        boolean openBeforeDoorBody = world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN);
        ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player, Hand.MAIN_HAND, upperDoorHit);
        a.equal("door body below an existing roof still toggles", !openBeforeDoorBody,
                world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN));
        BlockState toggledUpper = world.getBlockState(upperPos);
        ModBlock.STEEL_PLUG_DOOR.onUse(toggledUpper, world, upperPos, player, Hand.MAIN_HAND, upperDoorHit);
        lower = world.getBlockState(lowerPos);
        upper = world.getBlockState(upperPos);

        give(player, Blocks.STONE.asItem(), 2);
        BlockHitResult bodyHit = roofHit(upperPos, upper, roof, BoardArea.BODY);
        BlockHitResult topEdgeHit = roofHit(upperPos, upper, roof, BoardArea.TOP_EDGE);
        boolean openBeforeMaterial = world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN);
        ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player, Hand.MAIN_HAND, bodyHit);
        ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player, Hand.MAIN_HAND, topEdgeHit);
        a.equal("roof material interactions do not toggle door", openBeforeMaterial,
                world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN));
        String bodyKey = LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.BODY);
        String edgeKey = LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.TOP_EDGE);
        a.equal("roof BODY stores its own material", Blocks.STONE, roof.material(bodyKey).getBlock());
        a.equal("roof EDGE stores its own material", Blocks.STONE, roof.material(edgeKey).getBlock());
        a.equal("same material is paid once across roof regions", 1, countOf(player, Blocks.STONE.asItem()));

        give(player, AllItems.WRENCH.get(), 1);
        boolean openBeforeWrench = world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN);
        ActionResult edgeRemoved = ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player,
                Hand.MAIN_HAND, topEdgeHit);
        a.isTrue("ordinary wrench is handled through door onUse", edgeRemoved.isAccepted());
        a.isTrue("ordinary wrench clears only the hit EDGE", roof.material(edgeKey).isAir());
        a.equal("ordinary wrench keeps BODY material", Blocks.STONE, roof.material(bodyKey).getBlock());
        a.equal("shared paid material is not refunded while BODY still uses it", 0,
                countOf(player, Blocks.STONE.asItem()));
        a.equal("ordinary wrench roof hit does not toggle door", openBeforeWrench,
                world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN));
        give(player, Blocks.STONE.asItem(), 1);
        ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player, Hand.MAIN_HAND, topEdgeHit);
        a.equal("reapplying shared material does not charge again", 1,
                countOf(player, Blocks.STONE.asItem()));

        List<LayeredBoardParts.Junction> corners = LayeredBoardParts.junctions(
                roof.occupancy(), roof.windows(), roof.junctionOwners()).stream()
                .filter(LayeredBoardParts.Junction::corner).toList();
        a.equal("single roof layer keeps four Layered Board corner rules", 4, corners.size());
        a.isTrue("each roof corner can choose between its two adjacent edges",
                corners.stream().allMatch(junction -> junction.candidates().size() == 2));

        give(player, ModItem.DETAIL_CHISEL, 1);
        boolean openBeforeChisel = world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN);
        ActionResult chiseled = ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player,
                Hand.MAIN_HAND, bodyHit);
        a.isTrue("detail chisel routes roof BODY to Layered Board window logic", chiseled.isAccepted());
        a.isTrue("detail chisel opens the roof window", roof.hasWindow(FaceDir.UP));
        a.equal("detail chisel roof hit does not toggle door", openBeforeChisel,
                world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN));

        NbtCompound savedRoof = roof.createNbt();
        SteelPlugDoorBlockEntity restoredRoof = new SteelPlugDoorBlockEntity(upperPos, upper);
        restoredRoof.readNbt(savedRoof);
        a.isTrue("roof occupancy survives NBT round-trip", restoredRoof.hasRoof());
        a.isTrue("roof window survives NBT round-trip", restoredRoof.hasWindow(FaceDir.UP));
        a.equal("roof BODY material survives NBT round-trip", Blocks.STONE,
                restoredRoof.material(bodyKey).getBlock());
        a.equal("roof EDGE material survives NBT round-trip", Blocks.STONE,
                restoredRoof.material(edgeKey).getBlock());
        a.isTrue("roof payment survives NBT round-trip", restoredRoof.hasPaidFor(Blocks.STONE.getDefaultState()));

        Vec3d canonicalTopEdge = topEdgeHit.getPos().subtract(upperPos.getX(), upperPos.getY(), upperPos.getZ());
        give(player, AllItems.WRENCH.get(), 1);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            BlockState facingLower = lower.with(net.minecraft.block.DoorBlock.FACING, facing);
            BlockState facingUpper = upper.with(net.minecraft.block.DoorBlock.FACING, facing);
            world.setBlockState(lowerPos, facingLower, Block.NOTIFY_LISTENERS);
            world.setBlockState(upperPos, facingUpper, Block.NOTIFY_LISTENERS);
            roof.setMaterial(edgeKey, Blocks.STONE.getDefaultState(), null);
            Vec3d worldLocal = SteelPlugDoorRoof.toWorld(canonicalTopEdge, facing);
            Vec3d roundTrip = SteelPlugDoorRoof.toCanonical(worldLocal, facing);
            a.isTrue("roof local hit round-trips for facing " + facing,
                    roundTrip.squaredDistanceTo(canonicalTopEdge) < 1.0E-12);
            LayeredCopycatBoardBlock.BoardHit facingHit = LayeredCopycatBoardBlock.hitAt(roundTrip, roof);
            a.isTrue("TOP edge remains local TOP for facing " + facing,
                    facingHit != null && facingHit.area() == BoardArea.TOP_EDGE);
            BlockHitResult wrenchHit = new BlockHitResult(worldLocal.add(Vec3d.of(upperPos)),
                    Direction.UP, upperPos, false);
            boolean openBefore = world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN);
            ActionResult facingWrench = ModBlock.STEEL_PLUG_DOOR.onUse(facingUpper, world, upperPos,
                    player, Hand.MAIN_HAND, wrenchHit);
            a.isTrue("ordinary wrench reaches roof for facing " + facing, facingWrench.isAccepted());
            a.isTrue("ordinary wrench clears TOP edge for facing " + facing, roof.material(edgeKey).isAir());
            a.equal("roof wrench does not toggle door for facing " + facing, openBefore,
                    world.getBlockState(lowerPos).get(net.minecraft.block.DoorBlock.OPEN));
        }
        world.setBlockState(lowerPos, lower, Block.NOTIFY_LISTENERS);
        world.setBlockState(upperPos, upper, Block.NOTIFY_LISTENERS);

        clearInventory(player);
        // The harness player deliberately has no network handler, while offerOrDrop sends an
        // inventory packet for survival players. Exercise the full component-removal route in
        // creative here; survival payment/refund accounting is covered by the shared board tests.
        creative(player, true);
        ItemUsageContext removalContext = new ItemUsageContext(player, Hand.MAIN_HAND, bodyHit);
        ActionResult removed = ((LintelThresholdThinDoorBlock) ModBlock.STEEL_PLUG_DOOR)
                .onSneakWrenched(upper, removalContext);
        a.isTrue("shift-wrench roof removal succeeds", removed.isAccepted());
        a.isFalse("shift-wrench clears only the roof component", roof.hasRoof());
        a.equal("creative shift-wrench does not duplicate a structure item", 0,
                countOf(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem()));
        a.equal("creative shift-wrench does not duplicate paid material", 0, countOf(player, Blocks.STONE.asItem()));
        a.isTrue("shift-wrench keeps lower door half", world.getBlockState(lowerPos).isOf(ModBlock.STEEL_PLUG_DOOR));
        a.isTrue("shift-wrench keeps upper door half", world.getBlockState(upperPos).isOf(ModBlock.STEEL_PLUG_DOOR));

        give(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem(), 1);
        ModBlock.STEEL_PLUG_DOOR.onUse(upper, world, upperPos, player, Hand.MAIN_HAND, centerHit);
        a.isTrue("creative insert creates roof", roof.hasRoof());
        a.isFalse("reinserted roof starts with its window closed", roof.hasWindow(FaceDir.UP));
        a.equal("creative insert does not consume board", 1,
                countOf(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem()));

        roof.setMaterial(bodyKey, Blocks.STONE.getDefaultState(), new ItemStack(Blocks.STONE));
        harness.clearEntitiesAt(lowerPos);
        world.breakBlock(lowerPos, false);
        List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class,
                new Box(lowerPos).expand(2.0), entity -> true);
        int roofItems = drops.stream().filter(entity -> entity.getStack().isOf(
                ModBlock.LAYERED_COPYCAT_BOARD.asItem())).mapToInt(entity -> entity.getStack().getCount()).sum();
        int materialItems = drops.stream().filter(entity -> entity.getStack().isOf(Blocks.STONE.asItem()))
                .mapToInt(entity -> entity.getStack().getCount()).sum();
        a.equal("breaking either door structure settles one roof item", 1, roofItems);
        a.equal("breaking either door structure settles paid roof material once", 1, materialItems);
    }

    private static BlockHitResult roofHit(BlockPos upperPos, BlockState upper,
                                          SteelPlugDoorBlockEntity roof, BoardArea wanted) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Vec3d canonical = new Vec3d((x + 0.5) / 16.0, 31.0 / 32.0, (z + 0.5) / 16.0);
                LayeredCopycatBoardBlock.BoardHit hit = LayeredCopycatBoardBlock.hitAt(canonical, roof);
                if (hit != null && hit.face() == FaceDir.UP && hit.layer() == BoardLayer.OUTER
                        && hit.area() == wanted) {
                    Vec3d local = SteelPlugDoorRoof.toWorld(canonical,
                            upper.get(net.minecraft.block.DoorBlock.FACING));
                    return new BlockHitResult(local.add(Vec3d.of(upperPos)), Direction.UP, upperPos, false);
                }
            }
        }
        throw new IllegalStateException("No roof hit sample for " + wanted);
    }

    private static void runSteelDoorAnimation(Assertions a, List<String> log) {
        log.add("== 3c. steel plug door hinge animation");
        double thickness = SteelPlugDoorAnimation.THICKNESS;

        for (Direction facing : Direction.Type.HORIZONTAL) {
            for (DoorHinge hinge : DoorHinge.values()) {
                Box closed = transformedLeafBox(facing, hinge, 0.0F);
                Box expectedClosed = SteelPlugDoorRoof.toWorld(
                        new Box(0.0, 0.0, 0.0, thickness, 1.0, 1.0), facing);
                a.isTrue("closed leaf matches static pose for " + facing + "/" + hinge,
                        boxesClose(closed, expectedClosed));

                Box canonicalOpen = hinge == DoorHinge.LEFT
                        ? new Box(0.0, 0.0, 0.0, 1.0, 1.0, thickness)
                        : new Box(0.0, 0.0, 1.0 - thickness, 1.0, 1.0, 1.0);
                Box expectedOpen = SteelPlugDoorRoof.toWorld(canonicalOpen, facing);
                Box open = transformedLeafBox(facing, hinge, 1.0F);
                a.isTrue("open leaf matches DoorBlock direction for " + facing + "/" + hinge,
                        boxesClose(open, expectedOpen));

                Vec3d pivot = SteelPlugDoorAnimation.hingePivot(hinge);
                Vec3d worldPivot = SteelPlugDoorRoof.toWorld(pivot, facing);
                for (float progress : new float[]{0.25F, 0.5F, 0.75F, 1.0F}) {
                    Vec3d transformedPivot = SteelPlugDoorAnimation.transform(pivot, facing, hinge, progress);
                    a.isTrue("hinge axis remains fixed for " + facing + "/" + hinge + " at " + progress,
                            transformedPivot.squaredDistanceTo(worldPivot) < 1.0E-12);
                }

                Vec3d lowerPoint = new Vec3d(0.0, 0.5, 0.5);
                Vec3d upperPoint = new Vec3d(0.0, 1.5, 0.5);
                Vec3d movedLower = SteelPlugDoorAnimation.transform(lowerPoint, facing, hinge, 0.5F);
                Vec3d movedUpper = SteelPlugDoorAnimation.transform(upperPoint, facing, hinge, 0.5F);
                a.isTrue("upper and lower leaf share one horizontal transform for " + facing + "/" + hinge,
                        Math.abs(movedLower.x - movedUpper.x) < 1.0E-12
                                && Math.abs(movedLower.z - movedUpper.z) < 1.0E-12
                                && Math.abs((movedUpper.y - movedLower.y) - 1.0) < 1.0E-12);
            }
        }

        a.isTrue("left and right hinges swing in opposite directions",
                SteelPlugDoorAnimation.swingAngle(DoorHinge.LEFT, 0.5F)
                        == -SteelPlugDoorAnimation.swingAngle(DoorHinge.RIGHT, 0.5F));
        a.equal("closed smoothing endpoint", 0.0F, SteelPlugDoorAnimation.smoothProgress(0.0F));
        a.equal("open smoothing endpoint", 1.0F, SteelPlugDoorAnimation.smoothProgress(1.0F));
        a.isFalse("settled closed door does not duplicate fixed frame caps",
                SteelPlugDoorAnimation.shouldRenderFrameCaps(0.0F));
        a.isTrue("moving door renders fixed frame caps",
                SteelPlugDoorAnimation.shouldRenderFrameCaps(0.5F));
        a.isTrue("settled open door keeps fixed frame caps",
                SteelPlugDoorAnimation.shouldRenderFrameCaps(1.0F));
        a.isTrue("upper lintel underside renders without roof while moving",
                SteelPlugDoorAnimation.shouldRenderUpperFrameCap(0.5F, false));
        a.isFalse("roof suppresses the upper lintel underside cap",
                SteelPlugDoorAnimation.shouldRenderUpperFrameCap(0.5F, true));
        a.isTrue("lower threshold top is recognised as the inner frame cap",
                SteelPlugDoorAnimation.isInnerFrameCap(DoubleBlockHalf.LOWER,
                        1.0 / 16.0, 1.0 / 16.0, 1.0 / 16.0, 1.0 / 16.0));
        a.isTrue("upper lintel underside is recognised as the inner frame cap",
                SteelPlugDoorAnimation.isInnerFrameCap(DoubleBlockHalf.UPPER,
                        15.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0, 15.0 / 16.0));
        a.isFalse("vertical frame quad is not mistaken for an inner cap",
                SteelPlugDoorAnimation.isInnerFrameCap(DoubleBlockHalf.LOWER,
                        0.0, 0.0, 1.0 / 16.0, 1.0 / 16.0));

        LerpedFloat animation = LerpedFloat.linear().startWithValue(0.0F);
        animation.chase(1.0F, 0.15F, Chaser.LINEAR);
        animation.tickChaser();
        animation.tickChaser();
        float beforeReverse = animation.getValue(1.0F);
        animation.chase(0.0F, 0.15F, Chaser.LINEAR);
        float atReverse = animation.getValue(1.0F);
        animation.tickChaser();
        float afterReverse = animation.getValue(1.0F);
        a.equal("changing chase target does not jump animation progress", beforeReverse, atReverse);
        a.isTrue("animation continues smoothly back from its current progress",
                afterReverse < beforeReverse && afterReverse > 0.0F);

        BlockState closedState = ModBlock.STEEL_PLUG_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .with(net.minecraft.block.DoorBlock.OPEN, false);
        BlockState openState = closedState.with(net.minecraft.block.DoorBlock.OPEN, true);
        a.equal("closed door BE initializes settled closed", 0.0F,
                new SteelPlugDoorBlockEntity(BlockPos.ORIGIN, closedState).doorAnimation(1.0F));
        a.equal("open door BE initializes settled open", 1.0F,
                new SteelPlugDoorBlockEntity(BlockPos.ORIGIN, openState).doorAnimation(1.0F));
    }

    private static Box transformedLeafBox(Direction facing, DoorHinge hinge, float progress) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{0.0, SteelPlugDoorAnimation.THICKNESS}) {
            for (double y : new double[]{0.0, 1.0}) {
                for (double z : new double[]{0.0, 1.0}) {
                    Vec3d point = SteelPlugDoorAnimation.transform(new Vec3d(x, y, z), facing, hinge, progress);
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    minZ = Math.min(minZ, point.z);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                    maxZ = Math.max(maxZ, point.z);
                }
            }
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean boxesClose(Box first, Box second) {
        return Math.abs(first.minX - second.minX) < 1.0E-12
                && Math.abs(first.minY - second.minY) < 1.0E-12
                && Math.abs(first.minZ - second.minZ) < 1.0E-12
                && Math.abs(first.maxX - second.maxX) < 1.0E-12
                && Math.abs(first.maxY - second.maxY) < 1.0E-12
                && Math.abs(first.maxZ - second.maxZ) < 1.0E-12;
    }

    private static void runGuardrailSlots(Assertions a, List<String> log) {
        log.add("== 4. 伪装护栏 adapter 暴露的槽位");

        BlockState state = ModBlock.COPYCAT_GUARDRAIL.getDefaultState();
        var adapter = new GuardrailCopycatAdapter();
        int northBit = CopycatGuardrailBlock.bit(Direction.NORTH);
        int eastBit = CopycatGuardrailBlock.bit(Direction.EAST);

        PlacementConfig defaults = PlacementConfig.of(state);
        a.equal("默认朝向掩码 = NORTH 的位", northBit, GuardrailCopycatAdapter.facesMask(defaults));

        var defaultSlots = adapter.slots(state, defaults);
        a.equal("槽位总数 8（4 横梁 + 4 角柱）", 8, defaultSlots.size());
        a.equal("单方向下可见 3 个槽（1 横梁 + 2 角柱）", 3,
                (int) defaultSlots.stream().filter(s -> s.structure()).count());
        a.equal("槽键与 GuardrailParts 完全一致", GuardrailParts.allKeys(),
                defaultSlots.stream().map(s -> s.key()).toList());

        PlacementConfig all = GuardrailCopycatAdapter.withFaces(defaults, 0xF);
        a.equal("四面全开时 8 个槽全部可见", 8,
                (int) adapter.slots(state, all).stream().filter(s -> s.structure()).count());

        PlacementConfig none = GuardrailCopycatAdapter.withFaces(defaults, 0x0);
        a.equal("一个方向都不开时没有可见槽", 0,
                (int) adapter.slots(state, none).stream().filter(s -> s.structure()).count());

        // 结构属性真的写进方块状态
        BlockState shapedAll = adapter.stateFrom(state, all);
        for (Direction direction : CopycatGuardrailBlock.FACES) {
            a.isTrue("掩码 0xF 下 " + direction + " 为 true",
                    CopycatGuardrailBlock.hasFace(shapedAll, direction));
        }
        BlockState shapedNone = adapter.stateFrom(state, none);
        for (Direction direction : CopycatGuardrailBlock.FACES) {
            a.isFalse("掩码 0x0 下 " + direction + " 为 false",
                    CopycatGuardrailBlock.hasFace(shapedNone, direction));
        }
        BlockState shapedEast = adapter.stateFrom(state,
                GuardrailCopycatAdapter.withFaces(defaults, eastBit));
        a.isTrue("只开 EAST 时 EAST 为 true、NORTH 为 false",
                CopycatGuardrailBlock.hasFace(shapedEast, Direction.EAST)
                        && !CopycatGuardrailBlock.hasFace(shapedEast, Direction.NORTH));
    }

    // ================================================================ 5. 创造模式放置

    private static void runCopycatLadderAdapter(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 4b. Copycat ladder adapter");

        CopycatPlacementAdapter fixedAdapter = PlacementAdapters.resolve(ModBlock.COPYCAT_STEEL_FIXED_LADDER)
                .orElseThrow();
        CopycatPlacementAdapter verticalAdapter = PlacementAdapters.resolve(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER)
                .orElseThrow();
        a.equal("fixed ladder adapter", "maris-decoration:copycat_ladder", fixedAdapter.name());
        a.equal("vertical ladder adapter", "maris-decoration:copycat_ladder", verticalAdapter.name());

        PlacementConfig fixedConfig = fixedAdapter.defaultConfig(ModBlock.COPYCAT_STEEL_FIXED_LADDER)
                .withState(ModBlock.COPYCAT_STEEL_FIXED_LADDER.getDefaultState()
                        .with(LadderBlock.FACING, Direction.NORTH))
                .withSlot(CopycatLadderParts.MATERIAL, Blocks.OAK_PLANKS.getDefaultState());
        a.equal("fixed ladder exposes one material slot", List.of(CopycatLadderParts.MATERIAL),
                fixedAdapter.slots(fixedConfig.state(), fixedConfig).stream().map(AdapterSlot::key).toList());
        a.isTrue("fixed ladder has no extra hidden property",
                fixedAdapter.hiddenProperties(fixedConfig).isEmpty());

        PlacementConfig verticalConfig = verticalAdapter.defaultConfig(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER)
                .withState(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.getDefaultState()
                        .with(LadderBlock.FACING, Direction.NORTH)
                        .with(VerticalLadderBlock.SHAPE, PropLadderShape.NORMAL))
                .withSlot(CopycatLadderParts.SUPPORT, Blocks.OAK_PLANKS.getDefaultState())
                .withSlot(CopycatLadderParts.RUNG, Blocks.WHITE_WOOL.getDefaultState());
        a.equal("vertical ladder exposes support and rung slots",
                List.of(CopycatLadderParts.SUPPORT, CopycatLadderParts.RUNG),
                verticalAdapter.slots(verticalConfig.state(), verticalConfig).stream()
                        .map(AdapterSlot::key).toList());
        a.equal("vertical ladder hides only its derived shape property",
                Set.of(VerticalLadderBlock.SHAPE.getName()), verticalAdapter.hiddenProperties(verticalConfig));

        BlockPos fixedPos = harness.next();
        harness.world().setBlockState(fixedPos.south(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlacementResult fixedResult = PlacementService.place(harness.world(), fixedPos, fixedConfig);
        a.isTrue("fixed ladder placer placement succeeds", fixedResult.success());
        if (harness.blockEntityAt(fixedPos) instanceof CopycatLadderBlockEntity fixedEntity) {
            a.isTrue("fixed ladder material is applied",
                    fixedEntity.material(CopycatLadderParts.MATERIAL).isOf(Blocks.OAK_PLANKS));
        } else {
            a.check("fixed ladder block entity exists", false, String.valueOf(harness.blockEntityAt(fixedPos)));
        }

        BlockPos lowerPos = harness.next();
        BlockPos upperPos = lowerPos.up();
        harness.clearEntitiesAt(upperPos);
        harness.world().setBlockState(upperPos.south(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlacementResult upperResult = PlacementService.place(harness.world(), upperPos, verticalConfig);
        a.isTrue("supported vertical ladder placement succeeds", upperResult.success());
        a.equal("supported vertical ladder is START", PropLadderShape.START,
                harness.stateAt(upperPos).get(VerticalLadderBlock.SHAPE));
        if (harness.blockEntityAt(upperPos) instanceof CopycatLadderBlockEntity verticalEntity) {
            a.isTrue("vertical support material is applied",
                    verticalEntity.material(CopycatLadderParts.SUPPORT).isOf(Blocks.OAK_PLANKS));
            a.isTrue("vertical rung material is applied",
                    verticalEntity.material(CopycatLadderParts.RUNG).isOf(Blocks.WHITE_WOOL));
        } else {
            a.check("vertical ladder block entity exists", false, String.valueOf(harness.blockEntityAt(upperPos)));
        }

        PlacementResult hangingResult = PlacementService.place(harness.world(), lowerPos, verticalConfig);
        a.isTrue("vertical ladder can hang below the same facing ladder", hangingResult.success());
        a.equal("hanging vertical ladder is NORMAL", PropLadderShape.NORMAL,
                harness.stateAt(lowerPos).get(VerticalLadderBlock.SHAPE));
    }

    private static void runCopycatVerticalAttachment(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 4c. Copycat vertical ladder relaxed attachment");

        ServerWorld world = harness.world();
        CopycatPlacementAdapter adapter = PlacementAdapters.resolve(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER)
                .orElseThrow();
        List<Direction> facings = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

        for (int i = 0; i < facings.size(); i++) {
            Direction facing = facings.get(i);
            BlockPos supportPos = harness.nextBare().add(0, 0, 8 + i * 3);
            BlockPos ladderPos = supportPos.offset(facing);
            world.setBlockState(supportPos, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(ladderPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

            BlockState ladder = ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.getDefaultState()
                    .with(LadderBlock.FACING, facing)
                    .with(VerticalLadderBlock.SHAPE, PropLadderShape.START);
            a.isTrue("copycat vertical ladder accepts a narrow support facing " + facing,
                    ladder.canPlaceAt(world, ladderPos));

            PlacementConfig config = adapter.defaultConfig(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER)
                    .withState(ladder);
            PlacementResult result = PlacementService.place(world, ladderPos, config);
            a.isTrue("placer uses relaxed copycat ladder attachment facing " + facing, result.success());
            a.isTrue("placed copycat vertical ladder keeps facing " + facing,
                    world.getBlockState(ladderPos).isOf(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER)
                            && world.getBlockState(ladderPos).get(LadderBlock.FACING) == facing);

            world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            a.isTrue("copycat vertical ladder breaks after support is removed facing " + facing,
                    world.getBlockState(ladderPos).isAir());
        }

        BlockPos supportPos = harness.nextBare().add(0, 0, 24);
        BlockPos ladderPos = supportPos.offset(Direction.NORTH);
        BlockState northLadder = ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.getDefaultState()
                .with(LadderBlock.FACING, Direction.NORTH)
                .with(VerticalLadderBlock.SHAPE, PropLadderShape.START);

        world.setBlockState(supportPos,
                Blocks.OAK_FENCE.getDefaultState().with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL);
        a.isTrue("copycat vertical ladder accepts a waterlogged solid block",
                northLadder.canPlaceAt(world, ladderPos));

        world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        a.isFalse("copycat vertical ladder rejects air", northLadder.canPlaceAt(world, ladderPos));
        world.setBlockState(supportPos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        a.isFalse("copycat vertical ladder rejects water", northLadder.canPlaceAt(world, ladderPos));
        world.setBlockState(supportPos, Blocks.LAVA.getDefaultState(), Block.NOTIFY_ALL);
        a.isFalse("copycat vertical ladder rejects lava", northLadder.canPlaceAt(world, ladderPos));

        BlockPos lowerPos = harness.nextBare().add(0, 0, 28);
        BlockPos upperPos = lowerPos.up();
        BlockState eastLadder = ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.getDefaultState()
                .with(LadderBlock.FACING, Direction.EAST)
                .with(VerticalLadderBlock.SHAPE, PropLadderShape.START);
        world.setBlockState(upperPos.offset(Direction.WEST), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(upperPos, eastLadder, Block.NOTIFY_ALL);
        a.isTrue("copycat vertical ladder still hangs below the same facing ladder",
                eastLadder.canPlaceAt(world, lowerPos));
        a.isFalse("a differently facing ladder above does not support the chain",
                northLadder.canPlaceAt(world, lowerPos));
    }

    private static void runCreativePlacements(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 5. 创造模式放置（不要求库存、不消耗）");

        ServerPlayerEntity player = harness.fakePlayer(0, 0, 0);
        creative(player, true);
        clearInventory(player);

        // --- 分层薄板：只占 DOWN.OUTER 一层，给这一层的 BODY 与 TOP_EDGE 配材质
        //     （材质槽必须落在 occupancy 选中的层上，否则写了也不会显示——见 LayeredBoardCopycatAdapter）
        BlockPos pos = harness.next();
        String bodyKey = LayeredBoardSlots.materialKey(FaceDir.DOWN, BoardLayer.OUTER, BoardArea.BODY);
        String edgeKey = LayeredBoardSlots.materialKey(FaceDir.DOWN, BoardLayer.OUTER, BoardArea.TOP_EDGE);
        PlacementConfig config = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x1")
                .withSlot(bodyKey, Blocks.STONE.getDefaultState())
                .withSlot(edgeKey, Blocks.OAK_PLANKS.getDefaultState());

        PlacementResult result = PlacementService.place(harness.world(), pos, config, player);
        a.isTrue("薄板放置成功", result.success());
        if (!result.success()) {
            log.add("   薄板放置失败：" + result.failure().id()
                    + "，目标位置 = " + harness.stateAt(pos)
                    + "，预设状态 = " + config.stateForPlacement(false));
        }
        a.equal("用的是薄板 adapter", "maris-decoration:layered_copycat_board", result.adapter());
        a.isTrue("薄板真的进了世界", harness.stateAt(pos).isOf(ModBlock.LAYERED_COPYCAT_BOARD));
        a.isFalse("创造模式没有扣结构方块", result.structurePaid());
        a.equal("创造模式没有扣任何材质", 0, result.paidMaterials().size());
        a.equal("铺上了配置的两个槽", 2, result.appliedSlots().size());

        if (harness.blockEntityAt(pos) instanceof LayeredCopycatBoardBlockEntity board) {
            a.equal("occupancy 写进去了", 1, board.occupancy());
            a.isTrue("DOWN.OUTER 被占用", board.hasSlot(FaceDir.DOWN, BoardLayer.OUTER));
            a.isFalse("DOWN.INNER 没有被占用", board.hasSlot(FaceDir.DOWN, BoardLayer.INNER));
            a.isFalse("UP.OUTER 没有被占用", board.hasSlot(FaceDir.UP, BoardLayer.OUTER));
            a.isTrue("BODY 材质写进去了", board.material(bodyKey).isOf(Blocks.STONE));
            a.isTrue("TOP_EDGE 材质写进去了", board.material(edgeKey).isOf(Blocks.OAK_PLANKS));
            a.isFalse("没配的槽保持未伪装", board.hasMaterial(
                    LayeredBoardSlots.materialKey(FaceDir.DOWN, BoardLayer.OUTER, BoardArea.LEFT_EDGE)));
        } else {
            a.check("薄板方块实体存在", false, String.valueOf(harness.blockEntityAt(pos)));
        }

        // --- 分层薄板：开窗 + 面级窗材质
        BlockPos windowPos = harness.next();
        PlacementConfig windowConfig = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x1")
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_WINDOWS,
                        StructureMasks.write(LayeredBoardSlots.windowBit(FaceDir.DOWN)))
                .withSlot(LayeredBoardSlots.windowKey(FaceDir.DOWN), Blocks.GLASS.getDefaultState());
        PlacementResult windowResult = PlacementService.place(harness.world(), windowPos, windowConfig, player);
        a.isTrue("带窗的薄板放置成功", windowResult.success());
        if (harness.blockEntityAt(windowPos) instanceof LayeredCopycatBoardBlockEntity board) {
            a.isTrue("窗开关写进去了", board.hasWindow(FaceDir.DOWN));
            a.isFalse("没开窗的面保持关闭", board.hasWindow(FaceDir.UP));
            a.isTrue("窗材质写进去了",
                    board.material(LayeredBoardSlots.windowKey(FaceDir.DOWN)).isOf(Blocks.GLASS));
        }

        // --- 伪装护栏：NORTH + EAST，横梁与角柱各一种材质
        BlockPos railPos = harness.next();
        String northRow = GuardrailParts.rowKey(Direction.NORTH);
        PlacementConfig railConfig = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState())
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES,
                        StructureMasks.write(northBit() | eastBit()))
                .withSlot(northRow, Blocks.STONE.getDefaultState());
        // 从「这个结构下真实存在的角柱」里挑一个配材质，而不是自己拼字符串——
        // 角柱的归属规则（自有柱 / 共享柱）是当前代码说了算的，见 GuardrailParts
        BlockState railShaped = new GuardrailCopycatAdapter().stateFrom(
                ModBlock.COPYCAT_GUARDRAIL.getDefaultState(), railConfig);
        String cornerKey = GuardrailParts.columnKeys().stream()
                .filter(key -> GuardrailParts.visibleKeys(railShaped).contains(key))
                .findFirst().orElseThrow();
        railConfig = railConfig.withSlot(cornerKey, Blocks.OAK_PLANKS.getDefaultState());
        log.add("   护栏结构下可见的槽：" + GuardrailParts.visibleKeys(railShaped)
                + "，给角柱配材质的键：" + cornerKey);

        PlacementResult railResult = PlacementService.place(harness.world(), railPos, railConfig, player);
        a.isTrue("护栏放置成功", railResult.success());
        a.isTrue("护栏真的进了世界", harness.stateAt(railPos).isOf(ModBlock.COPYCAT_GUARDRAIL));
        a.isTrue("NORTH 面被置位", CopycatGuardrailBlock.hasFace(harness.stateAt(railPos), Direction.NORTH));
        a.isTrue("EAST 面被置位", CopycatGuardrailBlock.hasFace(harness.stateAt(railPos), Direction.EAST));
        a.isFalse("SOUTH 面没有置位", CopycatGuardrailBlock.hasFace(harness.stateAt(railPos), Direction.SOUTH));
        a.equal("铺上 2 个槽（1 横梁 + 1 角柱）", 2, railResult.appliedSlots().size());
        if (harness.blockEntityAt(railPos) instanceof CopycatGuardrailBlockEntity rail) {
            a.isTrue("NORTH 横梁材质写进去了", rail.material(northRow).isOf(Blocks.STONE));
            a.isTrue("角柱材质写进去了", rail.material(cornerKey).isOf(Blocks.OAK_PLANKS));
        } else {
            a.check("护栏方块实体存在", false, String.valueOf(harness.blockEntityAt(railPos)));
        }
    }

    // ================================================================ 6. 生存模式放置

    private static void runSurvivalPlacements(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 6. 生存模式放置 / 库存");

        ServerPlayerEntity player = harness.fakePlayer(0, 0, 0);
        creative(player, false);

        String northRow = GuardrailParts.rowKey(Direction.NORTH);
        PlacementConfig config = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState())
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, StructureMasks.write(northBit()))
                .withSlot(northRow, Blocks.STONE.getDefaultState());

        // --- 6a. 结构 + 材质都有：各扣 1
        BlockPos pos = harness.next();
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 3);
        player.getInventory().insertStack(new ItemStack(Blocks.STONE, 5));
        int railBefore = countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem());
        int stoneBefore = countOf(player, Blocks.STONE.asItem());

        PlacementResult result = PlacementService.place(harness.world(), pos, config, player);
        a.isTrue("生存模式放置成功", result.success());
        a.isTrue("扣了结构方块", result.structurePaid());
        a.equal("结构方块少一个", railBefore - 1, countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem()));
        a.equal("材质少一个", stoneBefore - 1, countOf(player, Blocks.STONE.asItem()));
        a.equal("账本上只有一种材质", 1, result.paidMaterials().size());

        // --- 6b. 没有结构方块：不放置、材质一个都不扣
        BlockPos noStructurePos = harness.next();
        give(player, Blocks.STONE.asItem(), 5);
        int stoneBefore2 = countOf(player, Blocks.STONE.asItem());
        PlacementResult noStructure = PlacementService.place(harness.world(), noStructurePos, config, player);
        a.equal("返回 NO_STRUCTURE_ITEM", PlacementFailure.NO_STRUCTURE_ITEM, noStructure.failure());
        a.isAir("世界里没有放下任何东西", harness.stateAt(noStructurePos));
        a.equal("一个材质都没扣", stoneBefore2, countOf(player, Blocks.STONE.asItem()));

        // --- 6c. 有结构没材质：照放、缺的槽留空、只扣结构方块
        BlockPos noMaterialPos = harness.next();
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 2);
        PlacementResult missingMaterial = PlacementService.place(harness.world(), noMaterialPos, config, player);
        a.isTrue("缺材质仍然放置成功", missingMaterial.success());
        a.isTrue("方块真的放上了", harness.stateAt(noMaterialPos).isOf(ModBlock.COPYCAT_GUARDRAIL));
        a.equal("只剩一个结构方块", 1, countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem()));
        a.equal("没有扣任何材质", 0, missingMaterial.paidMaterials().size());
        a.isTrue("缺材质的槽被如实报告为 NO_MATERIAL",
                missingMaterial.skippedSlots().stream()
                        .anyMatch(s -> s.reason() == PlacementContext.SkippedSlot.Reason.NO_MATERIAL));
        if (harness.blockEntityAt(noMaterialPos) instanceof CopycatGuardrailBlockEntity rail) {
            a.isFalse("缺材质的槽保持未伪装", rail.hasMaterial(northRow));
            a.equal("缺材质的槽没有留下付款记录", ItemStack.EMPTY, rail.consumedItem(northRow));
        }

        // --- 6d. 目标被占：不放、不扣
        BlockPos occupiedPos = harness.next();
        harness.world().setBlockState(occupiedPos, Blocks.STONE.getDefaultState());
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 2);
        PlacementResult blocked = PlacementService.place(harness.world(), occupiedPos, config, player);
        a.equal("目标被占返回 BLOCKED", PlacementFailure.BLOCKED, blocked.failure());
        a.equal("目标被占没扣结构方块", 2, countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem()));

        // --- 6e. 预设里没有方块 / 不是伪装方块
        a.equal("空预设返回 NO_BLOCK", PlacementFailure.NO_BLOCK,
                PlacementService.place(harness.world(), harness.next(), PlacementConfig.EMPTY, player).failure());
        a.equal("石头返回 NOT_COPYCAT", PlacementFailure.NOT_COPYCAT,
                PlacementService.place(harness.world(), harness.next(),
                        PlacementConfig.of(Blocks.STONE.getDefaultState()), player).failure());
    }

    // ================================================================ 7. 含水

    private static void runWaterlogged(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 7. WATERLOGGED");

        ServerPlayerEntity player = harness.fakePlayer(0, 0, 0);
        creative(player, true);

        for (Block block : List.of(ModBlock.LAYERED_COPYCAT_BOARD, ModBlock.COPYCAT_GUARDRAIL)) {
            String label = Registries.BLOCK.getId(block).getPath();

            BlockPos wet = harness.next();
            harness.world().setBlockState(wet, Blocks.WATER.getDefaultState());
            a.equal(label + "：目标位置确实是水", Fluids.WATER, harness.world().getFluidState(wet).getFluid());

            PlacementConfig config = PlacementConfig.of(block.getDefaultState());
            PlacementResult result = PlacementService.place(harness.world(), wet, config, player);
            a.isTrue(label + "：放进水里放置成功", result.success());
            a.isTrue(label + "：放置后含水",
                    harness.stateAt(wet).contains(Properties.WATERLOGGED)
                            && harness.stateAt(wet).get(Properties.WATERLOGGED));

            BlockPos dry = harness.next();
            a.isTrue(label + "：放在空气里成功",
                    PlacementService.place(harness.world(), dry, config, player).success());
            a.isFalse(label + "：空气里不含水", harness.stateAt(dry).get(Properties.WATERLOGGED));
        }

        // 预设里硬写了 WATERLOGGED=true，放进空气也必须按实际位置算回 false
        BlockPos overridden = harness.next();
        PlacementConfig polluted = PlacementConfig.of(
                ModBlock.COPYCAT_GUARDRAIL.getDefaultState().with(Properties.WATERLOGGED, true));
        PlacementService.place(harness.world(), overridden, polluted, player);
        a.isFalse("预设里的 WATERLOGGED 被实际水体覆盖",
                harness.stateAt(overridden).get(Properties.WATERLOGGED));
    }

    // ================================================================ 8. 付账语义

    private static void runPaymentSemantics(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 8. 付账语义（同格同材质只付一次）");

        ServerPlayerEntity player = harness.fakePlayer(0, 0, 0);
        creative(player, false);

        // --- 分层薄板：5 个槽都贴同一种石头，只应该扣 1 个
        //     occupancy 取 UP.OUTER | UP.INNER（0xF），材质给 UP.OUTER 的五个区域——
        //     槽必须落在选中的层上才会被铺，见 LayeredBoardCopycatAdapter。
        BlockPos pos = harness.next();
        PlacementConfig config = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0xf");
        for (BoardArea area : LayeredBoardSlots.MATERIAL_AREAS) {
            config = config.withSlot(LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, area),
                    Blocks.STONE.getDefaultState());
        }
        give(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem(), 1);
        player.getInventory().insertStack(new ItemStack(Blocks.STONE, 10));

        PlacementResult result = PlacementService.place(harness.world(), pos, config, player);
        a.isTrue("薄板多槽同材质放置成功", result.success());
        a.equal("铺上 5 个槽", 5, result.appliedSlots().size());
        a.equal("同一种材质只扣一个", 9, countOf(player, Blocks.STONE.asItem()));
        a.equal("账本上只有一种材质", 1, result.paidMaterials().size());

        // --- 分层薄板：两种材质各扣一个
        BlockPos twoMaterialsPos = harness.next();
        PlacementConfig two = config
                .withSlot(LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.BODY),
                        Blocks.STONE.getDefaultState())
                .withSlot(LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.TOP_EDGE),
                        Blocks.OAK_PLANKS.getDefaultState());
        give(player, ModBlock.LAYERED_COPYCAT_BOARD.asItem(), 1);
        player.getInventory().insertStack(new ItemStack(Blocks.STONE, 3));
        player.getInventory().insertStack(new ItemStack(Blocks.OAK_PLANKS, 3));
        PlacementService.place(harness.world(), twoMaterialsPos, two, player);
        a.equal("石头扣一个", 2, countOf(player, Blocks.STONE.asItem()));
        a.equal("木板扣一个", 2, countOf(player, Blocks.OAK_PLANKS.asItem()));

        // --- 护栏四面同材质：只扣一个
        BlockPos railPos = harness.next();
        PlacementConfig rail = GuardrailCopycatAdapter.withFaces(
                        PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState()), 0xF)
                .withSlot(GuardrailParts.rowKey(Direction.NORTH), Blocks.STONE.getDefaultState())
                .withSlot(GuardrailParts.rowKey(Direction.EAST), Blocks.STONE.getDefaultState())
                .withSlot(GuardrailParts.rowKey(Direction.SOUTH), Blocks.STONE.getDefaultState())
                .withSlot(GuardrailParts.rowKey(Direction.WEST), Blocks.STONE.getDefaultState());
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 1);
        player.getInventory().insertStack(new ItemStack(Blocks.STONE, 8));
        PlacementResult railResult = PlacementService.place(harness.world(), railPos, rail, player);
        a.isTrue("护栏四面同材质放置成功", railResult.success());
        a.equal("护栏铺上 4 个横梁槽", 4, railResult.appliedSlots().size());
        a.equal("护栏同一种材质只扣一个", 7, countOf(player, Blocks.STONE.asItem()));

        // --- 只有一个结构方块：用完就没了
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 1);
        PlacementService.place(harness.world(), harness.next(), rail, player);
        a.equal("只有一个结构方块时被用掉", 0,
                countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem()));
    }

    // ================================================================ 9. 第三方 adapter

    /**
     * Create 与 Copycats+ 的 adapter。
     *
     * <p>这两块都<b>容忍缺席</b>：开发用的服务端里不一定装了 Copycats+，
     * 而「装了但没有 multistate 方块」也只是环境问题，不该让整个自检报失败。
     * 所以先查「有没有可测的东西」，有才断言；确实测不了的就只记一行说明。
     */
    private static void runThirdPartyAdapters(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 9. Create / Copycats+ adapter");

        // 用 null 玩家 = 「不用付账」语义（创造式），省掉一个假玩家
        testCreateAdapter(harness, a, log);
        testCreateVersusCopycats(a, log);
        testCopycatsMultistate(harness, a, log);
        testCopycatsOrdinary(harness, a, log);
    }

    private static void testCreateAdapter(PlacementHarness harness, Assertions a, List<String> log) {
        // 注意：create:copycat_base 是普通 Block，**不是** CopycatBlock
        // （它是「还没伪装时的底」，Create 自己的方块实体默认材质用的就是它），
        // 所以它本来就不该被 adapter 认领。真正可放置的是 step / panel / bars 这些。
        Block createBlock = findCreateCopycatBlock();
        if (createBlock == null) {
            log.add("   （注册表里找不到任何 Create 的 CopycatBlock，跳过 Create adapter 断言）");
            return;
        }
        log.add("   Create 伪装方块：" + Registries.BLOCK.getId(createBlock)
                + "，class=" + createBlock.getClass().getSimpleName()
                + "，解析到 " + PlacementAdapters.resolve(createBlock).map(x -> x.name()).orElse("<none>"));
        a.equal("Create 伪装方块 → Create adapter", "create:copycat",
                PlacementAdapters.resolve(createBlock).map(x -> x.name()).orElse(null));

        BlockPos pos = harness.next();
        PlacementConfig config = PlacementConfig.of(createBlock.getDefaultState())
                .withSlot(CreateCopycatAdapter.SLOT, Blocks.STONE.getDefaultState());
        PlacementResult result = PlacementService.place(harness.world(), pos, config);
        a.isTrue("Create 伪装放置成功", result.success());
        a.isTrue("Create 伪装方块进了世界", harness.stateAt(pos).isOf(createBlock));
        if (harness.blockEntityAt(pos)
                instanceof com.simibubi.create.content.decoration.copycat.CopycatBlockEntity entity) {
            a.isTrue("Create 材质写进去了", entity.getMaterial().isOf(Blocks.STONE));
            a.isTrue("Create 记下了被消耗的物品", entity.getConsumedItem().isOf(Blocks.STONE.asItem()));
            a.isTrue("Create 认为已经不是默认材质", entity.hasCustomMaterial());
        } else {
            a.check("Create 方块实体存在", false, String.valueOf(harness.blockEntityAt(pos)));
        }
    }

    /** 找一个真的继承 Create {@code CopycatBlock} 的方块（step / panel / 第三方新增的）。 */
    private static Block findCreateCopycatBlock() {
        for (Block block : Registries.BLOCK) {
            if (Registries.BLOCK.getId(block).getNamespace().equals("create")
                    && block instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) {
                return block;
            }
        }
        return null;
    }

    private static void testCopycatsMultistate(PlacementHarness harness, Assertions a, List<String> log) {
        if (!BuiltinAdapters.copycatsLoaded()) {
            log.add("   （这个开发服务端没装 Copycats+，跳过 Copycats+ 相关断言）");
            return;
        }
        Block multistate = findMultistateBlock();
        if (multistate == null) {
            log.add("   （装了 Copycats+ 但注册表里没有 IMultiStateCopycatBlock，跳过）");
            return;
        }
        log.add("   找到 multistate 方块：" + Registries.BLOCK.getId(multistate));

        var adapter = PlacementAdapters.resolve(multistate)
                .orElse(new CopycatsMultistateAdapter());
        a.equal("multistate → multistate adapter", "copycats:multistate", adapter.name());

        IMultiStateCopycatBlock multiBlock = (IMultiStateCopycatBlock) multistate;
        BlockState defaultState = multistate.getDefaultState();
        List<String> properties = new ArrayList<>(multiBlock.storageProperties());

        // 槽位数只看 storageProperties()，与状态无关
        var defaultSlots = adapter.slots(defaultState, PlacementConfig.of(defaultState));
        a.equal("槽位数 = storageProperties().size()", properties.size(), defaultSlots.size());
        for (String property : properties) {
            a.isTrue("槽位键名带上 property：" + property,
                    defaultSlots.stream().anyMatch(s -> s.key().equals(CopycatsMultistateAdapter.slotKey(property))));
        }
        log.add("   storageProperties = " + properties);

        // 关键：默认状态可能一个 part 都没有（Copycats+ 的 copycat_board 出厂时六个面全 false），
        // adapter 必须自己提升出一个可配置的展示状态，否则 GUI 里所有材质槽都会被标成
        // 「结构不存在」、玩家无从下手。这里只断言「提升之后一定有东西可配」，
        // 不假设任何具体方块的默认状态长什么样。
        BlockState preview = CopycatsMultistateAdapter.minimalValidState(defaultState, multiBlock);
        var slots = adapter.slots(preview, PlacementConfig.of(preview));
        long previewPresent = slots.stream().filter(s -> s.structure()).count();
        log.add("   默认状态 = " + defaultState
                + "（存在的 part = " + defaultSlots.stream().filter(s -> s.structure()).count() + "）");
        log.add("   展示状态 = " + preview + "，其中结构存在的槽 = " + previewPresent);
        a.isTrue("提升后的展示状态至少有一个 part 可见", previewPresent > 0);

        // 真正放一次：结构用 states 表里「至少一个 part 存在」的那个状态，
        // 给所有存在的 part 都配同一种材质，验证每种材质只扣一次、且材质真的写进方块实体。
        BlockPos pos = harness.next();
        PlacementConfig config = PlacementConfig.of(preview);
        for (var slot : slots) {
            if (slot.structure()) {
                config = config.withSlot(slot.key(), Blocks.STONE.getDefaultState());
            }
        }
        PlacementResult result = PlacementService.place(harness.world(), pos, config);
        a.isTrue("multistate 放置成功", result.success());
        a.isTrue("multistate 方块进了世界", harness.stateAt(pos).isOf(multistate));
        a.isTrue("至少铺上一个槽", !result.appliedSlots().isEmpty());
        log.add("   实际铺上的槽：" + result.appliedSlots() + "，跳过：" + result.skippedSlots());

        if (harness.blockEntityAt(pos) instanceof IMultiStateCopycatBlockEntity entity) {
            String first = result.appliedSlots().get(0);
            String property = CopycatsMultistateAdapter.propertyOf(first);
            a.isTrue("multistate 材质真的写进了方块实体",
                    entity.getMaterialItemStorage().hasCustomMaterial(property));
        } else {
            a.check("multistate 方块实体存在", false, String.valueOf(harness.blockEntityAt(pos)));
        }
    }

    /**
     * 装了 Copycats+ 之后，Create 的伪装方块会被它的 mixin 额外实现 {@code ICopycatBlock}。
     * 如果 Create adapter 不排在 Copycats+ 普通之前，这些方块就会被后者抢走，
     * 而它们的方块实体是 Create 的 {@code CopycatBlockEntity}，材质根本写不进去。
     */
    private static void testCreateVersusCopycats(Assertions a, List<String> log) {
        if (!BuiltinAdapters.copycatsLoaded()) {
            return;
        }
        log.add("   —— Create 与 Copycats+ 的判据重叠检查");
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock)) {
                continue;
            }
            if (!Registries.BLOCK.getId(block).getNamespace().equals("create")) {
                continue;
            }
            String resolved = PlacementAdapters.resolve(block).map(x -> x.name()).orElse("<none>");
            boolean alsoCopycats = block instanceof com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
            log.add("   " + Registries.BLOCK.getId(block) + " → " + resolved
                    + "（同时是 ICopycatBlock=" + alsoCopycats + "）");
            a.equal("Create 的 " + Registries.BLOCK.getId(block).getPath() + " 仍由 Create adapter 处理",
                    "create:copycat", resolved);
        }
    }

    /**
     * Copycats+ <b>自己</b>的单材质方块（方块实体是 {@code CCCopycatBlockEntity}）。
     *
     * <p>注意不能拿 Create 的方块来测：装了 Copycats+ 之后那些方块也被 mixin 成
     * {@code ICopycatBlock}，但它们必须走 Create adapter（见 {@link #testCreateVersusCopycats}）。
     * 所以这里只挑 {@code copycats} 命名空间下的单材质方块。
     */
    private static void testCopycatsOrdinary(PlacementHarness harness, Assertions a, List<String> log) {
        if (!BuiltinAdapters.copycatsLoaded()) {
            return;
        }
        // 先把「装了 Copycats+ 之后到底有哪些方块自称 ICopycatBlock」全部打出来。
        // 这直接回答一个关键问题：这个 adapter 的真实作用域有多宽。
        for (Block block : Registries.BLOCK) {
            if (block instanceof com.copycatsplus.copycats.foundation.copycat.ICopycatBlock) {
                log.add("   ICopycatBlock: " + Registries.BLOCK.getId(block)
                        + " class=" + block.getClass().getName()
                        + " isCreateCopycatBlock="
                        + (block instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock)
                        + " isMultiState=" + (block instanceof IMultiStateCopycatBlock)
                        + " → " + PlacementAdapters.resolve(block).map(x -> x.name()).orElse("<none>"));
            }
        }
        Block ordinary = findCopycatsOrdinaryBlock();
        if (ordinary == null) {
            log.add("   （Copycats+ 里没有「非 Create CopycatBlock」的单材质方块："
                    + "它的普通伪装方块都包在 Create 的 CopycatBlock 之上，"
                    + "所以在装了 Copycats+ 的环境里 ordinary adapter 天然不会被命中——"
                    + "这正是 Create adapter 必须优先于它的原因）");
            return;
        }
        String id = Registries.BLOCK.getId(ordinary).toString();
        log.add("   Copycats+ 单材质方块：" + id
                + "，解析到 " + PlacementAdapters.resolve(ordinary).map(x -> x.name()).orElse("<none>"));
        a.equal("Copycats+ 单材质方块 → ordinary adapter", "copycats:ordinary",
                PlacementAdapters.resolve(ordinary).map(x -> x.name()).orElse(null));

        var adapter = PlacementAdapters.resolve(ordinary).orElseThrow();
        String key = CopycatsOrdinaryAdapter.slotKey(ordinary);
        var slots = adapter.slots(ordinary.getDefaultState(), PlacementConfig.of(ordinary.getDefaultState()));
        a.equal("ordinary adapter 只暴露一个槽", 1, slots.size());
        a.equal("槽键名带方块注册名", key, slots.get(0).key());

        BlockPos pos = harness.next();
        PlacementConfig config = PlacementConfig.of(ordinary.getDefaultState())
                .withSlot(key, Blocks.STONE.getDefaultState());
        PlacementResult result = PlacementService.place(harness.world(), pos, config);
        a.isTrue("Copycats+ 单材质方块放置成功", result.success());
        a.isTrue("方块进了世界", harness.stateAt(pos).isOf(ordinary));
        a.isTrue("铺上了材质槽", result.appliedSlots().contains(key));
        if (harness.blockEntityAt(pos)
                instanceof com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity entity) {
            a.isTrue("Copycats+ 材质真的写进了方块实体", entity.getMaterial().isOf(Blocks.STONE));
            a.isTrue("Copycats+ 记下了被消耗的物品",
                    entity.getConsumedItem().isOf(Blocks.STONE.asItem()));
        } else {
            a.check("Copycats+ 方块实体是 ICopycatBlockEntity", false,
                    String.valueOf(harness.blockEntityAt(pos)));
        }
    }

    /**
     * 找一个「不是 Create {@code CopycatBlock}、也不 multistate」的单材质伪装方块。
     *
     * <p>这才是 {@link CopycatsOrdinaryAdapter} 真正的适用对象（方块实体是
     * {@code CCCopycatBlockEntity}）。如果 Copycats+ 里不存在这样的方块，说明它的普通伪装方块
     * 都建在 Create 的 {@code CopycatBlock} 之上——那 ordinary adapter 在装了 Copycats+ 的环境里
     * 永远不会被命中，它存在的意义是「挡住第三方直接实现 {@code ICopycatBlock} 的方块」。
     */
    private static Block findCopycatsOrdinaryBlock() {
        for (Block block : Registries.BLOCK) {
            if (!Registries.BLOCK.getId(block).getNamespace().equals("copycats")) {
                continue;
            }
            if (block instanceof com.copycatsplus.copycats.foundation.copycat.ICopycatBlock
                    && !(block instanceof IMultiStateCopycatBlock)
                    && !(block instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock)) {
                return block;
            }
        }
        return null;
    }

    // ================================================================ 10. 工具物品 / 配置 NBT

    /**
     * 工具物品携带预设的那条链路：ItemStack NBT ↔ {@link PlacementConfig}。
     *
     * <p>这是需求 9（配置跟随这一把物品保存）的代码级验证：写进去、读回来、换一把不串味、
     * 清空真的清掉、坏数据不会崩。
     */
    private static void runItemAndConfigNbt(Assertions a, List<String> log) {
        log.add("== 10. 工具物品 / 配置 NBT");

        a.isTrue("伪装放置器已注册",
                Registries.ITEM.get(new Identifier("maris-decoration", CopycatPlacerItem.ID))
                        instanceof CopycatPlacerItem);

        ItemStack tool = new ItemStack(ModItem.COPYCAT_PLACER);
        a.isTrue("新工具没有配置", !PlacementConfigs.hasConfig(tool));
        a.equal("新工具的选中方块为空", null, PlacementConfigs.selectedState(tool));

        PlacementConfig config = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x5")
                .withSlot(LayeredBoardSlots.materialKey(FaceDir.UP, BoardLayer.OUTER, BoardArea.BODY),
                        Blocks.OAK_PLANKS.getDefaultState())
                .withSlot(LayeredBoardSlots.windowKey(FaceDir.UP), Blocks.GLASS.getDefaultState());

        PlacementConfigs.write(tool, config);
        a.isTrue("写进去之后 hasConfig 为真", PlacementConfigs.hasConfig(tool));
        a.equal("读回来的配置与写入的一致", config, PlacementConfigs.read(tool));
        a.equal("选中方块读得出来", ModBlock.LAYERED_COPYCAT_BOARD,
                PlacementConfigs.selectedState(tool).getBlock());

        // 换一把工具：配置必须<b>不</b>跟着走（这一点靠「存在物品 NBT 里」保证）
        ItemStack other = new ItemStack(ModItem.COPYCAT_PLACER);
        a.isTrue("另一把工具没有配置（配置不共享）", !PlacementConfigs.hasConfig(other));

        // 复制物品堆（模拟快捷栏里同一把工具被复制）：NBT 一起复制
        ItemStack copy = tool.copy();
        a.equal("物品堆复制后配置仍在", config, PlacementConfigs.read(copy));

        // 清空
        PlacementConfigs.clear(tool);
        a.isTrue("清空后没有配置", !PlacementConfigs.hasConfig(tool));
        a.equal("清空后子标签被移除", null, tool.getSubNbt(PlacementConfigs.NBT_KEY));

        // 坏数据：手写一份属性名不存在的方块状态。
        // 原版 NbtHelper 对这种配置是「静默忽略未知属性」，所以挡它必须靠我们自己在读的时候校验。
        ItemStack broken = new ItemStack(ModItem.COPYCAT_PLACER);
        NbtCompound bad = new NbtCompound();
        NbtCompound block = new NbtCompound();
        block.putString("Name", "maris-decoration:copycat_guardrail");
        NbtCompound properties = new NbtCompound();
        properties.putString("definitely_not_a_property", "true");
        block.put("Properties", properties);
        bad.put("block", block);
        broken.setSubNbt(PlacementConfigs.NBT_KEY, bad);
        PlacementConfig brokenConfig = PlacementConfigs.read(broken);
        a.isTrue("属性名不存在的配置退化成「没有方块」而不是抛异常",
                brokenConfig.state() == null);
        a.isFalse("这种配置被判为不合法", PlacementConfigs.isWellFormed(brokenConfig));

        // 合法属性名要能正常读出来（别把正常的也挡了）
        ItemStack good = new ItemStack(ModItem.COPYCAT_PLACER);
        NbtCompound goodNbt = new NbtCompound();
        NbtCompound goodBlock = new NbtCompound();
        goodBlock.putString("Name", "maris-decoration:copycat_guardrail");
        NbtCompound goodProps = new NbtCompound();
        goodProps.putString("north", "true");
        goodBlock.put("Properties", goodProps);
        goodNbt.put("block", goodBlock);
        good.setSubNbt(PlacementConfigs.NBT_KEY, goodNbt);
        BlockState goodState = PlacementConfigs.read(good).state();
        a.notNull("合法属性名正常读出来", goodState);
        a.isTrue("读回来的 north 是 true",
                goodState != null && CopycatGuardrailBlock.hasFace(goodState, Direction.NORTH));

        // 服务端校验：正常配置必须通过，石头必须被拒
        a.isTrue("正常的伪装方块配置通过校验", PlacementConfigs.isWellFormed(config));
        a.isFalse("普通方块配置不通过校验",
                PlacementConfigs.isWellFormed(PlacementConfig.of(Blocks.STONE.getDefaultState())));
        a.isFalse("空配置不通过校验", PlacementConfigs.isWellFormed(PlacementConfig.EMPTY));
    }

    // ================================================================ 11. GUI 逻辑

    /**
     * GUI 屏幕本身测不了（需要客户端上下文），但它依赖的那层逻辑全部在这里：
     * 方块列表怎么生成、属性怎么枚举、WATERLOGGED 有没有被排除、virtual property 怎么循环、
     * 材质槽怎么列。这些正是「GUI 不硬编码方块类型」的验证点。
     */
    private static void runGuiLogic(Assertions a, List<String> log) {
        log.add("== 11. GUI 逻辑（列表 / 属性 / virtual / 材质）");

        ItemStack tool = new ItemStack(ModItem.COPYCAT_PLACER);
        PlacerEditState edit = new PlacerEditState(tool);

        // --- 方块列表
        List<PlacerEditState.Entry> list = edit.blockList();
        log.add("   可选方块数 = " + list.size());
        a.isTrue("列表非空", !list.isEmpty());
        a.isTrue("列表里有本 mod 的护栏",
                list.stream().anyMatch(e -> e.block() == ModBlock.COPYCAT_GUARDRAIL));
        a.isTrue("列表里有本 mod 的分层薄板",
                list.stream().anyMatch(e -> e.block() == ModBlock.LAYERED_COPYCAT_BOARD));
        a.isFalse("列表里没有普通方块（石头）",
                list.stream().anyMatch(e -> e.block() == Blocks.STONE));
        a.isTrue("列表每一项都有 BlockItem",
                list.stream().allMatch(e -> e.block().asItem() instanceof BlockItem));
        a.isTrue("列表每一项都被 adapter 认领",
                list.stream().allMatch(e -> PlacementAdapters.isPlaceable(e.block())));
        // 排序必须稳定可复现
        List<String> ids = list.stream().map(e -> e.id().toString()).toList();
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.naturalOrder());
        a.equal("列表按注册名排序", sorted, ids);

        // 过滤
        edit.setFilter("guardrail");
        a.isTrue("过滤生效", edit.filteredBlocks().size() < list.size());
        a.isTrue("过滤结果全都匹配",
                edit.filteredBlocks().stream().allMatch(e -> e.id().toString().contains("guardrail")));
        edit.setFilter("");

        // --- 选方块 → 配置被重置成它的默认状态
        edit.selectBlock(ModBlock.LAYERED_COPYCAT_BOARD);
        a.equal("选方块后选中状态是它的默认状态",
                ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState(), edit.state());
        a.isTrue("选方块后 adapter 解析成功",
                edit.adapter() == PlacementAdapters.resolve(ModBlock.LAYERED_COPYCAT_BOARD).orElseThrow());

        // --- 属性枚举：WATERLOGGED 必须被排除
        //
        // 注意薄板自己的方块状态里<b>只有</b> WATERLOGGED（12 层占用、窗都在方块实体的掩码里），
        // 所以「属性区为空」对薄板是正确的表现——结构配置全在 virtual property 上。
        // 真正能验证属性枚举的是护栏（四个方向 BooleanProperty）。
        BlockState boardDisplay = edit.displayState();
        a.isTrue("薄板确实有 WATERLOGGED 属性（不是因为方块没属性才空）",
                boardDisplay.contains(Properties.WATERLOGGED));
        List<PropertySpec> boardProps = visiblePropsOf(boardDisplay);
        a.equal("薄板的可编辑属性为空（结构全在 virtual property 上）", 0, boardProps.size());

        edit.selectBlock(ModBlock.COPYCAT_GUARDRAIL);
        BlockState railDisplay = edit.displayState();
        List<PropertySpec> railProps = visiblePropsOf(railDisplay);
        a.equal("护栏有四个可编辑属性（四个方向）", 4, railProps.size());
        a.isFalse("WATERLOGGED 没有出现在可编辑属性里",
                railProps.stream().anyMatch(p -> p.property() == Properties.WATERLOGGED));
        a.isTrue("四个属性都是布尔（GUI 会渲染成开关）",
                railProps.stream().allMatch(PropertySpec::isBoolean));
        log.add("   护栏可编辑属性 = " + railProps.stream().map(PropertySpec::name).toList());

        // 布尔属性的切换：取反后必须真的写进方块状态
        PropertySpec north = railProps.stream()
                .filter(p -> p.name().equals(Direction.NORTH.getName())).findFirst().orElseThrow();
        boolean before = railDisplay.get((BooleanProperty) north.property());
        BlockState flipped = railDisplay.with((BooleanProperty) north.property(), !before);
        a.equal("布尔属性取反生效", !before, flipped.get((BooleanProperty) north.property()));

        // --- virtual property：分层薄板是「12 个层开关 + 动态开窗 + 动态交汇点」
        edit.selectBlock(ModBlock.LAYERED_COPYCAT_BOARD);
        CopycatPlacementAdapter boardAdapter = edit.adapter();
        List<VirtualSpec> boardSpecs = boardAdapter.virtualSpecs(edit.config());
        long layerToggles = boardSpecs.stream().filter(s -> s.key().startsWith("layer.")).count();
        long windowToggles = boardSpecs.stream().filter(s -> s.key().startsWith("window.")).count();
        long junctionChoices = boardSpecs.stream().filter(s -> s.key().startsWith("junction.")).count();
        log.add("   分层薄板结构项：层开关 " + layerToggles + "，开窗 " + windowToggles
                + "，交汇点 " + junctionChoices + "（默认只有下面外层）");
        a.equal("默认结构下有 12 个层开关", 12, (int) layerToggles);
        a.isTrue("没有板的方向不出现开窗项（默认只有下面有板）", windowToggles <= 1);
        a.isTrue("单块板的角落交汇点会出现（候选 ≥ 2 条边）", junctionChoices >= 1);

        // 逐项开关：打开一个层只动这一位
        PlacementConfig beforeToggle = edit.config();
        VirtualSpec upOuter = boardSpecs.stream()
                .filter(s -> s.key().equals("layer.up.outer")).findFirst().orElseThrow();
        edit.update(upOuter.with(edit.config(), upOuter.next(edit.config())));
        a.isTrue("打开上面外层之后这一位是开的",
                LayeredBoardCopycatAdapter.slotEnabled(edit.config(), FaceDir.UP, BoardLayer.OUTER));
        a.isTrue("其它层原样保留（下面外层仍然开着）",
                LayeredBoardCopycatAdapter.slotEnabled(edit.config(), FaceDir.DOWN, BoardLayer.OUTER));
        a.isTrue("点一下之后出现了 UP 的开窗项",
                boardAdapter.virtualSpecs(edit.config()).stream()
                        .anyMatch(s -> s.key().equals("window.up")));
        a.equal("结构项改动写进了工具 NBT", edit.config(), PlacementConfigs.read(tool));

        // 关掉 UP 的两层 → 开窗项消失；再开回来 → 之前那个窗的值还在
        VirtualSpec upWindow = boardAdapter.virtualSpecs(edit.config()).stream()
                .filter(s -> s.key().equals("window.up")).findFirst().orElseThrow();
        edit.update(upWindow.with(edit.config(), upWindow.next(edit.config())));
        boolean upWindowValue = LayeredBoardCopycatAdapter.windowEnabled(edit.config(), FaceDir.UP);
        edit.update(LayeredBoardCopycatAdapter.withSlot(edit.config(), FaceDir.UP, BoardLayer.OUTER, false));
        a.isFalse("两层都关掉之后 UP 的开窗项消失",
                boardAdapter.virtualSpecs(edit.config()).stream()
                        .anyMatch(s -> s.key().equals("window.up")));
        edit.update(LayeredBoardCopycatAdapter.withSlot(edit.config(), FaceDir.UP, BoardLayer.OUTER, true));
        a.equal("UP 的开窗值在隐藏期间没有被重置", upWindowValue,
                LayeredBoardCopycatAdapter.windowEnabled(edit.config(), FaceDir.UP));
        edit.update(beforeToggle);

        // --- virtual property 的「内部键 / 内部值」与「显示文本」必须分开
        // 配置里存的是 occupancy=0x… / guardrail_faces=0x… 这类内部表示，交汇点还存着槽位名；
        // 界面上只允许画 LabelPart 里的文本。这一段用代码把两者钉开。
        checkStructureItemDisplay(a, log, ModBlock.COPYCAT_GUARDRAIL);
        checkStructureItemDisplay(a, log, ModBlock.LAYERED_COPYCAT_BOARD);

        // --- 材质槽列表：全量显示 + 无效项置灰（与本 GUI 里 copycat_board 的展示方式一致）
        edit.selectBlock(ModBlock.COPYCAT_GUARDRAIL);
        var guardrailDisplay = edit.displayState();
        var slots = edit.adapter().slots(guardrailDisplay, edit.config());
        a.equal("护栏 adapter 给全量 8 个槽（4 横梁 + 4 角柱）", 8, slots.size());
        a.isTrue("槽位带翻译键", slots.stream().allMatch(s -> s.labelKey() != null && !s.labelKey().isBlank()));
        a.equal("界面列出全量 8 个槽（不做过滤）", 8, guiSlotKeys(edit.adapter(), guardrailDisplay, edit.config()).size());
        a.equal("其中当前有效（有几何）的 3 个", 3,
                (int) slots.stream().filter(AdapterSlot::structure).count());
        a.isTrue("无效槽是可以被界面识别出来置灰的数据状态",
                slots.stream().anyMatch(slot -> !slot.structure()));

        // 材质写入 / 清除
        String key = slots.get(0).key();
        edit.update(edit.config().withSlot(key, Blocks.STONE.getDefaultState()));
        a.isTrue("材质写进了配置", edit.config().slots().containsKey(key));
        edit.update(edit.config().withoutSlot(key));
        a.isFalse("材质被移除", edit.config().slots().containsKey(key));

        // --- 清空选择
        edit.clearSelection();
        a.equal("清空后没有方块", null, edit.state());
        a.isTrue("清空后工具 NBT 里也没有配置", !PlacementConfigs.hasConfig(tool));
    }

    /** 把 {@code OrderedText} 里真正会被画出来的字符取出来（诊断用）。 */
    private static String orderedTextOf(net.minecraft.text.Text text) {
        StringBuilder out = new StringBuilder();
        net.minecraft.text.OrderedText ordered = text.asOrderedText();
        ordered.accept(new net.minecraft.text.CharacterVisitor() {
            @Override
            public boolean accept(int index, net.minecraft.text.Style style, int codePoint) {
                out.appendCodePoint(codePoint);
                return true;
            }
        });
        return out.toString();
    }

    /**
     * 结构项的「内部标识」与「显示文本」必须彻底分开。
     *
     * <p>内部标识是配置 / NBT 里的东西：{@code occupancy=0x1}、{@code guardrail_faces=0x8}、
     * 交汇点的槽位名 {@code down.outer.top_edge}。显示文本只允许来自
     * {@link VirtualSpec.LabelPart}。这一段用代码把两者钉开：
     * <ul>
     *   <li>每一段显示文本都非空、且不是裸数值 / 十六进制 / 内部键名；</li>
     *   <li>每一段都有翻译键（配合第 18 段的「翻译键必须在打包语言文件里」，界面不可能出现裸 key）；</li>
     *   <li>候选项的 id 只写进配置、不出现在显示文本里；</li>
     *   <li>配置里放一个候选集合里没有的值时，{@code current} 返回 {@code null}（界面显示「配置值无效」），
     *       而 {@code next} 归一化到第一个候选项。</li>
     * </ul>
     */
    private static void checkStructureItemDisplay(Assertions a, List<String> log, Block block) {
        CopycatPlacementAdapter adapter = PlacementAdapters.resolve(block).orElseThrow();
        PlacementConfig config = adapter.defaultConfig(block);
        int specs = 0;
        for (VirtualSpec spec : adapter.virtualSpecs(config)) {
            specs++;
            String tag = adapter.name() + " 的 `" + spec.key() + "`";
            a.isTrue(tag + " 有标题文本", !spec.label().isEmpty());
            checkLabelParts(a, tag + " 的标题", spec.label(), spec.key());
            a.isTrue(tag + " 至少有一个候选项", !spec.options().isEmpty());
            for (VirtualSpec.Option option : spec.options()) {
                a.isTrue(tag + " 的候选项 " + option.id() + " 有显示文本", !option.label().isEmpty());
                checkLabelParts(a, tag + " 的候选项 " + option.id(), option.label(), spec.key());
                for (VirtualSpec.LabelPart part : option.label()) {
                    a.check(tag + " 的候选项显示文本不是内部 id",
                            !part.labelText().contains(option.id()),
                            "labelText=" + part.labelText() + "，id=" + option.id());
                }
            }

            // 配置里放一个候选集合里没有的值：界面显示「无效」，点击归一化到第一个候选
            VirtualSpec.Option bogus = VirtualSpec.Option.of("no.such.slot", "test.bogus", "bogus");
            PlacementConfig broken = spec.with(config, bogus);
            if (spec.current(broken) == null) {
                a.equal(tag + "：非法值不进候选（界面显示「无效」）", -1, spec.indexOfCurrent(broken));
                a.equal(tag + "：非法值点击后归一化到第一个候选项",
                        spec.options().get(0).id(), spec.next(broken).id());
            } else {
                log.add("   （" + tag + " 会把非法值归一化，跳过非法值断言）");
            }
        }
        a.isTrue(adapter.name() + " 有结构项可检", specs > 0);
        log.add("   " + adapter.name() + " 结构项 " + specs + " 项，显示文本检查通过");
    }

    /** 一组显示文本：非空、有翻译键、不是裸数值 / 十六进制 / 内部键名。 */
    private static void checkLabelParts(Assertions a, String tag, List<VirtualSpec.LabelPart> parts,
                                        String internalKey) {
        for (VirtualSpec.LabelPart part : parts) {
            a.check(tag + " 的显示文本非空",
                    part.labelText() != null && !part.labelText().isBlank(), "labelText=" + part.labelText());
            a.check(tag + " 的翻译键非空",
                    part.labelKey() != null && !part.labelKey().isBlank(), "labelKey=" + part.labelKey());
            a.check(tag + " 的显示文本不是裸数值 / 十六进制",
                    !part.labelText().matches("(?i)^(0x[0-9a-f]+|[0-9]+)$"),
                    "labelText=" + part.labelText());
            a.check(tag + " 的显示文本不是内部键名",
                    !part.labelText().equals(internalKey) && !part.labelKey().equals(internalKey),
                    "labelText=" + part.labelText() + "，key=" + internalKey);
        }
    }

    // ================================================================ 12. 服务端校验 + 右键放置
    /**
     * 需求 10~14 的代码级验证：把「手持工具右键方块」那条链路按 {@code CopycatPlacerItem#useOnBlock}
     * 的写法走一遍。
     *
     * <p>不直接调 {@code useOnBlock}（那需要 {@code ItemUsageContext}，构造起来要一个客户端玩家），
     * 而是走它内部完全相同的三步：读工具 NBT → {@code PlacementService.place} → 把消息分类出来。
     * 这样测到的就是真实路径上的行为，而不是复制出来的一份逻辑。
     */
    private static void runToolPlacement(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 12. 服务端校验 + 手持工具放置");

        ServerPlayerEntity player = harness.fakePlayer(0, 0, 0);
        creative(player, false);

        // --- 没选方块：提示「请先选择伪装方块」，不放置
        ItemStack emptyTool = new ItemStack(ModItem.COPYCAT_PLACER);
        BlockPos emptyPos = harness.next();
        PlacementResult noBlock = PlacementService.place(harness.world(), emptyPos,
                PlacementConfigs.read(emptyTool), player);
        a.equal("没选方块时失败原因是 NO_BLOCK", PlacementFailure.NO_BLOCK, noBlock.failure());
        a.equal("没选方块时的提示是「请先选择伪装方块」", Text.translatable(PlacementFeedback.NO_BLOCK_KEY),
                PlacementFeedback.messageFor(noBlock));
        a.isAir("没选方块时不放置", harness.stateAt(emptyPos));

        // --- 生存缺结构伪装板：提示「伪装板不足」，材质一个不扣
        ItemStack tool = new ItemStack(ModItem.COPYCAT_PLACER);
        String rowKey = GuardrailParts.rowKey(Direction.NORTH);
        PlacementConfig config = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState())
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, StructureMasks.write(northBit()))
                .withSlot(rowKey, Blocks.STONE.getDefaultState());
        PlacementConfigs.write(tool, config);

        BlockPos noBoardPos = harness.next();
        give(player, Blocks.STONE.asItem(), 4);
        int stoneBefore = countOf(player, Blocks.STONE.asItem());
        PlacementResult noBoard = PlacementService.place(harness.world(), noBoardPos,
                PlacementConfigs.read(tool), player);
        a.equal("缺伪装板时失败原因是 NO_STRUCTURE_ITEM",
                PlacementFailure.NO_STRUCTURE_ITEM, noBoard.failure());
        a.equal("缺伪装板时的提示是「伪装板不足」", Text.translatable(PlacementFeedback.NO_STRUCTURE_KEY),
                PlacementFeedback.messageFor(noBoard));
        a.equal("缺伪装板时材质一个不扣", stoneBefore, countOf(player, Blocks.STONE.asItem()));
        a.isAir("缺伪装板时不放置", harness.stateAt(noBoardPos));

        // --- 有板有料：放置 + 材质铺上 + 各扣一个
        BlockPos okPos = harness.next();
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 2);
        player.getInventory().insertStack(new ItemStack(Blocks.STONE, 4));
        PlacementResult ok = PlacementService.place(harness.world(), okPos,
                PlacementConfigs.read(tool), player);
        a.isTrue("手持工具放置成功", ok.success());
        a.isTrue("方块进了世界", harness.stateAt(okPos).isOf(ModBlock.COPYCAT_GUARDRAIL));
        a.equal("成功时不播报任何消息", null, PlacementFeedback.messageFor(ok));
        a.equal("结构方块扣一个", 1, countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem()));
        if (harness.blockEntityAt(okPos) instanceof CopycatGuardrailBlockEntity rail) {
            a.isTrue("材质铺上了", rail.material(rowKey).isOf(Blocks.STONE));
        }

        // --- 有板没料：仍然放置成功、槽留空、<b>不播报失败</b>
        BlockPos noMaterialPos = harness.next();
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 2);
        PlacementResult noMaterial = PlacementService.place(harness.world(), noMaterialPos,
                PlacementConfigs.read(tool), player);
        a.isTrue("缺材质仍然放置成功", noMaterial.success());
        a.equal("缺材质时也不播报任何消息（需求 13）", null, PlacementFeedback.messageFor(noMaterial));
        a.isTrue("方块仍然放进去了", harness.stateAt(noMaterialPos).isOf(ModBlock.COPYCAT_GUARDRAIL));

        // --- 配置跟着工具走：换一把工具就没有配置，放置会退化成 NO_BLOCK
        ItemStack anotherTool = new ItemStack(ModItem.COPYCAT_PLACER);
        a.isTrue("另一把工具没有配置", !PlacementConfigs.hasConfig(anotherTool));
        a.equal("用没配置的工具放置 → NO_BLOCK", PlacementFailure.NO_BLOCK,
                PlacementService.place(harness.world(), harness.next(),
                        PlacementConfigs.read(anotherTool), player).failure());

        // --- 创造模式：有配置就够，不要求库存也不消耗
        ItemStack creativeTool = new ItemStack(ModItem.COPYCAT_PLACER);
        PlacementConfigs.write(creativeTool, config);
        ServerPlayerEntity creativePlayer = harness.fakePlayer(0, 0, 0);
        creative(creativePlayer, true);
        clearInventory(creativePlayer);
        BlockPos creativePos = harness.next();
        PlacementResult creativeResult = PlacementService.place(harness.world(), creativePos,
                PlacementConfigs.read(creativeTool), creativePlayer);
        a.isTrue("创造模式无需库存即可放置", creativeResult.success());
        a.isFalse("创造模式没扣结构方块", creativeResult.structurePaid());
        a.equal("创造模式没扣材质", 0, creativeResult.paidMaterials().size());
        if (harness.blockEntityAt(creativePos) instanceof CopycatGuardrailBlockEntity rail) {
            a.isTrue("创造模式材质也照常铺上", rail.material(rowKey).isOf(Blocks.STONE));
        }
    }

    // ================================================================ 20b. 竖直面附着

    /**
     * 竖直外层面能不能被原版梯子当作「完整实心面」。
     *
     * <h2>原版的判据到底是什么</h2>
     * {@code LadderBlock#canPlaceOn} 只有一条：
     * {@code state.isSideSolidFullSquare(world, pos, facing.getOpposite())}。
     * 它在 1.20.1 里落到 {@code SideShapeType.FULL.matches} →
     * {@code Block.isFaceFullSquare(state.getSidesShape(...), side)}，
     * 而 {@code getSidesShape} 默认返回的就是<b>碰撞箱</b>。所以：
     * 判据看的是「碰撞箱在那个面上是不是铺满 16×16」，
     * 不是 {@code getOutlineShape}，也不是「这个方块上贴了什么材质」。
     *
     * <h2>这一段的由来</h2>
     * 薄板的碰撞箱来自方块实体的占用掩码（1px 厚的一层板），几何上它在那一面确实是完整正方形。
     * 但原版会为每个方块状态<b>预烤</b>一份 {@code ShapeCache}——用的是没有方块实体的空视图，
     * 于是 {@code isSideSolidFullSquare} 读到一个恒为 false 的缓存值，梯子贴不上去。
     * 修法是给方块声明 {@code dynamicShape()}（见 {@code LayeredCopycatBoardBlock#getOutlineShape}）。
     *
     * <p>这一段的每一条都直接读真实世界的碰撞箱与 {@code isSideSolidFullSquare}，
     * 并用<b>同一份判据</b>去问石头（对照组），保证「薄板 == 完整方块」。
     * 刻意不经过 {@code PlacementService}：梯子是原版方块，这里验的是「本 mod 的方块有没有把
     * 方块逻辑该看到的东西给出去」。
     */
    private static void runFaceAttachments(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 20b. 竖直面附着（原版梯子的 isSideSolidFullSquare）");

        ServerWorld world = harness.world();

        // --- ① 对照组：完整方块六个面都是完整实心面
        BlockPos stone = harness.nextBare();
        world.setBlockState(stone, Blocks.STONE.getDefaultState());
        for (Direction side : Direction.values()) {
            a.isTrue("对照：完整方块 " + side + " 面是完整实心面", canAttachTo(world, stone, side));
        }

        int placed = 0;
        for (FaceDir face : FaceDir.values()) {
            Direction support = face.toDirection();
            boolean vertical = support.getAxis() != Direction.Axis.Y;
            int occupancy = LayeredBoardSlots.slotBitMask(face, BoardLayer.OUTER);

            // --- ② 一块只开这一个外层面的薄板
            BlockPos boardPos = harness.nextBare();
            world.setBlockState(boardPos, ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState());
            if (!(world.getBlockEntity(boardPos) instanceof LayeredCopycatBoardBlockEntity board)) {
                a.check("薄板方块实体存在（" + face.getName() + ".outer）", false,
                        String.valueOf(world.getBlockEntity(boardPos)));
                continue;
            }
            board.setOccupancy(occupancy);
            board.setMaterial(LayeredBoardSlots.materialKey(face, BoardLayer.OUTER, BoardArea.BODY),
                    Blocks.OAK_PLANKS.getDefaultState(), null);

            VoxelShape collision = world.getBlockState(boardPos)
                    .getCollisionShape(world, boardPos, ShapeContext.absent());
            log.add("   " + face.getName() + ".outer：碰撞箱 = " + collision.getBoundingBox()
                    + "，六面实心 = " + solidFaceSummary(world, boardPos));
            a.isFalse("薄板有碰撞箱（" + face.getName() + ".outer）", collision.isEmpty());
            a.equal("薄板的形状与几何函数一致（" + face.getName() + ".outer）",
                    LayeredBoardParts.shape(occupancy).getBoundingBox(), collision.getBoundingBox());
            if (!vertical) {
                // 上 / 下面：原版梯子只有四个水平朝向，贴不到水平面上；这里只记录形状
                log.add("   （" + face.getName() + " 是水平面，梯子不适用）");
                world.setBlockState(boardPos, Blocks.AIR.getDefaultState());
                continue;
            }

            // --- ③ 找到这块板「完整实心」的那一面（1px 板只有外侧那一面是完整正方形）
            Direction solid = null;
            for (Direction side : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                if (canAttachTo(world, boardPos, side)) {
                    solid = side;
                    break;
                }
            }
            a.notNull("薄板 " + face.getName() + ".outer 至少有一个竖直面是完整实心面（能贴梯子的前提）",
                    solid);
            if (solid == null) {
                world.setBlockState(boardPos, Blocks.AIR.getDefaultState());
                continue;
            }
            // 与完整方块在同一方向上的判据必须一致
            a.equal("薄板 " + face.getName() + ".outer 在 " + solid + " 面的附着判据与完整方块一致",
                    canAttachTo(world, stone, solid), canAttachTo(world, boardPos, solid));

            // --- ④ 梯子放在这个实心面外侧，朝向背向薄板：
            //        梯子格 = 薄板格 + 实心面的方向，梯子 FACING = 实心面的方向
            BlockPos ladderPos = boardPos.offset(solid);
            Direction ladderFacing = solid;
            world.setBlockState(ladderPos, Blocks.AIR.getDefaultState());
            BlockState ladder = Blocks.LADDER.getDefaultState()
                    .with(LadderBlock.FACING, ladderFacing)
                    .with(LadderBlock.WATERLOGGED, false);

            world.setBlockState(ladderPos, ladder, Block.NOTIFY_ALL);
            BlockState inWorld = world.getBlockState(ladderPos);
            log.add("   " + face.getName() + ".outer：实心面=" + solid + "，梯子 facing=" + ladderFacing
                    + "，canPlaceAt=" + ladder.canPlaceAt(world, ladderPos)
                    + "，放进世界之后 = " + inWorld.getBlock());
            a.isTrue("薄板 " + face.getName() + ".outer 能真的贴住梯子", inWorld.isOf(Blocks.LADDER));
            if (inWorld.isOf(Blocks.LADDER)) {
                a.equal("贴住的梯子朝向正确", ladderFacing, inWorld.get(LadderBlock.FACING));
                placed++;
                // 邻居更新之后梯子仍然认为自己贴得住
                world.setBlockState(boardPos, world.getBlockState(boardPos), Block.NOTIFY_ALL);
                a.isTrue("邻居更新之后梯子仍然在（" + face.getName() + "）",
                        world.getBlockState(ladderPos).isOf(Blocks.LADDER));
            }

            world.setBlockState(ladderPos, Blocks.AIR.getDefaultState());
            world.setBlockState(boardPos, Blocks.AIR.getDefaultState());
        }
        a.equal("四个竖直方向都能真正贴住梯子", 4, placed);

        // --- ⑤ 反过来：一个槽都没有时形状为空，不该凭空支持附着
        BlockPos emptyBoard = harness.nextBare();
        world.setBlockState(emptyBoard, ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState());
        a.isTrue("空占用（一个槽都没有）时形状为空",
                world.getBlockState(emptyBoard)
                        .getCollisionShape(world, emptyBoard, ShapeContext.absent()).isEmpty());
        a.isFalse("空占用的薄板不支持任何一面的附着",
                canAttachTo(world, emptyBoard, Direction.NORTH));

        // --- ⑥ 12 个槽全占：每一面都应当是完整实心面
        BlockPos fullBoard = harness.nextBare();
        world.setBlockState(fullBoard, ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState());
        if (world.getBlockEntity(fullBoard) instanceof LayeredCopycatBoardBlockEntity board) {
            board.setOccupancy(LayeredBoardSlots.FULL_OCCUPANCY);
            for (Direction side : Direction.values()) {
                a.isTrue("12 个槽全占时 " + side + " 面是完整实心面",
                        canAttachTo(world, fullBoard, side));
            }
        }
        log.add("   竖直面附着：4 个竖直方向全部与完整方块同判据；空占用不支持附着");
    }

    /** 原版梯子的判据：{@code state.isSideSolidFullSquare(world, pos, side)}。 */
    private static boolean canAttachTo(BlockView world, BlockPos pos, Direction side) {
        return world.getBlockState(pos).isSideSolidFullSquare(world, pos, side);
    }

    /** 一个方块六个面各自的「完整实心面」判定，紧凑成一行便于读日志。 */
    private static String solidFaceSummary(BlockView world, BlockPos pos) {
        StringBuilder out = new StringBuilder();
        for (Direction side : Direction.values()) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(side.getName().charAt(0)).append('=').append(canAttachTo(world, pos, side));
        }
        return out.toString();
    }

    /**
     * 本 mod 的两个伪装梯与 {@code minecraft:ladder} 的附着对比。
     *
     * <p>三种梯子问<b>同一组支撑面</b>，而且位置逐字节相同：梯子格 = 支撑格 + NORTH、
     * 梯子 FACING = NORTH、要贴的那一面 = 支撑格的 SOUTH 面。所以三者结果不同只可能来自
     * 它们自己的代码，不可能是测试摆位造成的。
     *
     * <p>支撑面覆盖三类：
     * <ul>
     *   <li><b>原版完整方块</b>——基本盘；</li>
     *   <li><b>分层伪装薄板</b>——几何来自方块实体的动态面（这一轮刚修好 {@code dynamicBounds}）；</li>
     *   <li><b>Copycats+ 伪装板</b>——装了才测，材质是石头，所以那一面是完整实心面。</li>
     * </ul>
     *
     * <p>判据用<b>原版梯子自己的那条</b>（{@code isSideSolidFullSquare}）当基准：
     * 两个伪装梯的 {@code canPlaceAt} 必须与 {@code minecraft:ladder} 逐格相同。
     * 垂直梯历史上用的是 {@code BlockState#isSolid()}——那是注册期烤好的 boolean，
     * 看不到动态几何，所以薄板那一格只有它会是 false。
     */
    private static void runLadderComparison(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 20c. 梯子放置对比（minecraft:ladder / copycat_fixed / copycat_vertical）");

        ServerWorld world = harness.world();
        List<Block> ladders = List.of(Blocks.LADDER, ModBlock.COPYCAT_STEEL_FIXED_LADDER,
                ModBlock.COPYCAT_STEEL_VERTICAL_LADDER);

        // --- 支撑面一：原版完整方块
        BlockPos stone = harness.nextBare();
        world.setBlockState(stone, Blocks.STONE.getDefaultState());

        // --- 支撑面二：分层薄板，北面外层 + 完整方块材质
        BlockPos board = harness.nextBare();
        world.setBlockState(board, ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState());
        if (world.getBlockEntity(board) instanceof LayeredCopycatBoardBlockEntity boardEntity) {
            boardEntity.setOccupancy(LayeredBoardSlots.slotBitMask(FaceDir.NORTH, BoardLayer.OUTER));
            boardEntity.setMaterial(
                    LayeredBoardSlots.materialKey(FaceDir.NORTH, BoardLayer.OUTER, BoardArea.BODY),
                    Blocks.OAK_PLANKS.getDefaultState(), null);
        }

        // --- 支撑面三：Copycats+ 伪装板（把一个 part 打开并配石头，找出它真正实心的那一面）
        BlockPos copycatSupport = null;
        Direction copycatFace = null;
        if (BuiltinAdapters.copycatsLoaded()) {
            Block copycatBoard = Registries.BLOCK.get(new Identifier("copycats:copycat_board"));
            if (copycatBoard != Blocks.AIR
                    && copycatBoard instanceof IMultiStateCopycatBlock multistate) {
                outer:
                for (String propertyName : multistate.storageProperties()) {
                    Property<?> property = copycatBoard.getStateManager().getProperty(propertyName);
                    if (!(property instanceof BooleanProperty booleanProperty)
                            || !copycatBoard.getDefaultState().contains(booleanProperty)) {
                        continue;
                    }
                    BlockState state = copycatBoard.getDefaultState().with(booleanProperty, true);
                    BlockPos candidate = harness.nextBare();
                    world.setBlockState(candidate, state);
                    if (!(world.getBlockEntity(candidate) instanceof IMultiStateCopycatBlockEntity entity)) {
                        continue;
                    }
                    entity.setMaterial(propertyName, Blocks.STONE.getDefaultState());
                    for (Direction side : Direction.values()) {
                        if (canAttachTo(world, candidate, side)) {
                            copycatSupport = candidate;
                            copycatFace = side;
                            log.add("   （Copycats+ 伪装板：part=" + propertyName
                                    + "，实心面=" + side + "）");
                            break outer;
                        }
                    }
                }
            }
            if (copycatSupport == null) {
                log.add("   （Copycats+ 伪装板没找到「有完整实心面」的形态，跳过这一个 case）");
            }
        } else {
            log.add("   （未装 Copycats+：跳过 Copycats+ 伪装板）");
        }

        // 每一个 case 都是「支撑格 + 要贴的那一面」，梯子按同一套规则摆：
        // 梯子格 = 支撑格 + 那一面，梯子 FACING = 那一面。
        // 三类支撑面各挑一个「确实是完整实心面」的方向（1px 板只有外侧那一面成立，
        // 与完整方块只有表面成立是同一回事）。
        record LadderCase(String name, BlockPos support, Direction face) {
        }
        List<LadderCase> cases = new ArrayList<>();
        cases.add(new LadderCase("原版完整方块的北面", stone, Direction.NORTH));
        // 薄板 NORTH.OUTER 的完整实心面就是它的北面（08b 段已逐面验过）
        cases.add(new LadderCase("分层伪装薄板 NORTH.OUTER 的北面", board, Direction.NORTH));
        if (copycatSupport != null) {
            cases.add(new LadderCase("Copycats+ 伪装板的 " + copycatFace + " 面",
                    copycatSupport, copycatFace));
        }

        for (LadderCase testCase : cases) {
            BlockPos supportPos = testCase.support();
            Direction face = testCase.face();
            // 与 20b 段完全同一套摆位：梯子格 = 支撑格 + 实心面的方向，梯子 FACING = 实心面。
            // （20b 段已逐面验过这一组在真实世界里 canPlaceAt 恒为真。）
            BlockPos ladderPos = supportPos.offset(face);
            Direction ladderFacing = face;

            // 先用原版梯子实测一次，确认这一组摆位本身是成立的
            world.setBlockState(ladderPos, Blocks.AIR.getDefaultState());
            log.add("   [摆位] " + testCase.name() + "：梯子在 " + ladderPos.toShortString()
                    + "，facing=" + ladderFacing
                    + "，vanilla.canPlaceAt="
                    + buildLadder(Blocks.LADDER, ladderFacing).canPlaceAt(world, ladderPos));

            // 前提：这一面在真实世界里确实是完整实心面（三种梯子都应该能贴）
            a.isTrue("【" + testCase.name() + "】这一面是完整实心面",
                    canAttachTo(world, supportPos, face));

            Boolean vanillaCanPlace = null;
            for (Block ladderBlock : ladders) {
                String name = nameOf(ladderBlock);
                a.equal(name + " 的方块类", true, ladderBlock instanceof LadderBlock);
                if (ladderBlock == ModBlock.COPYCAT_STEEL_VERTICAL_LADDER) {
                    a.isTrue("copycat vertical ladder 继承 VerticalLadderBlock",
                            ladderBlock instanceof VerticalLadderBlock);
                }
                world.setBlockState(ladderPos, Blocks.AIR.getDefaultState());
                harness.clearEntitiesAt(ladderPos);

                BlockState ladder = buildLadder(ladderBlock, ladderFacing);
                boolean canPlace = ladder.canPlaceAt(world, ladderPos);
                if (vanillaCanPlace == null) {
                    vanillaCanPlace = canPlace;
                } else {
                    a.equal(name + " 的 canPlaceAt 与 minecraft:ladder 相同（" + testCase.name() + "）",
                            vanillaCanPlace, canPlace);
                }
                a.isTrue(name + " 的 canPlaceAt 为真（" + testCase.name() + "）", canPlace);
                // 直接对照「原版判据本身」：梯子的结论必须等于 isSideSolidFullSquare 的结果。
                // 垂直梯历史上用的是 isSolid()，在薄板 / 伪装板这种动态方块上会给出 false。
                a.equal(name + " 的 canPlaceAt == isSideSolidFullSquare（" + testCase.name() + "）",
                        canAttachTo(world, supportPos, face), canPlace);

                world.setBlockState(ladderPos, ladder, Block.NOTIFY_ALL);
                BlockState inWorld = world.getBlockState(ladderPos);
                log.add("   " + name + " 贴【" + testCase.name() + "】：canPlaceAt=" + canPlace
                        + "，放进世界 = " + nameOf(inWorld.getBlock()));
                a.isTrue(name + " 能贴住" + testCase.name(), inWorld.isOf(ladderBlock));
                if (inWorld.isOf(ladderBlock)) {
                    a.equal(name + " 朝向正确（" + testCase.name() + "）",
                            ladderFacing, inWorld.get(LadderBlock.FACING));
                    a.isTrue(name + " 在邻居更新后仍然贴住（" + testCase.name() + "）",
                            inWorld.canPlaceAt(world, ladderPos));
                }
                world.setBlockState(ladderPos, Blocks.AIR.getDefaultState());
            }
        }
        log.add("   梯子对比：原版与两种伪装梯对同一组支撑面的 canPlaceAt 与落世界结果完全一致");
    }

    /**
     * /** 造一个朝向给定的梯子状态；垂直梯需要补上它自己的 SHAPE 属性。 */
    private static BlockState buildLadder(Block block, Direction facing) {
        BlockState state = block.getDefaultState().with(LadderBlock.FACING, facing);
        if (block instanceof VerticalLadderBlock) {
            state = state.with(VerticalLadderBlock.SHAPE, PropLadderShape.START);
        }
        return state;
    }

    /** 方块的可读短名（注册名）。 */
    private static String nameOf(Block block) {
        return Registries.BLOCK.getId(block).toString();
    }

    // ================================================================ 13. 落点解析

    /**
     * 需求：落点必须是标准 BlockItem 的目标格语义，而且<b>点击面只决定「放到哪一格」</b>，
     * 绝不能反向修改预设里的结构。
     *
     * <p>这一段的每一条都对应一个曾经真实出错的行为：之前无论点哪个面、点什么都直接拿
     * {@code hit.getBlockPos()} 当落点，于是右键石头永远是「尝试替换石头本身 → 不可替换 → BLOCKED」。
     */
    private static void runTargetPos(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 13. 落点解析（点击面只决定格子）");

        ServerWorld world = harness.world();

        // --- ① 点击不可替换方块（石头）的顶面 → 落点在它上方
        BlockPos stone = harness.next();
        world.setBlockState(stone, Blocks.STONE.getDefaultState());
        BlockPos above = stone.offset(Direction.UP);
        world.setBlockState(above, Blocks.AIR.getDefaultState());
        a.equal("点石头顶面 → 落点是石头上方",
                above, PlacementService.resolveTargetPos(world, stone, Direction.UP));

        // --- ② 点击石头的东面 → 落点在它东侧
        for (Direction side : Direction.values()) {
            BlockPos neighbour = stone.offset(side);
            world.setBlockState(neighbour, Blocks.AIR.getDefaultState());
            a.equal("点石头 " + side + " 面 → 落点是它的 " + side + " 邻居",
                    neighbour, PlacementService.resolveTargetPos(world, stone, side));
        }

        // --- ③ 点击可替换方块 → 落点保持被点的那一格
        BlockPos air = harness.next();
        world.setBlockState(air, Blocks.AIR.getDefaultState());
        a.equal("点空气 → 落点还是那一格",
                air, PlacementService.resolveTargetPos(world, air, Direction.UP));

        BlockPos water = harness.next();
        world.setBlockState(water, Blocks.WATER.getDefaultState());
        a.equal("点水 → 落点还是那一格（水可替换）",
                water, PlacementService.resolveTargetPos(world, water, Direction.UP));

        BlockPos grass = harness.next();
        // 1.20.1 里草方块物品是 GRASS（1.20.3+ 才改名成 SHORT_GRASS）
        world.setBlockState(grass, Blocks.GRASS.getDefaultState());
        a.equal("点草 → 落点还是那一格（草可替换）",
                grass, PlacementService.resolveTargetPos(world, grass, Direction.UP));

        // --- ④ 真正的放置：预设 NORTH.OUTER，点不同的面，结构必须一模一样
        //     这一条是「hitSide 不能修改 preset」的正面验证。
        //     掩码用 slotBitMask 算，不写死数字——位序由 LayeredBoardSlots 说了算。
        int presetMask = LayeredBoardSlots.slotBitMask(FaceDir.NORTH, BoardLayer.OUTER);
        PlacementConfig preset = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, StructureMasks.write(presetMask))
                .withSlot(LayeredBoardSlots.materialKey(FaceDir.NORTH, BoardLayer.OUTER, BoardArea.BODY),
                        Blocks.OAK_PLANKS.getDefaultState());
        a.equal("预设占用就是 NORTH.OUTER（掩码 " + StructureMasks.write(presetMask) + "）",
                presetMask, LayeredBoardCopycatAdapter.occupancy(preset));
        log.add("   NORTH.OUTER 掩码 = " + StructureMasks.write(presetMask)
                + "（" + presetMask + "）");

        for (Direction clickedSide : List.of(Direction.UP, Direction.EAST, Direction.WEST,
                Direction.NORTH, Direction.SOUTH, Direction.DOWN)) {
            // 每次都在一块新石头上点它的一面。
            // 用 nextBare：这一段的重点是自己摆的「被点方块 + 目标格」，不要平台帮忙铺地基。
            BlockPos base = harness.nextBare();
            world.setBlockState(base, Blocks.STONE.getDefaultState());
            BlockPos target = PlacementService.resolveTargetPos(world, base, clickedSide);
            world.setBlockState(target, Blocks.AIR.getDefaultState());
            // 薄板有碰撞箱，而它和石头（被点的方块）紧挨着——放置前必须保证目标格里没实体
            harness.clearEntitiesAt(target);

            PlacementResult result = PlacementService.place(world, target, preset);
            a.isTrue("点 " + clickedSide + " 面放置成功", result.success());
            if (harness.blockEntityAt(target) instanceof LayeredCopycatBoardBlockEntity board) {
                a.equal("点 " + clickedSide + " 面后 occupancy 仍是预设的 NORTH.OUTER",
                        presetMask, board.occupancy());
            } else {
                a.check("点 " + clickedSide + " 面后拿到薄板方块实体", false,
                        String.valueOf(harness.blockEntityAt(target)));
            }
            // 预设对象本身不能因为放置被改（PlacementConfig 不可变，这里是防回归）
            a.equal("点 " + clickedSide + " 面后预设对象未被修改", presetMask,
                    LayeredBoardCopycatAdapter.occupancy(preset));
        }

        // --- ⑤ 护栏同理：预设 NORTH，点哪一面都只出 NORTH
        PlacementConfig railPreset = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState())
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, StructureMasks.write(northBit()))
                .withSlot(GuardrailParts.rowKey(Direction.NORTH), Blocks.STONE.getDefaultState());
        for (Direction clickedSide : List.of(Direction.UP, Direction.EAST, Direction.DOWN)) {
            BlockPos base = harness.nextBare();
            world.setBlockState(base, Blocks.STONE.getDefaultState());
            BlockPos target = PlacementService.resolveTargetPos(world, base, clickedSide);
            world.setBlockState(target, Blocks.AIR.getDefaultState());
            harness.clearEntitiesAt(target);

            PlacementResult result = PlacementService.place(world, target, railPreset);
            a.isTrue("护栏：点 " + clickedSide + " 面放置成功", result.success());
            // 先确认真的放下的是护栏，再读它的方向属性——直接对空气读属性会抛异常，
            // 那样「放置失败」会以一个看不懂的 IllegalArgumentException 的形式冒出来
            BlockState placed = world.getBlockState(target);
            a.isTrue("护栏：点 " + clickedSide + " 面后目标格真的是护栏",
                    placed.isOf(ModBlock.COPYCAT_GUARDRAIL));
            if (placed.isOf(ModBlock.COPYCAT_GUARDRAIL)) {
                a.isTrue("护栏：点 " + clickedSide + " 面后仍然只有 NORTH",
                        CopycatGuardrailBlock.hasFace(placed, Direction.NORTH)
                                && !CopycatGuardrailBlock.hasFace(placed, Direction.EAST)
                                && !CopycatGuardrailBlock.hasFace(placed, Direction.SOUTH)
                                && !CopycatGuardrailBlock.hasFace(placed, Direction.WEST));
            }
        }

        // --- ⑥ 落点被占时才该 BLOCKED（回归：以前是「永远 BLOCKED」）
        BlockPos occupied = harness.next();
        world.setBlockState(occupied, Blocks.STONE.getDefaultState());
        a.equal("落点被石头占着 → BLOCKED", PlacementFailure.BLOCKED,
                PlacementService.place(world, occupied, preset).failure());
        // 而同一个石头，点它的顶面就能放（这就是修好之前永远做不到的事）
        BlockPos onTop = occupied.offset(Direction.UP);
        world.setBlockState(onTop, Blocks.AIR.getDefaultState());
        a.isTrue("同一块石头，点顶面就能放",
                PlacementService.place(world, PlacementService.resolveTargetPos(world, occupied, Direction.UP),
                        preset).success());
    }

    // ================================================================ 14. GUI/config 一致性

    /**
     * 需求：{@code PlacementConfig} 必须是唯一事实来源。
     *
     * <p>这一段的由来是一次真实事故：为了让 GUI 发现 Copycats+ multistate 的材质槽，
     * {@code displayState} 曾经返回「所有 storageProperties 都是 true」的<b>临时状态</b>，
     * 而 config 里存的仍是默认的全 false。于是界面显示六个面都是「是」却改不动
     * （点一下是从临时状态取反、再写进 config，看起来毫无反应），放下去则是一个
     * 看不见任何结构的幽灵方块。这里把「显示 = 配置 = NBT = 放置」四者钉死。
     */
    private static void runConfigConsistency(Assertions a, List<String> log) {
        log.add("== 14. GUI/config 一致性");

        Block block = ModBlock.LAYERED_COPYCAT_BOARD;
        CopycatPlacementAdapter adapter = PlacementAdapters.resolve(block).orElseThrow();

        // --- 默认配置必须是「有结构」的，而且这就是 config 的起点
        PlacementConfig config = adapter.defaultConfig(block);
        a.notNull("默认配置有方块状态", config.state());
        a.equal("默认配置的方块就是选中的那个", block, config.block());
        a.isTrue("默认配置结构非空（不是零部件的幽灵方块）",
                adapter.validateStructure(adapter.stateFrom(config.state(), config), config) == null);

        // --- ① 显示的当前值 == config 的值
        BlockState display = adapter.displayState(config.state(), config);
        for (Property<?> property : display.getProperties()) {
            a.equal("显示值与 config 一致：" + property.getName(),
                    config.state().get(property), display.get(property));
        }

        // --- ② 点一下切换 → 写进 config 的值必须真的变了
        PlacementConfig before = config;
        boolean downOuterBefore = LayeredBoardCopycatAdapter.slotEnabled(
                config, FaceDir.DOWN, BoardLayer.OUTER);
        VirtualSpec downOuterSpec = adapter.virtualSpecs(config).stream()
                .filter(spec -> spec.key().equals("layer.down.outer"))
                .findFirst().orElseThrow();
        PlacementConfig after = downOuterSpec.with(config, downOuterSpec.next(config));
        a.isTrue("切换一个层开关后 config 真的变了",
                LayeredBoardCopycatAdapter.slotEnabled(after, FaceDir.DOWN, BoardLayer.OUTER)
                        != downOuterBefore);
        a.isTrue("切换后旧配置未被修改（不可变）",
                LayeredBoardCopycatAdapter.slotEnabled(before, FaceDir.DOWN, BoardLayer.OUTER)
                        == downOuterBefore);
        a.isTrue("切换一个层不会动其它层（下面内层仍然关着）",
                !LayeredBoardCopycatAdapter.slotEnabled(after, FaceDir.DOWN, BoardLayer.INNER));

        // --- ③ NBT 往返 == config
        PlacementConfig roundTrip = PlacementConfig.fromNbt(after.toNbt());
        a.equal("NBT 往返后与 config 完全一致", after, roundTrip);
        a.equal("NBT 往返后方块状态一致", after.state(), roundTrip.state());
        a.equal("NBT 往返后结构一致", after.structures(), roundTrip.structures());

        // --- ④ 放置用的状态 == config
        BlockState placementState = stateWithWaterFor(adapter, after);
        a.equal("最终放置状态与 config 的方块一致", after.block(), placementState.getBlock());
        a.equal("最终放置状态与 adapter.stateFrom(config) 一致",
                adapter.stateFrom(after.state(), after), placementState);

        // --- ⑤ 多部件方块的核心场景：只开 NORTH 时，任何环节都不能变成六面全 true/false
        //     用一个「只有一面」的 config 走完整条链，逐环节比对
        BlockState oneFace = block.getDefaultState();
        PlacementConfig northOnly = PlacementConfig.of(oneFace)
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY,
                        StructureMasks.write(LayeredBoardSlots.slotBitMask(FaceDir.NORTH, BoardLayer.OUTER)));
        BlockState shownState = adapter.displayState(northOnly.state(), northOnly);
        a.equal("显示状态的方块状态 == config 的方块状态", northOnly.state(), shownState);
        a.equal("显示状态没有任何属性被偷偷改掉",
                northOnly.state().getEntries(), shownState.getEntries());
        a.equal("NBT 往返后仍然是「只开 NORTH」",
                LayeredBoardCopycatAdapter.occupancy(northOnly),
                LayeredBoardCopycatAdapter.occupancy(PlacementConfig.fromNbt(northOnly.toNbt())));

        // --- ⑥ 护栏：structure 掩码 ↔ 方块状态那四个方向属性必须双向一致
        Block guardrail = ModBlock.COPYCAT_GUARDRAIL;
        CopycatPlacementAdapter railAdapter = PlacementAdapters.resolve(guardrail).orElseThrow();
        for (int mask = 0; mask < 16; mask++) {
            PlacementConfig railConfig = GuardrailCopycatAdapter.withFaces(
                    PlacementConfig.of(guardrail.getDefaultState()), mask);
            BlockState railState = railAdapter.displayState(railConfig.state(), railConfig);
            for (Direction dir : CopycatGuardrailBlock.FACES) {
                a.equal("护栏掩码 " + StructureMasks.write(mask) + " 下 " + dir + " 属性一致",
                        CopycatGuardrailBlock.maskHas(mask, dir),
                        CopycatGuardrailBlock.hasFace(railState, dir));
            }
        }

        // --- ⑦ 被 virtual property 接管的属性必须被声明出来（GUI 靠它去重，避免两套入口）
        List<String> managed = new ArrayList<>();
        PlacementConfig railSample = railAdapter.defaultConfig(guardrail);
        for (VirtualSpec spec : railAdapter.virtualSpecs(railSample)) {
            managed.addAll(spec.managedProperties());
        }
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            a.isTrue("护栏的 " + dir.getName() + " 已被 VirtualSpec 接管（GUI 会隐藏它）",
                    managed.contains(dir.getName()));
        }
        a.equal("分层薄板没有需要隐藏的属性（结构全在方块实体里）",
                0, adapter.virtualSpecs(config).stream().mapToInt(s -> s.managedProperties().size()).sum());
    }

    /** 复刻 PlacementService 里「adapter 结构属性 + 含水」那一步，用来比对最终状态。 */
    private static BlockState stateWithWaterFor(CopycatPlacementAdapter adapter, PlacementConfig config) {
        BlockState shaped = adapter.stateFrom(config.state(), config);
        return shaped.contains(Properties.WATERLOGGED)
                ? shaped.with(Properties.WATERLOGGED, false)
                : shaped;
    }

    // ================================================================ 15. 零结构校验

    /**
     * 需求：服务端绝不能允许「零部件的方块」写进世界。
     *
     * <p>这类状态本身是合法 BlockState、也能真的放进去，但占着格子却什么都看不见，
     * 再想放别的东西还会提示「这里放不下」——就是那个幽灵方块。
     */
    private static void runStructureValidation(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 15. 零结构校验（幽灵方块）");

        ServerWorld world = harness.world();

        // --- 护栏：0 个方向必须被拒
        CopycatPlacementAdapter railAdapter = PlacementAdapters.resolve(ModBlock.COPYCAT_GUARDRAIL).orElseThrow();
        PlacementConfig noFace = GuardrailCopycatAdapter.withFaces(
                PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.getDefaultState()), 0);
        a.notNull("护栏 0 方向被判为无效结构",
                railAdapter.validateStructure(railAdapter.stateFrom(noFace.state(), noFace), noFace));
        a.equal("护栏 0 方向能通过 >=1 的判据吗（应为 null 表示有效）",
                null, railAdapter.validateStructure(railAdapter.stateFrom(
                        GuardrailCopycatAdapter.withFaces(noFace, 0x1).state(), noFace), 
                        GuardrailCopycatAdapter.withFaces(noFace, 0x1)));

        // 真正放一次：必须 INVALID_STRUCTURE，而且世界不被改动、不扣东西
        BlockPos railPos = harness.next();
        PlacementResult railResult = PlacementService.place(world, railPos, noFace);
        a.equal("护栏 0 方向放置返回 INVALID_STRUCTURE",
                PlacementFailure.INVALID_STRUCTURE, railResult.failure());
        a.isAir("护栏 0 方向时世界里没有留下任何东西", harness.stateAt(railPos));
        a.equal("护栏 0 方向的提示正确",
                Text.translatable(PlacementFeedback.INVALID_STRUCTURE_KEY),
                PlacementFeedback.messageFor(railResult));

        // 至少一个方向就有效
        BlockPos railOk = harness.next();
        PlacementConfig oneFace = GuardrailCopycatAdapter.withFaces(noFace, northBit());
        a.isTrue("护栏 1 个方向能正常放置",
                PlacementService.place(world, railOk, oneFace).success());

        // --- 分层薄板：occupancy == 0 必须被拒
        CopycatPlacementAdapter boardAdapter = PlacementAdapters.resolve(ModBlock.LAYERED_COPYCAT_BOARD).orElseThrow();
        PlacementConfig emptyOccupancy = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x0");
        a.notNull("分层薄板 occupancy=0 被判为无效结构",
                boardAdapter.validateStructure(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState(), emptyOccupancy));

        BlockPos boardPos = harness.next();
        PlacementResult boardResult = PlacementService.place(world, boardPos, emptyOccupancy);
        a.equal("分层薄板 occupancy=0 返回 INVALID_STRUCTURE",
                PlacementFailure.INVALID_STRUCTURE, boardResult.failure());
        a.isAir("分层薄板 occupancy=0 时不留下幽灵方块", harness.stateAt(boardPos));

        // occupancy != 0 有效
        BlockPos boardOk = harness.next();
        PlacementConfig oneLayer = PlacementConfig.of(ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState())
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x1");
        a.isTrue("分层薄板 occupation=0x1 能正常放置",
                PlacementService.place(world, boardOk, oneLayer).success());

        // --- 默认配置必须本身有效（否则玩家第一次选方块就得到一个幽灵方块）
        a.isTrue("护栏的默认配置有结构",
                railAdapter.validateStructure(
                        railAdapter.stateFrom(railAdapter.defaultConfig(ModBlock.COPYCAT_GUARDRAIL).state(),
                                railAdapter.defaultConfig(ModBlock.COPYCAT_GUARDRAIL)),
                        railAdapter.defaultConfig(ModBlock.COPYCAT_GUARDRAIL)) == null);
        PlacementConfig boardDefault = boardAdapter.defaultConfig(ModBlock.LAYERED_COPYCAT_BOARD);
        a.isTrue("分层薄板的默认配置 occupancy 非 0",
                LayeredBoardCopycatAdapter.occupancy(boardDefault) != 0);
        a.isTrue("分层薄板默认配置有效",
                boardAdapter.validateStructure(boardDefault.state(), boardDefault) == null);

        // --- Copycats+ multistate：逐方块验证「零 part 无效、有 part 有效」。
        //     这一段只有在装了 Copycats+ 的环境里才跑得起来（run/mods 里有 jar 时）。
        if (BuiltinAdapters.copycatsLoaded()) {
            testMultistateZeroStructure(harness, a, log);
        } else {
            log.add("   （没装 Copycats+，跳过 multistate 的零结构断言）");
        }
        // --- 不扣账验证：生存模式 + 无效结构 → 结构方块与材质都不动
        ServerPlayerEntity player = harness.fakePlayer(0, 0, 0);
        creative(player, false);
        give(player, ModBlock.COPYCAT_GUARDRAIL.asItem(), 3);
        player.getInventory().insertStack(new ItemStack(Blocks.STONE, 5));
        int railBefore = countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem());
        int stoneBefore = countOf(player, Blocks.STONE.asItem());
        PlacementConfig withMaterial = noFace.withSlot(
                GuardrailParts.rowKey(Direction.NORTH), Blocks.STONE.getDefaultState());
        PlacementResult denied = PlacementService.place(world, harness.next(), withMaterial, player);
        a.equal("生存模式下无效结构也是 INVALID_STRUCTURE",
                PlacementFailure.INVALID_STRUCTURE, denied.failure());
        a.equal("无效结构不扣结构方块", railBefore, countOf(player, ModBlock.COPYCAT_GUARDRAIL.asItem()));
        a.equal("无效结构不扣材质", stoneBefore, countOf(player, Blocks.STONE.asItem()));
    }

    /**
     * 逐个 Copycats+ multistate 方块验证零结构。
     *
     * <p>覆盖用户点名的那些：Copycat Board / Byte / Byte Panel / Half Layer / Stacked Half Layer。
     * 判据一律用方块自己的 {@code partExists}，<b>不按 block id 特判</b>——
     * 所以第三方新增的 multistate 方块也自动被覆盖到。
     */
    private static void testMultistateZeroStructure(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("   —— Copycats+ multistate 的零结构校验");
        int examined = 0;
        int zeroStateFound = 0;

        for (Block block : Registries.BLOCK) {
            if (!(block instanceof IMultiStateCopycatBlock multiState)) {
                continue;
            }
            if (!Registries.BLOCK.getId(block).getNamespace().equals("copycats")) {
                continue;
            }
            examined++;
            String id = Registries.BLOCK.getId(block).getPath();
            CopycatPlacementAdapter adapter = PlacementAdapters.resolve(block).orElseThrow();

            // ① 默认配置必须有效（玩家第一次选这个方块就不能拿到幽灵方块）
            BlockState minimal = CopycatsMultistateAdapter.minimalValidState(block.getDefaultState(), multiState);
            a.isTrue(id + "：默认状态提升后至少有一个 part",
                    hasAnyPartOf(minimal, multiState));
            PlacementConfig defaults = adapter.defaultConfig(block);
            a.equal(id + "：默认配置有效", null,
                    adapter.validateStructure(defaults.state(), defaults));

            // ② 默认状态（往往是零 part）必须被判为无效——这正是幽灵方块的来源
            BlockState defaultState = block.getDefaultState();
            if (!hasAnyPartOf(defaultState, multiState)) {
                zeroStateFound++;
                PlacementConfig zeroConfig = PlacementConfig.of(defaultState);
                a.notNull(id + "：零 part 的默认状态被判为无效结构",
                        adapter.validateStructure(defaultState, zeroConfig));
                // 真正放一次，必须 INVALID_STRUCTURE 且世界不被改动
                BlockPos pos = harness.next();
                PlacementResult result = PlacementService.place(harness.world(), pos, zeroConfig);
                a.equal(id + "：零 part 放置返回 INVALID_STRUCTURE",
                        PlacementFailure.INVALID_STRUCTURE, result.failure());
                a.isAir(id + "：零 part 时不留下幽灵方块", harness.stateAt(pos));
            }

            // ③ 最小有效状态必须能真的放下。
            //
            // 注意可能因为「方块自己的放置条件」而失败（例如大型伪装齿轮需要特殊支撑），
            // 那是方块自身的要求，与「零结构」无关——那种情况只记录、不算失败。
            // 这一段要证明的只是「算法给出的状态有 part、不是幽灵方块」。
            BlockPos okPos = harness.next();
            PlacementConfig okConfig = PlacementConfig.of(minimal);
            PlacementResult ok = PlacementService.place(harness.world(), okPos, okConfig);
            if (!ok.success()) {
                log.add("   " + id + "：最小有效状态未能放下（" + ok.failure()
                        + "）——这是方块自身的放置条件，与零结构无关");
            }
            // validateStructure 返回 null 表示「有效」，所以这里期望 null
            a.equal(id + "：最小有效状态本身是有效的（有 part）", null,
                    adapter.validateStructure(minimal, okConfig));
            log.add("   " + id + "：storageProperties=" + multiState.storageProperties().size()
                    + "，默认状态零 part=" + !hasAnyPartOf(defaultState, multiState)
                    + "，最小有效状态=" + minimal);
        }
        log.add("   共检查 " + examined + " 个 multistate 方块，其中默认状态是零 part 的有 " + zeroStateFound + " 个");
        a.isTrue("Copycats+ 里存在 multistate 方块", examined > 0);
        a.isTrue("其中有默认状态就是零 part 的（否则这段测试没验到东西）", zeroStateFound > 0);
    }

    /** 一个状态下有没有至少一个 part（复刻 adapter 内部的判据，供测试直接断言）。 */
    private static boolean hasAnyPartOf(BlockState state, IMultiStateCopycatBlock block) {
        for (String property : block.storageProperties()) {
            if (block.partExists(state, property)) {
                return true;
            }
        }
        return false;
    }
    // ================================================================ 16. 搜索

    /**
     * 搜索是纯逻辑，可以完整测：按显示名、完整注册名、注册名路径三者任一命中即可。
     *
     * <p>注意「显示名」这一项依赖当前语言：开发服务端上语言文件通常是加载了的，
     * 所以中文查询「薄板」应当能命中；万一语言没加载，按 id 的查询仍然必须可用，
     * 这里两种都验，避免测试自身依赖运行环境。
     */
    private static void runSearch(Assertions a, List<String> log) {
        log.add("== 16. 搜索（名称 / id / path）");

        ItemStack tool = new ItemStack(ModItem.COPYCAT_PLACER);
        PlacerEditState edit = new PlacerEditState(tool);
        List<PlacerEditState.Entry> all = edit.blockList();
        a.isTrue("列表非空", !all.isEmpty());

        // --- 按注册名路径
        List<PlacerEditState.Entry> byPath = PlacerEditState.filterEntries(all, "guardrail");
        a.isTrue("搜 guardrail 命中护栏",
                byPath.stream().anyMatch(e -> e.block() == ModBlock.COPYCAT_GUARDRAIL));

        List<PlacerEditState.Entry> byId = PlacerEditState.filterEntries(all, "maris-decoration:layered");
        a.isTrue("搜完整 id 片段命中分层薄板",
                byId.stream().anyMatch(e -> e.block() == ModBlock.LAYERED_COPYCAT_BOARD));

        List<PlacerEditState.Entry> byBoard = PlacerEditState.filterEntries(all, "copycat_board");
        a.isTrue("搜 copycat_board 命中分层薄板",
                byBoard.stream().anyMatch(e -> e.block() == ModBlock.LAYERED_COPYCAT_BOARD));

        // --- 大小写不敏感
        a.equal("搜索大小写不敏感",
                PlacerEditState.filterEntries(all, "GUARDRAIL").size(),
                PlacerEditState.filterEntries(all, "guardrail").size());

        // --- 按当前语言的显示名
        String localName = ModBlock.LAYERED_COPYCAT_BOARD.getName().getString();
        a.equal("本地化名可读", localName, Text.translatable(
                ModBlock.LAYERED_COPYCAT_BOARD.getTranslationKey()).getString());
        if (!localName.equals(ModBlock.LAYERED_COPYCAT_BOARD.getTranslationKey())) {
            List<PlacerEditState.Entry> byName = PlacerEditState.filterEntries(all, localName);
            a.isTrue("搜当前语言显示名「" + localName + "」命中分层薄板",
                    byName.stream().anyMatch(e -> e.block() == ModBlock.LAYERED_COPYCAT_BOARD));
            // 按显示名搜索必须与显示名本身一致（用当前语言的名字当查询词，不依赖具体是哪种语言）
            a.isTrue("用完整显示名搜索能命中该方块本身",
                    byName.stream().anyMatch(e -> e.block() == ModBlock.LAYERED_COPYCAT_BOARD));
            // 取显示名的一段子串，同样应当命中——这才是「输入薄板能匹配伪装薄板」那条需求的通式
            String fragment = localName.length() >= 2
                    ? localName.substring(0, Math.max(2, localName.length() / 2))
                    : localName;
            List<PlacerEditState.Entry> byFragment = PlacerEditState.filterEntries(all, fragment);
            a.isTrue("用显示名片段「" + fragment + "」搜索能命中",
                    byFragment.stream().anyMatch(e -> e.block() == ModBlock.LAYERED_COPYCAT_BOARD));
            log.add("   当前语言显示名 = " + localName
                    + "；按全名命中 " + byName.size() + " 条，按片段「" + fragment + "」命中 " + byFragment.size() + " 条");
        } else {
            log.add("   （语言文件未加载，跳过按显示名的断言；按 id 的搜索已验证）");
        }

        // --- 空词返回全部；无匹配返回空
        a.equal("空搜索词返回全部", all.size(), PlacerEditState.filterEntries(all, "").size());
        a.equal("空白搜索词返回全部", all.size(), PlacerEditState.filterEntries(all, "   ").size());
        a.equal("无匹配返回空", 0, PlacerEditState.filterEntries(all, "zzzz_no_such_block").size());

        // --- 材质筛选用同一套匹配规则
        a.isTrue("材质搜索：按 path 命中石头", PlacerEditState.matches(Blocks.STONE, "stone"));
        a.isTrue("材质搜索：按完整 id 命中石头",
                PlacerEditState.matches(Blocks.STONE, "minecraft:stone"));
        a.isFalse("材质搜索：不匹配的词返回 false",
                PlacerEditState.matches(Blocks.STONE, "zzzz"));
        a.isTrue("材质搜索：空词全部通过", PlacerEditState.matches(Blocks.STONE, ""));
    }

    // ================================================================ 17. 材质准入过滤

    /** Create 的材质白名单标签（唯一的「绕过形状检查」入口）。 */
    private static final TagKey<Block> COPYCAT_ALLOW =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("create", "copycat_allow"));

    /** Create 的材质黑名单标签。 */
    private static final TagKey<Block> COPYCAT_DENY =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("create", "copycat_deny"));

    /**
     * 材质准入的探针方块。
     *
     * <p>挑的原则是<b>覆盖判据链上每一条分支各至少一个</b>，而不是「随便挑几个看着像的」：
     * 完整立方体、形状不完整（半砖/玻璃板/门）、显式排除（楼梯）、方块实体、标签放行（桶）、
     * 标签禁止（梯子/树苗/炼药锅）、伪装方块自己，再加上 Create 自己的机器与外壳方块
     * （它们的结果<b>由真实 API 决定</b>，这里只如实打印，不预先假设）。
     */
    private static final List<String> MATERIAL_PROBES = List.of(
            "minecraft:stone",
            "minecraft:oak_planks",
            "minecraft:glass",
            "minecraft:oak_slab",
            "minecraft:glass_pane",
            "minecraft:iron_bars",
            "minecraft:oak_stairs",
            "minecraft:oak_door",
            "minecraft:oak_trapdoor",
            "minecraft:barrel",
            "minecraft:chest",
            "minecraft:furnace",
            "minecraft:hopper",
            "minecraft:ladder",
            "minecraft:oak_sapling",
            "minecraft:water_cauldron",
            "create:andesite_casing",
            "create:copper_casing",
            "create:mechanical_press",
            "create:copycat_panel",
            "create:copycat_step",
            "copycats:copycat_board"
    );

    /** 一个探针方块：注册名 + 默认状态。 */
    private record MaterialProbe(String id, BlockState state) {
    }

    /** 一个「用放置器放置的目标方块」及其材质准入上下文。 */
    private record MaterialTarget(String label, Block block, CopycatPlacementAdapter adapter,
                                  PlacementConfig config, String slotKey) {
    }

    /**
     * 材质候选必须由 adapter 决定，而不是本类维护黑白名单。
     *
     * <h2>为什么这一版必须带真实世界</h2>
     * Create 的 {@code CopycatBlock#getAcceptedBlockState} 与 Copycats+ 的
     * {@code ICopycatBlock#getAcceptedBlockState}（两者字节码逐条相同）是这么写的：
     * <pre>
     *   非 BlockItem                        → 拒绝
     *   是伪装方块（CopycatBlock / ICopycatBlock）→ 拒绝
     *   在 create:copycat_allow 里            → 放行（跳过后面全部检查）
     *   在 create:copycat_deny 里             → 拒绝
     *   是 BlockEntityProvider                → 拒绝
     *   是 StairsBlock                        → 拒绝
     *   world != null 时：轮廓必须是完整立方体、碰撞箱非空 → 否则拒绝
     * </pre>
     * 注意最后一条在 {@code world != null} <b>里面</b>：传 null 或一个「到处是空气」的视图
     * 会让整段形状检查被跳过，半砖、玻璃板、门就全混进候选了（这正是上一版的缺陷）。
     * 所以这一版用<b>真实世界</b>（服务端是 {@link ServerWorld}，客户端是
     * {@code MinecraftClient.world}），判定与玩家正常右键放材质时完全一致。
     *
     * <h2>验什么</h2>
     * <ol>
     *   <li>计数链：注册表 → 有 BlockItem → 基础过滤 → 本目标接受，确认判据真的在过滤；</li>
     *   <li>逐个探针打印「接受 / 拒绝 + 拒绝原因」，四个目标都打（Create 伪装板、
     *       Copycats+ 伪装板、本 mod 护栏、本 mod 分层薄板的 BODY 槽）；</li>
     *   <li>硬规则断言：石头接受；半砖 / 玻璃板 / 楼梯 / 门 / 空气 / 伪装方块自己拒绝；</li>
     *   <li>跨实现一致性：本 mod 两个方块与 Create 判据逐探针一致，
     *       唯一允许的差异是 Create 伪装板自己的「栏杆 / 活板门」白名单
     *       （{@code CopycatPanelBlock#isAcceptedRegardless}）；</li>
     *   <li>判定与「判据链解释」一致：解释函数按上面那条真实顺序逐条问，两者必须同结论。</li>
     * </ol>
     */
    private static void runMaterialFilter(PlacementHarness harness, Assertions a, List<String> log) {
        log.add("== 17. 材质准入过滤（真实世界上下文）");

        ServerWorld world = harness.world();
        // 形状查询的位置：判据里没有任何「看邻居」的部分（完整立方体 / 碰撞箱 / 楼梯类型
        // 都与邻接无关），所以一个孤立的空气格足够，也不会给测试引入随机性。
        BlockPos pos = harness.nextBare();

        // 标签是数据包内容。专用服务端启动后标签已加载，但这里先问一句：
        // 「标签没加载」和「判据错了」是两件事，不能混为一谈。
        boolean tagsLoaded = Registries.BLOCK.getEntryList(COPYCAT_ALLOW).isPresent()
                && Registries.BLOCK.getEntryList(COPYCAT_DENY).isPresent();
        log.add("   create:copycat_allow / copycat_deny 标签已加载 = " + tagsLoaded);

        List<MaterialTarget> targets = new ArrayList<>();
        addTarget(targets, "create:copycat_panel", null);
        if (BuiltinAdapters.copycatsLoaded()) {
            // multipart 的槽键就是方块自己声明的属性名，取第一个「真的存在」的槽即可
            addTarget(targets, "copycats:copycat_board", null);
        } else {
            log.add("   （未装 Copycats+：跳过 copycats:copycat_board）");
        }
        addTarget(targets, Registries.BLOCK.getId(ModBlock.COPYCAT_GUARDRAIL).toString(), null);
        // 分层薄板明确点名 BODY 槽
        addTarget(targets, Registries.BLOCK.getId(ModBlock.LAYERED_COPYCAT_BOARD).toString(), ".body");

        List<MaterialProbe> probes = new ArrayList<>();
        for (String id : MATERIAL_PROBES) {
            if (!Registries.BLOCK.containsId(new Identifier(id))) {
                log.add("   （注册表里没有 " + id + "，跳过该探针）");
                continue;
            }
            probes.add(new MaterialProbe(id, Registries.BLOCK.get(new Identifier(id)).getDefaultState()));
        }

        List<List<Boolean>> verdicts = new ArrayList<>();
        for (MaterialTarget target : targets) {
            String label = target.label();
            log.add("   --- " + label + "（adapter=" + target.adapter().name()
                    + "，slot=\"" + target.slotKey() + "\"）");

            // --- 计数链
            int total = 0;
            int withItem = 0;
            int basePass = 0;
            int accepted = 0;
            for (Block block : Registries.BLOCK) {
                total++;
                net.minecraft.item.Item item = block.asItem();
                if (item == null || item == net.minecraft.item.Items.AIR || !(item instanceof BlockItem)) {
                    continue;
                }
                withItem++;
                BlockState state = block.getDefaultState();
                // 基础过滤 = 接口默认实现那一层：非空气 + 不是伪装方块自己
                if (state.isAir() || PlacementAdapters.isPlaceable(block)) {
                    continue;
                }
                basePass++;
                if (target.adapter().acceptsMaterial(state, target.slotKey(), target.config(), world, pos)) {
                    accepted++;
                }
            }
            log.add("       注册表 " + total + " → 有 BlockItem " + withItem
                    + " → 基础过滤 " + basePass + " → 接受 " + accepted
                    + "，拒绝 " + (basePass - accepted));
            a.isTrue(label + "：材质候选非空", accepted > 0);
            a.isTrue(label + "：准入判据确实在过滤", accepted < basePass);

            // --- 逐探针判定 + 拒绝原因
            List<Boolean> row = new ArrayList<>(probes.size());
            for (MaterialProbe probe : probes) {
                boolean ok = target.adapter().acceptsMaterial(probe.state(), target.slotKey(),
                        target.config(), world, pos);
                row.add(ok);
                String reason = rejectReason(probe.state(), world, pos);
                log.add("       " + probe.id() + " → " + (ok ? "接受" : "拒绝（" + reason + "）"));
                // 判定必须与判据链解释同结论；Create 伪装板的栏杆/活板门白名单是唯一例外
                boolean expected = reason.equals(REASON_PASS);
                boolean whitelisted = CopycatSpecialCases.isBarsMaterial(probe.state())
                        || CopycatSpecialCases.isTrapdoorMaterial(probe.state());
                a.check(label + "：" + probe.id() + " 的判定与判据链一致",
                        ok == expected || (whitelisted && ok),
                        "adapter=" + ok + "，判据链=" + reason);
            }
            verdicts.add(row);

            // --- 硬规则
            a.isTrue(label + "：石头（完整立方体）可以当材质", verdict(row, probes, "minecraft:stone"));
            a.isFalse(label + "：半砖拒绝（轮廓不是完整立方体）",
                    verdict(row, probes, "minecraft:oak_slab"));
            a.isFalse(label + "：玻璃板拒绝（轮廓不是完整立方体）",
                    verdict(row, probes, "minecraft:glass_pane"));
            a.isFalse(label + "：楼梯拒绝（判据里显式排除 StairsBlock）",
                    verdict(row, probes, "minecraft:oak_stairs"));
            a.isFalse(label + "：门拒绝（轮廓不是完整立方体）",
                    verdict(row, probes, "minecraft:oak_door"));
            a.isFalse(label + "：空气拒绝", target.adapter().acceptsMaterial(Blocks.AIR.getDefaultState(),
                    target.slotKey(), target.config(), world, pos));
            a.isFalse(label + "：伪装方块自己不能当材质",
                    target.adapter().acceptsMaterial(target.block().getDefaultState(),
                            target.slotKey(), target.config(), world, pos));
        }

        // --- 跨实现一致性：本 mod 两个方块的判据是 Create 那套规则的复刻
        int guardrail = indexOfTarget(targets, "copycat_guardrail");
        int layered = indexOfTarget(targets, "layered_copycat_board");
        int panel = indexOfTarget(targets, "create:copycat_panel");
        int board = indexOfTarget(targets, "copycats:copycat_board");
        // 方法注释里的顺序是真的：Copycats+ 的判据与 Create 的逐条相同，只有伪装板自带白名单
        if (guardrail >= 0) {
            if (layered >= 0) {
                compareVerdicts(targets, verdicts, probes, guardrail, layered, false, a, log);
            }
            if (panel >= 0) {
                compareVerdicts(targets, verdicts, probes, guardrail, panel, true, a, log);
            }
        }
        if (board >= 0 && panel >= 0) {
            compareVerdicts(targets, verdicts, probes, board, panel, true, a, log);
        }

        // --- 只有 Create 伪装板会接受活板门，本 mod 两个方块不接受（白名单是它自己的语义）
        if (panel >= 0) {
            a.isTrue("Create 伪装板接受活板门（CopycatPanelBlock 自己的白名单）",
                    verdict(verdicts.get(panel), probes, "minecraft:oak_trapdoor"));
        }
        if (guardrail >= 0 && panel >= 0) {
            a.isFalse("本 mod 护栏不接受活板门（没有那份白名单）",
                    verdict(verdicts.get(guardrail), probes, "minecraft:oak_trapdoor"));
        }

        // --- 标签类探针：只有标签真的加载了才断言
        if (tagsLoaded) {
            for (int t = 0; t < targets.size(); t++) {
                String label = targets.get(t).label();
                List<Boolean> row = verdicts.get(t);
                a.isTrue(label + "：桶接受（在 create:copycat_allow 里，绕过方块实体检查）",
                        verdict(row, probes, "minecraft:barrel"));
                a.isFalse(label + "：炼药锅拒绝（在 create:copycat_deny 里）",
                        verdict(row, probes, "minecraft:water_cauldron"));
                a.isFalse(label + "：梯子拒绝（在 create:copycat_deny 的 climbable 里）",
                        verdict(row, probes, "minecraft:ladder"));
                a.isFalse(label + "：树苗拒绝（在 create:copycat_deny 里）",
                        verdict(row, probes, "minecraft:oak_sapling"));
            }
        } else {
            log.add("   （标签数据不可用，跳过 create:copycat_allow / deny 相关断言）");
        }
    }

    private static final String REASON_PASS = "通过";

    /**
     * 拒绝原因（<b>只用于解释，判定本身仍然走 adapter</b>）。
     *
     * <p>顺序逐条抄自 Create 的 {@code CopycatBlock#getAcceptedBlockState} 与
     * Copycats+ 的 {@code ICopycatBlock#getAcceptedBlockState}——两者的字节码在这里完全一致，
     * 所以一张表就能解释四个目标方块里三个。Create 伪装板多的那条白名单不在这里体现，
     * 调用方单独用 {@code CopycatSpecialCases} 判。
     */
    private static String rejectReason(BlockState state, ServerWorld world, BlockPos pos) {
        Block block = state.getBlock();
        net.minecraft.item.Item item = block.asItem();
        if (item == null || item == net.minecraft.item.Items.AIR || !(item instanceof BlockItem)) {
            return "没有 BlockItem";
        }
        if (PlacementAdapters.isPlaceable(block)) {
            return "它自己就是伪装方块";
        }
        if (state.isIn(COPYCAT_ALLOW)) {
            return REASON_PASS;
        }
        if (state.isIn(COPYCAT_DENY)) {
            return "在 create:copycat_deny 标签里";
        }
        if (block instanceof net.minecraft.block.BlockEntityProvider) {
            return "是方块实体方块（BlockEntityProvider）";
        }
        if (block instanceof net.minecraft.block.StairsBlock) {
            return "是楼梯（判据里显式排除 StairsBlock）";
        }
        net.minecraft.util.shape.VoxelShape shape = state.getOutlineShape(world, pos);
        if (shape.isEmpty() || !shape.getBoundingBox()
                .equals(net.minecraft.util.shape.VoxelShapes.fullCube().getBoundingBox())) {
            return "轮廓不是完整立方体";
        }
        if (state.getCollisionShape(world, pos).isEmpty()) {
            return "碰撞箱为空";
        }
        return REASON_PASS;
    }

    /** 按注册名找目标；找不到（或不是伪装方块）时返回 {@code null}，由调用方跳过。 */
    private static @Nullable MaterialTarget materialTarget(String blockId, @Nullable String slotHint) {
        Identifier id = new Identifier(blockId);
        if (!Registries.BLOCK.containsId(id)) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        Optional<CopycatPlacementAdapter> resolved = PlacementAdapters.resolve(block);
        if (resolved.isEmpty()) {
            return null;
        }
        CopycatPlacementAdapter adapter = resolved.get();
        PlacementConfig config = adapter.defaultConfig(block);
        BlockState display = adapter.displayState(block.getDefaultState(), config);
        if (display == null) {
            display = block.getDefaultState();
        }
        // 槽键：优先取带提示词的槽（例如分层薄板的 .body），否则取第一个「真的存在」的槽
        String hint = slotHint;
        String slotKey = null;
        if (hint != null) {
            for (AdapterSlot slot : adapter.slots(display, config)) {
                if (slot.structure() && slot.key().contains(hint)) {
                    slotKey = slot.key();
                    break;
                }
            }
        }
        if (slotKey == null) {
            for (AdapterSlot slot : adapter.slots(display, config)) {
                if (slot.structure()) {
                    slotKey = slot.key();
                    break;
                }
            }
        }
        if (slotKey == null) {
            slotKey = "";
        }
        return new MaterialTarget(blockId, block, adapter, config, slotKey);
    }

    private static void addTarget(List<MaterialTarget> out, String blockId, @Nullable String slotHint) {
        MaterialTarget target = materialTarget(blockId, slotHint);
        if (target != null) {
            out.add(target);
        }
    }

    /** 探针在某个目标上的判定；探针不存在时返回 {@code false}，同时说明「没验到」。 */
    private static boolean verdict(List<Boolean> row, List<MaterialProbe> probes, String id) {
        for (int i = 0; i < probes.size(); i++) {
            if (probes.get(i).id().equals(id)) {
                return row.get(i);
            }
        }
        return false;
    }

    private static int indexOfTarget(List<MaterialTarget> targets, String labelFragment) {
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).label().contains(labelFragment)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 两个目标的逐探针判定必须一致。
     *
     * @param allowPanelWhitelist 允许在 Create 伪装板的「栏杆 / 活板门」白名单上有差异
     */
    private static void compareVerdicts(List<MaterialTarget> targets, List<List<Boolean>> verdicts,
                                        List<MaterialProbe> probes, int i, int j,
                                        boolean allowPanelWhitelist, Assertions a, List<String> log) {
        List<String> diffs = new ArrayList<>();
        for (int k = 0; k < probes.size(); k++) {
            if (verdicts.get(i).get(k).equals(verdicts.get(j).get(k))) {
                continue;
            }
            MaterialProbe probe = probes.get(k);
            if (allowPanelWhitelist && (CopycatSpecialCases.isBarsMaterial(probe.state())
                    || CopycatSpecialCases.isTrapdoorMaterial(probe.state()))) {
                continue;
            }
            diffs.add(probe.id() + "（" + targets.get(i).label() + "=" + verdicts.get(i).get(k)
                    + " / " + targets.get(j).label() + "=" + verdicts.get(j).get(k) + "）");
        }
        a.isTrue(targets.get(i).label() + " 与 " + targets.get(j).label() + " 逐探针一致"
                + (allowPanelWhitelist ? "（Create 伪装板的栏杆/活板门白名单除外）" : ""), diffs.isEmpty());
        if (!diffs.isEmpty()) {
            log.add("       差异：" + diffs);
        }
    }
    // ================================================================ 19. 界面几何

    /**
     * 界面几何的代码级验证：<b>被标记为可见的行一定落在视口里，视口外的行一定点不中</b>。
     *
     * <h2>为什么验的是 {@link PlacerLayout} 而不是 {@code PlacerScreen}</h2>
     * 上一版的缺陷是「材质区的行画到了面板外、盖住物品栏」。当时的修法思路是「记得调
     * {@code enableScissor}」——但 {@code enableScissor} 被调用过这件事本身什么都证明不了：
     * 裁剪框可以是错的、行坐标可以是错的、点击命中又可以是第三套坐标。
     * 真正的判据只能是<b>坐标本身</b>：任何一行只要被画出来，它的矩形就必须与裁剪矩形相交；
     * 反过来，裁剪框外的行必须连点都点不到。
     *
     * <p>而 {@code PlacerScreen} 在客户端源集里，专用服务端的自检永远跑不到它。
     * 所以这里验的是<b>界面真正在用的那份几何计算</b>：{@code PlacerScreen} 的渲染、裁剪、
     * 点击命中、滚动条全部读 {@link PlacerLayout#compute} 的结果，没有第二份实现。
     *
     * <h2>覆盖的输入</h2>
     * 多种窗口尺寸（含 320x240 基准与几个奇数尺寸）× 多种内容规模（空 / 一屏 / 远超一屏）
     * × 多种滚动量（0 / 中间 / 越界值）。越界值必须被夹紧——这是「滚到底之后再滚」的真实路径。
     */
    private static void runLayoutGeometry(Assertions a, List<String> log) {
        log.add("== 19. 界面几何（视口 / 行 / 点击裁剪）");

        int[][] screens = {
                {320, 240}, {640, 480}, {854, 480}, {1280, 720}, {1920, 1080},
                {331, 241}, {1000, 400}, {400, 300},
        };
        List<PlacerLayout.Content> contents = List.of(
                PlacerLayout.Content.empty(),
                new PlacerLayout.Content(1, 1, 1),
                new PlacerLayout.Content(60, 8, 30),
                new PlacerLayout.Content(500, 40, 400));

        int combos = 0;
        for (int[] screen : screens) {
            for (PlacerLayout.Content content : contents) {
                for (int scroll : new int[]{0, 3, 99_999}) {
                    checkGeometry(a, screen[0], screen[1], content, scroll);
                    combos++;
                }
            }
        }
        log.add("   覆盖 " + screens.length + " 种窗口尺寸 × " + contents.size()
                + " 种内容规模 × 3 种滚动量 = " + combos + " 组几何");

        // 具体回归：上一版出问题的那一组（基准分辨率 + 内容远超一屏）
        PlacerLayout.Geometry base = PlacerLayout.compute(320, 240,
                new PlacerLayout.Content(500, 40, 400), 0, 0, 0);
        int panelBottom = (240 - PlacerLayout.PANEL_HEIGHT) / 2 + PlacerLayout.PANEL_HEIGHT;
        log.add("   320x240：面板下边界 y=" + panelBottom
                + "，材质区 = [" + base.material().y() + ", " + base.material().bottom() + ")"
                + "，列表下边界 = " + base.list().bottom()
                + "，内容下边界 = " + base.contentBottom());
        a.isTrue("材质区下边界不超过面板下边界（溢出缺陷的直接回归）",
                base.material().bottom() <= panelBottom);
        a.equal("材质区下边界恰好是内容下边界（不多不少一个像素）",
                base.contentBottom(), base.material().bottom());
    }

    /** 覆核一组几何：视口边界、小节标题、行的可见性与点击命中、滚动条。 */
    private static void checkGeometry(Assertions a, int screenW, int screenH,
                                      PlacerLayout.Content content, int scroll) {
        String tag = screenW + "x" + screenH + "/内容(" + content.listRows() + ","
                + content.propertyRows() + "," + content.materialRows() + ")/滚动" + scroll;
        PlacerLayout.Geometry g = PlacerLayout.compute(screenW, screenH, content, scroll, scroll, scroll);

        int panelLeft = (screenW - PlacerLayout.PANEL_WIDTH) / 2;
        int panelTop = (screenH - PlacerLayout.PANEL_HEIGHT) / 2;
        int panelRight = panelLeft + PlacerLayout.PANEL_WIDTH;
        int panelBottom = panelTop + PlacerLayout.PANEL_HEIGHT;

        // --- 1) 三个视口都在面板里，且下边界不越过内容下边界
        for (PlacerLayout.Viewport viewport : g.all()) {
            a.check(tag + " 视口在面板横向范围内",
                    viewport.x() >= panelLeft && viewport.right() <= panelRight,
                    "视口 x=[" + viewport.x() + "," + viewport.right() + ")，面板 x=["
                            + panelLeft + "," + panelRight + ")");
            a.check(tag + " 视口在面板纵向范围内",
                    viewport.y() >= panelTop && viewport.bottom() <= panelBottom,
                    "视口 y=[" + viewport.y() + "," + viewport.bottom() + ")，面板 y=["
                            + panelTop + "," + panelBottom + ")");
            a.check(tag + " 视口下边界不越过内容下边界",
                    viewport.bottom() <= g.contentBottom(),
                    "视口下边界 " + viewport.bottom() + "，内容下边界 " + g.contentBottom());
            a.check(tag + " 视口至少能放下一行",
                    viewport.h() >= PlacerLayout.LINE_HEIGHT && viewport.w() > 0,
                    "视口 " + viewport.w() + "x" + viewport.h());
            a.check(tag + " 滚动量被夹进合法范围",
                    viewport.scroll() >= 0 && viewport.scroll() <= viewport.maxScroll(),
                    "scroll=" + viewport.scroll() + "，maxScroll=" + viewport.maxScroll());
        }
        a.equal(tag + " 列表下边界恰好是内容下边界",
                g.contentBottom(), g.list().bottom());
        a.equal(tag + " 材质区下边界恰好是内容下边界（由可用高度反推，不是各算一遍）",
                g.contentBottom(), g.material().bottom());
        a.check(tag + " 属性区不与材质小节标题重叠",
                g.property().bottom() <= g.materialTitleY(),
                "属性下边界 " + g.property().bottom() + "，材质标题行 " + g.materialTitleY());
        a.check(tag + " 搜索框完全在列表上方",
                g.searchTop() + PlacerLayout.SEARCH_HEIGHT <= g.list().y(),
                "搜索框底 " + (g.searchTop() + PlacerLayout.SEARCH_HEIGHT) + "，列表顶 " + g.list().y());

        // --- 2) 小节标题：文字必须整行落在内容区上方
        checkSectionTitle(a, tag + " 属性标题", g.propertyTitleY(), g.property().y(), panelTop, panelBottom);
        checkSectionTitle(a, tag + " 材质标题", g.materialTitleY(), g.material().y(), panelTop, panelBottom);

        // --- 3) 行：可见 ⇔ 与视口相交；视口外的行点不中
        checkRows(a, tag + " 列表", g.list(), PlacerLayout.ENTRY_HEIGHT);
        checkRows(a, tag + " 属性", g.property(), PlacerLayout.LINE_HEIGHT);
        checkRows(a, tag + " 材质", g.material(), PlacerLayout.LINE_HEIGHT);

        // --- 4) 滚动条：thumb 永远在视口里；拖动换算单调且夹紧
        for (PlacerLayout.Viewport viewport : g.all()) {
            if (!viewport.hasScrollbar()) {
                continue;
            }
            int thumbHeight = PlacerLayout.thumbHeightFor(viewport.h(), viewport.contentHeight());
            for (int value : new int[]{0, 1, viewport.maxScroll() / 2, viewport.maxScroll()}) {
                PlacerLayout.Viewport scrolled = viewport.withScroll(value);
                int top = PlacerLayout.thumbY(scrolled);
                a.check(tag + " 滚动条 thumb 在视口里",
                        top >= viewport.y() && top + thumbHeight <= viewport.bottom(),
                        "thumb y=[" + top + "," + (top + thumbHeight) + ")，视口 y=["
                                + viewport.y() + "," + viewport.bottom() + ")");
            }
            int travel = viewport.h() - thumbHeight;
            a.equal(tag + " 拖动到行程末端正好滚到底",
                    viewport.maxScroll(), PlacerLayout.scrollForDrag(viewport, 0, 0, travel));
            a.equal(tag + " 拖动超出下界被夹到 maxScroll",
                    viewport.maxScroll(), PlacerLayout.scrollForDrag(viewport, 0, 0, 100_000));
            a.equal(tag + " 拖动超出上界被夹到 0",
                    0, PlacerLayout.scrollForDrag(viewport, viewport.maxScroll(), 0, -100_000));
        }
    }

    /** 小节标题：文字整行在内容区上方，且标题条本身不越出面板。 */
    private static void checkSectionTitle(Assertions a, String tag, int titleY, int contentTop,
                                          int panelTop, int panelBottom) {
        a.check(tag + " 的文字不压到内容行",
                titleY + PlacerLayout.TITLE_TEXT_OFFSET + PlacerLayout.TITLE_TEXT_HEIGHT <= contentTop,
                "标题文字底 " + (titleY + PlacerLayout.TITLE_TEXT_OFFSET + PlacerLayout.TITLE_TEXT_HEIGHT)
                        + "，内容顶 " + contentTop);
        a.check(tag + " 的标题条在面板里",
                titleY - 1 >= panelTop && titleY + PlacerLayout.LINE_HEIGHT - 2 <= panelBottom,
                "标题条 y=[" + (titleY - 1) + "," + (titleY + PlacerLayout.LINE_HEIGHT - 2)
                        + ")，面板 y=[" + panelTop + "," + panelBottom + ")");
    }

    /**
     * 一个区域里所有行的可见性与点击命中。
     *
     * <p>三条断言就是「裁剪是否真的成立」的全部内容：
     * <ol>
     *   <li>{@code rowVisible} 为真的行，其矩形必须与视口相交（否则就是「画了看不见的东西」）；</li>
     *   <li>{@code rowVisible} 为假的行，其矩形必须与视口<b>完全不相交</b>（否则就是「漏画」）；</li>
     *   <li>视口内任意一个像素点，{@code rowIndexAt} 给出的行要么不存在，要么一定是可见行；
     *       并且每个可见行都真的能被点到（往返一致）——这就是「点击也被裁剪」。</li>
     * </ol>
     */
    private static void checkRows(Assertions a, String tag, PlacerLayout.Viewport viewport, int rowHeight) {
        int rows = viewport.contentHeight() / rowHeight;
        int midX = viewport.x() + viewport.w() / 2;

        for (int index = 0; index < rows; index++) {
            int top = viewport.rowY(index, rowHeight);
            int bottom = top + rowHeight;
            boolean intersects = bottom > viewport.y() && top < viewport.bottom();
            a.equal(tag + " 第 " + index + " 行的可见性与相交性一致",
                    intersects, viewport.rowVisible(index, rowHeight));
            if (viewport.rowVisible(index, rowHeight)) {
                // 用「区间求交」独立算一遍：可见行与视口的交集必须非空，
                // 且交集整体落在视口里（也就是被画出来的那一段一定在裁剪框内）。
                int intersectionTop = Math.max(top, viewport.y());
                int intersectionBottom = Math.min(bottom, viewport.bottom());
                a.check(tag + " 第 " + index + " 行与视口的交集非空",
                        intersectionBottom > intersectionTop,
                        "行 y=[" + top + "," + bottom + ")，视口 y=["
                                + viewport.y() + "," + viewport.bottom() + ")");
                a.check(tag + " 第 " + index + " 行的交集落在视口内",
                        intersectionTop >= viewport.y() && intersectionBottom <= viewport.bottom(),
                        "交集 y=[" + intersectionTop + "," + intersectionBottom + ")");
            }
            // 可见行必须点得到（往返一致）。探测点要夹进视口内：半露在边缘的行只有在
            // 它露出来的那部分上才点得到。
            int probeY = Math.min(Math.max(top + rowHeight / 2, viewport.y()), viewport.bottom() - 1);
            int hit = viewport.rowIndexAt(midX, probeY, rowHeight);
            a.check(tag + " 第 " + index + " 行可见就能被点到",
                    !viewport.rowVisible(index, rowHeight) || hit == index,
                    "命中的行 " + hit + "，探测点 y=" + probeY);
        }

        // 视口外一律点不中；视口内命中的行必须存在且可见
        a.equal(tag + " 视口上方点不中任何行",
                -1, viewport.rowIndexAt(midX, viewport.y() - 1, rowHeight));
        a.equal(tag + " 视口下方点不中任何行",
                -1, viewport.rowIndexAt(midX, viewport.bottom(), rowHeight));
        a.equal(tag + " 视口外的横向位置点不中任何行",
                -1, viewport.rowIndexAt(viewport.x() - 1, viewport.y() + rowHeight / 2, rowHeight));
        for (int mouseY = viewport.y(); mouseY < viewport.bottom(); mouseY++) {
            int hit = viewport.rowIndexAt(midX, mouseY, rowHeight);
            if (hit >= 0 && hit < rows) {
                a.check(tag + " y=" + mouseY + " 命中的行必须是可见行",
                        viewport.rowVisible(hit, rowHeight), "命中行 " + hit);
            }
        }
    }

    // ================================================================ 20. 结构配置模型

    /**
     * 结构配置模型的代码级验证：<b>逐项布尔 + 动态展开</b>。
     *
     * <p>覆盖两组方块各自的结构项集合、角柱 / 开窗 / 交汇点的出现规则、材质槽的动态收缩、
     * 以及旧配置的兼容转换。这一段的判据全部来自 adapter 的公开 API（与 GUI 走的是同一批），
     * 所以「界面会长什么样」在这里是被算出来的，不是被描述的。
     */
    private static void runStructureModel(Assertions a, List<String> log) {
        log.add("== 20. 结构配置模型（逐项布尔 + 动态展开）");

        // 界面文本的构造路径必须「宽度与字形同源」：这里断言解析出来的字符串与真正会被
        // 画出来的字形一致。原版 MutableText 的 asOrderedText() 会缓存重排结果而 append 不失效，
        // 所以界面现在一律用解析好的字符串重新构造文本，这条断言就是那道防线。
        net.minecraft.text.MutableText literal = net.minecraft.text.Text.literal("ABCDEF");
        a.equal("Text.literal 的字形与 getString 一致", literal.getString(), orderedTextOf(literal));

        runGuardrailStructureModel(a, log);
        runBoardStructureModel(a, log);
        runStructureCompat(a, log);

        // 直接打出「默认配置下界面会长什么样」——用兜底文本（服务端没有语言文件，
        // 走的就是 labelText 那一支），所以这一段既是证据也是排版检查。
        for (Block block : List.of(ModBlock.COPYCAT_GUARDRAIL, ModBlock.LAYERED_COPYCAT_BOARD)) {
            CopycatPlacementAdapter adapter = PlacementAdapters.resolve(block).orElseThrow();
            PlacementConfig defaults = adapter.defaultConfig(block);
            log.add("   默认配置下的结构项（adapter 给出的顺序）：");
            for (VirtualSpec spec : adapter.virtualSpecs(defaults)) {
                StringBuilder label = new StringBuilder();
                for (VirtualSpec.LabelPart part : spec.label()) {
                    if (label.length() > 0) {
                        label.append(" / ");
                    }
                    label.append(part.labelText());
                }
                VirtualSpec.Option current = spec.current(defaults);
                log.add("     " + label + " = "
                        + (current == null ? "<无效>" : current.label().get(0).labelText()));
            }
        }
    }

    // ---------------------------------------------------------------- 护栏渲染：cullFace 判定

    /**
     * 护栏渲染的 cullFace 判定（代码级验证，不需要客户端）。
     *
     * <h2>为什么这一步必须在 main 侧验</h2>
     * 渲染路径用的几何是 {@link GuardrailParts#boxesByKey} 给出的盒子，材质模型的 quad 由
     * {@code BakedModelHelper.cropAndMove} 逐顶点 clamp 进这些盒子。那个搬运<b>不会</b>改
     * quad 的 cullFace——所以如果渲染层照抄源材质模型的 cullFace，就会出现「贴在方块内部的
     * 端面带着某个方向的 cullFace」。这里把渲染层真正会发射的每一张 quad 复算一遍，逐张断言
     * 「带 cullFace 的 quad 必须真的落在对应方向的外边界上」。
     *
     * <h2>覆盖范围</h2>
     * 15 种方向组合（单方向 / 相邻两方向 / 相对两方向 / 三方向 / 四方向）× 每种组合的每个部件，
     * 所以不存在「只验了截图里那个方向」。
     */
    private static void runGuardrailCullFaceModel(Assertions a, List<String> log) {
        log.add("== 20d. 护栏渲染 cullFace（cropAndMove 之后的落点判定）");

        int checked = 0;
        /** 0 = 贴边界面的 quad、1 = 方块内部的 quad、2 = 修复前会被错误剔除的内部 quad。 */
        int[] counters = new int[3];

        for (int mask = 1; mask < 16; mask++) {
            BlockState state = guardrailState(mask);
            Map<String, List<Box>> boxes = GuardrailParts.boxesByKey(state, Set.of());
            a.isFalse("掩码 " + mask + " 至少有一个可见槽位", boxes.isEmpty());

            for (Map.Entry<String, List<Box>> entry : boxes.entrySet()) {
                String key = entry.getKey();
                for (Box box : entry.getValue()) {
                    // 掩码里每个方向都必须真的有横梁几何（多方向不能把某个方向吃掉）
                    if (key.endsWith("_row")) {
                        Direction dir = directionOfName(key.substring(0, key.length() - "_row".length()));
                        a.notNull("横梁键名能解析回方向：" + key, dir);
                        if (dir != null) {
                            a.isTrue("掩码 " + mask + " 里 " + dir + " 有横梁，所以它的位必须开着",
                                    CopycatGuardrailBlock.maskHas(mask, dir));
                        }
                    }
                    checked += emitAndCheckBox(a, key, box, mask, counters);
                }
            }
        }

        int boundaryQuads = counters[0];
        int interiorQuads = counters[1];
        int regressionQuads = counters[2];
        log.add("   护栏 cullFace：覆盖 15 种方向组合、共 " + checked + " 个盒子，"
                + "贴边界面的 quad " + boundaryQuads + " 张、方块内部 quad " + interiorQuads
                + " 张（其中 " + regressionQuads + " 张是修复前会被邻居错误剔除的）");
        a.isTrue("确实存在贴外边界的 quad（否则说明判定把所有面都判成内部了）", boundaryQuads > 0);
        a.isTrue("确实存在方块内部的 quad（否则说明判定把所有面都判成边界了）", interiorQuads > 0);
        // 这一条保证这段自检真的覆盖了那个 bug：如果一张「会被错误剔除」的内部面都没有，
        // 说明几何或摆位变了、这段验证已经触及不到问题所在，必须有人重新看一眼。
        a.isTrue("确实存在「修复前会被错误剔除」的内部 quad（回归覆盖有效）", regressionQuads > 0);

        // --- 四方向对称：每根横梁的盒子必定贴着本方向的 BlockPos 边界，
        //     而且那一张「外侧」quad 必须正好拿到本方向的 cullFace。
        //     四个方向走的是同一套规则，所以这里不需要按方向写特例。
        for (int mask = 1; mask < 16; mask++) {
            BlockState state = guardrailState(mask);
            Map<String, List<Box>> boxes = GuardrailParts.boxesByKey(state, Set.of());
            for (Direction dir : CopycatGuardrailBlock.FACES) {
                if (!CopycatGuardrailBlock.maskHas(mask, dir)) {
                    continue;
                }
                List<Box> rows = boxes.get(GuardrailParts.rowKey(dir));
                a.notNull("掩码 " + mask + " 里 " + dir + " 横梁必须有几何", rows);
                if (rows == null) {
                    continue;
                }
                int axis = dir.getAxis().ordinal();
                boolean atMin = dir.getDirection() == Direction.AxisDirection.NEGATIVE;
                for (Box box : rows) {
                    a.equal("掩码 " + mask + " " + dir + " 横梁贴在本方向边界上的那一面",
                            dir, boundaryFaceOf(dir, box));
                    Direction outerCull = BoardFaceCulling.boxCullFace(axis,
                            (float) (atMin ? axisMin(box, axis) : axisMax(box, axis)), box);
                    a.equal("掩码 " + mask + " " + dir + " 横梁朝本方向那一张 quad 的 cullFace",
                            dir, outerCull);
                }
            }
        }

        // --- 多方向不许互相污染：某一根横梁的判定只能取决于它自己那一根，
        //     与同格里还有哪些别的方向无关。参照是「只开这一个方向」时的签名；
        //     加入任何其它方向之后都必须逐字不变。
        //     （东/西、南/北互为镜像，签名本来就差一个符号，所以不能横向互相比较。）
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            List<String> alone = rowSignatureOf(guardrailState(CopycatGuardrailBlock.bit(dir)), dir);
            for (int mask = 1; mask < 16; mask++) {
                if (!CopycatGuardrailBlock.maskHas(mask, dir) || mask == CopycatGuardrailBlock.bit(dir)) {
                    continue;
                }
                a.equal("掩码 " + mask + " 里 " + dir + " 横梁的 cullFace 签名必须与只开它自己时一致"
                                + "（同格其它方向不许污染它）",
                        alone, rowSignatureOf(guardrailState(mask), dir));
            }
        }

        log.add("   护栏 cullFace：四方向对称性、多方向互不污染、单方向 vs 多方向一致性均通过");
    }

    /**
     * 这根横梁「贴在本方向边界上」的那一面是哪个方向；没有贴边界的面时返回 {@code null}。
     *
     * <p>横梁是本方向那一面的一部分，所以它必定在「本方向的轴」上顶着 0 或 1。
     */
    private static Direction boundaryFaceOf(Direction dir, Box box) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() != dir.getAxis()) {
                continue;
            }
            boolean atMin = side.getDirection() == Direction.AxisDirection.NEGATIVE;
            double coord = atMin ? axisMin(box, side.getAxis().ordinal())
                    : axisMax(box, side.getAxis().ordinal());
            boolean onBoundary = atMin
                    ? coord <= BoardFaceCulling.PLANE_EPSILON
                    : coord >= 1.0 - BoardFaceCulling.PLANE_EPSILON;
            if (onBoundary) {
                return side;
            }
        }
        return null;
    }

    /** 某个掩码里某个方向的横梁的 cullFace 签名（已排序，可比）。 */
    private static List<String> rowSignatureOf(BlockState state, Direction dir) {
        List<String> out = new ArrayList<>();
        List<Box> boxes = GuardrailParts.boxesByKey(state, Set.of())
                .get(GuardrailParts.rowKey(dir));
        if (boxes == null) {
            return out;
        }
        for (Box box : boxes) {
            out.addAll(cullSignature(box));
        }
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * 一个盒子上六类 quad 的<b>旋转无关</b>判定签名：{轴 + 方向 + 有没有 cullFace}。
     *
     * <p>刻意不写绝对方向名与盒子坐标：把结构整体转 90° 时它们本来就会跟着变，
     * 写进去只会让「判定是不是对称」这件事被坐标差异淹没。真正该不变的是
     * 「这个部件的哪一类 quad 被判定成贴着外边界」。
     */
    private static List<String> cullSignature(Box box) {
        List<String> out = new ArrayList<>();
        for (int[] quad : MATERIAL_CUBE_QUADS) {
            int axis = quad[1];
            boolean atMin = quad[0] < 0;
            float plane = atMin ? (float) axisMin(box, axis) : (float) axisMax(box, axis);
            Direction cull = BoardFaceCulling.boxCullFace(axis, plane, box);
            out.add(axis + (atMin ? "-" : "+") + "|" + (cull != null));
        }
        return out;
    }

    /**
     * 复算一个盒子真正会发射的 quad 并逐张断言。
     *
     * <p>源四边形的形状在这里不重要——{@code cropAndMove} 是逐顶点 clamp，任何与盒子某轴垂直的
     * 源面落进盒子之后都会落在盒子的某个面上；决定落点的是「源面的法线指向哪一个轴、哪一个方向」。
     * 所以对每个轴、每个符号各算一次就覆盖了全部落点。
     */
    private static int emitAndCheckBox(Assertions a, String key, Box box, int mask, int[] counters) {
        int quads = 0;
        for (int[] quad : MATERIAL_CUBE_QUADS) {
            int sign = quad[0];
            int axis = quad[1];
            boolean atMin = sign < 0;
            float plane = atMin ? (float) axisMin(box, axis) : (float) axisMax(box, axis);
            Direction cull = BoardFaceCulling.boxCullFace(axis, plane, box);
            quads++;

            double boxMin = axisMin(box, axis);
            double boxMax = axisMax(box, axis);
            boolean boxFaceAtBlockBoundary = atMin
                    ? boxMin <= BoardFaceCulling.PLANE_EPSILON
                    : boxMax >= 1.0 - BoardFaceCulling.PLANE_EPSILON;

            String tag = "掩码 " + mask + " " + key + " " + boxKey(box)
                    + " quad(轴=" + axis + (atMin ? "-" : "+") + ")";

            if (cull != null) {
                counters[0]++;
                // 核心不变量：带 cullFace 的 quad 必须真的贴在对应方向的外边界上
                a.isTrue(tag + " 带了 cullFace=" + cull + "，所以 box 在该方向必须压在方块边界上",
                        boxFaceAtBlockBoundary);
                a.equal(tag + " cullFace 必须与落点方向一致", boundaryOf(axis, atMin), cull);
            } else {
                counters[1]++;
                // 落在方块内部的面绝不允许带 cullFace，否则邻居的完整固体方块会把内部几何一起剔掉
                a.isFalse(tag + " 落在方块内部，所以不允许带任何 cullFace", boxFaceAtBlockBoundary);
                // 与修复前的做法逐张对照：旧代码只是 emitter.copyFrom(quad) 之后直接 emit，
                // 于是 quad 保留了材质模型给它的 cullFace —— 也就是 (axis, atMin) 对应的那个方向。
                // 这一张 quad 落在方块内部，所以旧值必定非空且与新判定不同：
                // 它是「照抄源 cullFace 就会出错」的受害面，这条断言就是那个 bug 的回归防线。
                Direction copiedFromMaterial = boundaryOf(axis, atMin);
                a.isFalse(tag + " 修复前会照抄材质模型的 cullFace=" + copiedFromMaterial
                                + "，新判定必须是 null",
                        cull == copiedFromMaterial);
                counters[2]++;
            }
        }
        return quads;
    }

    /** 某个轴负/正端对应的世界方向。 */
    private static Direction boundaryOf(int axis, boolean atMin) {
        if (axis == 0) {
            return atMin ? Direction.WEST : Direction.EAST;
        }
        if (axis == 1) {
            return atMin ? Direction.DOWN : Direction.UP;
        }
        return atMin ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * 一个完整立方体材质模型的 6 张面，写成 {@code {符号, 轴}}：符号 -1 表示该轴的负端面。
     *
     * <p>任何伪装材质模型都至少包含这 6 张与坐标轴垂直的 quad，它们就是 cropAndMove 之后
     * 唯一能留下面积的那些（其余朝向会被 clamp 成零面积）。
     */
    private static final int[][] MATERIAL_CUBE_QUADS = {
            {-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {1, 2}
    };

    private static double axisMin(Box box, int axis) {
        return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
    }

    private static double axisMax(Box box, int axis) {
        return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
    }

    private static String boxKey(Box box) {
        return String.format(java.util.Locale.ROOT, "[%.4f,%.4f,%.4f -> %.4f,%.4f,%.4f]",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /** 用位掩码造一个护栏状态。 */
    private static BlockState guardrailState(int mask) {
        BlockState state = ModBlock.COPYCAT_GUARDRAIL.getDefaultState();
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            state = state.with(CopycatGuardrailBlock.PROPERTY_BY_DIRECTION.get(dir),
                    CopycatGuardrailBlock.maskHas(mask, dir));
        }
        return state;
    }

    /** 由 {@code north}/{@code east}/… 反解方向。 */
    private static Direction directionOfName(String name) {
        for (Direction dir : CopycatGuardrailBlock.FACES) {
            if (dir.asString().equals(name)) {
                return dir;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 护栏

    private static void runGuardrailStructureModel(Assertions a, List<String> log) {
        CopycatPlacementAdapter adapter = PlacementAdapters.resolve(ModBlock.COPYCAT_GUARDRAIL).orElseThrow();
        BlockState state = ModBlock.COPYCAT_GUARDRAIL.getDefaultState();
        int north = CopycatGuardrailBlock.bit(Direction.NORTH);
        int east = CopycatGuardrailBlock.bit(Direction.EAST);
        int south = CopycatGuardrailBlock.bit(Direction.SOUTH);
        int west = CopycatGuardrailBlock.bit(Direction.WEST);

        // --- ① 默认配置：只有北面 = 是，四个角柱默认都在
        PlacementConfig defaults = adapter.defaultConfig(ModBlock.COPYCAT_GUARDRAIL);
        a.equal("护栏默认只有北面", north, GuardrailCopycatAdapter.facesMask(defaults));
        a.isTrue("护栏默认北面 = 是", GuardrailCopycatAdapter.faceEnabled(defaults, Direction.NORTH));
        a.isFalse("护栏默认东面 = 否", GuardrailCopycatAdapter.faceEnabled(defaults, Direction.EAST));
        a.isFalse("护栏默认南面 = 否", GuardrailCopycatAdapter.faceEnabled(defaults, Direction.SOUTH));
        a.isFalse("护栏默认西面 = 否", GuardrailCopycatAdapter.faceEnabled(defaults, Direction.WEST));
        a.equal("护栏默认四个角柱都是「是」", GuardrailCopycatAdapter.DEFAULT_CORNERS,
                GuardrailCopycatAdapter.cornersMask(defaults));
        for (GuardrailCopycatAdapter.Corner corner : GuardrailCopycatAdapter.Corner.values()) {
            a.isTrue("默认角柱 " + corner.getName() + " = 是",
                    GuardrailCopycatAdapter.cornerEnabled(defaults, corner));
        }

        // --- ② 四面全否 → 非法结构
        PlacementConfig noFace = GuardrailCopycatAdapter.withFaces(defaults, 0);
        a.notNull("四面全否被判为非法结构",
                adapter.validateStructure(adapter.stateFrom(noFace.state(), noFace), noFace));
        for (int mask = 1; mask < 16; mask++) {
            PlacementConfig anyFace = GuardrailCopycatAdapter.withFaces(defaults, mask);
            a.isTrue("掩码 " + StructureMasks.write(mask) + " 至少一面 → 合法",
                    adapter.validateStructure(adapter.stateFrom(anyFace.state(), anyFace), anyFace) == null);
        }

        // --- ③ 只开北面 → 只出现东北角柱 / 西北角柱
        a.equal("只开北面时的结构项 = 4 个面 + 东北 + 西北",
                List.of("face.north", "face.east", "face.south", "face.west",
                        "corner.northeast", "corner.northwest"),
                specKeys(adapter, defaults));

        // --- ④ 开北 + 东 → 角柱动态集合为 东北 / 西北 / 东南
        PlacementConfig northEast = GuardrailCopycatAdapter.withFaces(defaults, north | east);
        a.equal("开北 + 东时的角柱 = 东北 / 西北 / 东南",
                List.of("corner.northeast", "corner.northwest", "corner.southeast"),
                specKeys(adapter, northEast).stream().filter(key -> key.startsWith("corner.")).toList());
        PlacementConfig allFaces = GuardrailCopycatAdapter.withFaces(defaults, 0xF);
        a.equal("四面全开时四个角柱都出现",
                List.of("corner.northeast", "corner.northwest", "corner.southeast", "corner.southwest"),
                specKeys(adapter, allFaces).stream().filter(key -> key.startsWith("corner.")).toList());
        PlacementConfig southWest = GuardrailCopycatAdapter.withFaces(defaults, south | west);
        a.equal("开南 + 西时的角柱 = 西北（西）/ 东南（南）/ 西南",
                List.of("corner.northwest", "corner.southeast", "corner.southwest"),
                specKeys(adapter, southWest).stream().filter(key -> key.startsWith("corner.")).toList());

        // --- ⑤ 材质槽：全量列出，只有「当前有效」的那些会被标成有效（GUI 据此决定是否置灰）
        List<AdapterSlot> railSlots = adapter.slots(state, defaults);
        a.equal("护栏材质区列出全量 8 个槽（4 横梁 + 4 角柱）", 8, guiSlotKeys(adapter, state, defaults).size());
        a.equal("只开北面时有效的槽 = 北横梁 + 东北角柱 + 西北角柱",
                List.of("north_row", "15_0", "0_0"), activeSlotKeys(adapter, state, defaults));
        a.equal("开北 + 东时有效的槽多了东横梁与东南角柱",
                List.of("north_row", "east_row", "15_0", "15_15", "0_0"),
                activeSlotKeys(adapter, state, northEast));
        PlacementConfig northNoCorner = GuardrailCopycatAdapter.withCorner(
                defaults, GuardrailCopycatAdapter.Corner.NORTHEAST, false);
        a.equal("关掉东北角柱之后它变成无效槽（仍然列出，只是置灰）",
                List.of("north_row", "0_0"), activeSlotKeys(adapter, state, northNoCorner));
        a.equal("关掉东北角柱之后材质区仍然是全量 8 个槽",
                8, guiSlotKeys(adapter, state, northNoCorner).size());
        a.isTrue("无效槽确实带着 structure=false 这个「置灰用」的状态",
                railSlots.stream().filter(slot -> slot.key().equals("15_0"))
                        .noneMatch(AdapterSlot::structure)
                        || railSlots.stream().anyMatch(slot -> !slot.structure()));
        a.equal("全量清单里标记为有效的槽 = 3", 3,
                (int) railSlots.stream().filter(AdapterSlot::structure).count());

        // --- ⑥ 关掉一个角柱 = 那根柱子真的有隐藏数据（与细实体的隐藏集合同一份）
        a.equal("关掉东北角柱 → 隐藏集合只含 15_0",
                List.of("15_0"),
                List.copyOf(GuardrailCopycatAdapter.hiddenColumns(northNoCorner)));
        a.isTrue("四个角柱全开时没有隐藏集合",
                GuardrailCopycatAdapter.hiddenColumns(defaults).isEmpty());

        // --- ⑦ 角柱开关「隐藏—再出现」不重置
        PlacementConfig cornerOff = GuardrailCopycatAdapter.withCorner(defaults,
                GuardrailCopycatAdapter.Corner.NORTHEAST, false);
        PlacementConfig backToNorth = GuardrailCopycatAdapter.withFaces(cornerOff, north);
        a.isFalse("重新只开北面之后东北角柱的选择还在（= 否）",
                GuardrailCopycatAdapter.cornerEnabled(backToNorth, GuardrailCopycatAdapter.Corner.NORTHEAST));
        PlacementConfig facesOff = GuardrailCopycatAdapter.withFaces(cornerOff, 0);
        List<String> hiddenWhileInvisible = specKeys(adapter, facesOff);
        a.equal("四面全关时没有任何角柱项（面都关了）",
                List.of("face.north", "face.east", "face.south", "face.west"), hiddenWhileInvisible);
        a.isFalse("四面全关期间角柱的值也没被改掉",
                GuardrailCopycatAdapter.cornerEnabled(facesOff, GuardrailCopycatAdapter.Corner.NORTHEAST));

        // --- ⑧ 显示文本：不含 raw mask / raw key
        for (VirtualSpec spec : adapter.virtualSpecs(allFaces)) {
            for (VirtualSpec.LabelPart part : spec.label()) {
                a.check("护栏结构项标题不是裸掩码 / 裸键：" + part.labelText(),
                        !part.labelText().matches("(?i)^(0x[0-9a-f]+|[0-9]+|face\\..*|corner\\..*)$"),
                        "labelText=" + part.labelText());
                a.isFalse("护栏结构项标题不含 0x：" + part.labelText(),
                        part.labelText().toLowerCase(java.util.Locale.ROOT).contains("0x"));
            }
        }

        // --- ⑨ 角柱身份与几何的角点键名一致（不能用另一套编号）
        for (GuardrailCopycatAdapter.Corner corner : GuardrailCopycatAdapter.Corner.values()) {
            a.isTrue("角柱 " + corner.getName() + " 的角点键名在几何里存在：" + corner.columnKey(),
                    GuardrailParts.columnKeys().contains(corner.columnKey()));
        }
        log.add("   护栏：默认只有北面；角柱按相邻面动态出现；材质槽全量 8 个，其中真的有几何的 "
                + activeSlotKeys(adapter, state, defaults).size() + " 个");
    }

    // ---------------------------------------------------------------- 分层薄板

    private static void runBoardStructureModel(Assertions a, List<String> log) {
        CopycatPlacementAdapter adapter = PlacementAdapters.resolve(ModBlock.LAYERED_COPYCAT_BOARD).orElseThrow();
        BlockState state = ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState();
        PlacementConfig defaults = adapter.defaultConfig(ModBlock.LAYERED_COPYCAT_BOARD);

        // --- ⑦ 默认配置：只有下面外层 = 是
        a.equal("薄板默认只有下面外层", LayeredBoardSlots.slotBitMask(FaceDir.DOWN, BoardLayer.OUTER),
                LayeredBoardCopycatAdapter.occupancy(defaults));
        for (FaceDir face : FaceDir.values()) {
            for (BoardLayer layer : BoardLayer.values()) {
                boolean expected = face == FaceDir.DOWN && layer == BoardLayer.OUTER;
                a.equal("默认 " + LayeredBoardSlots.describe(face, layer) + " = " + expected,
                        expected, LayeredBoardCopycatAdapter.slotEnabled(defaults, face, layer));
            }
        }
        a.equal("薄板默认不开窗", 0, LayeredBoardCopycatAdapter.windows(defaults));

        // --- ⑧ 12 个面全否 → 非法结构
        PlacementConfig empty = LayeredBoardCopycatAdapter.withOccupancy(defaults, 0);
        a.notNull("12 个面全否被判为非法结构",
                adapter.validateStructure(adapter.stateFrom(empty.state(), empty), empty));
        a.isTrue("只有一个面 → 合法",
                adapter.validateStructure(adapter.stateFrom(defaults.state(), defaults), defaults) == null);

        // --- ⑨⑩ 开窗项按「这个方向有没有板」动态出现
        List<String> defaultSpecs = specKeys(adapter, defaults);
        long defaultWindows = defaultSpecs.stream().filter(key -> key.startsWith("window.")).count();
        a.equal("默认只有下面有板 → 只有下面开窗项", List.of("window.down"),
                defaultSpecs.stream().filter(key -> key.startsWith("window.")).toList());
        a.equal("12 个层开关一直都在", 12,
                (int) defaultSpecs.stream().filter(key -> key.startsWith("layer.")).count());

        PlacementConfig upOuterOnly = LayeredBoardCopycatAdapter.withOccupancy(
                defaults, LayeredBoardSlots.slotBitMask(FaceDir.UP, BoardLayer.OUTER));
        a.equal("只开上面外层 → 只有上面开窗项", List.of("window.up"),
                specKeys(adapter, upOuterOnly).stream().filter(k -> k.startsWith("window.")).toList());

        PlacementConfig upBoth = LayeredBoardCopycatAdapter.withSlot(upOuterOnly, FaceDir.UP,
                BoardLayer.INNER, true);
        a.equal("上面两层都开 → 仍然是一个上面开窗项（窗是面级的）", List.of("window.up"),
                specKeys(adapter, upBoth).stream().filter(k -> k.startsWith("window.")).toList());

        PlacementConfig upInnerOff = LayeredBoardCopycatAdapter.withSlot(upBoth, FaceDir.UP,
                BoardLayer.INNER, false);
        a.equal("只留上面外层 → 上面开窗项仍然在", List.of("window.up"),
                specKeys(adapter, upInnerOff).stream().filter(k -> k.startsWith("window.")).toList());
        PlacementConfig upAllOff = LayeredBoardCopycatAdapter.withSlot(upInnerOff, FaceDir.UP,
                BoardLayer.OUTER, false);
        a.isTrue("两层都关 → 上面开窗项消失",
                specKeys(adapter, upAllOff).stream().noneMatch(k -> k.startsWith("window.")));

        // 开窗项隐藏—再出现不重置
        PlacementConfig upWindowOn = LayeredBoardCopycatAdapter.withWindow(upBoth, FaceDir.UP, true);
        PlacementConfig upOffWithWindow = LayeredBoardCopycatAdapter.withSlot(
                LayeredBoardCopycatAdapter.withSlot(upWindowOn, FaceDir.UP, BoardLayer.OUTER, false),
                FaceDir.UP, BoardLayer.INNER, false);
        a.isTrue("两层关掉期间，上面开窗项不出现（值仍在配置里）",
                specKeys(adapter, upOffWithWindow).stream().noneMatch(k -> k.startsWith("window.")));
        a.isTrue("上面开窗的值没有被重置",
                LayeredBoardCopycatAdapter.windowEnabled(upOffWithWindow, FaceDir.UP));

        // --- ⑪ 材质槽：全量 66 个都列出，只有真的有几何的才是「有效」
        List<String> onePlateActive = activeSlotKeys(adapter, state, defaults);
        log.add("   薄板：默认（只开下面外层）时有效的材质槽 = " + onePlateActive);
        a.equal("只开一块板时有效的材质槽 = 这一层的 5 个区域",
                List.of("down.outer.body", "down.outer.top_edge", "down.outer.bottom_edge",
                        "down.outer.left_edge", "down.outer.right_edge"),
                onePlateActive);
        a.isTrue("有效槽远少于 66 个", onePlateActive.size() <= 10);
        a.equal("材质区仍然列出全量 66 个槽（无效的置灰，不过滤）",
                66, guiSlotKeys(adapter, state, defaults).size());
        a.equal("默认结构下有效的槽就是那 5 个", 5,
                (int) adapter.slots(state, defaults).stream().filter(AdapterSlot::structure).count());

        a.equal("开了下面两层 → 10 个有效区域槽",
                10, activeSlotKeys(adapter, state,
                        LayeredBoardCopycatAdapter.withOccupancy(defaults,
                                LayeredBoardSlots.slotBitMask(FaceDir.DOWN, BoardLayer.OUTER)
                                        | LayeredBoardSlots.slotBitMask(FaceDir.DOWN, BoardLayer.INNER)))
                        .size());
        a.isTrue("开窗之后窗槽才变成有效",
                activeSlotKeys(adapter, state, LayeredBoardCopycatAdapter.withWindow(defaults, FaceDir.DOWN, true))
                        .contains("down.window"));
        a.isFalse("没开窗时窗槽不是有效槽（但仍在全量列表里）",
                onePlateActive.contains("down.window"));
        a.isTrue("没开窗时窗槽仍然列在材质区里（置灰）",
                guiSlotKeys(adapter, state, defaults).contains("down.window"));

        // --- ⑫ 顶点材质归属：只列 corner 级的 junction
        PlacementConfig twoPlates = LayeredBoardCopycatAdapter.withOccupancy(defaults,
                LayeredBoardSlots.slotBitMask(FaceDir.DOWN, BoardLayer.OUTER)
                        | LayeredBoardSlots.slotBitMask(FaceDir.NORTH, BoardLayer.OUTER));
        int junctionsOne = junctionSpecs(adapter, defaults).size();
        int junctionsTwo = junctionSpecs(adapter, twoPlates).size();
        log.add("   薄板：顶点归属项 单块板 " + junctionsOne + " 个，两块板 " + junctionsTwo + " 个");
        a.isTrue("单块板至少有一个可切换的顶点（自身的四个角）", junctionsOne >= 1);
        a.isTrue("结构变复杂之后顶点归属项变多", junctionsTwo > junctionsOne);
        for (VirtualSpec spec : junctionSpecs(adapter, twoPlates)) {
            a.isTrue("顶点归属项必然有多个候选边：" + spec.key(), spec.options().size() >= 2);
        }

        // --- ⑫′ GUI 只暴露顶点：中 / 棱 / 面 junction 不生成 StructureSpec
        runVertexOnlyFilter(a, log, adapter, defaults, twoPlates);

        for (LayeredBoardParts.Junction junction : LayeredBoardParts.junctions(
                LayeredBoardCopycatAdapter.occupancy(twoPlates),
                LayeredBoardCopycatAdapter.windows(twoPlates),
                LayeredBoardCopycatAdapter.junctionOwners(twoPlates))) {
            a.isTrue("几何枚举出的交汇点也必然有多个候选：" + junction.key(),
                    junction.candidates().size() >= 2);
        }

        // 交汇点选择会写进配置，并且真的改掉「这个交汇点显示哪条边」
        VirtualSpec junction = junctionSpecs(adapter, twoPlates).get(0);
        String shownBefore = junction.current(twoPlates).id();
        PlacementConfig switched = junction.with(twoPlates, junction.next(twoPlates));
        a.isFalse("点击交汇点项之后显示归属变了",
                shownBefore.equals(junction.current(switched).id()));
        List<VirtualSpec> rebuilt = adapter.virtualSpecs(switched);
        String shownAfter = rebuilt.stream().filter(spec -> spec.key().equals(junction.key()))
                .findFirst().orElseThrow().current(switched).id();
        a.equal("重建结构项之后显示的仍是新归属", junction.current(switched).id(), shownAfter);
        a.isTrue("交汇点归属写进了配置的结构字段",
                !LayeredBoardCopycatAdapter.junctionOwners(switched).isEmpty());
        a.equal("写回的值就是候选槽名",
                LayeredBoardCopycatAdapter.junctionOwners(switched).values().stream().findFirst().orElseThrow(),
                junction.current(switched).id());

        // --- 材质槽集合必须与「这份结构真正画出来的几何」逐一相同（含交点归属的影响）
        for (PlacementConfig sample : List.of(defaults, twoPlates, switched, upBoth,
                LayeredBoardCopycatAdapter.withWindow(defaults, FaceDir.DOWN, true))) {
            java.util.Set<String> geometry = LayeredBoardParts.materialKeys(
                    LayeredBoardCopycatAdapter.occupancy(sample),
                    LayeredBoardCopycatAdapter.windows(sample),
                    LayeredBoardCopycatAdapter.junctionOwners(sample));
            a.equal("界面列出的有效槽集合 == 几何键集合（" + LayeredBoardCopycatAdapter.occupancy(sample) + "）",
                    new java.util.TreeSet<>(geometry),
                    new java.util.TreeSet<>(activeSlotKeys(adapter, state, sample)));
            a.equal("每份结构下材质区都是全量 66 个槽（无效的置灰）",
                    66, guiSlotKeys(adapter, state, sample).size());
        }

        // --- ⑬ 交汇点显示的是人类可读文案，不是 raw value
        for (VirtualSpec spec : junctionSpecs(adapter, twoPlates)) {
            for (VirtualSpec.Option option : spec.options()) {
                a.isTrue("交汇点候选 id 是槽名（内部标识）：" + option.id(),
                        option.id().contains("."));
                for (VirtualSpec.LabelPart part : option.label()) {
                    a.check("交汇点候选显示文本不是裸值：" + part.labelText(),
                            !part.labelText().matches("(?i)^(0x[0-9a-f]+|[0-9]+)$"),
                            "labelText=" + part.labelText());
                    a.check("交汇点候选显示文本不是槽位码：" + part.labelText(),
                            !part.labelText().equals(option.id()),
                            "labelText=" + part.labelText() + "，id=" + option.id());
                }
            }
        }

        runJunctionLabels(a, log, adapter, defaults, twoPlates);
        runStructureTooltipRules(a, log, adapter, defaults, twoPlates);
    }

    // ---------------------------------------------------------------- 交点标题 + 悬停提示

    /**
     * 交点行的标题必须描述「这是哪个物理交点」，而不是把所有候选边名拼起来。
     *
     * <p>判据用<b>兜底文本</b>（中文）：服务端没有加载 zh_cn，解析出来的是 en_us，
     * 而这里要验的是「默认语言下这一行到底有多长、读起来是不是一个位置」。
     */
    private static void runJunctionLabels(Assertions a, List<String> log, CopycatPlacementAdapter adapter,
                                          PlacementConfig defaults, PlacementConfig twoPlates) {
        // --- 只开下面外层：恰好 4 个交点，名字就是这一层的四个角
        List<VirtualSpec> single = junctionSpecs(adapter, defaults);
        a.equal("单块板恰好 4 个交点行", 4, single.size());
        List<String> names = new ArrayList<>();
        for (VirtualSpec spec : single) {
            names.add(joinFallback(spec.label()));
        }
        log.add("   单块板交点标题 = " + names);
        a.equal("四个交点标题就是这一层的四个角",
                List.of("底面·外层·左下角", "底面·外层·右下角", "底面·外层·左上角", "底面·外层·右上角"),
                names);
        for (int i = 0; i < single.size(); i++) {
            VirtualSpec spec = single.get(i);
            a.isTrue("交点标题很短（默认语言下 ≤ 9 字）：" + names.get(i),
                    names.get(i).length() <= 9);
            a.isFalse("交点标题里没有候选边名的拼接：" + names.get(i),
                    names.get(i).contains("边"));
            // 右侧取值必须是这一行候选之一
            String shown = spec.current(defaults).id();
            a.isTrue("交点「" + names.get(i) + "」当前归属在自己的候选里：" + shown,
                    spec.options().stream().anyMatch(option -> option.id().equals(shown)));
        }
        a.equal("单块板的四个交点标题互不重复",
                single.size(), new java.util.HashSet<>(names).size());

        // --- 多块板交汇：标题也必须是短的位置名，候选只在提示里列
        List<VirtualSpec> multi = junctionSpecs(adapter, twoPlates);
        List<String> multiNames = new ArrayList<>();
        for (VirtualSpec spec : multi) {
            String name = joinFallback(spec.label());
            multiNames.add(name);
            a.isTrue("多面交点标题很短（默认语言下 ≤ 12 字，含分隔符）：" + name, name.length() <= 12);
            a.isFalse("多面交点标题不是候选拼接：" + name, name.contains("边"));
            for (VirtualSpec.Option option : spec.options()) {
                a.isFalse("多面交点标题里没有塞候选名：" + name,
                        name.contains(joinFallback(option.label())));
            }
            String shown = spec.current(twoPlates).id();
            a.isTrue("多面交点「" + name + "」当前归属在自己的候选里",
                    spec.options().stream().anyMatch(option -> option.id().equals(shown)));
        }
        // 标题必须能区分不同的交点：同一份结构下不允许重名
        a.equal("多面交点标题互不重复（每个交点一个位置名）",
                multi.size(), new java.util.HashSet<>(multiNames).size());
        // 标题长度：正常 GUI 宽度下尽量不截断（截断了也还有悬停提示兜底）
        int longest = 0;
        String longestName = "";
        for (int occupancy = 1; occupancy < LayeredBoardSlots.FULL_OCCUPANCY; occupancy += 29) {
            PlacementConfig sample = LayeredBoardCopycatAdapter.withOccupancy(defaults, occupancy);
            for (VirtualSpec spec : junctionSpecs(adapter, sample)) {
                String name = joinFallback(spec.label());
                if (name.length() > longest) {
                    longest = name.length();
                    longestName = name;
                }
            }
        }
        log.add("   交点标题最长 = " + longestName + "（" + longest + " 个字符，含分隔符）");
        a.isTrue("交点标题最长不超过 14 个字符（默认语言）：" + longestName, longest <= 14);
        log.add("   两块板交点原始数据 = " + LayeredBoardParts.junctions(
                        LayeredBoardCopycatAdapter.occupancy(twoPlates),
                        LayeredBoardCopycatAdapter.windows(twoPlates),
                        LayeredBoardCopycatAdapter.junctionOwners(twoPlates)).stream()
                .map(junction -> (junction.corner() ? "角" : "棱") + "@" + junction.cell()
                        + " shown=" + junction.shown().name()
                        + " candidates=" + junction.candidates().stream()
                                .map(LayeredBoardParts.Slot::name).toList())
                .toList());
        log.add("   两块板交点标题 = " + multiNames);
        // 各种占用组合下都不许出现重名（标题就是玩家区分行的唯一依据）
        for (int occupancy = 1; occupancy < LayeredBoardSlots.FULL_OCCUPANCY; occupancy += 137) {
            PlacementConfig sample = LayeredBoardCopycatAdapter.withOccupancy(defaults, occupancy);
            List<String> sampleNames = new ArrayList<>();
            for (VirtualSpec spec : junctionSpecs(adapter, sample)) {
                sampleNames.add(joinFallback(spec.label()));
            }
            if (sampleNames.size() != new java.util.HashSet<>(sampleNames).size()
                    && log.stream().noneMatch(line -> line.startsWith("   [重名诊断]"))) {
                log.add("   [重名诊断] 占用 " + StructureMasks.write(occupancy));
                for (LayeredBoardParts.Junction junction : LayeredBoardParts.junctions(
                        LayeredBoardCopycatAdapter.occupancy(sample),
                        LayeredBoardCopycatAdapter.windows(sample),
                        LayeredBoardCopycatAdapter.junctionOwners(sample))) {
                    log.add("     [j] corner=" + junction.corner() + " cell=" + junction.cell()
                            + " candidates=" + junction.candidates().stream()
                                    .map(LayeredBoardParts.Slot::name).toList());
                }
                for (VirtualSpec spec : junctionSpecs(adapter, sample)) {
                    log.add("     [n] " + joinFallback(spec.label()));
                }
            }
            a.equal("占用掩码 " + StructureMasks.write(occupancy) + " 下交点标题互不重复",
                    sampleNames.size(), new java.util.HashSet<>(sampleNames).size());
        }
    }

    /**
     * 结构区的<b>顺序</b>与<b>corner-only 过滤</b>。
     *
     * <p>顺序是需求里的三段：12 个层开关（主结构）→ 有板方向的 6 个开窗开关 → 顶点材质归属。
     * 「控制面存不存在的配置永远在上面」，顶点项不允许插到 12 个面开关前面。
     *
     * <p>过滤是另一条需求：棱 / 面 / 中间的 junction 继续参与后端渲染与细工凿交互，
     * 但<b>不生成</b>玩家可编辑的 StructureSpec；GUI 里只留角点。
     * 这里同时拿「几何枚举出的全部 junction」做对照组：被 GUI 丢掉的恰好是 {@code corner == false} 的那些。
     */
    private static void runVertexOnlyFilter(Assertions a, List<String> log, CopycatPlacementAdapter adapter,
                                            PlacementConfig defaults, PlacementConfig twoPlates) {
        for (PlacementConfig sample : List.of(defaults, twoPlates,
                LayeredBoardCopycatAdapter.withWindows(
                        LayeredBoardCopycatAdapter.withOccupancy(defaults, LayeredBoardSlots.FULL_OCCUPANCY),
                        (1 << FaceDir.values().length) - 1))) {
            List<VirtualSpec> specs = adapter.virtualSpecs(sample);
            long layers = specs.stream().filter(spec -> spec.key().startsWith("layer.")).count();
            long windows = specs.stream().filter(spec -> spec.key().startsWith("window.")).count();
            List<VirtualSpec> vertices = junctionSpecs(adapter, sample);

            // ① 12 个层开关必须都在最前面
            a.equal("结构区前 12 项是层开关", 12, (int) layers);
            for (int i = 0; i < 12; i++) {
                a.isTrue("第 " + (i + 1) + " 项是层开关：" + specs.get(i).key(),
                        specs.get(i).key().startsWith("layer."));
            }
            // ② 紧接着是开窗开关（只对当前有板的面出现）
            for (int i = 12; i < 12 + (int) windows; i++) {
                a.isTrue("第 " + (i + 1) + " 项是开窗开关：" + specs.get(i).key(),
                        specs.get(i).key().startsWith("window."));
            }
            a.equal("开窗开关个数 = 当前有板的方向数", (int) windows,
                    (int) LayeredBoardCopycatAdapter.DISPLAY_FACES.stream()
                            .filter(face -> LayeredBoardCopycatAdapter.faceHasLayer(sample, face)).count());
            // ③ 其余全部是顶点归属，且不允许插到前面去
            a.equal("层 / 窗开关之后剩下的全部是顶点归属", specs.size(),
                    12 + (int) windows + vertices.size());
            for (int i = 12 + (int) windows; i < specs.size(); i++) {
                a.isTrue("第 " + (i + 1) + " 项是顶点归属（corner junction）：" + specs.get(i).key(),
                        isVertexSpec(specs.get(i)));
            }
            for (int i = 0; i < 12 + (int) windows; i++) {
                a.isFalse("开关项里不混顶点项：" + specs.get(i).key(), isVertexSpec(specs.get(i)));
            }

            // ④ GUI 里的顶点项必须与「几何枚举出的 corner junction」逐一对应
            java.util.Set<String> geometryCorners = new java.util.LinkedHashSet<>();
            int nonCorner = 0;
            for (LayeredBoardParts.Junction junction
                    : LayeredBoardParts.junctions(LayeredBoardCopycatAdapter.occupancy(sample),
                    LayeredBoardCopycatAdapter.windows(sample),
                    LayeredBoardCopycatAdapter.junctionOwners(sample))) {
                if (junction.corner()) {
                    geometryCorners.add(LayeredBoardCopycatAdapter.JUNCTION_PREFIX + junction.key());
                } else {
                    nonCorner++;
                }
            }
            java.util.Set<String> guiVertices = new java.util.LinkedHashSet<>();
            for (VirtualSpec spec : vertices) {
                guiVertices.add(spec.key());
            }
            a.equal("GUI 顶点项集合 == 几何里 corner junction 的集合", geometryCorners, guiVertices);
            if (nonCorner > 0) {
                for (int i = 0; i < specs.size(); i++) {
                    a.isFalse("位置 " + (i + 1) + " 没有棱 / 面 junction：" + specs.get(i).key(),
                            specs.get(i).key().startsWith("junction.")
                                    && !geometryCorners.contains(specs.get(i).key()));
                }
                if (log.stream().noneMatch(line -> line.startsWith("   [corner-only]"))) {
                    log.add("   [corner-only] 占用 " + StructureMasks.write(LayeredBoardCopycatAdapter.occupancy(sample))
                            + "：几何共 " + (geometryCorners.size() + nonCorner) + " 个 junction，其中 GUI 暴露 "
                            + geometryCorners.size() + " 个顶点，另有 " + nonCorner + " 个中 / 棱 / 面 junction 被过滤掉");
                }
            }
            // ⑤ 顶点项永远有 ≥2 个候选，且当前归属一定落在候选里
            for (VirtualSpec spec : vertices) {
                a.isTrue("顶点项有多个候选：" + spec.key(), spec.options().size() >= 2);
                VirtualSpec.Option current = spec.current(sample);
                a.notNull("顶点项有当前归属：" + spec.key(), current);
                if (current != null) {
                    a.isTrue("顶点项当前归属在候选里：" + spec.key(),
                            spec.options().stream().anyMatch(option -> option.id().equals(current.id())));
                }
            }
        }
    }

    /**
     * 悬停提示的触发条件：没有可说的东西就<b>一个框都不画</b>。
     *
     * <p>这正是上一版的行为缺陷：只要鼠标落在结构行上就无条件 {@code drawTooltip}，
     * 普通布尔项也会弹一个紫黑色空框。现在只有两种情况会画：
     * 文本真的被截断、或者这一项自己有多条候选来源。
     */
    private static void runStructureTooltipRules(Assertions a, List<String> log,
                                                 CopycatPlacementAdapter adapter,
                                                 PlacementConfig defaults, PlacementConfig twoPlates) {
        // --- 护栏：四个面 + 四个角柱这些普通布尔项，没截断就什么都不画
        CopycatPlacementAdapter rail = PlacementAdapters.resolve(ModBlock.COPYCAT_GUARDRAIL).orElseThrow();
        PlacementConfig railConfig = rail.defaultConfig(ModBlock.COPYCAT_GUARDRAIL);
        for (VirtualSpec spec : rail.virtualSpecs(railConfig)) {
            List<StructureTooltip.Line> lines = StructureTooltip.lines(spec, railConfig, false, false);
            a.equal("护栏普通结构项没截断时不产生提示：" + spec.key(), List.of(), lines);
            a.isFalse("护栏普通结构项没截断时不该画提示：" + spec.key(),
                    StructureTooltip.shouldDraw(lines));
        }
        // 被截断时给出完整标题
        VirtualSpec railSpec = rail.virtualSpecs(railConfig).get(0);
        List<StructureTooltip.Line> truncated = StructureTooltip.lines(railSpec, railConfig, true, false);
        a.equal("护栏结构项标题被截断时给一行完整标题", 1, truncated.size());
        a.equal("给出的就是完整标题（同一组文本段）", railSpec.label(), truncated.get(0).parts());
        a.isFalse("给出的标题解析后不是空白", truncated.get(0).resolve().isBlank());
        a.isTrue("被截断时有内容可画", StructureTooltip.shouldDraw(truncated));

        // --- 薄板：12 个层开关 + 6 个开窗开关（全部展示出来时）同样什么都不画
        PlacementConfig allBoard = LayeredBoardCopycatAdapter.withWindows(
                LayeredBoardCopycatAdapter.withOccupancy(defaults, LayeredBoardSlots.FULL_OCCUPANCY),
                (1 << 6) - 1);
        int switches = 0;
        for (VirtualSpec spec : adapter.virtualSpecs(allBoard)) {
            if (spec.listsCandidates()) {
                continue;
            }
            switches++;
            a.equal("薄板开关项没截断时不产生提示：" + spec.key(),
                    List.of(), StructureTooltip.lines(spec, allBoard, false, false));
        }
        a.equal("薄板共有 12 + 6 个开关项", 18, switches);

        // --- 顶点归属：主动给出「这一项干什么」+ 候选来源 + 当前归属
        for (VirtualSpec spec : junctionSpecs(adapter, twoPlates)) {
            List<StructureTooltip.Line> lines = StructureTooltip.lines(spec, twoPlates, false, false);
            a.isTrue("顶点项在没有截断时也给提示（候选来源是有效信息）：" + spec.key(),
                    StructureTooltip.shouldDraw(lines));
            // 说明一句 + 「候选材质来源」标题 + 每个候选一行 + 「当前」标题 + 当前一行
            a.equal("顶点提示行数 = 1 + 1 + 候选数 + 1 + 1",
                    spec.options().size() + 4, lines.size());
            a.equal("顶点提示第一行是「这一项干什么」的说明",
                    StructureTooltip.VERTEX_HINT, lines.get(0).parts().get(0));
            a.equal("第二行是候选来源标题",
                    StructureTooltip.CANDIDATES_TITLE, lines.get(1).parts().get(0));
            for (StructureTooltip.Line line : lines) {
                a.isFalse("顶点提示没有空白行", line.resolve().isBlank());
            }
            // 候选项在提示里带行首符号（BULLET + 候选自己的文本段），所以不能整段直接相等
            for (VirtualSpec.Option option : spec.options()) {
                a.isTrue("顶点提示列出了候选：" + option.id(),
                        lines.stream().anyMatch(line -> isBulletLineOf(line, option)));
            }
            // 说明文字本身必须真的读得懂，不能是裸 key
            String hintText = StructureTooltip.resolve(StructureTooltip.VERTEX_HINT);
            a.isFalse("顶点说明文本不是裸翻译键：" + hintText, hintText.contains(".tooltip."));
            a.isTrue("顶点说明文本非空：" + hintText, !hintText.isBlank());
            a.isTrue("顶点提示给出了当前归属",
                    lines.stream().anyMatch(line -> line.parts().get(0)
                            .equals(StructureTooltip.CURRENT_TITLE)));
        }

        // --- 顶点归属项一定带说明行（这是它区别于普通开关的地方）
        for (VirtualSpec spec : junctionSpecs(adapter, twoPlates)) {
            List<StructureTooltip.Line> lines = StructureTooltip.lines(spec, twoPlates, false, false);
            a.isTrue("顶点项提示非空：" + spec.key(), !lines.isEmpty());
            a.isTrue("顶点项提示解释了「切换这个顶点使用哪条边的材质」：" + spec.key(),
                    lines.stream().anyMatch(line -> StructureTooltip.VERTEX_HINT.equals(line.parts().get(0))));
            a.isTrue("顶点项提示带候选来源标题",
                    lines.stream().anyMatch(line -> line.parts().get(0).equals(StructureTooltip.CANDIDATES_TITLE)));
            a.isTrue("顶点项提示带当前归属标题",
                    lines.stream().anyMatch(line -> line.parts().get(0).equals(StructureTooltip.CURRENT_TITLE)));
        }

        // --- 取值被截断时给出完整取值（这一支走的是普通开关项：开关不主动列候选）
        VirtualSpec toggleSpec = null;
        for (VirtualSpec spec : adapter.virtualSpecs(allBoard)) {
            if (!spec.listsCandidates()) {
                toggleSpec = spec;
                break;
            }
        }
        a.notNull("薄板里能找到普通开关项", toggleSpec);
        if (toggleSpec != null) {
            a.equal("开关项没截断时不提示",
                    List.of(), StructureTooltip.lines(toggleSpec, allBoard, false, false));
            List<StructureTooltip.Line> valueLines = StructureTooltip.lines(toggleSpec, allBoard, false, true);
            a.equal("开关项取值被截断时给出一行完整取值", 1, valueLines.size());
            a.equal("给出的就是当前取值",
                    toggleSpec.current(allBoard).label(), valueLines.get(0).parts());
        }
        // 顶点项本来就把取值列在候选里，所以取值截断标志不影响它的行数
        VirtualSpec junction = junctionSpecs(adapter, twoPlates).get(0);
        a.equal("顶点项的行数不受取值截断标志影响",
                StructureTooltip.lines(junction, twoPlates, false, false).size(),
                StructureTooltip.lines(junction, twoPlates, false, true).size());
        a.isTrue("顶点项的当前归属一定出现在提示里",
                StructureTooltip.lines(junction, twoPlates, false, false).stream()
                        .anyMatch(line -> isBulletLineOf(line, junction.current(twoPlates))));

        // --- 兜底：空列表 / 全空白一律不画
        a.isFalse("空提示列表不画框", StructureTooltip.shouldDraw(List.of()));
        StructureTooltip.Line blank = new StructureTooltip.Line(
                List.of(VirtualSpec.LabelPart.of("", "   ")), "");
        a.isFalse("只有空白行的提示不画框", StructureTooltip.shouldDraw(List.of(blank)));
        a.isFalse("解析不出来的键不会变成裸 key",
                StructureTooltip.resolve(VirtualSpec.LabelPart.of("no.such.key", "兜底")).contains("no.such.key"));

        // --- 绘制阶段：提示必须由<b>最终 overlay 阶段</b>画，内容阶段只能排队
        runOverlayPhaseRules(a);

        log.add("   悬停提示：普通开关项 0 行（不画框），顶点项 "
                + StructureTooltip.lines(junction, twoPlates, false, false).size() + " 行");
    }

    /**
     * 提示的<b>绘制阶段</b>规则（{@link PlacerOverlay} 的状态机）。
     *
     * <p>这一条对应的是「tooltip 被遮挡」那个缺陷：提示框本身有内容，但画在了结构区行循环里，
     * 于是后面画的材质区、滚动条、搜索框压在上面。修法是把它排进队列、留到整帧最后画。
     * 这里把「哪一步能排队、哪一步能取走」变成可执行的断言，而不是靠读代码确认。
     */
    private static void runOverlayPhaseRules(Assertions a) {
        PlacerOverlay<String> overlay = new PlacerOverlay<>();
        a.equal("初始状态是 IDLE", PlacerOverlay.Phase.IDLE, overlay.phase());
        a.isFalse("IDLE 阶段不能排队", overlay.queue("提示"));
        a.equal("IDLE 阶段取不到东西", List.of(), overlay.takeForOverlay());

        overlay.beginContent();
        a.equal("beginContent 之后是 CONTENT 阶段", PlacerOverlay.Phase.CONTENT, overlay.phase());
        a.isTrue("内容阶段可以排队", overlay.queue("顶点提示"));
        a.isTrue("内容阶段不能取走（必须等到 overlay）", overlay.takeForOverlay().isEmpty());
        overlay.queue("第二条");

        overlay.beginOverlay();
        a.equal("beginOverlay 之后是 OVERLAY 阶段", PlacerOverlay.Phase.OVERLAY, overlay.phase());
        List<String> drawn = overlay.takeForOverlay();
        a.equal("overlay 阶段取到本帧排队的全部提示", List.of("顶点提示", "第二条"), drawn);
        a.equal("取走即清空，不会重复画", List.of(), overlay.takeForOverlay());
        a.isFalse("overlay 阶段不再接受排队", overlay.queue("迟到"));
        a.isTrue("队里已经是空的", overlay.isEmpty());

        overlay.endFrame();
        a.equal("endFrame 回到 IDLE", PlacerOverlay.Phase.IDLE, overlay.phase());

        // 上一帧的残留不许带到下一帧
        overlay.beginContent();
        overlay.queue("上一帧的提示");
        overlay.endFrame();
        overlay.beginContent();
        a.isTrue("新一帧开始会清掉上一帧的残留", overlay.isEmpty());
        overlay.beginOverlay();
        a.equal("残留被清掉之后 overlay 什么都不画", List.of(), overlay.takeForOverlay());
        overlay.endFrame();
    }

    /** 这一行是不是「行首符号 + 某个候选项的文本段」（提示里的候选 / 当前两栏都长这样）。 */
    private static boolean isBulletLineOf(StructureTooltip.Line line, VirtualSpec.Option option) {
        List<VirtualSpec.LabelPart> parts = line.parts();
        if (parts.size() != option.label().size() + 1) {
            return false;
        }
        if (!StructureTooltip.BULLET.equals(parts.get(0))) {
            return false;
        }
        return parts.subList(1, parts.size()).equals(option.label());
    }

    /** 一段显示文本的兜底文本拼起来（服务端没有中文语言文件，用它代表默认语言的显示效果）。 */
    private static String joinFallback(List<VirtualSpec.LabelPart> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append("·");
            }
            out.append(parts.get(i).labelText());
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- 旧配置兼容

    /** 旧配置（掩码 / 名字两种写法）读进来之后必须能正常编辑、保存后写回新格式。 */
    private static void runStructureCompat(Assertions a, List<String> log) {
        BlockState railState = ModBlock.COPYCAT_GUARDRAIL.getDefaultState();
        CopycatPlacementAdapter rail = PlacementAdapters.resolve(ModBlock.COPYCAT_GUARDRAIL).orElseThrow();
        BlockState boardState = ModBlock.LAYERED_COPYCAT_BOARD.getDefaultState();
        CopycatPlacementAdapter board = PlacementAdapters.resolve(ModBlock.LAYERED_COPYCAT_BOARD).orElseThrow();

        // --- 旧护栏配置：只有 faces 掩码，没有 corners 键
        PlacementConfig oldRail = PlacementConfig.of(railState)
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, "0x9");
        PlacementConfig fromNbt = PlacementConfig.fromNbt(oldRail.toNbt());
        a.equal("旧护栏配置：面掩码读回不变", 0x9, GuardrailCopycatAdapter.facesMask(fromNbt));
        a.equal("旧护栏配置：缺角柱键 → 四个角柱都在", GuardrailCopycatAdapter.DEFAULT_CORNERS,
                GuardrailCopycatAdapter.cornersMask(fromNbt));
        a.isTrue("旧护栏配置能通过结构校验",
                rail.validateStructure(rail.stateFrom(fromNbt.state(), fromNbt), fromNbt) == null);
        a.equal("旧护栏配置能算出结构项（北 + 西 → 东北 / 西北 / 西南角柱）",
                List.of("face.north", "face.east", "face.south", "face.west",
                        "corner.northeast", "corner.northwest", "corner.southwest"),
                specKeys(rail, fromNbt));
        // 界面全量列出 8 个槽；「这一份结构里真的有几何」的是下面这 5 个。
        a.equal("旧护栏配置的材质区仍然全量 8 个槽",
                8, guiSlotKeys(rail, railState, fromNbt).size());
        a.equal("旧护栏配置里有效的材质槽（顺序为先横梁后角柱）",
                List.of("north_row", "west_row", "15_0", "0_15", "0_0"),
                activeSlotKeys(rail, railState, fromNbt));
        // 在旧配置上做一次界面操作 → 写回新格式（含角柱键）
        PlacementConfig editedOld = GuardrailCopycatAdapter.withCorner(fromNbt,
                GuardrailCopycatAdapter.Corner.NORTHWEST, false);
        a.notNull("编辑旧配置之后角柱键真的写进了结构字段",
                editedOld.structure(GuardrailCopycatAdapter.STRUCTURE_CORNERS));
        PlacementConfig editedRoundTrip = PlacementConfig.fromNbt(editedOld.toNbt());
        a.equal("编辑旧配置之后 NBT 往返一致", editedOld, editedRoundTrip);
        a.isFalse("写回之后那一位是「关」",
                GuardrailCopycatAdapter.cornerEnabled(editedRoundTrip,
                        GuardrailCopycatAdapter.Corner.NORTHWEST));

        // --- 旧护栏配置：名字形式
        PlacementConfig namedRail = PlacementConfig.of(railState)
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, "north+east")
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_CORNERS, "none");
        a.equal("名字形式 north+east → 两个面",
                CopycatGuardrailBlock.bit(Direction.NORTH) | CopycatGuardrailBlock.bit(Direction.EAST),
                GuardrailCopycatAdapter.facesMask(namedRail));
        a.equal("名字形式 none → 四个角柱都关", 0, GuardrailCopycatAdapter.cornersMask(namedRail));
        a.isTrue("名字形式的旧配置能通过结构校验",
                rail.validateStructure(rail.stateFrom(namedRail.state(), namedRail), namedRail) == null);
        a.equal("名字形式的旧配置里角柱关掉 → 有效的只剩两根横梁",
                List.of("north_row", "east_row"), activeSlotKeys(rail, railState, namedRail));
        a.equal("名字形式的旧配置里材质区仍然是全量 8 个槽（角柱置灰）",
                8, guiSlotKeys(rail, railState, namedRail).size());

        // --- 旧薄板配置：occupancy / windows 掩码
        PlacementConfig oldBoard = PlacementConfig.of(boardState)
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "0x3")
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_WINDOWS, "0x1");
        PlacementConfig boardNbt = PlacementConfig.fromNbt(oldBoard.toNbt());
        a.equal("旧薄板配置：占用掩码读回不变", 3, LayeredBoardCopycatAdapter.occupancy(boardNbt));
        a.equal("旧薄板配置：窗掩码读回不变", 1, LayeredBoardCopycatAdapter.windows(boardNbt));
        a.isTrue("旧薄板配置能通过结构校验",
                board.validateStructure(board.stateFrom(boardNbt.state(), boardNbt), boardNbt) == null);
        a.equal("旧薄板配置能算出 10 个有效区域槽 + 1 个有效窗槽",
                11, activeSlotKeys(board, boardState, boardNbt).size());
        a.equal("旧薄板配置的材质区仍然全量 66 个槽（无效的置灰）",
                66, guiSlotKeys(board, boardState, boardNbt).size());
        a.isTrue("旧薄板配置能算出结构项",
                !board.virtualSpecs(boardNbt).isEmpty());
        // 旧薄板的「占用组合」名字形式
        PlacementConfig namedBoard = PlacementConfig.of(boardState)
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_OCCUPANCY, "outer_only")
                .withStructure(LayeredBoardCopycatAdapter.STRUCTURE_WINDOWS, "all");
        a.equal("名字形式 outer_only → 六面外层", LayeredBoardCopycatAdapter.outerOnly(),
                LayeredBoardCopycatAdapter.occupancy(namedBoard));
        a.equal("名字形式 all → 六面都开窗", (1 << 6) - 1,
                LayeredBoardCopycatAdapter.windows(namedBoard));
        a.equal("名字形式下每个方向都有开窗项", 6,
                (int) specKeys(board, namedBoard).stream().filter(k -> k.startsWith("window.")).count());
        // 在旧配置上编辑 → NBT 往返
        PlacementConfig boardEdited = LayeredBoardCopycatAdapter.withSlot(namedBoard,
                FaceDir.UP, BoardLayer.INNER, false);
        a.equal("编辑名字形式的旧配置之后 NBT 往返一致",
                boardEdited, PlacementConfig.fromNbt(boardEdited.toNbt()));

        // --- 不影响其它 copycat 方块
        Block createPanel = Registries.BLOCK.get(new Identifier("create:copycat_panel"));
        if (createPanel != net.minecraft.block.Blocks.AIR) {
            CopycatPlacementAdapter create = PlacementAdapters.resolve(createPanel).orElseThrow();
            a.isTrue("Create 伪装板没有任何结构项（不受本轮改动影响）",
                    create.virtualSpecs(PlacementConfig.EMPTY).isEmpty());
            // 旧接口 listInactiveSlots() 已经取消：「是否列出无效槽」不再是 adapter 的差异，
            // 而是界面的统一策略（全量显示 + 置灰）。这里改成验「槽总数 == 界面列出的总数」——
            // 等价于旧断言想表达的「不存在的槽也在列表里」。
            List<AdapterSlot> createSlots = create.slots(createPanel.getDefaultState(),
                    create.defaultConfig(createPanel));
            a.isTrue("Create 伪装板没有任何结构项，槽位照旧全部列出",
                    createSlots.stream().noneMatch(AdapterSlot::structure) || !createSlots.isEmpty());
            a.equal("Create 伪装板界面列出的槽 = adapter 给出的全部槽",
                    createSlots.size(), guiSlotKeys(create, createPanel.getDefaultState(),
                            create.defaultConfig(createPanel)).size());
        }
        a.isTrue("没有结构项的 adapter 用默认实现",
                PlacementAdapters.all().stream()
                        .filter(adapter -> !adapter.name().contains("maris-decoration"))
                        .allMatch(adapter -> adapter.virtualSpecs(PlacementConfig.EMPTY).isEmpty()));
        log.add("   旧配置兼容：掩码形式与名字形式都能读出等价的逐项结构");
    }

    // ---------------------------------------------------------------- 结构项辅助

    /** adapter 在当前配置下会给出的结构项的 key（顺序即界面顺序）。 */
    private static List<String> specKeys(CopycatPlacementAdapter adapter, PlacementConfig config) {
        List<String> keys = new ArrayList<>();
        for (VirtualSpec spec : adapter.virtualSpecs(config)) {
            keys.add(spec.key());
        }
        return keys;
    }

    /**
     * 某个结构下界面会列出的材质槽 key。
     *
     * <p>界面是<b>全量显示 + 无效项置灰</b>（与 Copycats+ 的伪装薄板在这个 GUI 里的表现对齐），
     * 所以这里就是 {@code adapter.slots(...)} 的全部 key，不做任何过滤。
     * 「哪些槽当前有效」由 {@link #activeSlotKeys} 回答，两者之差就是界面要置灰的那些。
     */
    private static List<String> guiSlotKeys(CopycatPlacementAdapter adapter, BlockState state,
                                            PlacementConfig config) {
        List<String> keys = new ArrayList<>();
        for (AdapterSlot slot : adapter.slots(state, config)) {
            keys.add(slot.key());
        }
        return keys;
    }

    /**
     * 某个结构下<b>真正有效</b>的材质槽 key（界面把它们画成正常亮色，其余置灰）。
     *
     * <p>判据是 {@link AdapterSlot#structure()}，与渲染 / 放置用的是同一个标志。
     */
    private static List<String> activeSlotKeys(CopycatPlacementAdapter adapter, BlockState state,
                                               PlacementConfig config) {
        List<String> keys = new ArrayList<>();
        for (AdapterSlot slot : adapter.slots(state, config)) {
            if (slot.structure()) {
                keys.add(slot.key());
            }
        }
        return keys;
    }

    /**
     * 当前配置下界面会给出的<b>顶点材质归属</b>结构项（corner 级 junction）。
     *
     * <p>GUI 只暴露角点：棱 / 面 / 中间的 junction 仍然在后端参与渲染与细工凿交互，
     * 但不生成玩家可编辑的 StructureSpec。所以这里按 key 前缀筛出来的必须是全集的<b>子集</b>，
     * 每一条又都对应一个 {@code corner() == true} 的几何 junction。
     */
    private static List<VirtualSpec> junctionSpecs(CopycatPlacementAdapter adapter, PlacementConfig config) {
        List<VirtualSpec> specs = new ArrayList<>();
        for (VirtualSpec spec : adapter.virtualSpecs(config)) {
            if (spec.key().startsWith(LayeredBoardCopycatAdapter.JUNCTION_PREFIX)) {
                specs.add(spec);
            }
        }
        return specs;
    }

    /** 这一项的 key 是不是「顶点（角点）材质归属」。 */
    private static boolean isVertexSpec(VirtualSpec spec) {
        return spec.key().startsWith(LayeredBoardCopycatAdapter.JUNCTION_PREFIX);
    }

    /** 结构项用到的样例配置：默认 / 全开 / 只开一块，保证覆盖到所有动态分支。 */
    private static List<PlacementConfig> structureSampleConfigs(Block block, CopycatPlacementAdapter adapter) {
        List<PlacementConfig> configs = new ArrayList<>();
        PlacementConfig defaults = adapter.defaultConfig(block);
        configs.add(defaults);
        if (block == ModBlock.COPYCAT_GUARDRAIL) {
            configs.add(GuardrailCopycatAdapter.withFaces(defaults, 0xF));
            configs.add(GuardrailCopycatAdapter.withFaces(defaults, 0x0));
        } else if (block == ModBlock.LAYERED_COPYCAT_BOARD) {
            int all = LayeredBoardSlots.FULL_OCCUPANCY;
            configs.add(LayeredBoardCopycatAdapter.withWindows(
                    LayeredBoardCopycatAdapter.withOccupancy(defaults, all), (1 << 6) - 1));
            configs.add(LayeredBoardCopycatAdapter.withOccupancy(defaults, 0));
        }
        return configs;
    }

    /** 全部结构项的标题翻译键（去重）。 */
    private static List<String> structureLabelKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        forEachStructureLabel(false, keys::add);
        return List.copyOf(keys);
    }

    /** 全部结构项候选项的翻译键（去重）。 */
    private static List<String> structureOptionLabelKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        forEachStructureLabel(true, keys::add);
        return List.copyOf(keys);
    }

    private static void forEachStructureLabel(boolean options, java.util.function.Consumer<String> consumer) {
        for (Block block : List.of(ModBlock.COPYCAT_GUARDRAIL, ModBlock.LAYERED_COPYCAT_BOARD)) {
            CopycatPlacementAdapter adapter = PlacementAdapters.resolve(block).orElseThrow();
            for (PlacementConfig config : structureSampleConfigs(block, adapter)) {
                for (VirtualSpec spec : adapter.virtualSpecs(config)) {
                    if (!options) {
                        for (VirtualSpec.LabelPart part : spec.label()) {
                            consumer.accept(part.labelKey());
                        }
                    } else {
                        for (VirtualSpec.Option option : spec.options()) {
                            for (VirtualSpec.LabelPart part : option.label()) {
                                consumer.accept(part.labelKey());
                            }
                        }
                    }
                }
            }
        }
    }

    // ================================================================ 18. 翻译资源

    /**
     * 翻译必须在<b>打包后的运行时资源里真的可读</b>。
     *
     * <p>这一段的由来：上一轮我把词条写进了 {@code src/datagen/resources/lang/*.json}，
     * 但一次都没重跑 datagen，于是 {@code src/main/generated/.../lang/*.json} 还是几天前的旧文件，
     * 打包进 jar 的也就没有新词条——游戏里满屏裸 key。
     *
     * <p>读取方式刻意用<b>类加载器</b>而不是 {@code server.getResourceManager()}：后者按资源包类型
     * 过滤，在专用服务端上未必把 {@code assets/} 扫进来（实测就是读不到，但那不代表 jar 里没有）。
     * 类加载器拿到的就是最终打进 jar / 输出目录的那一份，正是我们想验的东西。
     */
    private static void runTranslations(ServerWorld world, Assertions a, List<String> log) {
        log.add("== 18. 翻译资源（打包后实际可读）");

        for (String code : List.of("zh_cn", "en_us")) {
            String path = "assets/maris-decoration/lang/" + code + ".json";
            String text = readClasspathResource(path);
            a.isTrue("打包资源里存在 " + path, text != null);
            if (text == null) {
                continue;
            }
            log.add("   " + code + "：文件长度 = " + text.length());

            List<String> required = List.of(
                    "item.maris-decoration.copycat_placer",
                    "item.maris-decoration.copycat_placer.tooltip.empty",
                    "item.maris-decoration.copycat_placer.tooltip.block",
                    "item.maris-decoration.copycat_placer.tooltip.materials",
                    "item.maris-decoration.copycat_placer.tooltip.hint",
                    "item.maris-decoration.copycat_placer.no_block",
                    "item.maris-decoration.copycat_placer.no_structure",
                    "item.maris-decoration.copycat_placer.blocked",
                    "item.maris-decoration.copycat_placer.screen.title",
                    "item.maris-decoration.copycat_placer.screen.search",
                    "item.maris-decoration.copycat_placer.screen.blocks",
                    "item.maris-decoration.copycat_placer.screen.structure",
                    "item.maris-decoration.copycat_placer.screen.materials",
                    "item.maris-decoration.copycat_placer.screen.no_selection",
                    "item.maris-decoration.copycat_placer.screen.no_match",
                    "item.maris-decoration.copycat_placer.screen.no_slots",
                    "item.maris-decoration.copycat_placer.screen.material_unset",
                    "item.maris-decoration.copycat_placer.screen.value_unknown",
                    "item.maris-decoration.copycat_placer.material.title",
                    "item.maris-decoration.copycat_placer.material.hint",
                    "item.maris-decoration.copycat_placer.option.on",
                    "item.maris-decoration.copycat_placer.option.off",
                    // 顶点归属的悬停提示：这一句是玩家理解「顶点材质归属」这一项的唯一入口，
                    // 上一轮就是漏了它（界面上只显示候选边名，玩家不知道点它是干什么的）。
                    "item.maris-decoration.copycat_placer.tooltip.vertex",
                    "item.maris-decoration.copycat_placer.tooltip.candidates",
                    "item.maris-decoration.copycat_placer.tooltip.current",
                    "maris-decoration.copycat_placer.slot.create_copycat",
                    "maris-decoration.copycat_placer.slot.guardrail.north_row",
                    "maris-decoration.copycat_placer.slot.layered_board.up.outer.body",
                    "maris-decoration.copycat_placer.slot.layered_board.down.window",
                    "maris-decoration.property_line",
                    "maris-decoration.property.facing",
                    "maris-decoration.property.axis",
                    "maris-decoration.property.half",
                    "maris-decoration.property.top_northeast",
                    "maris-decoration.property.top_northwest",
                    "maris-decoration.property.top_southeast",
                    "maris-decoration.property.top_southwest",
                    "maris-decoration.property.bottom_northeast",
                    "maris-decoration.property.bottom_northwest",
                    "maris-decoration.property.bottom_southeast",
                    "maris-decoration.property.bottom_southwest",
                    "maris-decoration.property_value.true",
                    "maris-decoration.property_value.false",
                    "maris-decoration.property_value.north",
                    "maris-decoration.property_value.bottom_left");
            List<String> missing = new ArrayList<>();
            for (String key : required) {
                if (!text.contains("\"" + key + "\"")) {
                    missing.add(key);
                }
            }
            a.equal(code + " 包含全部 " + required.size() + " 个必需词条", List.of(), missing);

            // 结构项（护栏的面 / 角柱、薄板的层 / 开窗）的每一个翻译键都必须真的在打包语言文件里。
            // 这条断言是自维护的：adapter 新增一项却没加翻译，这里立刻失败——
            // 界面上的表现就是「一行裸 key」，而那正是这一轮要消灭的东西。
            List<String> structureKeys = structureLabelKeys();
            List<String> missingStructure = new ArrayList<>();
            for (String key : structureKeys) {
                if (!text.contains("\"" + key + "\"")) {
                    missingStructure.add(key);
                }
            }
            a.equal(code + " 结构项的 " + structureKeys.size() + " 个翻译键全部存在",
                    List.of(), missingStructure);
            log.add("   " + code + "：结构项翻译键 " + structureKeys.size() + " 个");

            // 每一个候选项（含「开 / 关」）的翻译键同样必须存在
            List<String> optionKeys = structureOptionLabelKeys();
            List<String> missingOptions = new ArrayList<>();
            for (String key : optionKeys) {
                if (!text.contains("\"" + key + "\"")) {
                    missingOptions.add(key);
                }
            }
            a.equal(code + " 结构项候选项的 " + optionKeys.size() + " 个翻译键全部存在",
                    List.of(), missingOptions);
        }

        // 物品模型与贴图也顺带确认（漏了同样只在游戏里才看得出来）
        a.notNull("打包资源里有 copycat_placer 的物品模型",
                readClasspathResource("assets/maris-decoration/models/item/copycat_placer.json"));
        a.notNull("打包资源里有 copycat_placer 的贴图",
                readClasspathResource("assets/maris-decoration/textures/item/copycat_placer.png"));

        // --- 本地化函数本身的回退行为（与语言文件是否加载无关）
        PropertySpec facing = PropertySpec.of(
                net.minecraft.state.property.Properties.HORIZONTAL_FACING);
        a.isFalse("facing 不会原样显示属性名", "facing".equals(facing.displayLabel().getString()));
        log.add("   facing → " + facing.displayLabel().getString());

        PropertySpec waterlogged = PropertySpec.of(Properties.WATERLOGGED);
        String yes = waterlogged.displayValue(Blocks.OAK_STAIRS.getDefaultState()
                .with(Properties.WATERLOGGED, true)).getString();
        String no = waterlogged.displayValue(Blocks.OAK_STAIRS.getDefaultState()
                .with(Properties.WATERLOGGED, false)).getString();
        a.isFalse("布尔取值不再是裸 true", "true".equals(yes));
        a.isFalse("布尔取值不再是裸 false", "false".equals(no));
        log.add("   布尔取值 → " + yes + " / " + no);

        // 多部件方块的角属性（截图里出现过 bottom_left 这类裸值）
        PropertySpec shape = PropertySpec.of(net.minecraft.state.property.Properties.STAIR_SHAPE);
        String corner = shape.displayValue(Blocks.OAK_STAIRS.getDefaultState()
                .with(net.minecraft.state.property.Properties.STAIR_SHAPE,
                        net.minecraft.block.enums.StairShape.OUTER_LEFT)).getString();
        a.isFalse("stairs shape 取值不再是裸标识符", "outer_left".equals(corner));
        log.add("   stairs shape → " + corner);

        // 完全未知的动态属性：必须人读化
        a.equal("未知属性人读化", "Some third party thing",
                PropertySpec.humanize("some_third_party_thing"));
    }

    /** 从类加载器读一个打包资源；读不到返回 null（不抛异常）。 */
    private static @Nullable String readClasspathResource(String path) {
        try (java.io.InputStream stream = PlacementSelfTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }
    // ================================================================ 工具

    /**
     * 界面上会显示哪些属性：方块自己的全部属性<b>减去 WATERLOGGED</b>。
     *
     * <p>与 {@code PlacerScreen#visibleProperties} 保持同一份口径——含水不是玩家可配置项
     * （由实际放置位置的水体决定），所以不该出现在配置界面上。这里是它在服务端的可测版本。
     */
    private static List<PropertySpec> visiblePropsOf(BlockState display) {
        List<PropertySpec> specs = new ArrayList<>();
        for (Property<?> property : display.getProperties()) {
            if (property == Properties.WATERLOGGED) {
                continue;
            }
            specs.add(PropertySpec.of(property));
        }
        return specs;
    }

    private static int northBit() {
        return CopycatGuardrailBlock.bit(Direction.NORTH);
    }

    private static int eastBit() {
        return CopycatGuardrailBlock.bit(Direction.EAST);
    }

    private static Block findMultistateBlock() {
        for (Block block : Registries.BLOCK) {
            if (block instanceof IMultiStateCopycatBlock) {
                return block;
            }
        }
        return null;
    }

    private static String report(Assertions assertions, List<String> sections) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n===== Copycat Placer 自检 =====\n");
        for (String line : sections) {
            builder.append(line).append('\n');
        }
        builder.append("------------------------------\n");
        builder.append("检查项：").append(assertions.total())
                .append("，失败：").append(assertions.failures().size()).append('\n');
        if (assertions.passed()) {
            builder.append("结果：全部通过\n");
        } else {
            builder.append("结果：失败项如下\n");
            for (String failure : assertions.failures()) {
                builder.append("  x ").append(failure).append('\n');
            }
        }
        builder.append("==============================");
        String text = builder.toString();
        // 同时写进日志与控制台：跑无头服务端时这是唯一能拿到结果的地方
        if (assertions.passed()) {
            MarisDecoration.LOGGER.info(text);
        } else {
            MarisDecoration.LOGGER.error(text);
        }
        // 再落一份文件：无头服务端的 stdout 可能被 gradle 缓冲，文件是最可靠的取回方式
        writeReportFile(text);
        return text;
    }

    /** 把报告写到工作目录下的 {@code maris-placer-selftest.txt}；失败只记日志，不影响结果。 */
    private static void writeReportFile(String text) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("maris-placer-selftest.txt"), text);
        } catch (Exception exception) {
            MarisDecoration.LOGGER.warn("无法写出自检报告文件", exception);
        }
    }

    /**
     * 自检中途抛异常时也要留一份可读的报告。
     *
     * <p>没有它的话，无头服务端上只能看到原版那句「An unexpected error occurred trying to execute
     * that command」，完全不知道炸在哪一步。这里把栈一起写进报告文件。
     */
    public static void writeFailureReport(BlockPos origin, Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        writer.write("===== Copycat Placer 自检：异常中断 =====\n");
        writer.write("平台原点：" + origin.toShortString() + "\n");
        writer.write("异常：" + throwable + "\n");
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        writeReportFile(writer.toString());
    }
}

