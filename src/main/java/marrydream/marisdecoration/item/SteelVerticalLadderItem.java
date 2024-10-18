package marrydream.marisdecoration.item;

import marrydream.marisdecoration.block.VerticalLadderBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.List;

public class SteelVerticalLadderItem extends BlockItem {
    public SteelVerticalLadderItem( VerticalLadderBlock block, Settings settings ) {
        super( block, settings );
    }

    @Override
    public void appendTooltip( ItemStack stack, World world, List<Text> tooltip, TooltipContext context ) {
        tooltip.add( Text.translatable( "block.maris-decoration.steel_vertical_ladder.tooltip" ) );
    }
}
