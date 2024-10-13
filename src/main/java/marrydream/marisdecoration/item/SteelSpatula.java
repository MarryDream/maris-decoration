package marrydream.marisdecoration.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 钢铲
 */
public class SteelSpatula extends Item {
    public final static String ID = "steel_spatula";

    private static Settings getSetting( ) {
        return new Settings().maxCount( 1 );
    }

    public SteelSpatula( ) {
        super( SteelSpatula.getSetting() );
    }

    @Override
    public void appendTooltip( ItemStack stack, World world, List<Text> tooltip, TooltipContext context ) {
        tooltip.add( Text.translatable( "item.maris-decoration.steel_spatula.tooltip" ) );
        tooltip.add( Text.translatable( "item.maris-decoration.steel_spatula.remark.tooltip" ).formatted( Formatting.BLUE ) );
    }
}
