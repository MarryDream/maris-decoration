package marrydream.marisdecoration.client;

import com.simibubi.create.foundation.model.BakedModelHelper;
import io.github.fabricators_of_create.porting_lib.models.CustomParticleIconModel;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity.Materials;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code copycat_guardrail} 的动态模型。
 *
 * <p>模板模型（{@code models/block/copycat_guardrail/*.json}）只描述几何形状，其中
 * {@code #column} 与 {@code #row} 指向两张<b>占位</b>贴图。渲染时读方块实体里的两个伪装
 * 材质，把占位贴图替换成材质对应的贴图——这一步直接复用 Create 的
 * {@link BakedModelHelper#generateModel}，它顺带处理剔除面与粒子图标。
 *
 * <p>之所以必须用两张<i>不同</i>的占位贴图，是因为模板本身没有语义信息，只有靠贴图
 * 身份才能在遍历四边形时区分「柱」与「横梁」，进而分别套用两个材质槽。
 */
public class CopycatGuardrailModel extends ForwardingBakedModel implements CustomParticleIconModel {

    /** 占位贴图，必须与 tools/gen-copycat-guardrail.mjs 里的 COLUMN_MARKER / ROW_MARKER 一致。 */
    private static final Identifier COLUMN_MARKER = new Identifier("maris-decoration", "block/steel_block");
    private static final Identifier ROW_MARKER = new Identifier("maris-decoration", "block/black_steel_block");
    /** 未伪装时的默认外观，与 Create 的 copycat 保持一致。 */
    private static final Identifier DEFAULT_TEXTURE = new Identifier("create", "block/copycat_base");

    private static final Materials UNPAINTED = new Materials(
            CopycatGuardrailBlockEntity.NO_MATERIAL, CopycatGuardrailBlockEntity.NO_MATERIAL);

    private final Map<Materials, BakedModel> cache = new ConcurrentHashMap<>();

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
        Materials materials = readMaterials(blockView, pos);
        if (!DIAGNOSED) {
            DIAGNOSED = true;
            // 只打一次：用于确认「模型包装生效」以及「伪装材质是否同步到了客户端」。
            // 若这行根本不出现，说明模型包装没挂上；若出现但 materials 两栏都是 air，
            // 说明是方块实体同步的问题。
            MarisDecoration.LOGGER.info("[copycat_guardrail] 模型包装已生效，渲染数据 = {}", materials);
        }
        resolve(materials).emitBlockQuads(blockView, state, pos, randomSupplier, context);
    }

    private static volatile boolean DIAGNOSED = false;

    /**
     * 读取方块实体里的伪装材质。
     *
     * <p>{@code BlockView} 已通过 Loom 的接口注入实现了 {@code FabricBlockView}，
     * 所以这里可以直接调用 {@code getBlockEntityRenderData}。
     */
    @Nullable
    private static Materials readMaterials(BlockRenderView view, BlockPos pos) {
        Object data = view.getBlockEntityRenderData(pos);
        if (data instanceof Materials materials) {
            return materials;
        }
        return null;
    }

    private BakedModel resolve(@Nullable Materials materials) {
        return cache.computeIfAbsent(materials == null ? UNPAINTED : materials, this::repaint);
    }

    private BakedModel repaint(Materials materials) {
        Sprite columnSprite = spriteFor(materials.column());
        Sprite rowSprite = spriteFor(materials.row());
        Sprite columnMarker = atlas().getSprite(COLUMN_MARKER);
        Sprite rowMarker = atlas().getSprite(ROW_MARKER);
        return BakedModelHelper.generateModel(wrapped, sprite -> {
            if (sprite == columnMarker) {
                return columnSprite;
            }
            if (sprite == rowMarker) {
                return rowSprite;
            }
            return null;
        });
    }

    /** 取伪装材质的贴图；没有材质时回退到 Create 的默认伪装贴图。 */
    private static Sprite spriteFor(BlockState state) {
        if (state.isAir()) {
            return atlas().getSprite(DEFAULT_TEXTURE);
        }
        BlockRenderManager renderManager = MinecraftClient.getInstance().getBlockRenderManager();
        BakedModel model = renderManager.getModel(state);
        if (model == null) {
            return atlas().getSprite(DEFAULT_TEXTURE);
        }
        Sprite icon = model.getParticleSprite();
        if (icon != null) {
            return icon;
        }
        List<BakedQuad> quads = model.getQuads(state, Direction.NORTH, Random.create(42L));
        if (!quads.isEmpty()) {
            return quads.get(0).getSprite();
        }
        return atlas().getSprite(DEFAULT_TEXTURE);
    }

    private static SpriteAtlasTexture atlas() {
        return MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    }

    /**
     * 破坏粒子跟随伪装材质，而不是模板里的占位贴图。
     *
     * <p>{@code CustomParticleIconModel} 来自 Porting Lib（Create 传递引入），
     * 不是 Fabric API 的接口。
     */
    @Override
    public Sprite getParticleIcon(@Nullable Object data) {
        if (data instanceof Materials materials && !materials.column().isAir()) {
            return spriteFor(materials.column());
        }
        return wrapped.getParticleSprite();
    }
}
