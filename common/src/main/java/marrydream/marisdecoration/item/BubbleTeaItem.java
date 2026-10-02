package marrydream.marisdecoration.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 奶茶
 */
public class BubbleTeaItem extends Item {
    public final static String ID = "bubble_tea";

    private static Properties getSetting( ) {
        // 食物配置
        FoodProperties foodComponent = new FoodProperties.Builder()
                .nutrition( 3 )
                // 1f = 100%
                .saturationMod( 0.3f )
                .alwaysEat()
                // 20 游戏刻为 1 秒
                .effect( new MobEffectInstance( MobEffects.DIG_SPEED, 20 * 20 ), 1.0f )
                .build();
        // 返回配置项
        return new Item.Properties().food( foodComponent );
    }

    public BubbleTeaItem( ) {
        super( BubbleTeaItem.getSetting() );
    }

    @Override
    public void appendHoverText( ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context ) {
        tooltip.add( Component.translatable( "item.maris-decoration.bubble_tea.tooltip" ) );
        tooltip.add( Component.translatable( "item.maris-decoration.bubble_tea.effect.tooltip" ).withStyle( ChatFormatting.YELLOW ) );
    }
}
