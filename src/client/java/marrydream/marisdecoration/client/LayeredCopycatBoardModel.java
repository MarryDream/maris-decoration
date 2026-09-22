package marrydream.marisdecoration.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.model.BakedModelHelper;
import io.github.fabricators_of_create.porting_lib.models.CustomParticleIconModel;
import marrydream.marisdecoration.MarisDecoration;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity.RenderData;
import marrydream.marisdecoration.block.utils.LayeredBoardParts;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code layered_copycat_board} 的动态模型。
 *
 * <p>渲染方式与 {@link CopycatGuardrailModel}、Create 的伪装板、Create: Copycats+ 完全一致：
 * <b>把伪装材质方块自己的模型发射出来，再逐面裁剪进薄板的几何体</b>
 * （{@link BakedModelHelper#cropAndMove}）。这样每面用的仍是材质对应那一面的贴图，
 * 材质的朝向、纹理方向都会如实体现——「取一张贴图拉伸铺满」的做法看不出朝向差异，不能用。
 *
 * <p>几何体来自 {@link LayeredBoardParts#boxesByKey}：它已经按占用掩码 / 窗 / 角归属算好了
 * 每个材质槽要画的盒子，并且把互相穿插的板之间那些内部面剔掉了。这里只负责按材质分组发射。
 *
 * <p>与护栏相比多了一步：<b>同一个材质槽可能有很多个盒子</b>（一片板被切成若干 1px 格子），
 * 所以每个材质只发射一次材质模型，然后把这个模型裁剪进该材质的每个盒子。
 */
public class LayeredCopycatBoardModel extends ForwardingBakedModel implements CustomParticleIconModel {

    /** 某个槽位没有材质时用的底材，就是 Create 伪装板的底材。 */
    private static final BlockState UNPAINTED = AllBlocks.COPYCAT_BASE.getDefaultState();

    public LayeredCopycatBoardModel( BakedModel wrapped ) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter( ) {
        // 必须走 Fabric 的渲染路径，否则 Indium/Sodium 不会调用 emitBlockQuads。
        return false;
    }

    @Override
    public void emitBlockQuads( BlockRenderView blockView, BlockState state, BlockPos pos,
                                Supplier<Random> randomSupplier, RenderContext context ) {
        RenderData data = readRenderData( blockView, pos );
        Map<String, List<Box>> boxes = LayeredBoardParts.boxesByKey(
                data.occupancy(), data.windows(), data.junctionOwners() );
        if ( boxes.isEmpty() ) {
            return;
        }

        Map<String, BlockState> materials = data.materials();
        if ( !DIAGNOSED ) {
            DIAGNOSED = true;
            // 只打一次：用于确认「模型包装生效」以及「方块实体数据是否同步到了客户端」。
            MarisDecoration.LOGGER.info( "[layered_copycat_board] 模型包装已生效，occupancy={} windows={}，材质 = {}",
                    data.occupancy(), data.windows(), materials );
        }

        MeshBuilder meshBuilder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        QuadEmitter emitter = meshBuilder.getEmitter();
        SpriteFinder spriteFinder = SpriteFinder.get(
                MinecraftClient.getInstance().getBakedModelManager().getAtlas( SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE ) );

        // 同一种材质的所有几何体合成一组，每组只发射一次材质模型
        Map<BlockState, List<Box>> grouped = new LinkedHashMap<>();
        boxes.forEach( ( key, list ) -> {
            BlockState material = materials.get( key );
            if ( material == null || material.isAir() ) {
                material = UNPAINTED;
            }
            grouped.computeIfAbsent( material, unused -> new ArrayList<>() ).addAll( list );
        } );

        grouped.forEach( ( material, list ) -> {
            BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel( material );
            // 材质自己的 render layer，必须在 quad 被搬进 mesh 之前就换上
            RenderMaterial materialBlendMode = blendModeOf( material );
            // 四边形不能在 transform 里直接发射，所以先收集进 mesh，最后一次性输出
            context.pushTransform( quad -> {
                // 材质模型发出的 quad 都带 BlendMode.DEFAULT，意思是「用本方块注册的那个层」。
                // 本方块注册在默认层，于是草方块/树叶的 alpha 会被当成不透明、玻璃会被当成实心，
                // 表现就是树叶和草方块侧面覆盖层糊成黑块、玻璃丢掉透明。
                // 这里把它换成伪装材质自己 render layer 对应的 blend mode——与 Create 的
                // CopycatModel#MaterialFixer 完全一致，对任何材质通用，不需要按方块特判。
                if ( quad.material().blendMode() == BlendMode.DEFAULT ) {
                    quad.material( materialBlendMode );
                }
                // tint 也必须跟着 quad 自己的材质走，见 applyMaterialTint
                applyMaterialTint( quad, material, blockView, pos );
                for ( Box box : list ) {
                    emitter.copyFrom( quad );
                    BakedModelHelper.cropAndMove( emitter, spriteFinder.find( emitter ), box, Vec3d.ZERO );
                    // 材质模型里的 quad 带的是「那个原方块」的 cullFace。搬进我们自己的几何体之后，
                    // 只有真正贴着本 BlockPos 六个外边界的那个面才配继续带这个 cullFace——
                    // 否则原版只会去看「那个方向的相邻 BlockPos 是不是实心方块」，
                    // 完全不知道这张面其实在方块内部第 2px 或第 15px，于是把 INNER 层、
                    // 方块内部平面、窗内壁的贴图一起剔掉。表现就是「同方向第三块板的贴图
                    // 被紧挨着的那个实体方块吃掉，把那个实体方块挖掉又恢复」。
                    Direction cullFace = emitter.cullFace( );
                    if ( cullFace != null && !touchesBlockBoundary( box, cullFace ) ) {
                        emitter.cullFace( null );
                    }
                    emitter.emit();
                }
                return false;
            } );
            model.emitBlockQuads( blockView, material, pos, randomSupplier, context );
            context.popTransform();
        } );

        meshBuilder.build().outputTo( context.getEmitter() );
    }

    /**
     * 伪装材质自己那套 render layer 对应的 {@link RenderMaterial}。
     *
     * <p>照搬 Create 的 {@code CopycatModel.MaterialFixer}：用
     * {@code RenderLayers.getBlockLayer(material)} 取材质真正会被画进哪一个层，再转成 FRAPI 的
     * {@link BlendMode}。草方块与树叶是 {@code cutout_mipped}（侧面覆盖层带 alpha），玻璃是
     * {@code translucent}——只有跟着材质走才对。
     *
     * <p>结果按 {@link BlendMode} 缓存：它只取决于材质所在的 render layer，而那是 FRAPI 的固定
     * 枚举，所以缓存上限就是这几个，<b>不会</b>随着见过的材质种类膨胀。缓存挂在模型实例上
     * 而不是 static：模型实例的生命周期就是一次资源加载，不会跨资源包残留。区块网格可以并行
     * 烘焙，因此用并发映射。
     */
    private final Map<BlendMode, RenderMaterial> renderMaterialsByBlendMode = new ConcurrentHashMap<>( 4 );

    private RenderMaterial blendModeOf( BlockState material ) {
        BlendMode blendMode = BlendMode.fromRenderLayer( RenderLayers.getBlockLayer( material ) );
        return renderMaterialsByBlendMode.computeIfAbsent( blendMode,
                mode -> RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode( mode ).find() );
    }

    /**
     * 把伪装材质自己的 tint 烘进 quad 的顶点色。
     *
     * <p><b>为什么必须在这里做</b>：tint 不是模型算的，是<b>渲染器</b>按「方块状态对应的
     * {@code BlockColorProvider}」算的——quad 上的 colorIndex 只是提问，答案由方块提供器给。
     * 草方块、树叶的颜色就是这么来的。Create 的解决方式是给伪装方块注册一个提供器，
     * 它从方块实体取出材质、再把问题<b>转发</b>给材质自己的提供器。
     *
     * <p><b>为什么不能照搬</b>：那个提供器的签名只有 {@code (state, world, pos, tintIndex)}，
     * 拿不到「这个 quad 属于哪个材质槽」。这里一个方块最多 66 份材质，方块级提供器根本分不清
     * 该用哪一份——给整块注册一个统一颜色，要么串色、要么全都变成同一种颜色。
     *
     * <p>所以把能力下移到 quad 级：每个 quad 都已经知道自己来自哪个 material，就用那个 material
     * 去问同一个 {@code BlockColors}，把答案乘进顶点色，然后清掉 colorIndex。
     */
    private static void applyMaterialTint( MutableQuadView quad, BlockState material,
                                           BlockRenderView blockView, BlockPos pos ) {
        int colorIndex = quad.colorIndex();
        if ( colorIndex < 0 ) {
            return;
        }
        // 与 Create 的 WrappedBlockColor 问的是同一个来源：材质自己的颜色提供器
        int tint = MinecraftClient.getInstance().getBlockColors().getColor( material, blockView, pos, colorIndex );
        if ( tint != -1 ) {
            for ( int vertex = 0; vertex < 4; vertex++ ) {
                quad.color( vertex, multiplyRgb( quad.color( vertex ), tint ) );
            }
        }
        // 颜色已经烘进顶点色，清掉 colorIndex，避免渲染器再按薄板自己的状态算一遍
        quad.colorIndex( -1 );
    }

    /**
     * 逐通道相乘，保留原顶点色的 alpha。
     *
     * <p>原版 tint 的 alpha 位是 0（草色是 {@code 0x91BD59} 这种 RGB），不能连 alpha 一起乘，
     * 否则整个 quad 会变透明。
     */
    private static int multiplyRgb( int color, int tint ) {
        int a = ( color >>> 24 ) & 0xFF;
        int r = ( ( ( color >> 16 ) & 0xFF ) * ( ( tint >> 16 ) & 0xFF ) ) / 255;
        int g = ( ( ( color >> 8 ) & 0xFF ) * ( ( tint >> 8 ) & 0xFF ) ) / 255;
        int b = ( ( color & 0xFF ) * ( tint & 0xFF ) ) / 255;
        return ( a << 24 ) | ( r << 16 ) | ( g << 8 ) | b;
    }

    private static volatile boolean DIAGNOSED = false;

    /**
     * 这个盒子是否真的贴着方块在 {@code direction} 那一侧的外边界。
     *
     * <p>只有贴外边界的面，它的 cullFace 才和「相邻 BlockPos 是否实心」这件事有关；
     * 其余的（INNER 层、方块内部平面、窗内壁、Edge / Corner 的内表面）留在方块内部，
     * 必须把 cullFace 清成 {@code null}，否则会被原版错误剔除。
     */
    private static boolean touchesBlockBoundary( Box box, Direction direction ) {
        return switch ( direction ) {
            case DOWN -> box.minY <= 0.0;
            case UP -> box.maxY >= 1.0;
            case NORTH -> box.minZ <= 0.0;
            case SOUTH -> box.maxZ >= 1.0;
            case WEST -> box.minX <= 0.0;
            case EAST -> box.maxX >= 1.0;
        };
    }

    /**
     * 读取方块实体交给渲染层的快照。
     *
     * <p>方块实体数据还没同步过来时给一份空板，避免渲染层因为 {@code null} 崩掉。
     */
    private static RenderData readRenderData( BlockRenderView view, BlockPos pos ) {
        Object data = view.getBlockEntityRenderData( pos );
        return data instanceof RenderData renderData ? renderData : EMPTY;
    }

    private static final RenderData EMPTY = LayeredCopycatBoardBlockEntity.emptyRenderData();

    /**
     * 破坏粒子跟随伪装材质。
     *
     * <p>{@code CustomParticleIconModel} 来自 Porting Lib（Create 传递引入），不是 Fabric API 的接口。
     */
    @Override
    public Sprite getParticleIcon( @Nullable Object data ) {
        if ( data instanceof RenderData renderData ) {
            for ( BlockState material : renderData.materials().values() ) {
                if ( !material.isAir() ) {
                    return particleSprite( material );
                }
            }
        }
        return particleSprite( UNPAINTED );
    }

    private static Sprite particleSprite( BlockState state ) {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel( state );
        Sprite icon = model == null ? null : model.getParticleSprite();
        return icon != null ? icon : wrappedOrFallback();
    }

    private static Sprite wrappedOrFallback( ) {
        return MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas( SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE )
                .getSprite( new Identifier( "create", "block/copycat_base" ) );
    }
}
