package marrydream.marisdecoration.platform;
import static marrydream.marisdecoration.init.ModBlock.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.level.BlockEvent;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
public final class ForgeWoodHooks {
 private static final Map<Block,Integer> FUEL=new IdentityHashMap<>();
 public static void init() {
  for(Block b:List.of(TEAK_LOG,TEAK_WOOD,STRIPPED_TEAK_LOG,STRIPPED_TEAK_WOOD,TEAK_PLANKS,WEATHERED_TEAK_PLANKS,TEAK_STAIRS,TEAK_TRAPDOOR,TEAK_FENCE,TEAK_FENCE_GATE,TEAK_PRESSURE_PLATE)) FUEL.put(b,300);
  FUEL.put(TEAK_SLABS,150); FUEL.put(TEAK_BUTTON,100); FUEL.put(TEAK_SAPLING,100);
  FireBlock fire=(FireBlock)Blocks.FIRE;
  for(Block b:List.of(TEAK_LOG,TEAK_WOOD,STRIPPED_TEAK_LOG,STRIPPED_TEAK_WOOD)) fire.setFlammable(b,5,5);
  for(Block b:List.of(TEAK_PLANKS,WEATHERED_TEAK_PLANKS,TEAK_STAIRS,TEAK_SLABS,TEAK_FENCE,TEAK_FENCE_GATE)) fire.setFlammable(b,5,20);
  fire.setFlammable(TEAK_LEAVES,30,60);
  ComposterBlock.COMPOSTABLES.put(TEAK_LEAVES.asItem(),0.3F); ComposterBlock.COMPOSTABLES.put(TEAK_SAPLING.asItem(),0.3F);
  MinecraftForge.EVENT_BUS.addListener((FurnaceFuelBurnTimeEvent event)-> {
   Block b=Block.byItem(event.getItemStack().getItem()); if(FUEL.containsKey(b)) event.setBurnTime(FUEL.get(b));
  });
  var strippables=new java.util.HashMap<>(net.minecraft.world.item.AxeItem.STRIPPABLES);
  strippables.put(TEAK_LOG,STRIPPED_TEAK_LOG); strippables.put(TEAK_WOOD,STRIPPED_TEAK_WOOD);
  net.minecraft.world.item.AxeItem.STRIPPABLES=com.google.common.collect.ImmutableMap.copyOf(strippables);
 }
}
