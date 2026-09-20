package marrydream.marisdecoration.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.model.BakedModelHelper;
import io.github.fabricators_of_create.porting_lib.models.CustomParticleIconModel;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, RenderContext context) {
        Map<String, List<Box>> boxes = GuardrailParts.boxesByKey(state);
        if (boxes.isEmpty()) {
            return;
        }

        Map<String, BlockState> materials = readMaterials(blockView, pos);
        if (!DIAGNOSED) {
            DIAGNOSED = true;
            // 只打一次：用于确认「模型包装生效」以及「伪装材质是否同步到了客户端」。
            MarisDecoration.LOGGER.info("[copycat_guardrail] 模型包装已生效，渲染数据 = {}", materials);
        }

        // 同一种材质的所有几何体合成一组，每组只发射一次材质模型
        Map<BlockState, List<Box>> grouped = new LinkedHashMap<>();
        boxes.forEach((key, list) -> {
            BlockState material = materials.get(key);
            if (material == null || material.isAir()) {
                material = UNPAINTED;
            }
            grouped.computeIfAbsent(material, unused -> new ArrayList<>()).addAll(list);
        });

        SpriteFinder spriteFinder = SpriteFinder.get(
                MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        MeshBuilder meshBuilder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        QuadEmitter emitter = meshBuilder.getEmitter();

        grouped.forEach((material, list) -> {
            BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(material);
            // 四边形不能在 transform 里直接发射，所以先收集进 mesh，最后一次性输出
            context.pushTransform(quad -> {
                for (Box box : list) {
                    emitter.copyFrom(quad);
                    BakedModelHelper.cropAndMove(emitter, spriteFinder.find(emitter), box, Vec3d.ZERO);
                    emitter.emit();
                }
                return false;
            });
            model.emitBlockQuads(blockView, material, pos, randomSupplier, context);
            context.popTransform();
        });

        meshBuilder.build().outputTo(context.getEmitter());
    }

    private static volatile boolean DIAGNOSED = false;

    /**
     * 读取方块实体里的「部件 → 材质」映射。
     *
     * <p>{@code BlockView} 已通过 Loom 的接口注入实现了 {@code FabricBlockView}，
     * 所以这里可以直接调用 {@code getBlockEntityRenderData}。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, BlockState> readMaterials(BlockRenderView view, BlockPos pos) {
        Object data = view.getBlockEntityRenderData(pos);
        if (data instanceof Map<?, ?> map) {
            return (Map<String, BlockState>) map;
        }
        return Map.of();
    }

    /**
     * 破坏粒子跟随伪装材质。
     *
     * <p>{@code CustomParticleIconModel} 来自 Porting Lib（Create 传递引入），
     * 不是 Fabric API 的接口。
     */
    @Override
    public Sprite getParticleIcon(@Nullable Object data) {
        if (data instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof BlockState material && !material.isAir()) {
                    return particleSprite(material);
                }
            }
        }
        return particleSprite(UNPAINTED);
    }

    private static Sprite particleSprite(BlockState state) {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        Sprite icon = model == null ? null : model.getParticleSprite();
        return icon != null ? icon : wrappedOrFallback();
    }

    private static Sprite wrappedOrFallback() {
        return MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                .getSprite(new net.minecraft.util.Identifier("create", "block/copycat_base"));
    }
}
