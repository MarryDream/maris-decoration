package marrydream.marisdecoration.init;

import marrydream.marisdecoration.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroup {
    public static final CreativeModeTab MARIS_DECORATION = Platform.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ModInfo.id("maris_decoration"),
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.maris-decoration.maris_decoration"))
                    .icon(() -> new ItemStack(ModItem.BUBBLE_TEA))
                    .displayItems((context, entries) -> {
                        ModBlock.registeredItems().forEach(entries::accept);
                        ModItem.registeredItems().forEach(entries::accept);
                    })
                    .build()
    );

    private ModItemGroup() {
    }

    public static void init() {
        // Loading this class registers the item group.
    }
}
