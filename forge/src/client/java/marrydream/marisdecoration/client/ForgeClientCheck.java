package marrydream.marisdecoration.client;

import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.block.utils.*;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.platform.ClientPlatform;
import marrydream.marisdecoration.platform.RenderDataBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.client.model.data.ModelData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in development check: real main menu plus dynamic quads, layers and item render passes. */
public final class ForgeClientCheck {
    private static int ticks;
    public static void register() {
        ClientPlatform.onEndTick(client -> {
            if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)
                client.setScreen(new TitleScreen());
            if (!(client.screen instanceof TitleScreen) || ++ticks < 40) return;
            try {
                int quads = verifyModels(client);
                Screenshot.grab(client.gameDirectory,"forge-main-menu.png",client.getMainRenderTarget(),text -> {});
                Files.writeString(Path.of("forge-client-check.txt"),"PASS: Forge main menu reached; dynamic model checks, " + quads + " quads; screenshot captured.\n");
            } catch (Throwable error) {
                error.printStackTrace();
                try { Files.writeString(Path.of("forge-client-check.txt"),"FAIL: "+error); } catch(Exception ignored) {}
            } finally { client.stop(); }
        });
    }

    private static int verifyModels(Minecraft client) {
        int total=0;
        for(Block block:List.of(ModBlock.COPYCAT_GUARDRAIL,ModBlock.LAYERED_COPYCAT_BOARD,
                ModBlock.COPYCAT_STEEL_FIXED_LADDER,ModBlock.COPYCAT_STEEL_VERTICAL_LADDER,ModBlock.STEEL_PLUG_DOOR)) {
            for(BlockState state:block.getStateDefinition().getPossibleStates()) {
                var model=client.getBlockRenderer().getBlockModel(state);
                if(!(model instanceof ForgeCopycatModel)) throw new AssertionError("Model wrapper missing: "+state);
                for(BlockState material:List.of(Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),
                        Blocks.OAK_LOG.defaultBlockState(),Blocks.GLASS.defaultBlockState(),Blocks.OAK_LEAVES.defaultBlockState(),Blocks.GRASS_BLOCK.defaultBlockState())) {
                    Map<String,BlockState> materials=new LinkedHashMap<>();
                    Object snapshot;
                    boolean expectGeometry=true;
                    if(block==ModBlock.COPYCAT_GUARDRAIL) {
                        GuardrailParts.allKeys().forEach(key->materials.put(key,material));
                        snapshot=new CopycatGuardrailBlockEntity.RenderData(materials,Set.of());
                        expectGeometry=!GuardrailParts.boxesByKey(state,Set.of()).isEmpty();
                    } else if(block==ModBlock.LAYERED_COPYCAT_BOARD || block==ModBlock.STEEL_PLUG_DOOR) {
                        LayeredBoardSlots.allMaterialKeys().forEach(key->materials.put(key,material));
                        snapshot=new LayeredCopycatBoardBlockEntity.RenderData(block==ModBlock.STEEL_PLUG_DOOR ? SteelPlugDoorBlockEntity.ROOF_OCCUPANCY : 4095,63,Map.of(),materials);
                    } else {
                        (block==ModBlock.COPYCAT_STEEL_FIXED_LADDER ? CopycatLadderParts.fixedSlots() : CopycatLadderParts.verticalSlots()).forEach(key->materials.put(key,material));
                        snapshot=new CopycatLadderBlockEntity.RenderData(materials);
                    }
                    ModelData data=ModelData.builder().with(RenderDataBlockEntity.SNAPSHOT,snapshot).build();
                    var layers=model.getRenderTypes(state,RandomSource.create(1),data);
                    int count=0;
                    for(var layer:layers.asList()) {
                        List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,RandomSource.create(1),data,layer));
                        for(Direction face:Direction.values()) quads.addAll(model.getQuads(state,face,RandomSource.create(1),data,layer));
                        for(BakedQuad quad:quads) {
                            if(quad.getSprite().contents().name().getPath().equals("missingno")) throw new AssertionError("Missing texture: "+state);
                            int[] packed=quad.getVertices(); int stride=packed.length/4;
                            for(int v=0;v<4;v++) for(int offset:new int[]{0,1,2,4,5})
                                if(!Float.isFinite(Float.intBitsToFloat(packed[v*stride+offset]))) throw new AssertionError("Invalid quad: "+state);
                        }
                        count+=quads.size();
                    }
                    if(expectGeometry && count==0) throw new AssertionError("Empty dynamic geometry: "+state+" material="+material);
                    total+=count;
                }
            }
            if(block==ModBlock.COPYCAT_STEEL_FIXED_LADDER || block==ModBlock.COPYCAT_STEEL_VERTICAL_LADDER) {
                var stack=new net.minecraft.world.item.ItemStack(block);
                var item=client.getItemRenderer().getModel(stack,null,null,0);
                if(!(item instanceof ForgeCopycatModel) || item.getRenderPasses(stack,false).get(0)!=item
                        || item.getQuads(null,null,RandomSource.create(1)).isEmpty()) throw new AssertionError("Missing dynamic ladder item: "+block);
            }
        }
        return total;
    }
}
