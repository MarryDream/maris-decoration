package marrydream.marisdecoration.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 钢锤
 */
public class SteelHammer extends Item {
    public final static String ID = "steel_hammer";

    private static Settings getSetting( ) {
        return new Settings().maxCount( 1 );
    }

    public SteelHammer( ) {
        super( SteelHammer.getSetting() );
    }

    @Override
    public void appendTooltip( ItemStack stack, World world, List<Text> tooltip, TooltipContext context ) {
        tooltip.add( Text.translatable( "item.maris-decoration.steel_hammer.tooltip" ) );
        tooltip.add( Text.translatable( "item.maris-decoration.steel_hammer.remark.tooltip" ).formatted( Formatting.BLUE ) );
    }
}
