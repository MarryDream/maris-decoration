package marrydream.marisdecoration.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 细工凿
 *
 * <p>本 mod 通用的「构件形态切换工具」：右键切换方块构件的形态（连接形状、边缘纹理等）。
 * 后续新增的构件形态切换一律挂在这个工具上，不要再为单个构件另起一个工具。
 */
public class DetailChisel extends Item {
    public final static String ID = "detail_chisel";

    private static Settings getSetting( ) {
        return new Settings().maxCount( 1 );
    }

    public DetailChisel( ) {
        super( DetailChisel.getSetting() );
    }

    @Override
    public void appendTooltip( ItemStack stack, World world, List<Text> tooltip, TooltipContext context ) {
        tooltip.add( Text.translatable( "item.maris-decoration.detail_chisel.tooltip" ) );
        tooltip.add( Text.translatable( "item.maris-decoration.detail_chisel.remark.tooltip" ).formatted( Formatting.BLUE ) );
    }
}
