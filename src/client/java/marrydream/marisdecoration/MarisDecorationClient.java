package marrydream.marisdecoration;

import marrydream.marisdecoration.init.ModBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

public class MarisDecorationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        // 如果方块一些部分是透明的（例如玻璃、树苗、门），避免贴图上的透明部分变成黑色
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.TEAK_TRAPDOOR, RenderLayer.getCutout() );
        // 如果方块一些部分的材质是半透明的，例如玻璃
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL, RenderLayer.getTranslucent() );
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.CYAN_GLASS_ROOF_STEEL_TEAK_COMPONENT_WALL, RenderLayer.getTranslucent() );
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.STEEL_TRIM_CYAN_GLASS_WINDOW, RenderLayer.getTranslucent() );
	}
}