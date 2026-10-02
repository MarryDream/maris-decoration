package marrydream.marisdecoration.platform;
public abstract class TooltipAdapter implements com.simibubi.create.foundation.item.TooltipModifier {
 public abstract void appendTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip);
 @Override public final void modify(net.minecraftforge.event.entity.player.ItemTooltipEvent event) { appendTooltip(event.getToolTip()); }
}
