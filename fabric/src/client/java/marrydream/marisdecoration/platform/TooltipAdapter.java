package marrydream.marisdecoration.platform;
public abstract class TooltipAdapter implements com.simibubi.create.foundation.item.TooltipModifier {
 public abstract void appendTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip);
 @Override public final void modify(net.minecraft.world.item.ItemStack stack,net.minecraft.world.entity.player.Player player,net.minecraft.world.item.TooltipFlag flags,java.util.List<net.minecraft.network.chat.Component> tooltip) { appendTooltip(tooltip); }
}
