package marrydream.marisdecoration.init;

import marrydream.marisdecoration.block.TeakPlanks;
import marrydream.marisdecoration.block.TeakSlabs;
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
    public static final TeakPlanks TEAK_PLANKS = register( new TeakPlanks(), TeakPlanks.ID, TeakPlanks.getItemSetting() ); // 奶茶
    public static final TeakSlabs TEAK_SLAB = register( new TeakSlabs(), TeakSlabs.ID, TeakSlabs.getItemSetting() ); // 奶茶

    public static void init( ) {
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.BUILDING_BLOCKS ).register( content -> {
            content.addAfter( Items.CHERRY_BUTTON, ModBlock.TEAK_PLANKS );
            content.addAfter( ModBlock.TEAK_PLANKS, ModBlock.TEAK_SLAB );
        } );
    }

    public static <T extends Block> T register( T block, String id, Item.Settings settings ) {
        // 创建这个物体的标识符
        Identifier blockID = new Identifier( ModInfo.MOD_ID, id );
        // 注册这个物体
        Registry.register( Registries.BLOCK, blockID, block );
        Registry.register( Registries.ITEM, blockID, new BlockItem( block, settings ) );
        return block;
    }

}
