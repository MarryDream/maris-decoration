package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.Item;

public class TeakSlabs extends SlabBlock {
    public final static String ID = "teak_slab";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Block.Settings getBlockSetting( ) {
        // 返回配置项
        return FabricBlockSettings.create()
                .strength( 2.0f );
    }

    public TeakSlabs( ) {
        super( TeakSlabs.getBlockSetting() );
    }
}
