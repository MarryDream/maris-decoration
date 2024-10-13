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
    public static final RoofBlock TEAK_ROOF = register(
            "teak_roof",
            // solid() 会使方块变成固体方块，此时可以放置展示框等需要依附估计方块的方块，最关键的是可以挡雨
            // 如果没有手动设置，那么 mc 会判断方块的碰撞体积高度是否超过一定的值，如果超过了就会自动设置为固体方块
            new RoofBlock( FabricBlockSettings.copy( TEAK_PLANKS ).strength( 1.0F, 1.5F ).solid() ),
            true
    ); // 柚木房顶
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
            new GuardrailBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ).nonOpaque().notSolid() ),
            true
    ); // 钢护栏
    public static final GuardrailBlock BLACK_STEEL_GUARDRAIL = register(
            "black_steel_guardrail",
            new GuardrailBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_GUARDRAIL ) ),
            true
    ); // 黑色钢护栏
    public static final ComponentWallBlock STEEL_TEAK_COMPONENT_WALL = register(
            "steel_teak_component_wall",
            new ComponentWallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_BLOCK ).strength( 4.0F, 7.5f ) ),
            true
    ); // 钢层柚木组件墙
    public static final ComponentWallBlock CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL = register(
            "cyan_glass_steel_teak_component_wall",
            new ComponentWallBlock( STEEL_BLOCK.getDefaultState(), FabricBlockSettings.copy( STEEL_TEAK_COMPONENT_WALL ).nonOpaque() ),
            true
    ); // 青色玻璃钢层柚木组件墙
    public static final TrimRoofBlock STEEL_TEAK_TRIM_ROOF = register(
            "steel_teak_trim_roof",
            new TrimRoofBlock( TEAK_PLANKS.getDefaultState(), FabricBlockSettings.copy( TEAK_PLANKS ).strength( 2.0F, 3.0F ).solid() ),
            true
    ); // 钢层柚木房顶

    public static void init( ) {
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.BUILDING_BLOCKS ).register( content -> {
            content.addAfter( Items.CHERRY_BUTTON, ModBlock.TEAK_PLANKS );
            content.addAfter( ModBlock.TEAK_PLANKS, ModBlock.TEAK_STAIRS );
            content.addAfter( ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS );
            content.addAfter( ModBlock.TEAK_SLABS, ModBlock.TEAK_TRAPDOOR );
            content.addAfter( ModBlock.TEAK_TRAPDOOR, ModBlock.TEAK_ROOF );
            content.addAfter( ModBlock.TEAK_ROOF, ModBlock.STEEL_TEAK_TRIM_ROOF );
            content.addAfter( ModBlock.STEEL_TEAK_TRIM_ROOF, ModBlock.STEEL_TEAK_COMPONENT_WALL );
            content.addAfter( ModBlock.STEEL_TEAK_COMPONENT_WALL, ModBlock.CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL );

            content.addAfter( Items.LIGHT_WEIGHTED_PRESSURE_PLATE, ModBlock.STEEL_BLOCK );
            content.addAfter( ModBlock.STEEL_BLOCK, ModBlock.STEEL_SLABS );
            content.addAfter( ModBlock.STEEL_SLABS, ModBlock.STEEL_GUARDRAIL );
            content.addAfter( ModBlock.STEEL_GUARDRAIL, ModBlock.BLACK_STEEL_GUARDRAIL );
        } );

        // 如果方块一些部分是透明的（例如玻璃、树苗、门），避免贴图上的透明部分变成黑色
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.TEAK_TRAPDOOR, RenderLayer.getCutout() );
        // 如果方块一些部分的材质是半透明的，例如玻璃
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL, RenderLayer.getTranslucent() );

        // 注册燃料
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_PLANKS, 30 * 20 ); // 烧 30s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_STAIRS, 15 * 20 ); // 烧 15s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_SLABS, 75 * 2 ); // 烧 7.5s
        FuelRegistry.INSTANCE.add( ModBlock.TEAK_ROOF, 3 * 20 ); // 烧 3s
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
