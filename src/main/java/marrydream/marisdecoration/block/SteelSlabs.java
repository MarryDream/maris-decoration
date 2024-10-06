package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;

public class SteelSlabs extends SlabBlock {
    public final static String ID = "steel_slab";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Settings getBlockSetting( ) {
        // 返回配置项
        return FabricBlockSettings.create()
                .strength( 8.0f, 15.0f )
                .sounds( BlockSoundGroup.METAL )
                .requiresTool();
    }

    public SteelSlabs( ) {
        super( SteelSlabs.getBlockSetting() );
    }
}
