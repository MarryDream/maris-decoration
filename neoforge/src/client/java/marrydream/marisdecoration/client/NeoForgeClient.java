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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid=ModInfo.MOD_ID, bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class NeoForgeClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        SteelPlugDoorPartialModels.init();
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(ModBlockEntity.STEEL_PLUG_DOOR, SteelPlugDoorRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlock.TEAK_LEAVES, RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlock.TEAK_SAPLING, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlock.TEAK_TRAPDOOR, RenderType.cutout());
            PlacerClientHooks.init();
            if(Boolean.getBoolean("maris.neoforge.clientcheck") && marrydream.marisdecoration.platform.Platform.isDevelopmentEnvironment()) NeoForgeClientCheck.register();
            if(Boolean.getBoolean("maris.teak.clientcheck") && marrydream.marisdecoration.platform.Platform.isDevelopmentEnvironment()) {
                NeoForgeWorldCheck.registerPersistenceCheck();
                TeakClientCheck.register();
                NeoForgeWorldCheck.register();
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
            if(!id.id().getNamespace().equals(ModInfo.NAMESPACE)) return model;
            boolean inventory=id.variant().equals("inventory");
            NeoForgeCopycatModel.Kind kind=switch(id.id().getPath()) {
                case "copycat_guardrail" -> inventory ? null : NeoForgeCopycatModel.Kind.GUARDRAIL;
                case "layered_copycat_board" -> inventory ? null : NeoForgeCopycatModel.Kind.BOARD;
                case "steel_plug_door" -> inventory ? null : NeoForgeCopycatModel.Kind.DOOR;
                case "copycat_steel_fixed_ladder" -> NeoForgeCopycatModel.Kind.FIXED_LADDER;
                case "copycat_steel_vertical_ladder" -> NeoForgeCopycatModel.Kind.VERTICAL_LADDER;
                default -> null;
            };
            if(kind==null || model instanceof NeoForgeCopycatModel) return model;
            count[0]++;
            return new NeoForgeCopycatModel(model,kind);
        });
        marrydream.marisdecoration.MarisDecoration.LOGGER.info("NeoForge dynamic model wrappers installed: {}",count[0]);
    }
}
