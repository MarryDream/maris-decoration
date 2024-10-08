package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.item.Item;

public class TeakTrapdoor extends TrapdoorBlock {
    public final static String ID = "teak_trapdoor";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Settings getBlockSetting( TeakPlanks block ) {
        // nonOpaque() 使方块非透明，让其内部也进行渲染，避免通过透明纹理部分“看穿”整个世界
        return FabricBlockSettings.copy( block ).nonOpaque();
    }

    public TeakTrapdoor( TeakPlanks block ) {
        super( TeakTrapdoor.getBlockSetting( block ), BlockSetType.CHERRY );
        // 可当燃料，烧 15s
        FuelRegistry.INSTANCE.add( this, 15 * 20 );
    }
}
