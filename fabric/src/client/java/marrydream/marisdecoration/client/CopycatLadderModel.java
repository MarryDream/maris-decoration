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
                UvProjection projection = key.defaultMaterial() ? UvProjection.from(new FabricQuadCoordinates(quad), axis) : null;
                for (RenderPart part : parts) {
                    AABB box = part.box();
                    emitter.copyFrom(quad);
                    BakedModelHelper.cropAndMove(emitter, sprites.find(emitter), box, Vec3.ZERO);
                    if (projection != null) projection.remap(new FabricQuadCoordinates(emitter), box, part.defaultUvBox());
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
