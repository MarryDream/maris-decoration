package marrydream.marisdecoration.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.model.BakedModelHelper;
import io.github.fabricators_of_create.porting_lib.models.CustomParticleIconModel;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity.RenderData;
import marrydream.marisdecoration.block.utils.BoardFaceCulling;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code copycat_guardrail} 的动态模型。
 *
 * <p>渲染方式与 Create 的伪装板、Create: Copycats+ 一致：<b>把伪装材质方块自己的模型发射出来，
 * 再逐面裁剪进护栏的几何体</b>（{@link BakedModelHelper#cropAndMove}）。
 *
 * <p>为什么必须这么做：把一个材质模型的 6 个面分别裁剪进某个盒子，恰好得到那个盒子的 6 个面，
 * 而每面用的仍是材质对应那一面的贴图——于是材质的朝向、纹理方向都会如实体现。
 * 早先的实现是"给每个槽位取一张贴图然后拉伸铺满"，那样<b>任何只改朝向的材质都看不出区别</b>
 * （原木的 X/Y/Z 轴、楼梯朝向、观察者朝向的粒子贴图完全相同），
 * 右键旋转材质时视觉上不会有任何变化。
 *
 * <p>几何体来自 {@link GuardrailParts}，按槽位键分组；同一种材质的所有盒子共用一次模型发射。
 */
public class CopycatGuardrailModel extends ForwardingBakedModel implements CustomParticleIconModel {

    /** 某个槽位没有材质时用的底材，就是 Create 伪装板的底材。 */
    private static final BlockState UNPAINTED = AllBlocks.COPYCAT_BASE.getDefaultState();

    public CopycatGuardrailModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() {
        // 必须走 Fabric 的渲染路径，否则 Indium/Sodium 不会调用 emitBlockQuads。
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos,
                               Supplier<RandomSource> randomSupplier, RenderContext context) {
        RenderData data = readRenderData(blockView, pos);
        // 被细工凿藏起来的柱子在这里就被剔掉，模型与柱子状态天然一致
        Map<String, List<AABB>> boxes = GuardrailParts.boxesByKey(state, data.hiddenColumns());
        if (boxes.isEmpty()) {
            return;
        }

        Map<String, BlockState> materials = data.materials();
        if (!DIAGNOSED) {
            DIAGNOSED = true;
            // 只打一次：用于确认「模型包装生效」以及「伪装材质是否同步到了客户端」。
            MarisDecoration.LOGGER.info("[copycat_guardrail] 模型包装已生效，渲染数据 = {}", materials);
        }

        // 同一种材质的所有几何体合成一组，每组只发射一次材质模型
        Map<MaterialKey, List<RenderPart>> grouped = new LinkedHashMap<>();
        boxes.forEach((key, list) -> {
            BlockState stored = materials.get(key);
            boolean defaultMaterial = stored == null || stored.isAir();
            BlockState material = defaultMaterial ? UNPAINTED : stored;
            List<RenderPart> parts = grouped.computeIfAbsent(
                    new MaterialKey(material, defaultMaterial), unused -> new ArrayList<>());
            boolean defaultRail = defaultMaterial && key.endsWith("_row");
            for (AABB box : list)
                parts.add(new RenderPart(box, defaultRail ? DefaultMaterialCrop.withTopSample(box) : null));
        });

        SpriteFinder spriteFinder = SpriteFinder.get(
                Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS));
        MeshBuilder meshBuilder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        QuadEmitter emitter = meshBuilder.getEmitter();

        // 每次烘焙一个 scratch：区块网格是并行烘焙的，放在实例字段上会跨线程打架。
        // 一次调用只分配这一次（12 个 float），不是每个 quad / 每个盒子一次。
        float[] vertexScratch = new float[BoardFaceCulling.VERTEX_FLOATS];

        grouped.forEach((key, list) -> {
            BlockState material = key.material();
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
            // 材质自己的 render layer，必须在 quad 被搬进 mesh 之前就换上
            RenderMaterial materialBlendMode = blendModeOf(material);
            // 四边形不能在 transform 里直接发射，所以先收集进 mesh，最后一次性输出
            context.pushTransform(quad -> {
                // 材质模型发出的 quad 都带 BlendMode.DEFAULT，意思是「用本方块注册的那个层」。
                // 本方块注册在默认层，于是草方块/树叶的 alpha 会被当成不透明、玻璃会被当成实心，
                // 表现就是树叶和草方块侧面覆盖层糊成黑块、玻璃丢掉透明。
                // 这里把它换成伪装材质自己 render layer 对应的 blend mode——与 Create 的
                // CopycatModel#MaterialFixer 完全一致，对任何材质通用，不需要按方块特判。
                if (quad.material().blendMode() == BlendMode.DEFAULT) {
                    quad.material(materialBlendMode);
                }
                // tint 也必须跟着 quad 自己的材质走，见 applyMaterialTint
                applyMaterialTint(quad, material, blockView, pos);
                // 源 quad 的四个顶点只跟材质模型有关、与目标盒子无关，所以在盒子循环外取一次。
                // 复用同一个 scratch，避免每个 (quad, box) 组合都分配一次数组。
                for (int vertex = 0; vertex < 4; vertex++) {
                    vertexScratch[vertex * 3] = quad.x(vertex);
                    vertexScratch[vertex * 3 + 1] = quad.y(vertex);
                    vertexScratch[vertex * 3 + 2] = quad.z(vertex);
                }
                for (RenderPart part : list) {
                    AABB box = part.target();
                    // 只保留「垂直于某个轴」的源面：cropAndMove 是逐顶点 clamp，法线不与盒子轴
                    // 平行的源面会被压成一条零面积的线，一个像素都画不出来，直接跳过。
                    int quadAxis = BoardFaceCulling.quadPlaneAxis(vertexScratch);
                    if (quadAxis < 0) {
                        continue;
                    }
                    emitter.copyFrom(quad);
                    if (part.defaultSource() == null)
                        BakedModelHelper.cropAndMove(emitter, spriteFinder.find(emitter), box, Vec3.ZERO);
                    else
                        DefaultMaterialCrop.cropAndMove(emitter, spriteFinder.find(emitter),
                                part.defaultSource(), box);
                    // cullFace 必须按「这张 quad 最终落在盒子的哪一层」重判，见 BoardFaceCulling。
                    // 照抄源材质模型的 cullFace 正是这个方块之前的渲染 bug：一根贴着北边界的横梁，
                    // 它朝南那张内部面仍然带着材质模型的 SOUTH；于是南边挨着完整固体方块时，
                    // 原版会去问「南边那个 BlockPos 是不是实心方块」，把方块内部的面一起剔掉，
                    // 表现就是横梁末端缺面 / 尖角状残片。
                    emitter.cullFace(BoardFaceCulling.boxCullFace(quadAxis,
                            vertexScratch[quadAxis], box));
                    emitter.emit();
                }
                return false;
            });
            model.emitBlockQuads(blockView, material, pos, randomSupplier, context);
            context.popTransform();
        });

        meshBuilder.build().outputTo(context.getEmitter());
    }

    private record MaterialKey(BlockState material, boolean defaultMaterial) {
    }

    private record RenderPart(AABB target, @Nullable AABB defaultSource) {
    }

    /**
     * 伪装材质自己那套 render layer 对应的 {@link RenderMaterial}。
     *
     * <p>照搬 Create 的 {@code CopycatModel.MaterialFixer}：用
     * {@code RenderLayers.getBlockLayer(material)} 取材质真正会被画进哪一个层，再转成 FRAPI 的
     * {@link BlendMode}。草方块与树叶是 {@code cutout_mipped}（草方块侧面的覆盖层贴图带 alpha，
     * 所以原版把它们放在这个层），玻璃是 {@code translucent}——只有跟着材质走才对。
     *
     * <p>结果按 {@link BlendMode} 缓存：它只取决于材质所在的 render layer，而那是 FRAPI 的固定枚举
     * （solid / cutout / cutout_mipped / translucent），所以缓存上限就是这几个，<b>不会</b>随着
     * 见过的材质种类膨胀——刻意没做成 {@code Map<BlockState, RenderMaterial>}。
     *
     * <p>缓存挂在模型实例上而不是 static：模型实例的生命周期就是「一次资源加载」，
     * 不会跨资源包残留，也不引入全局可变状态。区块网格可以并行烘焙，因此用并发映射。
     */
    private final Map<BlendMode, RenderMaterial> renderMaterialsByBlendMode = new ConcurrentHashMap<>(4);

    private RenderMaterial blendModeOf(BlockState material) {
        BlendMode blendMode = BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(material));
        return renderMaterialsByBlendMode.computeIfAbsent(blendMode,
                mode -> RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(mode).find());
    }

    /**
     * 把伪装材质自己的 tint 烘进 quad 的顶点色。
     *
     * <p><b>为什么必须在这里做</b>：tint 不是模型算的，是<b>渲染器</b>按「方块状态对应的
     * {@code BlockColorProvider}」算的——quad 上的 colorIndex 只是提问，答案由方块提供器给。
     * 草方块、树叶的颜色就是这么来的。Create 的解决方式是给伪装方块注册一个提供器
     * （{@code CopycatBlock#wrappedColor}），它从方块实体取出材质、再把问题<b>转发</b>给材质自己的提供器。
     *
     * <p><b>为什么不能照搬</b>：那个提供器的签名只有 {@code (state, world, pos, tintIndex)}，
     * 拿不到「这个 quad 属于哪个部件」。Create 一个伪装方块只有一个材质，所以够用；我们一个方块最多
     * 八份材质（四根横梁按方向 + 四根柱子按角点），方块级提供器根本分不清该用哪一份
     * ——给整块注册一个统一颜色，要么串色、要么全都变成同一种颜色。
     *
     * <p>所以这里把能力下移到 quad 级：每个 quad 都已经知道自己来自哪个 material，
     * 就用那个 material 去问同一个 {@code BlockColors}，把答案乘进顶点色，然后清掉 colorIndex。
     * 这样每个部件各自颜色独立，也<b>不会</b>再被护栏自己的状态算第二次错误 tint。
     *
     * <p>对普通 solid 方块（tint = -1 或没有 colorIndex）这里什么都不做，与之前完全一致。
     */
    private static void applyMaterialTint(MutableQuadView quad, BlockState material,
                                          BlockAndTintGetter blockView, BlockPos pos) {
        int colorIndex = quad.colorIndex();
        if (colorIndex < 0) {
            return;
        }
        // 与 Create 的 WrappedBlockColor 问的是同一个来源：材质自己的颜色提供器
        int tint = Minecraft.getInstance().getBlockColors().getColor(material, blockView, pos, colorIndex);
        if (tint != -1) {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.color(vertex, multiplyRgb(quad.color(vertex), tint));
            }
        }
        // 颜色已经烘进顶点色，清掉 colorIndex，避免渲染器再按护栏自己的状态算一遍
        quad.colorIndex(-1);
    }

    /**
     * 逐通道相乘，保留原顶点色的 alpha。
     *
     * <p>原版 tint 的 alpha 位是 0（草色是 {@code 0x91BD59} 这种 RGB），所以不能连 alpha 一起乘，
     * 否则整个 quad 会变透明。保留顶点色自己的 alpha 最稳妥，也与「乘到原本的顶点颜色上」一致。
     */
    private static int multiplyRgb(int color, int tint) {
        int a = (color >>> 24) & 0xFF;
        int r = (((color >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255;
        int g = (((color >> 8) & 0xFF) * ((tint >> 8) & 0xFF)) / 255;
        int b = ((color & 0xFF) * (tint & 0xFF)) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static volatile boolean DIAGNOSED = false;

    /** 方块实体数据还没同步过来时的回落。 */
    private static final RenderData EMPTY = new RenderData(Map.of(), Set.of());

    /**
     * 读取方块实体交给渲染层的快照：部件→材质，以及被隐藏的柱子。
     *
     * <p>{@code BlockView} 已通过 Loom 的接口注入实现了 {@code FabricBlockView}，
     * 所以这里可以直接调用 {@code getBlockEntityRenderData}。
     */
    private static RenderData readRenderData(BlockAndTintGetter view, BlockPos pos) {
        Object data = view.getBlockEntityRenderData(pos);
        return data instanceof RenderData renderData ? renderData : EMPTY;
    }

    /**
     * 破坏粒子跟随伪装材质。
     *
     * <p>{@code CustomParticleIconModel} 来自 Porting Lib（Create 传递引入），
     * 不是 Fabric API 的接口。
     */
    @Override
    public TextureAtlasSprite getParticleIcon(@Nullable Object data) {
        if (data instanceof RenderData renderData) {
            for (BlockState material : renderData.materials().values()) {
                if (!material.isAir()) {
                    return particleSprite(material);
                }
            }
        }
        return particleSprite(UNPAINTED);
    }

    private static TextureAtlasSprite particleSprite(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        TextureAtlasSprite icon = model == null ? null : model.getParticleIcon();
        return icon != null ? icon : wrappedOrFallback();
    }

    private static TextureAtlasSprite wrappedOrFallback() {
        return Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(new net.minecraft.resources.ResourceLocation("create", "block/copycat_base"));
    }
}
