package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.Instrument;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;

public class TeakSlabs extends SlabBlock {
    public final static String ID = "teak_slab";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Block.Settings getBlockSetting( TeakPlanks block ) {
        // 返回配置项
        return FabricBlockSettings.copy( block );
    }

    public TeakSlabs( TeakPlanks block ) {
        super( TeakSlabs.getBlockSetting( block ) );
        // 可当燃料，烧 7.5s
        FuelRegistry.INSTANCE.add( this, 75 * 2 );
    }
}