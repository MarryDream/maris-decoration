package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.block.ComponentWallBlock;
import marrydream.marisdecoration.block.WallBlock;
import marrydream.marisdecoration.item.SteelVerticalLadderItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.*;
import net.minecraft.block.enums.Instrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModBlock {
    public static final Block TEAK_PLANKS = register(
            "teak_planks",
            new Block( FabricBlockSettings.create().mapColor( state -> MapColor.PALE_YELLOW ).instrument( Instrument.BASS ).strength( 2.0F, 3.0F ).sounds( BlockSoundGroup.WOOD ).burnable() ),
            true
    ); // 柚木木板
    public static final StairsBlock TEAK_STAIRS = register(
            "teak_stairs",
            new StairsBlock( TEAK_PLANKS.getDefaultState(), FabricBlockSettings.copy( TEAK_PLANKS ) ),
            true
    ); // 柚木楼梯
    public static final SlabBlock TEAK_SLABS = register(
            "teak_slab",
            new SlabBlock( FabricBlockSettings.copy( TEAK_PLANKS ) ),
            true
    ); // 柚木半砖
    public static final TrapdoorBlock TEAK_TRAPDOOR = register(
            "teak_trapdoor",
            new TrapdoorBlock( FabricBlockSettings.copy( TEAK_PLANKS ).nonOpaque(), BlockSetType.BIRCH ),
            true
    ); // 柚木活板门
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
    public static final SlabBlock STEEL_SLABS = register(
            "steel_slab",
            new SlabBlock( FabricBlockSettings.copy( STEEL_BLOCK ) ),
            true
    ); // 钢半砖
    public static final StairsBlock STEEL_STAIRS = register(
            "steel_stairs",
            new StairsBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ) ),
            true
    ); // 钢楼梯
    public static final StairsBlock CYAN_STEEL_STAIRS = register(
            "cyan_steel_stairs",
            new StairsBlock( CYAN_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_STEEL_BLOCK ) ),
            true
    ); // 青色钢楼梯
    public static final StairsBlock BLACK_STEEL_STAIRS = register(
            "black_steel_stairs",
            new StairsBlock( BLACK_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( BLACK_STEEL_BLOCK ) ),
            true
    ); // 黑色钢楼梯
    public static final SlabBlock CYAN_STEEL_SLABS = register(
            "cyan_steel_slab",
            new SlabBlock( FabricBlockSettings.copy( CYAN_STEEL_BLOCK ) ),
            true
    ); // 青色钢半砖
    public static final SlabBlock BLACK_STEEL_SLABS = register(
            "black_steel_slab",
            new SlabBlock( FabricBlockSettings.copy( BLACK_STEEL_BLOCK ) ),
            true
    ); // 黑色钢半砖
    public static final WallWithoutSwitchTextureBlock TEAK_WALL = register(
            "teak_wall",
            new WallWithoutSwitchTextureBlock( TEAK_PLANKS.getDefaultState(), FabricBlockSettings.copy( TEAK_PLANKS ).strength( 2.0F, 2.5f ) ),
            true
    ); // 柚木墙
    public static final WallWithoutSwitchTextureBlock STEEL_WALL = register(
            "steel_wall",
            new WallWithoutSwitchTextureBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ).strength( 4.0F, 7.5f ) ),
            true
    ); // 钢墙
    public static final WallBlock STEEL_ROOF_TEAK_WALL = register(
            "steel_roof_teak_wall",
            new WallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_WALL ) ),
            true
    ); // 钢屋顶柚木墙
    public static final WallBlock CYAN_STEEL_ROOF_TEAK_WALL = register(
            "cyan_steel_roof_teak_wall",
            new WallBlock( CYAN_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_STEEL_BLOCK ).strength( 4.0F, 7.5f ) ),
            true
    ); // 青钢屋顶柚木墙
    public static final WallBlock BLACK_STEEL_ROOF_TEAK_WALL = register(
            "black_steel_roof_teak_wall",
            new WallBlock( BLACK_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( BLACK_STEEL_BLOCK ).strength( 4.0F, 7.5f ) ),
            true
    ); // 黑钢屋顶柚木墙
    public static final WallBlock CYAN_ROOF_STEEL_WALL = register(
            "cyan_roof_steel_wall",
            new WallBlock( CYAN_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_STEEL_ROOF_TEAK_WALL ) ),
            true
    ); // 青色屋顶钢墙
    public static final WallBlock BLACK_ROOF_STEEL_WALL = register(
            "black_roof_steel_wall",
            new WallBlock( BLACK_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( BLACK_STEEL_ROOF_TEAK_WALL ) ),
            true
    ); // 黑色屋顶钢墙
    public static final WallBlock CYAN_ROOF_STEEL_TRIM_CYAN_WINDOW_WALL = register(
            "cyan_roof_steel_trim_cyan_window_wall",
            new WallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_ROOF_STEEL_WALL ).sounds( BlockSoundGroup.GLASS ).strength( 1.5F, 2.5f ).nonOpaque() ),
            true
    ); // 青色屋顶钢边青色窗墙
    public static final ComponentWallBlock STEEL_TEAK_COMPONENT_WALL = register(
            "steel_teak_component_wall",
            new ComponentWallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_WALL ).mapColor( MapColor.PALE_YELLOW ) ),
            true
    ); // 钢层柚木组件墙
    public static final ComponentWallBlock CYAN_ROOF_STEEL_TEAK_COMPONENT_WALL = register(
            "cyan_roof_steel_teak_component_wall",
            new ComponentWallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_ROOF_STEEL_WALL ) ),
            true
    ); // 青色屋顶钢层柚木组件墙
    public static final ComponentWallBlock CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL = register(
            "cyan_glass_steel_teak_component_wall",
            new ComponentWallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_TEAK_COMPONENT_WALL ).nonOpaque() ),
            true
    ); // 青色玻璃钢层柚木组件墙
    public static final ComponentWallBlock CYAN_GLASS_ROOF_STEEL_TEAK_COMPONENT_WALL = register(
            "cyan_glass_cyan_roof_steel_teak_component_wall",
            new ComponentWallBlock( CYAN_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_ROOF_STEEL_WALL ).nonOpaque() ),
            true
    ); // 青色玻璃屋顶钢层柚木组件墙
    public static final RoofBlock TEAK_ROOF = register(
            "teak_roof",
            // solid() 会使方块变成固体方块，此时可以放置展示框等需要依附估计方块的方块，最关键的是可以挡雨
            // 如果没有手动设置，那么 mc 会判断方块的碰撞体积高度是否超过一定的值，如果超过了就会自动设置为固体方块
            new RoofBlock( FabricBlockSettings.copy( TEAK_PLANKS ).strength( 1.0F, 1.5F ).solid() ),
            true
    ); // 柚木屋顶
    public static final RoofBlock STEEL_ROOF = register(
            "steel_roof",
            new RoofBlock( FabricBlockSettings.copy( STEEL_BLOCK ).strength( 3.0F, 5.0F ).solid() ),
            true
    ); // 钢屋顶
    public static final RoofBlock CYAN_STEEL_ROOF = register(
            "cyan_steel_roof",
            new RoofBlock( FabricBlockSettings.copy( CYAN_STEEL_BLOCK ).strength( 3.0F, 5.0F ).solid() ),
            true
    ); // 青色钢屋顶
    public static final RoofBlock BLACK_STEEL_ROOF = register(
            "black_steel_roof",
            new RoofBlock( FabricBlockSettings.copy( BLACK_STEEL_BLOCK ).strength( 3.0F, 5.0F ).solid() ),
            true
    ); // 黑色色钢屋顶
    public static final TrimRoofBlock STEEL_TEAK_TRIM_ROOF = register(
            "steel_teak_trim_roof",
            new TrimRoofBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ).mapColor( MapColor.PALE_YELLOW ).strength( 2.0F, 3.0F ).solid() ),
            true
    ); // 钢边柚木屋顶
    public static final TrimRoofBlock STEEL_TRIM_CYAN_STEEL_ROOF = register(
            "steel_trim_cyan_steel_roof",
            new TrimRoofBlock( CYAN_STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( CYAN_STEEL_ROOF ) ),
            true
    ); // 钢边青色钢屋顶
    public static final LadderBlock STEEL_FIXED_LADDER = register(
            "steel_fixed_ladder",
            new LadderBlock( FabricBlockSettings.copy( Blocks.LADDER ).strength( 1.5F ) ),
            true
    ); // 钢固定梯
    public static final VerticalLadderBlock STEEL_VERTICAL_LADDER = register(
            "steel_vertical_ladder",
            new VerticalLadderBlock( FabricBlockSettings.copy( STEEL_FIXED_LADDER ) ),
            block -> new SteelVerticalLadderItem( block, new Item.Settings() )
    ); // 垂直钢爬梯
    public static final GuardrailBlock STEEL_GUARDRAIL = register(
            "steel_guardrail",
            new GuardrailBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.create().instrument( Instrument.IRON_XYLOPHONE ).nonOpaque().notSolid() ),
            true
    ); // 钢护栏
    public static final GuardrailBlock BLACK_STEEL_GUARDRAIL = register(
            "black_steel_guardrail",
            new GuardrailBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_GUARDRAIL ) ),
            true
    ); // 黑色钢护栏
    public static final Block STEEL_PLUG_DOOR = register(
            "steel_plug_door",
            new LintelThresholdThinDoorBlock(
                    AbstractBlock.Settings.create().mapColor( STEEL_BLOCK.getDefaultMapColor() ).strength( 8.0F ).nonOpaque().pistonBehavior( PistonBehavior.DESTROY ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门
    public static final Block STEEL_PLUG_DOOR_WITH_ROOF = register(
            "steel_plug_door_with_roof",
            new RoofThresholdThinDoorBlock(
                    FabricBlockSettings.copy( STEEL_PLUG_DOOR ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门（带屋顶）
    public static final Block TEAK_STEEL_PLUG_DOOR_WITH_ROOF = register(
            "teak_steel_plug_door_with_roof",
            new RoofThresholdThinDoorBlock(
                    FabricBlockSettings.copy( STEEL_PLUG_DOOR ).mapColor( TEAK_PLANKS.getDefaultMapColor() ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门（带柚木屋顶）
    public static final Block CYAN_STEEL_PLUG_DOOR_WITH_ROOF = register(
            "cyan_steel_plug_door_with_roof",
            new RoofThresholdThinDoorBlock(
                    FabricBlockSettings.copy( STEEL_PLUG_DOOR ).mapColor( CYAN_STEEL_BLOCK.getDefaultMapColor() ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门（带青色屋顶）
    public static final Block BLACK_STEEL_PLUG_DOOR_WITH_ROOF = register(
            "black_steel_plug_door_with_roof",
            new RoofThresholdThinDoorBlock(
                    FabricBlockSettings.copy( STEEL_PLUG_DOOR ).mapColor( BLACK_STEEL_BLOCK.getDefaultMapColor() ),
                    BlockSetType.STONE
            ), true
    ); // 钢内嵌门（带黑色屋顶）

    public static void init( ) {
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.BUILDING_BLOCKS ).register( content -> {
            /* 柚木 */
            content.addAfter( Items.CHERRY_BUTTON, ModBlock.TEAK_PLANKS );
            content.addAfter( ModBlock.TEAK_PLANKS, ModBlock.TEAK_STAIRS );
            content.addAfter( ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS );
            content.addAfter( ModBlock.TEAK_SLABS, ModBlock.TEAK_TRAPDOOR );

            /* 钢 */
            content.add( ModBlock.STEEL_BLOCK );
            content.add( ModBlock.STEEL_STAIRS );
            content.add( ModBlock.STEEL_SLABS );
            content.add( ModBlock.CYAN_STEEL_BLOCK );
            content.add( ModBlock.CYAN_STEEL_STAIRS );
            content.add( ModBlock.CYAN_STEEL_SLABS );
            content.add( ModBlock.BLACK_STEEL_BLOCK );
            content.add( ModBlock.BLACK_STEEL_SLABS );
            content.add( ModBlock.BLACK_STEEL_STAIRS );

            /* 护栏 */
            content.add( ModBlock.STEEL_GUARDRAIL );
            content.add( ModBlock.BLACK_STEEL_GUARDRAIL );

            /* 屋顶 */
            content.add( ModBlock.TEAK_ROOF );
            content.add( ModBlock.STEEL_ROOF );
            content.add( ModBlock.CYAN_STEEL_ROOF );
            content.add( ModBlock.BLACK_STEEL_ROOF );
            content.add( ModBlock.STEEL_TEAK_TRIM_ROOF );
            content.add( ModBlock.STEEL_TRIM_CYAN_STEEL_ROOF );

            /* 墙 */
            content.add( ModBlock.TEAK_WALL );
            content.add( ModBlock.STEEL_WALL );
            content.add( ModBlock.STEEL_ROOF_TEAK_WALL );
            content.add( ModBlock.CYAN_STEEL_ROOF_TEAK_WALL );
            content.add( ModBlock.BLACK_STEEL_ROOF_TEAK_WALL );
            content.add( ModBlock.CYAN_ROOF_STEEL_WALL );
            content.add( ModBlock.BLACK_ROOF_STEEL_WALL );
            content.add( ModBlock.CYAN_ROOF_STEEL_TRIM_CYAN_WINDOW_WALL );
            content.add( ModBlock.STEEL_TEAK_COMPONENT_WALL );
            content.add( ModBlock.CYAN_ROOF_STEEL_TEAK_COMPONENT_WALL );
            content.add( ModBlock.CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL );
            content.add( ModBlock.CYAN_GLASS_ROOF_STEEL_TEAK_COMPONENT_WALL );
        } );

        ItemGroupEvents.modifyEntriesEvent( ItemGroups.FUNCTIONAL ).register( content -> {
            /* 梯子 */
            content.addAfter( Blocks.LADDER, ModBlock.STEEL_FIXED_LADDER );
            content.addAfter( Blocks.LADDER, ModBlock.STEEL_VERTICAL_LADDER );
        } );

        ItemGroupEvents.modifyEntriesEvent( ItemGroups.REDSTONE ).register( content -> {
            /* 门 */
            content.add( ModBlock.STEEL_PLUG_DOOR );
            content.add( ModBlock.STEEL_PLUG_DOOR_WITH_ROOF );
            content.add( ModBlock.TEAK_STEEL_PLUG_DOOR_WITH_ROOF );
            content.add( ModBlock.CYAN_STEEL_PLUG_DOOR_WITH_ROOF );
            content.add( ModBlock.BLACK_STEEL_PLUG_DOOR_WITH_ROOF );
        } );

        // 注册燃料
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_PLANKS, 30 * 20 ); // 烧 30s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_STAIRS, 15 * 20 ); // 烧 15s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_SLABS, 75 * 2 ); // 烧 7.5s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_WALL, 6 * 20 ); // 烧 6s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_ROOF, 3 * 20 ); // 烧 3s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_TRAPDOOR, 15 * 20 ); // 烧 15s
    }

    private static Identifier registerBlock( String id, Block block ) {
        // 创建这个物体的标识符
        Identifier blockID = new Identifier( ModInfo.MOD_ID, id );
        // 注册这个物体
        Registry.register( Registries.BLOCK, blockID, block );
        return blockID;
    }

    public static <T extends Block> T register( String id, T block, boolean shouldRegisterItem ) {
        Identifier blockID = registerBlock( id, block );
        if ( shouldRegisterItem ) {
            Registry.register( Registries.ITEM, blockID, new BlockItem( block, new Item.Settings() ) );
        }
        return block;
    }

    public static <T extends Block> T register( String id, T block, Function<T, BlockItem> blockItemFactory ) {
        Identifier blockID = registerBlock( id, block );
        Registry.register( Registries.ITEM, blockID, blockItemFactory.apply( block ) );
        return block;
    }

}
