package marrydream.marisdecoration.init;

import marrydream.marisdecoration.item.BubbleTeaItem;
import marrydream.marisdecoration.item.CopycatPlacerItem;
import marrydream.marisdecoration.item.DetailChisel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModItem {
    private static final List<Item> REGISTERED_ITEMS = new ArrayList<>();

    public static final BubbleTeaItem BUBBLE_TEA = register(BubbleTeaItem.ID, new BubbleTeaItem());
    public static final DetailChisel DETAIL_CHISEL = register(DetailChisel.ID, new DetailChisel());
    /** 伪装放置器：预先配好一份伪装，之后拿着它右键直接按配置放置。 */
    public static final CopycatPlacerItem COPYCAT_PLACER = register(CopycatPlacerItem.ID, new CopycatPlacerItem());

    private ModItem() {
    }

    public static void init() {
        // Loading this class performs registration through the static fields above.
    }

    public static <T extends Item> T register(String id, T item) {
        T registered = Registry.register(BuiltInRegistries.ITEM, ModInfo.id(id), item);
        REGISTERED_ITEMS.add(registered);
        return registered;
    }

    public static List<Item> registeredItems() {
        return Collections.unmodifiableList(REGISTERED_ITEMS);
    }
}
