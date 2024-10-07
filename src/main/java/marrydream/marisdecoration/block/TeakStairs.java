package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.StairsBlock;
import net.minecraft.item.Item;

public class TeakStairs extends StairsBlock {
    public final static String ID = "teak_stairs";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Block.Settings getBlockSetting( TeakPlanks block ) {
        // 返回配置项
        return FabricBlockSettings.copy( block );
    }

    public TeakStairs( TeakPlanks block ) {
        super( block.getDefaultState(), getBlockSetting( block ) );
        // 可当燃料，烧 7.5s
        FuelRegistry.INSTANCE.add( this, 15 * 20 );
    }
}
