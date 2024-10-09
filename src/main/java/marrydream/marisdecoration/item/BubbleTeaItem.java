package marrydream.marisdecoration.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 奶茶
 */
public class BubbleTeaItem extends Item {
    public final static String ID = "bubble_tea";

    private static Settings getSetting( ) {
        // 食物配置
        FoodComponent foodComponent = new FoodComponent.Builder()
                .hunger( 3 )
                // 1f = 100%
                .saturationModifier( 0.3f )
                .alwaysEdible()
                // 20 游戏刻为 1 秒
                .statusEffect( new StatusEffectInstance( StatusEffects.HASTE, 20 * 20 ), 1.0f )
                .build();
        // 返回配置项
        return new Item.Settings().food( foodComponent );
    }

    public BubbleTeaItem( ) {
        super( BubbleTeaItem.getSetting() );
    }

    @Override
    public void appendTooltip( ItemStack stack, World world, List<Text> tooltip, TooltipContext context ) {
        tooltip.add( Text.translatable( "item.maris-decoration.bubble_tea.tooltip" ) );
        tooltip.add( Text.translatable( "item.maris-decoration.bubble_tea.effect.tooltip" ).formatted( Formatting.YELLOW ) );
    }
}
