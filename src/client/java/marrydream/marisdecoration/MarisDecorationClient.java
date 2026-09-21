package marrydream.marisdecoration;

import marrydream.marisdecoration.client.CopycatGuardrailModel;
import marrydream.marisdecoration.client.LayeredCopycatBoardModel;
import marrydream.marisdecoration.client.tooltip.MarisTooltip;
import marrydream.marisdecoration.client.tooltip.MarisTooltip.MarisCharacteristic;
import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;

public class MarisDecorationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        // 如果方块一些部分是透明的（例如玻璃、树苗、门），避免贴图上的透明部分变成黑色
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.TEAK_TRAPDOOR, RenderLayer.getCutout() );
        // 如果方块一些部分的材质是半透明的，例如玻璃
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.CYAN_GLASS_STEEL_TEAK_COMPONENT_WALL, RenderLayer.getTranslucent() );
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.CYAN_GLASS_ROOF_STEEL_TEAK_COMPONENT_WALL, RenderLayer.getTranslucent() );
        BlockRenderLayerMap.INSTANCE.putBlock( ModBlock.CYAN_ROOF_STEEL_TRIM_CYAN_WINDOW_WALL, RenderLayer.getTranslucent() );

        // 说明文案走 Create 的 TooltipModifier 注册表：Create 的客户端事件会统一把它应用到物品上，
        // 所以我们不用自己挂 tooltip 回调。这里只声明「这个方块有哪几条特性」——
        // 标题取自共用的特性枚举，说明文字按特性名从语言文件里取，顺序就是这里的书写顺序。
        MarisTooltip.register( ModBlock.COPYCAT_GUARDRAIL.asItem(),
                MarisCharacteristic.CAMOUFLAGE,
                MarisCharacteristic.COMPOSITE_STATE,
                MarisCharacteristic.SEGMENT_CAMOUFLAGE,
                MarisCharacteristic.ADJUSTABLE_STATE );

        MarisTooltip.register( ModBlock.LAYERED_COPYCAT_BOARD.asItem(),
                MarisCharacteristic.CAMOUFLAGE,
                MarisCharacteristic.COMPOSITE_STATE,
                MarisCharacteristic.SEGMENT_CAMOUFLAGE,
                MarisCharacteristic.ADJUSTABLE_STATE );

        // copycat_guardrail 的几何由模板模型描述（每个方向一个单面模型，按角柱归属规则
        // 组合成 16 个变体），真正的贴图在渲染时根据方块实体里的伪装材质动态替换。
        //
        // 注意匹配条件：方块模型在 AfterBake 阶段的 id **不是模型文件路径**，而是
        // blockstate 变体位置，形如 maris-decoration:copycat_guardrail#north=true,...。
        // Create 自己的 ModelSwapper 也是按 BlockModelShaper.stateToModelLocation 建表匹配的，
        // 这里沿用同一套判定。
        ModelLoadingPlugin.register( context -> context.modifyModelAfterBake().register(
                ModelModifier.WRAP_PHASE,
                ( model, ctx ) -> {
                    Identifier id = ctx.id();
                    if ( id == null || !ModInfo.MOD_ID.equals( id.getNamespace() ) ) {
                        return model;
                    }
                    String path = id.getPath();
                    boolean variantModel = CopycatGuardrailBlock.ID_PATH.equals( path );
                    boolean jsonModel = path.startsWith( "block/copycat_guardrail/" );
                    if ( !variantModel && !jsonModel ) {
                        return model;
                    }
                    // 物品模型（变体的 inventory、以及它依赖的 block/.../item）必须保持静态：
                    // 物品渲染没有方块实体，走动态模型没有意义还容易出问题。
                    if ( path.endsWith( "/item" ) ) {
                        return model;
                    }
                    if ( id instanceof ModelIdentifier modelId && "inventory".equals( modelId.getVariant() ) ) {
                        return model;
                    }
                    // 变体模型与其依赖的 JSON 模型都可能命中，避免嵌套包装
                    if ( model instanceof CopycatGuardrailModel ) {
                        return model;
                    }
                    if ( !LOGGED_WRAP ) {
                        LOGGED_WRAP = true;
                        // 只打一次：确认模型匹配条件写对了。这行不出现就说明 id 判定仍然不匹配。
                        MarisDecoration.LOGGER.info( "[copycat_guardrail] 已包装动态模型，首个匹配 id = {}", id );
                    }
                    return new CopycatGuardrailModel( model );
                }
        ) );

        // layered_copycat_board 走同一套路子：blockstate 的全部变体都指向 minecraft:block/air，
        // 真正的几何在渲染时按方块实体的占用掩码 / 窗 / 角归属动态发射。
        // 匹配条件同样是「blockstate 变体位置」而不是模型文件路径，理由见上面那段注释。
        ModelLoadingPlugin.register( context -> context.modifyModelAfterBake().register(
                ModelModifier.WRAP_PHASE,
                ( model, ctx ) -> {
                    Identifier id = ctx.id();
                    if ( id == null || !ModInfo.MOD_ID.equals( id.getNamespace() ) ) {
                        return model;
                    }
                    if ( !LayeredCopycatBoardBlock.ID_PATH.equals( id.getPath() ) ) {
                        return model;
                    }
                    // 物品模型必须保持静态：物品渲染没有方块实体，走动态模型没有意义还容易出问题。
                    if ( id instanceof ModelIdentifier modelId && "inventory".equals( modelId.getVariant() ) ) {
                        return model;
                    }
                    if ( model instanceof LayeredCopycatBoardModel ) {
                        return model;
                    }
                    if ( !LOGGED_BOARD_WRAP ) {
                        LOGGED_BOARD_WRAP = true;
                        MarisDecoration.LOGGER.info( "[layered_copycat_board] 已包装动态模型，首个匹配 id = {}", id );
                    }
                    return new LayeredCopycatBoardModel( model );
                }
        ) );
	}

    private static volatile boolean LOGGED_WRAP = false;
    private static volatile boolean LOGGED_BOARD_WRAP = false;
}
