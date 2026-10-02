package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.item.LayeredCopycatBoardItem;
import marrydream.marisdecoration.worldgen.TeakSaplingGenerator;
import marrydream.marisdecoration.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModBlock {
    private static final List<Block> REGISTERED_BLOCKS = new ArrayList<>();
    private static final List<Item> REGISTERED_ITEMS = new ArrayList<>();
    public static final RotatedPillarBlock TEAK_LOG = register("teak_log",
            new RotatedPillarBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.OAK_LOG)), true);
    public static final RotatedPillarBlock TEAK_WOOD = register("teak_wood",
            new RotatedPillarBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.OAK_WOOD)), true);
    public static final RotatedPillarBlock STRIPPED_TEAK_LOG = register("stripped_teak_log",
            new RotatedPillarBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.STRIPPED_OAK_LOG)), true);
    public static final RotatedPillarBlock STRIPPED_TEAK_WOOD = register("stripped_teak_wood",
            new RotatedPillarBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.STRIPPED_OAK_WOOD)), true);
    public static final LeavesBlock TEAK_LEAVES = register("teak_leaves",
            new LeavesBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.OAK_LEAVES)), true);
    public static final SaplingBlock TEAK_SAPLING = register("teak_sapling",
            new SaplingBlock(TeakSaplingGenerator.grower(),
                    marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.OAK_SAPLING)), true);
    // ---- 柚木木板家族：方块设置逐项对齐原版木材（对照 net.minecraft.block.Blocks 的 oak_* 系列） ----
    public static final Block TEAK_PLANKS = register(
            "teak_planks",
            new Block( BlockBehaviour.Properties.of().mapColor( MapColor.SAND ).instrument( NoteBlockInstrument.BASS ).strength( 2.0F, 3.0F ).sound( SoundType.WOOD ).ignitedByLava() ),
            true
    ); // 柚木木板
    public static final Block WEATHERED_TEAK_PLANKS = register(
            "weathered_teak_planks",
            // 与原版「同族变体」一样只换贴图：整份复制柚木木板的设置（硬度、抗爆、音效、乐器、可点燃）
            new Block( marrydream.marisdecoration.init.ModInfo.copyProperties( TEAK_PLANKS ) ),
            true
    ); // 风化柚木木板
    public static final StairBlock TEAK_STAIRS = register(
            "teak_stairs",
            new StairBlock( TEAK_PLANKS.defaultBlockState(), marrydream.marisdecoration.init.ModInfo.copyProperties( TEAK_PLANKS ) ),
            true
    ); // 柚木楼梯
    public static final SlabBlock TEAK_SLABS = register(
            "teak_slab",
            new SlabBlock( marrydream.marisdecoration.init.ModInfo.copyProperties( TEAK_PLANKS ) ),
            true
    ); // 柚木台阶
    public static final TrapDoorBlock TEAK_TRAPDOOR = register(
            "teak_trapdoor",
            // 原版木活板门：strength( 3.0F ) + nonOpaque() + allowsSpawning( never ) + burnable()；
            // 音效由 BlockSetType 提供（TrapdoorBlock 构造器会自己写进设置里），所以不写 sounds()。
            //? if >=1.21 {
/*new TrapDoorBlock(ModWoodType.TEAK_SET_TYPE, BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).instrument( NoteBlockInstrument.BASS ).strength( 3.0F ).noOcclusion().isValidSpawn( (state, world, pos, entityType) -> false ).ignitedByLava())
*///?} else {
new TrapDoorBlock( BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).instrument( NoteBlockInstrument.BASS ).strength( 3.0F ).noOcclusion().isValidSpawn( (state, world, pos, entityType) -> false ).ignitedByLava(), ModWoodType.TEAK_SET_TYPE )
//?}
,
            true
    ); // 柚木活板门
    public static final FenceBlock TEAK_FENCE = register(
            "teak_fence",
            new FenceBlock( BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).forceSolidOn().instrument( NoteBlockInstrument.BASS ).strength( 2.0F, 3.0F ).sound( SoundType.WOOD ).ignitedByLava() ),
            true
    ); // 柚木栅栏
    public static final FenceGateBlock TEAK_FENCE_GATE = register(
            "teak_fence_gate",
            // 栅栏门的方块音效与开关音效都取自 WoodType
            //? if >=1.21 {
/*new FenceGateBlock(ModWoodType.TEAK, BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).forceSolidOn().instrument( NoteBlockInstrument.BASS ).strength( 2.0F, 3.0F ).ignitedByLava())
*///?} else {
new FenceGateBlock( BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).forceSolidOn().instrument( NoteBlockInstrument.BASS ).strength( 2.0F, 3.0F ).ignitedByLava(), ModWoodType.TEAK )
//?}
,
            true
    ); // 柚木栅栏门
    public static final PressurePlateBlock TEAK_PRESSURE_PLATE = register(
            "teak_pressure_plate",
            // 原版木压力板：0.5 硬度、无碰撞、可点燃、被活塞破坏，音效与咔哒声取自 BlockSetType
            //? if >=1.21 {
/*new PressurePlateBlock(ModWoodType.TEAK_SET_TYPE, BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).forceSolidOn().instrument( NoteBlockInstrument.BASS ).noCollission().strength( 0.5F ).ignitedByLava().pushReaction( PushReaction.DESTROY ))
*///?} else {
new PressurePlateBlock( PressurePlateBlock.Sensitivity.EVERYTHING,
                    BlockBehaviour.Properties.of().mapColor( TEAK_PLANKS.defaultMapColor() ).forceSolidOn().instrument( NoteBlockInstrument.BASS ).noCollission().strength( 0.5F ).ignitedByLava().pushReaction( PushReaction.DESTROY ),
                    ModWoodType.TEAK_SET_TYPE )
//?}
,
            true
    ); // 柚木压力板
    public static final ButtonBlock TEAK_BUTTON = register(
            "teak_button",
            // 原版木按钮：0.5 硬度、无碰撞、被活塞破坏，按下保持 30 tick、弹射物可触发；
            // 原版木按钮并不设为可点燃，这里保持一致。
            //? if >=1.21 {
/*new ButtonBlock(ModWoodType.TEAK_SET_TYPE, 30, BlockBehaviour.Properties.of().noCollission().strength( 0.5F ).pushReaction( PushReaction.DESTROY ))
*///?} else {
new ButtonBlock( BlockBehaviour.Properties.of().noCollission().strength( 0.5F ).pushReaction( PushReaction.DESTROY ), ModWoodType.TEAK_SET_TYPE, 30, true )
//?}
,
            true
    ); // 柚木按钮
    public static final Block STEEL_BLOCK = register(
            "steel_block",
            new Block( BlockBehaviour.Properties.of().mapColor( state -> MapColor.TERRACOTTA_CYAN ).instrument( NoteBlockInstrument.IRON_XYLOPHONE ).strength( 8.0f, 15.0f ) ),
            true
    ); // 钢块
    public static final Block CYAN_STEEL_BLOCK = register(
            "cyan_steel_block",
            new Block( marrydream.marisdecoration.init.ModInfo.copyProperties( STEEL_BLOCK ).mapColor( state -> MapColor.TERRACOTTA_CYAN ) ),
            true
    ); // 青色钢块
    public static final Block BLACK_STEEL_BLOCK = register(
            "black_steel_block",
            new Block( marrydream.marisdecoration.init.ModInfo.copyProperties( STEEL_BLOCK ).mapColor( state -> MapColor.COLOR_BLACK ) ),
            true
    ); // 黑色钢块
    public static final CopycatSteelFixedLadderBlock COPYCAT_STEEL_FIXED_LADDER = register(
            "copycat_steel_fixed_ladder",
            new CopycatSteelFixedLadderBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.LADDER).strength(1.5F)),
            true
    );
    public static final CopycatSteelVerticalLadderBlock COPYCAT_STEEL_VERTICAL_LADDER = register(
            "copycat_steel_vertical_ladder",
            new CopycatSteelVerticalLadderBlock(marrydream.marisdecoration.init.ModInfo.copyProperties(Blocks.LADDER).strength(1.5F)),
            true
    );
    public static final CopycatGuardrailBlock COPYCAT_GUARDRAIL = register(
            "copycat_guardrail",
            new CopycatGuardrailBlock(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .noOcclusion().forceSolidOff().strength(2.0F, 3.0F)),
            true
    ); // 伪装护栏（可贴任意方块材质，四向可叠加）
    public static final LayeredCopycatBoardBlock LAYERED_COPYCAT_BOARD = register(
            "layered_copycat_board",
            // dynamicBounds()：形状来自方块实体的占用掩码，必须声明成动态形状。
            // 否则原版会在注册期用「没有方块实体的空视图」预烤一份空碰撞箱，并让
            // isSideSolidFullSquare 永远返回 false——原版梯子因此贴不上竖直外层面。
            // 详见 LayeredCopycatBoardBlock#getOutlineShape 的说明。
            new LayeredCopycatBoardBlock(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .noOcclusion().forceSolidOff().strength(2.0F, 3.0F).dynamicShape()),
            // 物品侧也要能往已有薄板里追加槽位（点的是旁边的普通方块时方块自己的 onUse 不会执行）
            block -> new LayeredCopycatBoardItem( block, new Item.Properties() )
    ); // 分层伪装薄板（六面各最多两层 1px 板，可分别伪装）
    public static final Block STEEL_PLUG_DOOR = register(
            "steel_plug_door",
            new LintelThresholdThinDoorBlock(
                    BlockBehaviour.Properties.of().mapColor( STEEL_BLOCK.defaultMapColor() ).strength( 8.0F ).noOcclusion().pushReaction( PushReaction.DESTROY ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门
    public static void init( ) {
        Platform.initWood();
    }

    private static ResourceLocation registerBlock( String id, Block block ) {
        // 创建这个物体的标识符
        ResourceLocation blockID = ModInfo.id(id);
        // 注册这个物体
        Platform.register( BuiltInRegistries.BLOCK, blockID, block );
        REGISTERED_BLOCKS.add(block);
        return blockID;
    }

    public static <T extends Block> T register( String id, T block, boolean shouldRegisterItem ) {
        ResourceLocation blockID = registerBlock( id, block );
        if ( shouldRegisterItem ) {
            Platform.registerBlockItem(blockID, () -> new BlockItem(block, new Item.Properties()), REGISTERED_ITEMS);
        }
        return block;
    }

    public static <T extends Block> T register( String id, T block, Function<T, BlockItem> blockItemFactory ) {
        ResourceLocation blockID = registerBlock( id, block );
        Platform.registerBlockItem(blockID, () -> blockItemFactory.apply(block), REGISTERED_ITEMS);
        return block;
    }

    public static List<Item> registeredItems() {
        return Collections.unmodifiableList(REGISTERED_ITEMS);
    }

    public static List<Block> registeredBlocks() {
        return Collections.unmodifiableList(REGISTERED_BLOCKS);
    }

}
