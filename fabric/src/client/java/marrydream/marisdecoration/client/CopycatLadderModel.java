package marrydream.marisdecoration.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.model.BakedModelHelper;
import io.github.fabricators_of_create.porting_lib.models.CustomParticleIconModel;
import marrydream.marisdecoration.block.CopycatLadderBlockEntity.RenderData;
import marrydream.marisdecoration.block.CopycatSteelFixedLadderBlock;
import marrydream.marisdecoration.block.VerticalLadderBlock;
import marrydream.marisdecoration.block.enums.PropLadderShape;
import marrydream.marisdecoration.block.utils.BoardFaceCulling;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import marrydream.marisdecoration.init.ModBlock;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Dynamic material model for both copycat ladders. Geometry is shared with hit testing. */
public class CopycatLadderModel extends ForwardingBakedModel implements CustomParticleIconModel {
    private static final BlockState UNPAINTED = AllBlocks.COPYCAT_BASE.getDefaultState();
    private static final RenderData EMPTY = new RenderData(Map.of());
    private final Map<BlendMode, RenderMaterial> renderMaterials = new ConcurrentHashMap<>(4);
    private final boolean fixed;

    public CopycatLadderModel(BakedModel wrapped, boolean fixed) {
        this.wrapped = wrapped;
        this.fixed = fixed;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                               Supplier<RandomSource> randomSupplier, RenderContext context) {
        Object raw = view.getBlockEntityRenderData(pos);
        RenderData data = raw instanceof RenderData value ? value : EMPTY;
        boolean fixedState = state.getBlock() instanceof CopycatSteelFixedLadderBlock;
        Map<String, List<AABB>> boxes = fixedState
                ? CopycatLadderParts.fixedBoxes(state) : CopycatLadderParts.verticalBoxes(state);
        Map<String, List<AABB>> defaultUvBoxes = fixedState
                ? CopycatLadderParts.fixedDefaultUvBoxes(state) : CopycatLadderParts.verticalDefaultUvBoxes(state);

        Map<MaterialKey, List<RenderPart>> grouped = new LinkedHashMap<>();
        boxes.forEach((slot, list) -> {
            BlockState stored = data.materials().get(slot);
            boolean defaultMaterial = stored == null || stored.isAir();
            BlockState material = defaultMaterial ? UNPAINTED : stored;
            List<RenderPart> parts = grouped.computeIfAbsent(new MaterialKey(material, defaultMaterial), unused -> new ArrayList<>());
            List<AABB> samples = defaultUvBoxes.get(slot);
            for (int i = 0; i < list.size(); i++)
                parts.add(new RenderPart(list.get(i), defaultMaterial ? samples.get(i) : null));
        });

        emit(view, pos, randomSupplier, context, grouped, true, null);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        BlockState state = (fixed ? ModBlock.COPYCAT_STEEL_FIXED_LADDER : ModBlock.COPYCAT_STEEL_VERTICAL_LADDER)
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                        net.minecraft.core.Direction.WEST);
        if (!fixed) state = state.setValue(VerticalLadderBlock.SHAPE, PropLadderShape.NORMAL);
        Map<String, List<AABB>> boxes = fixed ? CopycatLadderParts.fixedBoxes(state) : CopycatLadderParts.verticalBoxes(state);
        Map<String, List<AABB>> samples = fixed ? CopycatLadderParts.fixedDefaultUvBoxes(state)
                : CopycatLadderParts.verticalDefaultUvBoxes(state);
        List<RenderPart> parts = new ArrayList<>();
        boxes.forEach((slot, list) -> {
            List<AABB> uv = samples.get(slot);
            for (int i = 0; i < list.size(); i++) parts.add(new RenderPart(list.get(i), uv.get(i)));
        });
        emit(null, BlockPos.ZERO, randomSupplier, context,
                Map.of(new MaterialKey(UNPAINTED, true), parts), false, stack);
    }

    private void emit(@Nullable BlockAndTintGetter view, BlockPos pos, Supplier<RandomSource> randomSupplier,
                      RenderContext context, Map<MaterialKey, List<RenderPart>> grouped,
                      boolean blockRender, @Nullable ItemStack stack) {

        SpriteFinder sprites = SpriteFinder.get(Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS));
        MeshBuilder mesh = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        QuadEmitter emitter = mesh.getEmitter();
        float[] vertices = new float[BoardFaceCulling.VERTEX_FLOATS];

        grouped.forEach((key, parts) -> {
            BlockState material = key.material();
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
            RenderMaterial blend = renderMaterial(material);
            context.pushTransform(quad -> {
                if (quad.material().blendMode() == BlendMode.DEFAULT) quad.material(blend);
                applyTint(quad, material, view, pos);
                for (int vertex = 0; vertex < 4; vertex++) {
                    vertices[vertex * 3] = quad.x(vertex);
                    vertices[vertex * 3 + 1] = quad.y(vertex);
                    vertices[vertex * 3 + 2] = quad.z(vertex);
                }
                int axis = BoardFaceCulling.quadPlaneAxis(vertices);
                if (axis < 0) return false;
                UvProjection projection = key.defaultMaterial() ? UvProjection.from(quad, axis) : null;
                for (RenderPart part : parts) {
                    AABB box = part.box();
                    emitter.copyFrom(quad);
                    BakedModelHelper.cropAndMove(emitter, sprites.find(emitter), box, Vec3.ZERO);
                    if (projection != null) projection.remap(emitter, box, part.defaultUvBox());
                    emitter.cullFace(blockRender ? BoardFaceCulling.boxCullFace(axis, vertices[axis], box) : null);
                    emitter.emit();
                }
                return false;
            });
            if (blockRender) model.emitBlockQuads(view, material, pos, randomSupplier, context);
            else model.emitItemQuads(stack, randomSupplier, context);
            context.popTransform();
        });
        mesh.build().outputTo(context.getEmitter());
    }

    private record MaterialKey(BlockState material, boolean defaultMaterial) {
    }

    private record RenderPart(AABB box, @Nullable AABB defaultUvBox) {
    }

    /** Preserves source orientation and pixel scale while moving default-texture sampling to an edge. */
    private record UvProjection(int firstAxis, int secondAxis,
                                float uS, float uT, float uC, float vS, float vT, float vC) {
        static UvProjection from(MutableQuadView quad, int planeAxis) {
            int first = planeAxis == 0 ? 1 : 0;
            int second = planeAxis == 2 ? 1 : 2;
            if (planeAxis == 1) second = 2;

            for (int b = 1; b < 3; b++) {
                for (int c = b + 1; c < 4; c++) {
                    float s0 = coordinate(quad, 0, first), t0 = coordinate(quad, 0, second);
                    float sb = coordinate(quad, b, first), tb = coordinate(quad, b, second);
                    float sc = coordinate(quad, c, first), tc = coordinate(quad, c, second);
                    float determinant = (sb - s0) * (tc - t0) - (sc - s0) * (tb - t0);
                    if (Math.abs(determinant) < 1.0e-6f) continue;
                    float[] u = affine(s0, t0, quad.u(0), sb, tb, quad.u(b), sc, tc, quad.u(c), determinant);
                    float[] v = affine(s0, t0, quad.v(0), sb, tb, quad.v(b), sc, tc, quad.v(c), determinant);
                    return new UvProjection(first, second, u[0], u[1], u[2], v[0], v[1], v[2]);
                }
            }
            return new UvProjection(first, second, 0, 0, quad.u(0), 0, 0, quad.v(0));
        }

        void remap(QuadEmitter quad, AABB geometry, AABB sample) {
            double minS = boxMin(geometry, firstAxis), minT = boxMin(geometry, secondAxis);
            double sizeS = boxMax(geometry, firstAxis) - minS, sizeT = boxMax(geometry, secondAxis) - minT;
            if (sizeS <= 0 || sizeT <= 0) return;
            for (int vertex = 0; vertex < 4; vertex++) {
                float s = (float) (boxMin(sample, firstAxis)
                        + (coordinate(quad, vertex, firstAxis) - minS) / sizeS
                        * (boxMax(sample, firstAxis) - boxMin(sample, firstAxis)));
                float t = (float) (boxMin(sample, secondAxis)
                        + (coordinate(quad, vertex, secondAxis) - minT) / sizeT
                        * (boxMax(sample, secondAxis) - boxMin(sample, secondAxis)));
                quad.uv(vertex, uS * s + uT * t + uC, vS * s + vT * t + vC);
            }
        }

        private static float[] affine(float s0, float t0, float q0, float s1, float t1, float q1,
                                      float s2, float t2, float q2, float determinant) {
            float a = ((q1 - q0) * (t2 - t0) - (q2 - q0) * (t1 - t0)) / determinant;
            float b = ((s1 - s0) * (q2 - q0) - (s2 - s0) * (q1 - q0)) / determinant;
            return new float[]{a, b, q0 - a * s0 - b * t0};
        }

        private static float coordinate(MutableQuadView quad, int vertex, int axis) {
            return switch (axis) {
                case 0 -> quad.x(vertex);
                case 1 -> quad.y(vertex);
                default -> quad.z(vertex);
            };
        }

        private static double boxMin(AABB box, int axis) {
            return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
        }

        private static double boxMax(AABB box, int axis) {
            return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
        }
    }

    private RenderMaterial renderMaterial(BlockState state) {
        BlendMode mode = BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(state));
        return renderMaterials.computeIfAbsent(mode,
                value -> RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(value).find());
    }

    private static void applyTint(MutableQuadView quad, BlockState material, BlockAndTintGetter view, BlockPos pos) {
        int index = quad.colorIndex();
        if (index < 0) return;
        int tint = Minecraft.getInstance().getBlockColors().getColor(material, view, pos, index);
        if (tint != -1) {
            for (int vertex = 0; vertex < 4; vertex++) quad.color(vertex, multiplyRgb(quad.color(vertex), tint));
        }
        quad.colorIndex(-1);
    }

    private static int multiplyRgb(int color, int tint) {
        int a = color >>> 24 & 255;
        int r = ((color >> 16 & 255) * (tint >> 16 & 255)) / 255;
        int g = ((color >> 8 & 255) * (tint >> 8 & 255)) / 255;
        int b = ((color & 255) * (tint & 255)) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@Nullable Object data) {
        if (data instanceof RenderData renderData) {
            for (BlockState state : renderData.materials().values()) {
                if (!state.isAir()) return particle(state);
            }
        }
        return particle(UNPAINTED);
    }

    private static TextureAtlasSprite particle(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        TextureAtlasSprite sprite = model == null ? null : model.getParticleIcon();
        return sprite != null ? sprite : Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(new ResourceLocation("create", "block/copycat_base"));
    }
}
