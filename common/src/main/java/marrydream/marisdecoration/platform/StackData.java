package marrydream.marisdecoration.platform;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
public final class StackData {
 private StackData() {}
 public static net.minecraft.nbt.Tag save(ItemStack stack, HolderLookup.Provider registries) {
  //? if >=1.21 {
/*return stack.saveOptional(registries);
*///?} else {
return stack.save(new CompoundTag());
//?}
 }
 public static ItemStack read(CompoundTag tag, HolderLookup.Provider registries) {
  //? if >=1.21 {
/*if (tag.isEmpty()) return ItemStack.EMPTY;
  CompoundTag modern = tag;
  if (tag.contains("Count", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
   com.mojang.serialization.Dynamic<net.minecraft.nbt.Tag> converted = net.minecraft.util.datafix.DataFixers.getDataFixer().update(
    net.minecraft.util.datafix.fixes.References.ITEM_STACK,
    new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag.copy()),
    3465, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
   if (!(converted.getValue() instanceof CompoundTag fixed)) throw new IllegalArgumentException("Invalid legacy consumed stack");
   modern = fixed;
  }
  return ItemStack.parseOptional(registries, modern);
*///?} else {
return ItemStack.of(tag);
//?}
 }
 public static boolean same(ItemStack a, ItemStack b) {
  //? if >=1.21 {
/*return ItemStack.isSameItemSameComponents(a,b);
*///?} else {
return ItemStack.isSameItemSameTags(a,b);
//?}
 }
}
