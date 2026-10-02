package marrydream.marisdecoration.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.model.BakedModelHelper;
import marrydream.marisdecoration.block.*;
import marrydream.marisdecoration.block.enums.PropLadderShape;
import marrydream.marisdecoration.block.utils.*;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.platform.RenderDataBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

/** Forge model adapter. Geometry, culling, window sampling and ladder UV rules are shared. */
public final class ForgeCopycatModel extends BakedModelWrapper<BakedModel> {
    public enum Kind { GUARDRAIL, BOARD, FIXED_LADDER, VERTICAL_LADDER, DOOR }
    private static final BlockState UNPAINTED = AllBlocks.COPYCAT_BASE.getDefaultState();
    private static final ModelProperty<View> VIEW = new ModelProperty<>();
    private record View(BlockAndTintGetter world, BlockPos pos) {}
    private record Part(BlockState material, AABB target, @Nullable AABB source,
                        @Nullable AABB ladderSample, boolean planar) {}
    private final Kind kind;

    public ForgeCopycatModel(BakedModel wrapped, Kind kind) { super(wrapped); this.kind = kind; }

    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack, boolean fabulous) {
        return List.of(this);
    }
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,
                                               com.mojang.blaze3d.vertex.PoseStack pose, boolean leftHand) {
        originalModel.applyTransform(context, pose, leftHand);
        return this;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
        // Use the immutable BE snapshot supplied by Forge, never read mutable BE maps in getQuads.
        return data.derive().with(VIEW, new View(world, pos.immutable())).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType layer) {
        boolean item = state == null;
        if (item && kind != Kind.FIXED_LADDER && kind != Kind.VERTICAL_LADDER)
            return originalModel.getQuads(null, side, random, data, layer);
        if (item) {
            state = (kind == Kind.FIXED_LADDER ? ModBlock.COPYCAT_STEEL_FIXED_LADDER
                    : ModBlock.COPYCAT_STEEL_VERTICAL_LADDER).defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
            if (kind == Kind.VERTICAL_LADDER) state = state.setValue(VerticalLadderBlock.SHAPE, PropLadderShape.NORMAL);
        }
        List<BakedQuad> result = new ArrayList<>();
        Object snapshot = data.get(RenderDataBlockEntity.SNAPSHOT);
        if (kind == Kind.DOOR) appendFrame(result, state, side, random, data, layer, snapshot);
        View view = data.get(VIEW);
        long sourceSeed = view == null ? 42L : state.getSeed(view.pos());
        // Equal material/source geometry is emitted in one group, preserving source model randomization.
        Map<BlockState, List<Part>> groups = new LinkedHashMap<>();
        for (Part part : parts(state, snapshot))
            groups.computeIfAbsent(part.material(), unused -> new ArrayList<>()).add(part);
        for (var entry : groups.entrySet()) {
            BlockState material = entry.getKey();
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
            ModelData materialData = view == null ? ModelData.EMPTY
                    : model.getModelData(view.world(), view.pos(), material, ModelData.EMPTY);
            if (layer != null && !model.getRenderTypes(material, random, materialData).contains(layer)) continue;
            // Vanilla resets the source model seed for each face. Weighted variants
            // must keep the same selection across all faces of a material model.
            RandomSource sourceRandom = RandomSource.create(sourceSeed);
            List<BakedQuad> sources = new ArrayList<>(model.getQuads(material, null, sourceRandom, materialData, layer));
            for (Direction face : Direction.values()) {
                sourceRandom.setSeed(sourceSeed);
                sources.addAll(model.getQuads(material, face, sourceRandom, materialData, layer));
            }
            for (BakedQuad source : sources) {
                PackedQuad coordinates = new PackedQuad(source.getVertices());
                float[] vertices = coordinates.positions();
                int axis = BoardFaceCulling.quadPlaneAxis(vertices);
                if (axis < 0) continue;
                UvProjection projection = UvProjection.from(coordinates, axis);
                for (Part part : entry.getValue()) {
                    if (part.planar() && !BoardFaceCulling.isParallelTo(vertices, BoardFaceCulling.planeAxis(part.target()))) continue;
                    Direction cull = item ? null : part.planar()
                            ? BoardFaceCulling.boundaryCullFace(part.target())
                            : BoardFaceCulling.boxCullFace(axis, vertices[axis], part.target());
                    if (cull != side) continue;
                    AABB crop = part.source() == null ? part.target() : part.source();
                    Vec3 move = part.source() == null ? Vec3.ZERO : new Vec3(
                            part.target().minX - crop.minX, part.target().minY - crop.minY, part.target().minZ - crop.minZ);
                    int[] packed = BakedModelHelper.cropAndMove(source.getVertices(), source.getSprite(), crop, move);
                    if (part.ladderSample() != null) projection.remap(new PackedQuad(packed), part.target(), part.ladderSample());
                    int tint = source.getTintIndex();
                    if (tint >= 0) {
                        int color = Minecraft.getInstance().getBlockColors().getColor(material,
                                view == null ? null : view.world(), view == null ? null : view.pos(), tint);
                        if (color != -1) multiplyTint(packed, color);
                    }
                    result.add(new BakedQuad(packed, -1, source.getDirection(), source.getSprite(), source.isShade()));
                }
            }
        }
        return result;
    }

    private List<Part> parts(BlockState state, @Nullable Object snapshot) {
        List<Part> result = new ArrayList<>();
        if (kind == Kind.GUARDRAIL) {
            var data = snapshot instanceof CopycatGuardrailBlockEntity.RenderData d ? d
                    : new CopycatGuardrailBlockEntity.RenderData(Map.of(), Set.of());
            GuardrailParts.boxesByKey(state, data.hiddenColumns()).forEach((key, boxes) -> {
                BlockState stored = data.materials().get(key);
                boolean unpainted = stored == null || stored.isAir();
                for (AABB box : boxes) result.add(new Part(unpainted ? UNPAINTED : stored, box,
                        unpainted && key.endsWith("_row") ? DefaultMaterialGeometry.withTopSample(box) : null, null, false));
            });
        } else if (kind == Kind.BOARD || kind == Kind.DOOR) {
            var data = snapshot instanceof LayeredCopycatBoardBlockEntity.RenderData d ? d
                    : LayeredCopycatBoardBlockEntity.emptyRenderData();
            UnaryOperator<AABB> transform = UnaryOperator.identity();
            if (kind == Kind.DOOR) {
                if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                        || data.occupancy() != SteelPlugDoorBlockEntity.ROOF_OCCUPANCY) return result;
                transform = box -> SteelPlugDoorRoof.toWorld(box, state.getValue(DoorBlock.FACING));
            }
            UnaryOperator<AABB> finalTransform = transform;
            LayeredBoardParts.boxesByKey(data.occupancy(), data.windows(), data.junctionOwners()).forEach((key, boxes) -> {
                BlockState stored = data.materials().get(key);
                boolean unpainted = stored == null || stored.isAir();
                var slot = unpainted && key.endsWith(".window") ? LayeredBoardParts.Slot.parse(key) : null;
                for (AABB box : boxes) {
                    if (slot == null) result.add(new Part(unpainted ? UNPAINTED : stored, finalTransform.apply(box), null, null, true));
                    else for (AABB piece : DefaultMaterialGeometry.splitWindowPanel(slot.face(), box)) {
                        AABB target = finalTransform.apply(piece);
                        result.add(new Part(UNPAINTED, target, DefaultMaterialGeometry.windowPanelSource(slot.face(), target), null, true));
                    }
                }
            });
        } else {
            var data = snapshot instanceof CopycatLadderBlockEntity.RenderData d ? d
                    : new CopycatLadderBlockEntity.RenderData(Map.of());
            boolean fixed = kind == Kind.FIXED_LADDER;
            var boxes = fixed ? CopycatLadderParts.fixedBoxes(state) : CopycatLadderParts.verticalBoxes(state);
            var samples = fixed ? CopycatLadderParts.fixedDefaultUvBoxes(state) : CopycatLadderParts.verticalDefaultUvBoxes(state);
            boxes.forEach((key, list) -> {
                BlockState stored = data.materials().get(key);
                boolean unpainted = stored == null || stored.isAir();
                for (int i = 0; i < list.size(); i++) result.add(new Part(unpainted ? UNPAINTED : stored,
                        list.get(i), null, unpainted ? samples.get(key).get(i) : null, false));
            });
        }
        return result;
    }

    private void appendFrame(List<BakedQuad> result, BlockState state, Direction side, RandomSource random,
                             ModelData data, RenderType layer, Object snapshot) {
        boolean roof = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && snapshot instanceof LayeredCopycatBoardBlockEntity.RenderData d
                && d.occupancy() == SteelPlugDoorBlockEntity.ROOF_OCCUPANCY;
        AABB frame = SteelPlugDoorAnimation.fixedFrameBox(state.getValue(DoorBlock.FACING), state.getValue(DoorBlock.HALF));
        for (BakedQuad quad : originalModel.getQuads(state, side, random, data, layer)) {
            PackedQuad p = new PackedQuad(quad.getVertices());
            boolean fixed = true;
            for (int v = 0; v < 4; v++) fixed &= SteelPlugDoorAnimation.contains(frame, p.x(v), p.y(v), p.z(v));
            if (fixed && !SteelPlugDoorAnimation.isInnerFrameCap(state.getValue(DoorBlock.HALF), p.y(0), p.y(1), p.y(2), p.y(3))
                    && (!roof || !SteelPlugDoorRoof.isCoveredDoorQuad(p.y(0), p.y(1), p.y(2), p.y(3)))) result.add(quad);
        }
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        List<ChunkRenderTypeSet> layers = new ArrayList<>();
        if (kind == Kind.DOOR) layers.add(originalModel.getRenderTypes(state, random, data));
        View view = data.get(VIEW);
        for (BlockState material : parts(state, data.get(RenderDataBlockEntity.SNAPSHOT)).stream().map(Part::material).distinct().toList()) {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
            ModelData materialData = view == null ? ModelData.EMPTY : model.getModelData(view.world(), view.pos(), material, ModelData.EMPTY);
            layers.add(model.getRenderTypes(material, random, materialData));
        }
        return ChunkRenderTypeSet.union(layers);
    }

    @Override public TextureAtlasSprite getParticleIcon(ModelData data) {
        Object snapshot = data.get(RenderDataBlockEntity.SNAPSHOT);
        Map<String,BlockState> materials = snapshot instanceof CopycatGuardrailBlockEntity.RenderData d ? d.materials()
                : snapshot instanceof LayeredCopycatBoardBlockEntity.RenderData d ? d.materials()
                : snapshot instanceof CopycatLadderBlockEntity.RenderData d ? d.materials() : Map.of();
        for (BlockState material : materials.values()) if (!material.isAir())
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getParticleIcon();
        return kind == Kind.DOOR ? originalModel.getParticleIcon(data)
                : Minecraft.getInstance().getBlockRenderer().getBlockModel(UNPAINTED).getParticleIcon();
    }

    private static void multiplyTint(int[] vertices, int tint) {
        int stride = vertices.length / 4;
        for (int v = 0; v < 4; v++) {
            int i = v * stride + 3, color = vertices[i];
            // Vanilla BLOCK vertices pack ABGR, while BlockColors returns RGB.
            int red = (color & 255) * (tint >>> 16 & 255) / 255;
            int green = (color >>> 8 & 255) * (tint >>> 8 & 255) / 255;
            int blue = (color >>> 16 & 255) * (tint & 255) / 255;
            vertices[i] = color & 0xff000000 | blue << 16 | green << 8 | red;
        }
    }

    private record PackedQuad(int[] vertices) implements QuadCoordinates {
        private int index(int vertex, int offset) { return vertex * (vertices.length / 4) + offset; }
        public float x(int v) { return Float.intBitsToFloat(vertices[index(v,0)]); }
        public float y(int v) { return Float.intBitsToFloat(vertices[index(v,1)]); }
        public float z(int v) { return Float.intBitsToFloat(vertices[index(v,2)]); }
        public float u(int v) { return Float.intBitsToFloat(vertices[index(v,4)]); }
        public float v(int v) { return Float.intBitsToFloat(vertices[index(v,5)]); }
        public void uv(int vertex,float u,float v) { vertices[index(vertex,4)]=Float.floatToRawIntBits(u); vertices[index(vertex,5)]=Float.floatToRawIntBits(v); }
        float[] positions() { float[] p=new float[12]; for(int v=0;v<4;v++){p[v*3]=x(v);p[v*3+1]=y(v);p[v*3+2]=z(v);}return p; }
    }
}
