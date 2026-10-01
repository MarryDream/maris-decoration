package marrydream.marisdecoration;

import marrydream.marisdecoration.client.CopycatGuardrailModel;
import marrydream.marisdecoration.client.CopycatLadderModel;
import marrydream.marisdecoration.client.LayeredCopycatBoardModel;
import marrydream.marisdecoration.client.SteelPlugDoorModel;
import marrydream.marisdecoration.client.SteelPlugDoorPartialModels;
import marrydream.marisdecoration.client.SteelPlugDoorRenderer;
import marrydream.marisdecoration.client.TeakClientCheck;
import marrydream.marisdecoration.client.tooltip.MarisTooltip;
import marrydream.marisdecoration.client.tooltip.MarisTooltip.MarisCharacteristic;
import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatSteelFixedLadderBlock;
import marrydream.marisdecoration.block.CopycatSteelVerticalLadderBlock;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModInfo;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.color.world.FoliageColors;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;

public class MarisDecorationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        SteelPlugDoorPartialModels.init();
        BlockEntityRendererFactories.register(ModBlockEntity.STEEL_PLUG_DOOR, SteelPlugDoorRenderer::new);
        if (Boolean.getBoolean("maris.teak.clientcheck")
                && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            TeakClientCheck.register();
        }
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutoutMipped(), ModBlock.TEAK_LEAVES);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlock.TEAK_SAPLING);
        ColorProviderRegistry.BLOCK.register(
                (state, world, pos, tint) -> world != null && pos != null
                        ? BiomeColors.getFoliageColor(world, pos)
                        : FoliageColors.getDefaultColor(), ModBlock.TEAK_LEAVES);
        ColorProviderRegistry.ITEM.register(
                (stack, tint) -> FoliageColors.getDefaultColor(), ModBlock.TEAK_LEAVES);
        // 伪装放置器：装上「打开配置界面 / 发送配置」这两个客户端钩子，
        // 并注册网络频道让发送合法。必须在物品被使用之前完成——这里就是最早的地方。
        marrydream.marisdecoration.placement.client.PlacerClientHooks.init();

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

        MarisTooltip.register(ModBlock.COPYCAT_STEEL_FIXED_LADDER.asItem(),
                MarisCharacteristic.CAMOUFLAGE);
        MarisTooltip.register(ModBlock.COPYCAT_STEEL_VERTICAL_LADDER.asItem(),
                MarisCharacteristic.CAMOUFLAGE,
                MarisCharacteristic.SEGMENT_CAMOUFLAGE);

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

        // The base steel plug door keeps its existing baked model and appends the optional
        // single-layer roof from the upper-half block entity.
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(
                ModelModifier.WRAP_PHASE,
                (model, ctx) -> {
                    Identifier id = ctx.id();
                    if (id == null || !ModInfo.MOD_ID.equals(id.getNamespace())
                            || !"steel_plug_door".equals(id.getPath())) {
                        return model;
                    }
                    if (id instanceof ModelIdentifier modelId && "inventory".equals(modelId.getVariant())) {
                        return model;
                    }
                    if (!LOGGED_STEEL_DOOR_WRAP) {
                        LOGGED_STEEL_DOOR_WRAP = true;
                        MarisDecoration.LOGGER.info("[steel_plug_door] wrapped optional roof model, first id = {}", id);
                    }
                    return model instanceof SteelPlugDoorModel ? model : new SteelPlugDoorModel(model);
                }
        ));

        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(
                ModelModifier.WRAP_PHASE, (model, ctx) -> {
                    Identifier id = ctx.id();
                    if (id == null || !ModInfo.MOD_ID.equals(id.getNamespace())) return model;
                    String path = id.getPath();
                    if (!CopycatSteelFixedLadderBlock.ID_PATH.equals(path)
                            && !CopycatSteelVerticalLadderBlock.ID_PATH.equals(path)) return model;
                    if (!LOGGED_LADDER_WRAP) {
                        LOGGED_LADDER_WRAP = true;
                        MarisDecoration.LOGGER.info("[copycat_ladders] 已包装动态模型，首个匹配 id = {}", id);
                    }
                    return model instanceof CopycatLadderModel ? model
                            : new CopycatLadderModel(model, CopycatSteelFixedLadderBlock.ID_PATH.equals(path));
                }));
	}

    private static volatile boolean LOGGED_WRAP = false;
    private static volatile boolean LOGGED_BOARD_WRAP = false;
    private static volatile boolean LOGGED_STEEL_DOOR_WRAP = false;
    private static volatile boolean LOGGED_LADDER_WRAP = false;
}
