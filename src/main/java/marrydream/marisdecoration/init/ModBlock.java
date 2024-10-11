package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.block.ComponentWallBlock;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.*;
import net.minecraft.block.enums.Instrument;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlock {
    public static final Block TEAK_PLANKS = register(
            "teak_planks",
            new Block( FabricBlockSettings.create().mapColor( MapColor.PALE_YELLOW ).instrument( Instrument.BASS ).strength( 2.0F, 3.0F ).sounds( BlockSoundGroup.WOOD ).burnable() ),
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
            new Block( FabricBlockSettings.create().mapColor( MapColor.TERRACOTTA_CYAN ).instrument( Instrument.IRON_XYLOPHONE ).requiresTool().strength( 8.0f, 15.0f ) ),
            true
    ); // 钢块
    public static final SlabBlock STEEL_SLABS = register(
            "steel_slab",
            new SlabBlock( FabricBlockSettings.copy( STEEL_BLOCK ) ),
            true
    ); // 钢半砖
    public static final GuardrailBlock STEEL_GUARDRAIL = register(
            "steel_guardrail",
            new GuardrailBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ).nonOpaque() ),
            true
    ); // 钢护栏
    public static final GuardrailBlock Black_STEEL_GUARDRAIL = register(
            "black_steel_guardrail",
            new GuardrailBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ).nonOpaque() ),
            true
    ); // 黑色钢护栏
    public static final ComponentWallBlock STEEL_TEAK_COMPONENT_WALL = register(
            "steel_teak_component_wall",
            new ComponentWallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ) ),
            true
    ); // 钢层柚木组件墙
    public static final TrimRoofBlock STEEL_TEAK_TRIM_ROOF = register(
            "steel_teak_trim_roof",
            new TrimRoofBlock( TEAK_PLANKS.getDefaultState(), FabricBlockSettings.copy( TEAK_PLANKS ) ),
            true
    ); // 钢层柚木房顶

    public static void init( ) {
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.BUILDING_BLOCKS ).register( content -> {
            content.addAfter( Items.CHERRY_BUTTON, ModBlock.TEAK_PLANKS );
            content.addAfter( ModBlock.TEAK_PLANKS, ModBlock.TEAK_STAIRS );
            content.addAfter( ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS );
            content.addAfter( ModBlock.TEAK_SLABS, ModBlock.TEAK_TRAPDOOR );

            content.addAfter( Items.LIGHT_WEIGHTED_PRESSURE_PLATE, ModBlock.STEEL_BLOCK );
            content.addAfter( ModBlock.STEEL_BLOCK, ModBlock.STEEL_SLABS );
            content.addAfter( ModBlock.STEEL_SLABS, ModBlock.STEEL_GUARDRAIL );
            content.addAfter( ModBlock.STEEL_GUARDRAIL, ModBlock.Black_STEEL_GUARDRAIL );

            content.addAfter( ModBlock.Black_STEEL_GUARDRAIL, ModBlock.STEEL_TEAK_COMPONENT_WALL );
            content.addAfter( ModBlock.STEEL_TEAK_COMPONENT_WALL, ModBlock.STEEL_TEAK_TRIM_ROOF );
        } );

        // 如果方块一些部分是透明的（例如玻璃、树苗、门），避免贴图上的透明部分变成黑色
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.TEAK_TRAPDOOR, RenderLayer.getCutout() );

        // 注册燃料
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_PLANKS, 30 * 20 ); // 烧 30s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_STAIRS, 15 * 20 ); // 烧 15s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_SLABS, 75 * 2 ); // 烧 7.5s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_TRAPDOOR, 15 * 20 ); // 烧 15s
    }

    public static <T extends Block> T register( String id, T block, boolean shouldRegisterItem ) {
        // 创建这个物体的标识符
        Identifier blockID = new Identifier( ModInfo.MOD_ID, id );
        // 注册这个物体
        Registry.register( Registries.BLOCK, blockID, block );
        if ( shouldRegisterItem ) {
            Registry.register( Registries.ITEM, blockID, new BlockItem( block, new Item.Settings() ) );
        }
        return block;
    }

}
