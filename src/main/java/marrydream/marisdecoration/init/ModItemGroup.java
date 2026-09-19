package marrydream.marisdecoration.init;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroup {
    public static final ItemGroup MARIS_DECORATION = Registry.register(
            Registries.ITEM_GROUP,
            ModInfo.id("maris_decoration"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.maris-decoration.maris_decoration"))
                    .icon(() -> new ItemStack(ModBlock.STEEL_BLOCK))
                    .entries((context, entries) -> {
                        ModBlock.registeredItems().forEach(entries::add);
                        ModItem.registeredItems().forEach(entries::add);
                    })
                    .build()
    );

    private ModItemGroup() {
    }

    public static void init() {
        // Loading this class registers the item group.
    }
}
