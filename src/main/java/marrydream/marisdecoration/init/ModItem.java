package marrydream.marisdecoration.init;

import marrydream.marisdecoration.item.BubbleTeaItem;
import marrydream.marisdecoration.item.SteelSpatula;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItem {
    public static final BubbleTeaItem BUBBLE_TEA = register( BubbleTeaItem.ID, new BubbleTeaItem() ); // 奶茶
    public static final SteelSpatula STEEL_SPATULA = register( SteelSpatula.ID, new SteelSpatula() ); // 钢铲
    public static final Item REBAR = register( "rebar", new Item( new Item.Settings() ) ); // 钢筋

    public static void init( ) {
        // 向饮品食物组添加内容
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.FOOD_AND_DRINK ).register( content -> {
            content.addAfter( Items.MILK_BUCKET, ModItem.BUBBLE_TEA );
        } );
        // 向工具组添加内容
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.TOOLS ).register( content -> {
            content.add( ModItem.STEEL_SPATULA );
        } );
        // 向原材料组添加内容
        ItemGroupEvents.modifyEntriesEvent( ItemGroups.INGREDIENTS ).register( content -> {
            content.addAfter( Items.STICK, ModItem.REBAR );
        } );
    }

    public static <T extends Item> T register( String id, T item ) {
        // 创建这个物体的标识符
        Identifier itemID = new Identifier( ModInfo.MOD_ID, id );
        // 注册这个物体
        return Registry.register( Registries.ITEM, itemID, item );
    }

}
