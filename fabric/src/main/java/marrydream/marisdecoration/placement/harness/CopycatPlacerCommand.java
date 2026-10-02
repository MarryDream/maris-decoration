package marrydream.marisdecoration.placement.harness;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.PlacementResult;
import marrydream.marisdecoration.placement.PlacementService;
import marrydream.marisdecoration.placement.adapter.AdapterSlot;
import marrydream.marisdecoration.placement.adapter.GuardrailCopycatAdapter;
import marrydream.marisdecoration.placement.adapter.PlacementAdapters;
import marrydream.marisdecoration.placement.adapter.StructureMasks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.List;

/**
 * 第一阶段的调试入口。
 *
 * <p>这一阶段刻意<b>不做</b> GUI、不做工具物品、不做网络包，所以「手工构造一份配置 → 调放置服务」
 * 这条验收路径需要一个能真的跑起来的入口。命令行就是它：服务端注册 {@code /marisplacer}，
 * 可以在游戏里敲，也可以在无头服务端的控制台里喂进去。
 *
 * <h2>只注册服务端命令</h2>
 * 用的是 Fabric 的 {@link CommandRegistrationCallback}，它在 {@code CommandManager}
 * 构造时触发——专用服务端与集成服务端都会走这条路，而纯客户端本身没有 {@code CommandManager}。
 * 命令不带任何权限要求，因为开发环境里通常用的是单人存档。
 *
 * <p>下一阶段（工具物品）落地后，这个命令应当保留：它是出问题时最快的定位手段。
 */
public final class CopycatPlacerCommand {

    /** 自检平台所在的 y：与 {@link PlacementHarness#PLATFORM_Y} 一致，够高，不会碰到地形。 */
    private static final int SELF_TEST_Y = PlacementHarness.PLATFORM_Y;

    private CopycatPlacerCommand() {
    }

    /** 注册命令。由 {@code MarisDecoration#onInitialize} 调用。 */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("marisplacer")
                    .requires(source -> true);

            root.then(Commands.literal("self-test").executes(context -> selfTest(context.getSource())));
            root.then(Commands.literal("here").executes(context -> placeHere(context.getSource())));
            root.then(Commands.literal("slots").executes(context -> slots(context.getSource())));
            root.then(Commands.literal("demo").executes(context -> demo(context.getSource())));

            dispatcher.register(root);
        });
    }

    // ---------------------------------------------------------------- 子命令

    /** 跑全套自检。返回的报告同时已经写进日志。 */
    private static int selfTest(CommandSourceStack source) {
        // 注意不能用 source.getWorld()：控制台命令源没有世界（返回 null），
        // 无头服务端恰恰只能从控制台执行。统一取主世界。
        ServerLevel world = source.getServer().overworld();
        BlockPos spawn = world.getSharedSpawnPos();
        // 挪开一点，避免和出生点建筑重叠
        BlockPos origin = new BlockPos(spawn.getX() + 24, SELF_TEST_Y, spawn.getZ() + 24);
        try {
            String report = PlacementSelfTest.run(world, origin);
            source.sendSuccess(() -> Component.literal(report), false);
            return 1;
        } catch (Throwable throwable) {
            // 命令里抛异常会被原版统一吞成「An unexpected error occurred」，什么都看不到。
            // 自检本身就是用来定位问题的，所以这里必须把栈打出来。
            MarisDecoration.LOGGER.error("Copycat Placer 自检抛出异常", throwable);
            PlacementSelfTest.writeFailureReport(origin, throwable);
            source.sendFailure(Component.literal("自检异常：" + throwable));
            return 0;
        }
    }

    /** 用一份最朴素的预设，在玩家脚边放一个四面全开的伪装护栏，肉眼确认流水线通了。 */
    private static int placeHere(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("marisplacer here 需要一个玩家"));
            return 0;
        }
        ServerLevel world = player.serverLevel();
        BlockPos target = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player.blockPosition()).above();
        var material = world.getBlockState(target.below());

        PlacementConfig config = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.defaultBlockState())
                .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, StructureMasks.write(0xF))
                .withSlot(GuardrailParts.rowKey(Direction.NORTH), material)
                .withSlot(GuardrailParts.rowKey(Direction.SOUTH), material);

        PlacementResult result = PlacementService.place(world, target, config, player);
        source.sendSuccess(() -> Component.literal(describe(target, result)), false);
        return result.success() ? 1 : 0;
    }

    /** 打印一个方块状态的全部 material slot，验证 adapter 把方块类型差异挡在了外面。 */
    private static int slots(CommandSourceStack source) {
        var state = ModBlock.LAYERED_COPYCAT_BOARD.defaultBlockState();
        List<AdapterSlot> slots = PlacementAdapters.slotsOf(state, PlacementConfig.of(state));
        StringBuilder builder = new StringBuilder();
        builder.append("已装 adapter：").append(PlacementAdapters.names()).append('\n');
        builder.append(ModBlock.LAYERED_COPYCAT_BOARD.getName().getString())
                .append(" 的 material slot：").append(slots.size()).append(" 个\n");
        int shown = 0;
        for (AdapterSlot slot : slots) {
            // 只列存在的，避免刷屏；不存在的数量单独说
            if (!slot.structure()) {
                continue;
            }
            if (shown++ >= 20) {
                builder.append("  …（其余省略）\n");
                break;
            }
            builder.append("  ").append(slot.key())
                    .append("  group=").append(slot.groupKey())
                    .append(slot.hasMaterial() ? "  =" + slot.material() : "")
                    .append('\n');
        }
        builder.append("结构存在的槽共 ")
                .append(slots.stream().filter(AdapterSlot::structure).count()).append(" 个");
        source.sendSuccess(() -> Component.literal(builder.toString()), false);
        return 1;
    }

    /**
     * 最小可诊断的放置演示：不碰玩家、不碰假玩家，只走「配置 → 放置 → 检查」的主干。
     *
     * <p>存在的理由是排查「自检里到底是哪一步炸的」：它每一步都往报告文件里追加一行，
     * 一旦某一步抛异常，报告里就停在那一行，比在无头服务端上看原版那句统一错误提示有用得多。
     */
    private static int demo(CommandSourceStack source) {
        StringBuilder trace = new StringBuilder();
        try {
            // 同样不能用 source.getWorld()：控制台源没有世界
            ServerLevel world = source.getServer().overworld();
            BlockPos spawn = world.getSharedSpawnPos();
            BlockPos pos = new BlockPos(spawn.getX() + 8, SELF_TEST_Y, spawn.getZ() + 8);

            trace.append("origin=").append(pos.toShortString()).append('\n');
            trace.append("adapter 表=").append(PlacementAdapters.names()).append('\n');
            trace.append("copycats loaded=")
                    .append(marrydream.marisdecoration.placement.adapter.BuiltinAdapters.copycatsLoaded())
                    .append('\n');

            world.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            trace.append("已清空目标位置\n");

            PlacementConfig config = PlacementConfig.of(ModBlock.COPYCAT_GUARDRAIL.defaultBlockState())
                    .withStructure(GuardrailCopycatAdapter.STRUCTURE_FACES, StructureMasks.write(0xF))
                    .withSlot(GuardrailParts.rowKey(Direction.NORTH),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            trace.append("配置：").append(config).append('\n');

            trace.append("解析 adapter = ")
                    .append(PlacementAdapters.resolve(config.block()).map(a -> a.name()).orElse("<none>"))
                    .append('\n');

            PlacementResult result = PlacementService.place(world, pos, config);
            trace.append("结果：").append(describe(pos, result)).append('\n');
            trace.append("世界里的方块：").append(world.getBlockState(pos)).append('\n');
            trace.append("方块实体：").append(world.getBlockEntity(pos)).append('\n');
            trace.append("OK\n");
        } catch (Throwable throwable) {
            java.io.StringWriter writer = new java.io.StringWriter();
            throwable.printStackTrace(new java.io.PrintWriter(writer));
            trace.append("!! 异常：").append(throwable).append('\n').append(writer);
        }
        writeTrace(trace.toString());
        source.sendSuccess(() -> Component.literal(trace.toString()), false);
        return 1;
    }

    /** 把一行行追踪写到 run/ 下，无头服务端上这是最可靠的回读方式。 */
    private static void writeTrace(String text) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("maris-placer-demo.txt"), text);
        } catch (Exception exception) {
            marrydream.marisdecoration.MarisDecoration.LOGGER.warn("写 demo 追踪失败", exception);
        }
    }

    private static String describe(BlockPos pos, PlacementResult result) {
        if (!result.success()) {
            return "放置失败：" + result.failure().id() + " @ " + pos.toShortString();
        }
        return "已放置 " + result.state().getBlock().getName().getString()
                + " @ " + pos.toShortString()
                + "，adapter=" + result.adapter()
                + "，铺上 " + result.appliedSlots().size() + " 个槽"
                + "，跳过 " + result.skippedSlots().size()
                + "，扣 " + result.paidMaterials().size() + " 份材质"
                + (result.structurePaid() ? "，扣 1 个结构方块" : "");
    }
}
