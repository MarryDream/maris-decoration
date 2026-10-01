package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.item.LayeredCopycatBoardItem;
import marrydream.marisdecoration.worldgen.TeakSaplingGenerator;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.*;
import net.minecraft.block.enums.Instrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Function;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModBlock {
    private static final List<Block> REGISTERED_BLOCKS = new ArrayList<>();
    private static final List<Item> REGISTERED_ITEMS = new ArrayList<>();
    public static final PillarBlock TEAK_LOG = register("teak_log",
            new PillarBlock(FabricBlockSettings.copy(Blocks.OAK_LOG)), true);
    public static final PillarBlock TEAK_WOOD = register("teak_wood",
            new PillarBlock(FabricBlockSettings.copy(Blocks.OAK_WOOD)), true);
    public static final PillarBlock STRIPPED_TEAK_LOG = register("stripped_teak_log",
            new PillarBlock(FabricBlockSettings.copy(Blocks.STRIPPED_OAK_LOG)), true);
    public static final PillarBlock STRIPPED_TEAK_WOOD = register("stripped_teak_wood",
            new PillarBlock(FabricBlockSettings.copy(Blocks.STRIPPED_OAK_WOOD)), true);
    public static final LeavesBlock TEAK_LEAVES = register("teak_leaves",
            new LeavesBlock(FabricBlockSettings.copy(Blocks.OAK_LEAVES)), true);
    public static final SaplingBlock TEAK_SAPLING = register("teak_sapling",
            new SaplingBlock(new TeakSaplingGenerator(),
                    FabricBlockSettings.copy(Blocks.OAK_SAPLING)), true);
    // ---- 柚木木板家族：方块设置逐项对齐原版木材（对照 net.minecraft.block.Blocks 的 oak_* 系列） ----
    public static final Block TEAK_PLANKS = register(
            "teak_planks",
            new Block( FabricBlockSettings.create().mapColor( MapColor.PALE_YELLOW ).instrument( Instrument.BASS ).strength( 2.0F, 3.0F ).sounds( BlockSoundGroup.WOOD ).burnable() ),
            true
    ); // 柚木木板
    public static final Block WEATHERED_TEAK_PLANKS = register(
            "weathered_teak_planks",
            // 与原版「同族变体」一样只换贴图：整份复制柚木木板的设置（硬度、抗爆、音效、乐器、可点燃）
            new Block( FabricBlockSettings.copy( TEAK_PLANKS ) ),
            true
    ); // 风化柚木木板
    public static final StairsBlock TEAK_STAIRS = register(
            "teak_stairs",
            new StairsBlock( TEAK_PLANKS.getDefaultState(), FabricBlockSettings.copy( TEAK_PLANKS ) ),
            true
    ); // 柚木楼梯
    public static final SlabBlock TEAK_SLABS = register(
            "teak_slab",
            new SlabBlock( FabricBlockSettings.copy( TEAK_PLANKS ) ),
            true
    ); // 柚木台阶
    public static final TrapdoorBlock TEAK_TRAPDOOR = register(
            "teak_trapdoor",
            // 原版木活板门：strength( 3.0F ) + nonOpaque() + allowsSpawning( never ) + burnable()；
            // 音效由 BlockSetType 提供（TrapdoorBlock 构造器会自己写进设置里），所以不写 sounds()。
            new TrapdoorBlock( FabricBlockSettings.create().mapColor( TEAK_PLANKS.getDefaultMapColor() ).instrument( Instrument.BASS ).strength( 3.0F ).nonOpaque().allowsSpawning( Blocks::never ).burnable(), ModWoodType.TEAK_SET_TYPE ),
            true
    ); // 柚木活板门
    public static final FenceBlock TEAK_FENCE = register(
            "teak_fence",
            new FenceBlock( FabricBlockSettings.create().mapColor( TEAK_PLANKS.getDefaultMapColor() ).solid().instrument( Instrument.BASS ).strength( 2.0F, 3.0F ).sounds( BlockSoundGroup.WOOD ).burnable() ),
            true
    ); // 柚木栅栏
    public static final FenceGateBlock TEAK_FENCE_GATE = register(
            "teak_fence_gate",
            // 栅栏门的方块音效与开关音效都取自 WoodType
            new FenceGateBlock( FabricBlockSettings.create().mapColor( TEAK_PLANKS.getDefaultMapColor() ).solid().instrument( Instrument.BASS ).strength( 2.0F, 3.0F ).burnable(), ModWoodType.TEAK ),
            true
    ); // 柚木栅栏门
    public static final PressurePlateBlock TEAK_PRESSURE_PLATE = register(
            "teak_pressure_plate",
            // 原版木压力板：0.5 硬度、无碰撞、可点燃、被活塞破坏，音效与咔哒声取自 BlockSetType
            new PressurePlateBlock( PressurePlateBlock.ActivationRule.EVERYTHING,
                    FabricBlockSettings.create().mapColor( TEAK_PLANKS.getDefaultMapColor() ).solid().instrument( Instrument.BASS ).noCollision().strength( 0.5F ).burnable().pistonBehavior( PistonBehavior.DESTROY ),
                    ModWoodType.TEAK_SET_TYPE ),
            true
    ); // 柚木压力板
    public static final ButtonBlock TEAK_BUTTON = register(
            "teak_button",
            // 原版木按钮：0.5 硬度、无碰撞、被活塞破坏，按下保持 30 tick、弹射物可触发；
            // 原版木按钮并不设为可点燃，这里保持一致。
            new ButtonBlock( FabricBlockSettings.create().noCollision().strength( 0.5F ).pistonBehavior( PistonBehavior.DESTROY ), ModWoodType.TEAK_SET_TYPE, 30, true ),
            true
    ); // 柚木按钮
    public static final Block STEEL_BLOCK = register(
            "steel_block",
            new Block( FabricBlockSettings.create().mapColor( state -> MapColor.TERRACOTTA_CYAN ).instrument( Instrument.IRON_XYLOPHONE ).strength( 8.0f, 15.0f ) ),
            true
    ); // 钢块
    public static final Block CYAN_STEEL_BLOCK = register(
            "cyan_steel_block",
            new Block( FabricBlockSettings.copy( STEEL_BLOCK ).mapColor( state -> MapColor.TERRACOTTA_CYAN ) ),
            true
    ); // 青色钢块
    public static final Block BLACK_STEEL_BLOCK = register(
            "black_steel_block",
            new Block( FabricBlockSettings.copy( STEEL_BLOCK ).mapColor( state -> MapColor.BLACK ) ),
            true
    ); // 黑色钢块
    public static final CopycatSteelFixedLadderBlock COPYCAT_STEEL_FIXED_LADDER = register(
            "copycat_steel_fixed_ladder",
            new CopycatSteelFixedLadderBlock(FabricBlockSettings.copy(Blocks.LADDER).strength(1.5F)),
            true
    );
    public static final CopycatSteelVerticalLadderBlock COPYCAT_STEEL_VERTICAL_LADDER = register(
            "copycat_steel_vertical_ladder",
            new CopycatSteelVerticalLadderBlock(FabricBlockSettings.copy(Blocks.LADDER).strength(1.5F)),
            true
    );
    public static final CopycatGuardrailBlock COPYCAT_GUARDRAIL = register(
            "copycat_guardrail",
            new CopycatGuardrailBlock(FabricBlockSettings.create().instrument(Instrument.IRON_XYLOPHONE)
                    .nonOpaque().notSolid().strength(2.0F, 3.0F)),
            true
    ); // 伪装护栏（可贴任意方块材质，四向可叠加）
    public static final LayeredCopycatBoardBlock LAYERED_COPYCAT_BOARD = register(
            "layered_copycat_board",
            // dynamicBounds()：形状来自方块实体的占用掩码，必须声明成动态形状。
            // 否则原版会在注册期用「没有方块实体的空视图」预烤一份空碰撞箱，并让
            // isSideSolidFullSquare 永远返回 false——原版梯子因此贴不上竖直外层面。
            // 详见 LayeredCopycatBoardBlock#getOutlineShape 的说明。
            new LayeredCopycatBoardBlock(FabricBlockSettings.create().instrument(Instrument.IRON_XYLOPHONE)
                    .nonOpaque().notSolid().strength(2.0F, 3.0F).dynamicBounds()),
            // 物品侧也要能往已有薄板里追加槽位（点的是旁边的普通方块时方块自己的 onUse 不会执行）
            block -> new LayeredCopycatBoardItem( block, new Item.Settings() )
    ); // 分层伪装薄板（六面各最多两层 1px 板，可分别伪装）
    public static final Block STEEL_PLUG_DOOR = register(
            "steel_plug_door",
            new LintelThresholdThinDoorBlock(
                    AbstractBlock.Settings.create().mapColor( STEEL_BLOCK.getDefaultMapColor() ).strength( 8.0F ).nonOpaque().pistonBehavior( PistonBehavior.DESTROY ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门
    public static void init( ) {
        StrippableBlockRegistry.register(TEAK_LOG, STRIPPED_TEAK_LOG);
        StrippableBlockRegistry.register(TEAK_WOOD, STRIPPED_TEAK_WOOD);
        var flammable = FlammableBlockRegistry.getDefaultInstance();
        // 原木类：燃烧几率 5 / 蔓延几率 5，与原版原木一致
        for (Block wood : List.of(TEAK_LOG, TEAK_WOOD, STRIPPED_TEAK_LOG, STRIPPED_TEAK_WOOD)) {
            FuelRegistry.INSTANCE.add(wood, 300);
            flammable.add(wood, 5, 5);
        }
        // 木板与衍生品：燃烧几率 5 / 蔓延几率 20，与原版木板、楼梯、台阶、栅栏、栅栏门一致。
        // 原版没有把活板门、压力板、按钮放进火焰蔓延表（三者只是燃料），这里同样不登记。
        for (Block wooden : List.of(TEAK_PLANKS, WEATHERED_TEAK_PLANKS, TEAK_STAIRS, TEAK_SLABS,
                TEAK_FENCE, TEAK_FENCE_GATE)) {
            flammable.add(wooden, 5, 20);
        }
        flammable.add(TEAK_LEAVES, 30, 60);
        FuelRegistry.INSTANCE.add(TEAK_SAPLING, 100);
        CompostingChanceRegistry.INSTANCE.add(TEAK_LEAVES, 0.3F);
        CompostingChanceRegistry.INSTANCE.add(TEAK_SAPLING, 0.3F);
        // 燃烧时间（tick）与原版木材一致：木板 / 楼梯 / 活板门 / 栅栏 / 栅栏门 / 压力板 300、台阶 150、按钮 100。
        // 这些值同样能由物品标签（#minecraft:planks 等）带出来；这里显式登记是把整族配置集中在一处，
        // 并覆盖没有对应原版标签的风化柚木木板。
        FuelRegistry.INSTANCE.add( TEAK_PLANKS, 300 );
        FuelRegistry.INSTANCE.add( WEATHERED_TEAK_PLANKS, 300 );
        FuelRegistry.INSTANCE.add( TEAK_STAIRS, 300 );
        FuelRegistry.INSTANCE.add( TEAK_SLABS, 150 );
        FuelRegistry.INSTANCE.add( TEAK_TRAPDOOR, 300 );
        FuelRegistry.INSTANCE.add( TEAK_FENCE, 300 );
        FuelRegistry.INSTANCE.add( TEAK_FENCE_GATE, 300 );
        FuelRegistry.INSTANCE.add( TEAK_PRESSURE_PLATE, 300 );
        FuelRegistry.INSTANCE.add( TEAK_BUTTON, 100 );
    }

    private static Identifier registerBlock( String id, Block block ) {
        // 创建这个物体的标识符
        Identifier blockID = ModInfo.id(id);
        // 注册这个物体
        Registry.register( Registries.BLOCK, blockID, block );
        REGISTERED_BLOCKS.add(block);
        return blockID;
    }

    public static <T extends Block> T register( String id, T block, boolean shouldRegisterItem ) {
        Identifier blockID = registerBlock( id, block );
        if ( shouldRegisterItem ) {
            REGISTERED_ITEMS.add(Registry.register( Registries.ITEM, blockID, new BlockItem( block, new Item.Settings() ) ));
        }
        return block;
    }

    public static <T extends Block> T register( String id, T block, Function<T, BlockItem> blockItemFactory ) {
        Identifier blockID = registerBlock( id, block );
        REGISTERED_ITEMS.add(Registry.register( Registries.ITEM, blockID, blockItemFactory.apply( block ) ));
        return block;
    }

    public static List<Item> registeredItems() {
        return Collections.unmodifiableList(REGISTERED_ITEMS);
    }

    public static List<Block> registeredBlocks() {
        return Collections.unmodifiableList(REGISTERED_BLOCKS);
    }

}
