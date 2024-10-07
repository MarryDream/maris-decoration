package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlock {
    public static final TeakPlanks TEAK_PLANKS = register( new TeakPlanks(), TeakPlanks.ID, TeakPlanks.getItemSetting() ); // 柚木木板
    public static final TeakStairs TEAK_STAIRS = register( new TeakStairs( TEAK_PLANKS ), TeakStairs.ID, TeakStairs.getItemSetting() ); // 柚木半砖
    public static final TeakSlabs TEAK_SLABS = register( new TeakSlabs( TEAK_PLANKS ), TeakSlabs.ID, TeakSlabs.getItemSetting() ); // 柚木半砖
    public static final SteelBlock STEEL_BLOCK = register( new SteelBlock(), SteelBlock.ID, SteelBlock.getItemSetting() ); // 钢块
    public static final SteelSlabs STEEL_SLABS = register( new SteelSlabs( STEEL_BLOCK ), SteelSlabs.ID, SteelSlabs.getItemSetting() ); // 钢半砖

    public static void init( ) {
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.BUILDING_BLOCKS ).register( content -> {
            content.addAfter( Items.CHERRY_BUTTON, ModBlock.TEAK_PLANKS );
            content.addAfter( ModBlock.TEAK_PLANKS, ModBlock.TEAK_STAIRS );
            content.addAfter( ModBlock.TEAK_STAIRS, ModBlock.TEAK_SLABS );

            content.addAfter( Items.LIGHT_WEIGHTED_PRESSURE_PLATE, ModBlock.STEEL_BLOCK );
            content.addAfter( ModBlock.STEEL_BLOCK, ModBlock.STEEL_SLABS );
        } );
    }

    public static <T extends Block> T register( T block, String id, Item.Settings settings ) {
        // 创建这个物体的标识符
        Identifier blockID = new Identifier( ModInfo.MOD_ID, id );
        // 注册这个物体
        Registry.register( Registries.BLOCK, blockID, block );
        if ( settings != null ) {
            Registry.register( Registries.ITEM, blockID, new BlockItem( block, new Item.Settings() ) );
        }
        return block;
    }

}
