package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;

public class TeakPlanks extends Block {
    public final static String ID = "teak_planks";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Block.Settings getBlockSetting( ) {
        // 返回配置项
        return FabricBlockSettings.create()
                .strength( 2.0f )
                .sounds( BlockSoundGroup.WOOD );
    }

    public TeakPlanks( ) {
        super( TeakPlanks.getBlockSetting() );
        // 可当燃料，烧 30s
        FuelRegistry.INSTANCE.add( this, 30 * 20 );
    }
}
