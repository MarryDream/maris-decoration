package marrydream.marisdecoration.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TeakPlanks extends Block {
    public final static String ID = "teak_planks";

    public static Item.Settings getItemSetting( ) {
        return new Item.Settings();
    }

    private static Block.Settings getBlockSetting( ) {
        // 返回配置项
        return FabricBlockSettings.create()
                .strength( 2.0f );
    }

    public TeakPlanks( ) {
        super( TeakPlanks.getBlockSetting() );
    }
}
