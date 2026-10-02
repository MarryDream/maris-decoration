package marrydream.marisdecoration.client;

import marrydream.marisdecoration.init.*;
import marrydream.marisdecoration.client.tooltip.MarisTooltip;
import marrydream.marisdecoration.client.tooltip.MarisTooltip.MarisCharacteristic;
import marrydream.marisdecoration.placement.client.PlacerClientHooks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.FoliageColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=ModInfo.MOD_ID, bus=Mod.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ForgeClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        SteelPlugDoorPartialModels.init();
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(ModBlockEntity.STEEL_PLUG_DOOR, SteelPlugDoorRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlock.TEAK_LEAVES, RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlock.TEAK_SAPLING, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlock.TEAK_TRAPDOOR, RenderType.cutout());
            PlacerClientHooks.init();
            if(Boolean.getBoolean("maris.forge.clientcheck") && marrydream.marisdecoration.platform.Platform.isDevelopmentEnvironment()) ForgeClientCheck.register();
            if(Boolean.getBoolean("maris.teak.clientcheck") && marrydream.marisdecoration.platform.Platform.isDevelopmentEnvironment()) {
                TeakClientCheck.register();
                ForgeWorldCheck.register();
            }
            for (var block : java.util.List.of(ModBlock.COPYCAT_GUARDRAIL, ModBlock.LAYERED_COPYCAT_BOARD))
                MarisTooltip.register(block.asItem(), MarisCharacteristic.CAMOUFLAGE, MarisCharacteristic.COMPOSITE_STATE,
                        MarisCharacteristic.SEGMENT_CAMOUFLAGE, MarisCharacteristic.ADJUSTABLE_STATE);
            MarisTooltip.register(ModBlock.COPYCAT_STEEL_FIXED_LADDER.asItem(), MarisCharacteristic.CAMOUFLAGE);
            MarisTooltip.register(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.asItem(), MarisCharacteristic.CAMOUFLAGE,
                    MarisCharacteristic.SEGMENT_CAMOUFLAGE);
        });
    }
    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tint) -> world != null && pos != null
                ? BiomeColors.getAverageFoliageColor(world,pos) : FoliageColor.getDefaultColor(), ModBlock.TEAK_LEAVES);
    }
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack,tint)->FoliageColor.getDefaultColor(), ModBlock.TEAK_LEAVES);
    }
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
        int[] count={0};
        event.getModels().replaceAll((id, model) -> {
            if(!id.getNamespace().equals(ModInfo.NAMESPACE)) return model;
            boolean inventory=id instanceof ModelResourceLocation m && m.getVariant().equals("inventory");
            ForgeCopycatModel.Kind kind=switch(id.getPath()) {
                case "copycat_guardrail" -> inventory ? null : ForgeCopycatModel.Kind.GUARDRAIL;
                case "layered_copycat_board" -> inventory ? null : ForgeCopycatModel.Kind.BOARD;
                case "steel_plug_door" -> inventory ? null : ForgeCopycatModel.Kind.DOOR;
                case "copycat_steel_fixed_ladder" -> ForgeCopycatModel.Kind.FIXED_LADDER;
                case "copycat_steel_vertical_ladder" -> ForgeCopycatModel.Kind.VERTICAL_LADDER;
                default -> null;
            };
            if(kind==null || model instanceof ForgeCopycatModel) return model;
            count[0]++;
            return new ForgeCopycatModel(model,kind);
        });
        marrydream.marisdecoration.MarisDecoration.LOGGER.info("Forge dynamic model wrappers installed: {}",count[0]);
    }
}
