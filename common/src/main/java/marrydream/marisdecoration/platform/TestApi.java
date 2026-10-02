package marrydream.marisdecoration.platform;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import marrydream.marisdecoration.placement.PlacementConfigs;
public final class TestApi {
 public static InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
  //? if >=1.21 {
/*var result = state.useItemOn(player.getItemInHand(hand),world,player,hand,hit);
  if (result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return state.useWithoutItem(world,player,hit);
  return result.result();
*///?} else {
return state.getBlock().use(state,world,pos,player,hand,hit);
//?}
 }
 public static CompoundTag placerTag(ItemStack stack) {
  //? if >=1.21 {
/*CompoundTag tag=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
  return tag.contains(PlacementConfigs.NBT_KEY) ? tag.getCompound(PlacementConfigs.NBT_KEY) : null;
*///?} else {
return stack.getTagElement(PlacementConfigs.NBT_KEY);
//?}
 }
 public static void setPlacerTag(ItemStack stack, CompoundTag tag) {
  //? if >=1.21 {
/*net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,stack,data->data.put(PlacementConfigs.NBT_KEY,tag));
*///?} else {
stack.addTagElement(PlacementConfigs.NBT_KEY,tag);
//?}
 }
}
