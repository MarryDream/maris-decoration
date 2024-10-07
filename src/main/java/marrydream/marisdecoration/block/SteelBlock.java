package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;

public class SteelBlock extends Block {
    public final static String ID = "steel_block";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Settings getBlockSetting( ) {
        // 返回配置项
        return FabricBlockSettings.create()
                .mapColor( MapColor.TERRACOTTA_CYAN )
                .instrument( Instrument.IRON_XYLOPHONE )
                .requiresTool()
                .strength( 8.0f, 15.0f );
    }

    public SteelBlock( ) {
        super( SteelBlock.getBlockSetting() );
    }
}
