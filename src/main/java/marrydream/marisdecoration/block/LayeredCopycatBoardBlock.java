package marrydream.marisdecoration.block;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.utils.LayeredBoardParts;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardArea;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.item.DetailChisel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 分层伪装薄板。
 *
 * <p>六个面各自可以贴最多两层 1px 的板（{@code OUTER} 贴边界、{@code INNER} 再向内 1px），
 * <b>两层是互相独立的状态</b>——允许出现「只有 INNER 没有 OUTER」。层级归属和存在与否全在
 * {@link LayeredCopycatBoardBlockEntity} 的一个 12 位掩码里，方块状态只保留 {@code WATERLOGGED}。
 *
 * <h2>放置：空间连续填充</h2>
 * 玩家右键哪个可见面，就沿那个面的法向找下一个还能放的 1px 槽：
 * <pre>
 * 点 UP 面 → 本格 DOWN.OUTER → DOWN.INNER → UP.INNER → UP.OUTER
 *          → 上方一格重复 → …
 * </pre>
 * 所以点竖直板的顶面会自然往上长水平板、点水平板的侧面会自然往侧面长竖直板——同一个扫描规则
 * 覆盖全部情况，没有任何「墙 / 屋顶」特判。只有手持同一个「分层伪装薄板」物品时才允许往已有
 * 同类方块里追加（照 Copycats+ 的做法 {@code context.getStack().isOf(this.asItem())}），
 * 否则就去相邻方块新起一块。每放一个 1px 槽消耗一个物品。
 *
 * <h2>伪装</h2>
 * 材质准入、多材质存储、稀疏 NBT、{@code RenderData} 快照、逐 quad tint、blend mode 缓存、
 * Create 扳手语义、无 tick——全部沿用 {@code CopycatGuardrailBlock} 那一套。
 * 板材本体按方块实体里的占用掩码在代码里掉落（不用战利品表表达 12 个槽），
 * 创造模式走 {@code onBreak} + {@code clearConsumedItems} 的现成路子。
 */
public class LayeredCopycatBoardBlock extends Block implements BlockEntityProvider, Waterloggable, IWrenchable {

    /** 方块 id 的路径部分，客户端注册动态模型时要用。 */
    public static final String ID_PATH = "layered_copycat_board";

    /** 必须复用原版的属性实例，否则原版的流体交互逻辑认不出来。 */
    public static final BooleanProperty WATERLOGGED = net.minecraft.state.property.Properties.WATERLOGGED;

    /** Create 的伪装允许标签：命中的方块可以无视下面的完整立方体检查。 */
    private static final TagKey<Block> COPYCAT_ALLOW =
            TagKey.of( RegistryKeys.BLOCK, new Identifier( "create", "copycat_allow" ) );
    /** Create 的伪装拒绝标签。 */
    private static final TagKey<Block> COPYCAT_DENY =
            TagKey.of( RegistryKeys.BLOCK, new Identifier( "create", "copycat_deny" ) );

    /** Copycats+ 是否在场（可选兼容，不是依赖）。 */
    private static final boolean COPYCATS_LOADED = FabricLoader.getInstance().isModLoaded( "copycats" );

    /**
     * 判断「命中点属于哪一格」时把点沿法线推开的极小量。
     *
     * <p>只需要足够小，作用是把恰好压在 BlockPos 边界上的命中点推进玩家所点表面的外侧空间；
     * 必须远小于 1/16（薄板厚度），才不会越过本该命中的那一层。
     */
    private static final double EPSILON = 1.0E-4;

    /** 起点格沿这个方向塞满后，最多再往法向找多少个方块。 */
    private static final int MAX_SCAN_DISTANCE = 16;

    /**
     * 每个法向对应的槽位填充顺序，构造见 {@link #buildScanOrder()}。
     */
    private static final Map<FaceDir, List<SlotRef>> SCAN_ORDER = buildScanOrder();

    /**
     * 最近一次 {@code getPlacementState} 选中的槽位。
     *
     * <p>原版放置流程是「{@code canReplace} 放行 → 消耗物品 → {@code getPlacementState} 给状态 →
     * 写进世界 → {@code onPlaced}」。占用掩码住在方块实体里，而 {@code getPlacementState} 只能返回
     * 方块状态，所以选中的槽位要另找一条路传给 {@code onPlaced}——原版没有提供这个上下文，
     * 只能在这里记一笔。放置一定是逐次串行的，用完即清。
     */
    private static final ThreadLocal<PendingSlot> PENDING = new ThreadLocal<>();

    /** 一次放置选中的「方块位置 + 槽位」。 */
    private record PendingSlot( BlockPos pos, FaceDir face, BoardLayer layer ) {
    }

    /**
     * {@code onBreak} 与 {@code onStateReplaced} 是同一次破坏里前后脚发生的两个回调，
     * 后者拿不到玩家、前者拿得到，用这个标记把「刚才是创造模式挖的」传过去。
     */
    private static final ThreadLocal<Boolean> brokenInCreative = new ThreadLocal<>();

    /** 扫描顺序里的一项。 */
    private record SlotRef( FaceDir face, BoardLayer layer ) {
    }

    public LayeredCopycatBoardBlock( AbstractBlock.Settings settings ) {
        super( settings );
        setDefaultState( getDefaultState().with( WATERLOGGED, false ) );
    }

    /**
     * 每个法向对应的槽位填充顺序：<b>沿这个法向推进时，先填最靠后的空位</b>。
     *
     * <p>点 {@code UP} 面时展开就是规格里那条：
     * {@code DOWN.OUTER → DOWN.INNER → UP.INNER → UP.OUTER}——
     * 先在对侧（法向的负端）从外向内，再到本面（正端）从内向外，最后才进相邻方块。
     * 所以一个空格子的第一块板落在 {@code DOWN.OUTER}（贴方块底部）而不是顶部。
     *
     * <p>六个面都是同一个构造：{@code [对侧.OUTER, 对侧.INNER, 本面.INNER, 本面.OUTER]}，
     * 四个侧面与 DOWN 完全同理，不需要任何特判。
     */
    private static Map<FaceDir, List<SlotRef>> buildScanOrder( ) {
        Map<FaceDir, List<SlotRef>> order = new EnumMap<>( FaceDir.class );
        for ( FaceDir face : FaceDir.values() ) {
            FaceDir opposite = face.opposite();
            order.put( face, List.of(
                    new SlotRef( opposite, BoardLayer.OUTER ),
                    new SlotRef( opposite, BoardLayer.INNER ),
                    new SlotRef( face, BoardLayer.INNER ),
                    new SlotRef( face, BoardLayer.OUTER ) ) );
        }
        return order;
    }

    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( WATERLOGGED );
    }

    @Override
    public BlockEntity createBlockEntity( BlockPos pos, BlockState state ) {
        return new LayeredCopycatBoardBlockEntity( pos, state );
    }

    // ---------------------------------------------------------------- 形状

    /**
     * 形状来自方块实体的占用掩码，所以方块设置里必须声明 {@code dynamicShape()}。
     *
     * <h2>为什么这条声明是必须的（不声明会怎样）</h2>
     * 原版给每个 {@code BlockState} 预烤了一份 {@code ShapeCache}
     * （{@code AbstractBlock.AbstractBlockState#initShapeCache}）：它在<b>注册期</b>用
     * {@code EmptyBlockView.INSTANCE} + {@code BlockPos.ORIGIN} 调一次 {@code getCollisionShape}
     * 与 {@code SideShapeType.matches}，把「碰撞箱」「六个面的 isSideSolid 结果」这些值全部缓存下来。
     * 而 {@link #shapeAt} 要靠 {@code world.getBlockEntity(pos)} 拿占用掩码——那个空视图里没有
     * 任何方块实体，于是预烤出来的碰撞箱是<b>空的</b>，六个面的 solid 判定全是 {@code false}。
     *
     * <p>后果不只是「没有碰撞箱」：原版 {@code AbstractBlockState#isSideSolid}（以及
     * {@code isSideSolidFullSquare} / {@code isFullCube} / {@code isOpaqueFullCube}）在有缓存时
     * <b>直接返回缓存值</b>，只有缓存为 {@code null} 时才拿真实世界现算。所以修复前一块贴了完整
     * 外层面、碰撞箱确实是整格方块的薄板，对外仍然报告「六个面都不是完整实心面」——
     * 原版梯子 {@code LadderBlock#canPlaceOn} 的唯一判据
     * {@code state.isSideSolidFullSquare(world, pos, side)} 因此永远为 false，梯子贴不上去。
     *
     * <p>{@code hasDynamicBounds()} 为真时 {@code initShapeCache} 会<b>跳过</b>整个预烤，
     * 上面那些查询于是全部退化成「拿真实世界现算」。这是原版给「形状取决于方块实体 / 邻居」的方块
     * 准备的正规开关（栅栏、墙、栅栏门走的就是它），不是本 mod 的补丁。
     *
     * <h2>代价</h2>
     * 只是不再缓存那几个标量；几何本身仍然由 {@link LayeredBoardParts#shape(int)} 按占用掩码缓存
     * （{@code LayeredCopycatBoardBlockEntity#shape} 还有一层实例缓存），没有重复构建形状的开销。
     */
    @Override
    public VoxelShape getOutlineShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        return shapeAt( world, pos );
    }

    @Override
    public VoxelShape getCollisionShape( BlockState state, BlockView world, BlockPos pos, ShapeContext context ) {
        return shapeAt( world, pos );
    }

    /**
     * 按方块实体里缓存的占用掩码取箱。
     *
     * <p>窗是<b>填充</b>不是洞，所以形状只跟占用有关，不需要读 {@code windows}。
     *
     * <p>拿不到方块实体时返回空箱：这是「这个位置现在没有薄板」的正确表达，
     * 也正是原版预烤缓存会看到的那个结果（所以那个结果不能被当成权威，见上面的说明）。
     */
    private static VoxelShape shapeAt( BlockView world, BlockPos pos ) {
        if ( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) {
            return board.shape();
        }
        return VoxelShapes.empty();
    }

    // ---------------------------------------------------------------- 含水

    /**
     * yarn 的 {@code Waterloggable} 不提供 {@code getFluidState}，必须自己实现，
     * 否则 {@code WATERLOGGED} 即使置位方块也不会真的报告有水。
     */
    @Override
    public FluidState getFluidState( BlockState state ) {
        return state.get( WATERLOGGED ) ? Fluids.WATER.getStill( false ) : super.getFluidState( state );
    }

    @Override
    public boolean canBucketPlace( BlockState state, Fluid fluid ) {
        return fluid == Fluids.WATER && !state.get( WATERLOGGED );
    }

    // ---------------------------------------------------------------- 放置

    /** 方块本地坐标（0..1）下的命中点。 */
    private static Vec3d localHit( Vec3d hitPos, BlockPos pos ) {
        return hitPos.subtract( pos.getX(), pos.getY(), pos.getZ() );
    }

    /**
     * 在<b>一个方块</b>里，沿 {@code normal} 找下一个该放的 1px 槽：<b>取离点击面最近的那个空槽</b>。
     *
     * <p>用「到点击面的距离」而不是固定顺序，是为了让「点哪一侧就在哪一侧长板」——
     * 点一块水平板的北侧小面，板就长在北侧（{@code NORTH.OUTER}），而不是跑到对侧去，
     * 让玩家以为根本没放上。四条侧面因此走的是同一个对称算法。
     *
     * <p>同一个轴连续加厚时这个规则给出和原来一样的顺序：例如已有 {@code DOWN.OUTER}（y=0..1）
     * 时点它的顶面（y=1），最近的空槽是 {@code DOWN.INNER}（y=1..2），继续点是 {@code UP.INNER}、
     * 最后 {@code UP.OUTER}。点一块空格子的底面时最近的则是 {@code DOWN.OUTER} 本身。
     *
     * <p>只处理当前这一格：四个槽都占了就返回 {@code null}，调用方据此去相邻方块，
     * <b>不要</b>沿着法向一路扫过很多方块找空位。
     */
    @Nullable
    private static SlotRef nextFreeSlot( int occupancy, Direction normal, Vec3d local ) {
        FaceDir face = LayeredBoardSlots.of( normal );
        if ( face == null ) {
            return null;
        }
        Direction.Axis axis = normal.getAxis();
        double hit = axis == Direction.Axis.X ? local.x : axis == Direction.Axis.Y ? local.y : local.z;
        SlotRef best = null;
        double bestDistance = Double.MAX_VALUE;
        for ( SlotRef slot : SCAN_ORDER.get( face ) ) {
            if ( LayeredBoardSlots.hasSlot( occupancy, slot.face(), slot.layer() ) ) {
                continue;
            }
            double distance = Math.abs(
                    LayeredBoardParts.slotCenter( slot.face(), slot.layer(), axis ) - hit );
            if ( distance < bestDistance ) {
                bestDistance = distance;
                best = slot;
            }
        }
        return best;
    }

    /**
     * 本次放置的搜索起点：把命中点沿命中面的法线向外推一个极小量，看它落进哪个 BlockPos。
     *
     * <p>这一条同时覆盖了所有情况，<b>不需要</b>判断「点的是不是伪装薄板」：
     * <ul>
     *   <li>命中面落在方块<b>内部</b>（例如已经贴好的 1px 板的大面）→ 推完仍在本格，
     *       于是继续往这一格里塞下一层；</li>
     *   <li>命中面正好压在 BlockPos 的<b>边界</b>上（薄板的侧边小面，或任何普通方块的表面）
     *       → 推完就进了相邻那一格，于是板贴在那边。</li>
     * </ul>
     * 所以「点地面往上放」「点板的大面继续加厚」「点板的侧边小面换一个轴向」
     * 「点普通方块的侧面贴板」是同一个规则，没有任何特判。
     */
    private static BlockPos searchStart( Vec3d hitPos, Direction normal ) {
        return BlockPos.ofFloored(
                hitPos.x + normal.getOffsetX( ) * EPSILON,
                hitPos.y + normal.getOffsetY( ) * EPSILON,
                hitPos.z + normal.getOffsetZ( ) * EPSILON );
    }

    /**
     * 从搜索起点开始沿 {@code normal} 推进，找第一个还放得下 1px 槽的格子与槽位。
     *
     * <p>正常情况下起点格本身就有空槽；只有起点格沿这个方向已经塞满时才会进到下一格。
     * 被别的实体方块挡死时返回 {@code null}（这次放置直接失败，物品不会被消耗）。
     */
    @Nullable
    private static PendingSlot resolveTarget( World world, Vec3d hitPos, Direction normal ) {
        BlockPos cursor = searchStart( hitPos, normal );
        for ( int step = 0; step <= MAX_SCAN_DISTANCE; step++ ) {
            int occupancy = 0;
            if ( world.getBlockEntity( cursor ) instanceof LayeredCopycatBoardBlockEntity board ) {
                occupancy = board.occupancy( );
            } else if ( !world.getBlockState( cursor ).isReplaceable( ) ) {
                return null;
            }
            // 命中点相对这一格的本地坐标。推进到下一格时它在法向轴上的分量正好落在
            // 入射那一侧的边界上，于是「离点击面最近的空槽」自然就是贴着入射面的那一个。
            Vec3d local = hitPos.subtract( cursor.getX( ), cursor.getY( ), cursor.getZ( ) );
            SlotRef slot = nextFreeSlot( occupancy, normal, local );
            if ( slot != null ) {
                return new PendingSlot( cursor.toImmutable( ), slot.face( ), slot.layer( ) );
            }
            cursor = cursor.offset( normal );
        }
        return null;
    }

    @Override
    public @Nullable BlockState getPlacementState( ItemPlacementContext context ) {
        World world = context.getWorld( );
        Direction normal = context.getSide( );
        // canReplace 已经把目标格定成搜索起点了（留在命中格、或者去相邻格），
        // 这里用同一个 resolveTarget 把槽位定下来即可。
        PendingSlot target = resolveTarget( world, context.getHitPos( ), normal );
        if ( target == null || !target.pos( ).equals( context.getBlockPos( ) ) ) {
            PENDING.remove( );
            return null;
        }
        PENDING.set( target );
        boolean waterlogged = world.getFluidState( target.pos( ) ).getFluid( ) == Fluids.WATER;
        return getDefaultState( ).with( WATERLOGGED, waterlogged );
    }

    /**
     * 能不能把这次放置「并进命中格」。
     *
     * <p>判据只有一条：搜索起点是不是就是命中格本身。是的话（命中面在方块内部）就让原版把目标
     * 留在当前格；不是的话（命中面压在 BlockPos 边界上）返回 false，原版会把目标移到
     * 命中面外侧的相邻方块——那正好就是搜索起点。
     *
     * <p>这里刻意<b>不</b>看「被点的方块是不是伪装薄板」：板、石头、地面走的是同一条规则。
     */
    @Override
    public boolean canReplace( BlockState state, ItemPlacementContext context ) {
        if ( !context.getStack( ).isOf( this.asItem( ) ) ) {
            return false;
        }
        // canPlace() 里的第二次调用问的是「目标格本身能不能接受放置」，必须放行，
        // 否则「去相邻格新起一块」这条整次放置会失败。
        if ( !context.canReplaceExisting( ) ) {
            return true;
        }
        PendingSlot target = resolveTarget( context.getWorld( ), context.getHitPos( ), context.getSide( ) );
        return target != null && target.pos( ).equals( context.getBlockPos( ) );
    }

    @Override
    public void onPlaced( World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack ) {
        super.onPlaced( world, pos, state, placer, itemStack );

        // 先把待处理的槽位取走再判断：客户端也会走到这个回调，不能把 ThreadLocal 留在原地。
        PendingSlot pending = PENDING.get();
        PENDING.remove();

        if ( world.isClient ) {
            return;
        }

        if ( !( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) ) {
            return;
        }

        if ( pending != null && pending.pos().equals( pos ) ) {
            // 本次放置选中的槽位：占上它（物品已经由原版扣掉一个）
            board.addSlot( pending.face(), pending.layer() );
        }

        if ( placer instanceof PlayerEntity player ) {
            autoCamoNewLayers( world, pos, board, player, Direction.getEntityFacingOrder( placer )[0] );
        }
    }

    /**
     * 副手拿着合法伪装方块时，把这一格里所有「已经存在但还没有材质」的 BODY 槽自动铺上。
     *
     * <p>照应护栏的做法，只铺 BODY——四条边和角留给玩家自己贴（护栏那边同样只填横梁、柱子留空）。
     * 同格追加（{@link #appendSlot}）与异格新建（{@link #onPlaced}）共用这一段，
     * 保证两条路径对「新加的那一层」的伪装行为完全一致。
     */
    private static void autoCamoNewLayers( World world, BlockPos pos, LayeredCopycatBoardBlockEntity board,
                                           PlayerEntity player, @Nullable Direction face ) {
        ItemStack offhand = player.getOffHandStack();
        BlockState material = getAcceptedMaterial( world, pos, offhand, face );
        if ( material == null ) {
            return;
        }
        for ( FaceDir slotFace : FaceDir.values() ) {
            for ( BoardLayer layer : BoardLayer.values() ) {
                if ( !board.hasSlot( slotFace, layer ) ) {
                    continue;
                }
                String key = LayeredBoardSlots.materialKey( slotFace, layer, BoardArea.BODY );
                if ( board.hasMaterial( key ) ) {
                    continue;
                }
                // 同一个 BlockPos 上同一种方块只在第一次使用时消耗一个物品
                boolean paid = board.hasPaidFor( material );
                board.setMaterial( key, material, paid ? null : offhand );
                if ( paid || player.isCreative() ) {
                    continue;
                }
                offhand.decrement( 1 );
                if ( offhand.isEmpty() ) {
                    player.setStackInHand( Hand.OFF_HAND, ItemStack.EMPTY );
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 伪装材质

    /**
     * 判断手里的方块能否作为伪装材质，并按点击面定向。
     *
     * <p>规则照搬 Create 的 {@code CopycatBlock#getAcceptedBlockState}：必须是方块物品、本身不能是
     * 伪装方块、不在 {@code create:copycat_allow} 里的要过 deny / 方块实体 / 楼梯 / 完整立方体这几关，
     * 最后把 FACING / HORIZONTAL_FACING / AXIS / HORIZONTAL_AXIS 按点击面定向。
     *
     * <p>做成 <b>static</b>：判据本身与方块实例无关，而「伪装放置器」的材质过滤
     * （{@code LayeredBoardCopycatAdapter#acceptsMaterial}）需要复用同一份规则。
     * 复刻一份出来迟早会与这里分叉。方法体与语义完全未变，既有调用点也不受影响。
     *
     * <p>{@code world} / {@code pos} 允许为 {@code null}（只用于「读方块轮廓形状」这一步），
     * 但<b>调用方应当尽量传真实世界</b>：传 null 会让「完整立方体轮廓 / 碰撞箱非空」这两条
     * 整段被跳过，半砖、玻璃板这类形状不完整的方块就会被误判为可用材质。
     * 正常右键放置时本来就是真实世界；放置器与材质选择界面也一并传真实世界。
     */
    public static BlockState getAcceptedMaterial( @Nullable BlockView world, @Nullable BlockPos pos,
                                                  ItemStack item, @Nullable Direction face ) {
        if ( !( item.getItem() instanceof BlockItem blockItem ) ) {
            return null;
        }
        Block block = blockItem.getBlock();
        if ( block instanceof CopycatBlock
                || block instanceof CopycatGuardrailBlock
                || block instanceof LayeredCopycatBoardBlock ) {
            return null;
        }
        // Copycats+ 的伪装方块不是 Create CopycatBlock 的子类，得单独挡一次。
        if ( COPYCATS_LOADED && block instanceof ICopycatBlock ) {
            return null;
        }

        BlockState state = block.getDefaultState();
        if ( !state.isIn( COPYCAT_ALLOW ) ) {
            if ( state.isIn( COPYCAT_DENY ) ) {
                return null;
            }
            if ( block instanceof BlockEntityProvider ) {
                return null;
            }
            if ( block instanceof StairsBlock ) {
                return null;
            }
            if ( world != null ) {
                VoxelShape shape = state.getOutlineShape( world, pos );
                if ( shape.isEmpty() || !shape.getBoundingBox().equals( VoxelShapes.fullCube().getBoundingBox() ) ) {
                    return null;
                }
                if ( state.getCollisionShape( world, pos ).isEmpty() ) {
                    return null;
                }
            }
        }

        if ( face != null ) {
            Direction.Axis axis = face.getAxis();
            if ( state.contains( net.minecraft.state.property.Properties.FACING ) ) {
                state = state.with( net.minecraft.state.property.Properties.FACING, face );
            }
            if ( state.contains( net.minecraft.state.property.Properties.HORIZONTAL_FACING ) && axis != Direction.Axis.Y ) {
                state = state.with( net.minecraft.state.property.Properties.HORIZONTAL_FACING, face );
            }
            if ( state.contains( net.minecraft.state.property.Properties.AXIS ) ) {
                state = state.with( net.minecraft.state.property.Properties.AXIS, axis );
            }
            if ( state.contains( net.minecraft.state.property.Properties.HORIZONTAL_AXIS ) && axis != Direction.Axis.Y ) {
                state = state.with( net.minecraft.state.property.Properties.HORIZONTAL_AXIS, axis );
            }
        }
        return state;
    }

    /** 一次几何命中：哪个面的哪一层、哪个区域。 */
    public record BoardHit( FaceDir face, BoardLayer layer, BoardArea area ) {
    }

    @Nullable
    private static BoardHit hitAt( Vec3d local, LayeredCopycatBoardBlockEntity board ) {
        // 命中走的是渲染同一份「最终生效的几何」：交汇点上显示哪条边，材质就写进哪条边。
        LayeredBoardParts.Slot slot = LayeredBoardParts.slotAt(
                board.occupancy(), board.windows(), board.junctionOwners(), local );
        return slot == null ? null : new BoardHit( slot.face(), slot.layer(), slot.area() );
    }

    /**
     * 右击贴材质：定位命中的槽位，付账（同一 BlockPos 同一种材质只付一次）、写材质、播音效。
     *
     * <p>{@code local} 为空表示没有可用的几何命中（例如副手自动伪装），此时落到 {@code BODY}。
     */
    private ActionResult applyMaterial( World world, BlockPos pos, LayeredCopycatBoardBlockEntity board,
                                        BlockState material, ItemStack stack, @Nullable PlayerEntity player,
                                        @Nullable Vec3d local ) {
        BoardHit hit = local == null ? null : hitAt( local, board );
        BoardLayer layer = hit == null ? BoardLayer.OUTER : hit.layer();
        BoardArea area = hit == null ? BoardArea.BODY : hit.area();
        FaceDir face = hit == null ? FaceDir.UP : hit.face();

        // 窗的材质按面共享：命中的是窗区域就落到该面的窗槽上
        String key = area == BoardArea.WINDOW
                ? LayeredBoardSlots.windowKey( face )
                : LayeredBoardSlots.materialKey( face, layer, area );

        if ( board.material( key ).isOf( material.getBlock() ) ) {
            if ( !board.cycleMaterial( key ) ) {
                // 这个材质没有任何可旋转的属性，交还给原版（手里的方块会被正常放置）
                return ActionResult.PASS;
            }
            world.playSound( null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.75F, 0.95F );
            return ActionResult.SUCCESS;
        }
        // 已经有材质的槽位要先用扳手还原，和 Create 的伪装板一致
        if ( board.hasMaterial( key ) ) {
            return ActionResult.PASS;
        }

        if ( !world.isClient ) {
            // 同一个 BlockPos 上同一种方块只在第一次使用时消耗一个物品：
            // 一块薄板最多 66 个材质槽，若逐个槽位都扣一次，拆掉整块时就要还 66 份，对不上。
            boolean paid = board.hasPaidFor( material );
            board.setMaterial( key, material, paid ? null : stack );
            world.playSound( null, pos, material.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.75F );
            if ( !paid && player != null && !player.isCreative() ) {
                stack.decrement( 1 );
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult onUse( BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit ) {
        ItemStack stack = player.getStackInHand( hand );

        // 细工凿：形态切换。放在 ItemStack#useOnBlock 里也有一份，见 DetailChisel。
        if ( stack.getItem() instanceof DetailChisel ) {
            return onChisel( world, pos, player, hit.getPos(), hit.getSide() );
        }
        if ( !( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) ) {
            return ActionResult.PASS;
        }

        // 手里拿着同一块薄板：先按「命中点沿法线外推」算出这次真正要填的槽位。
        // 点的是薄板自己、还是旁边普通方块的某个面，在这一层没有任何区别——
        // 目标格当前是不是已有薄板，决定后面怎么执行。
        if ( stack.isOf( this.asItem( ) ) ) {
            ActionResult appended = tryAppendExisting( world, hit.getPos( ), hit.getSide( ), stack, player );
            if ( appended != null ) {
                return appended;
            }
            // 目标格还不是薄板（空气 / 可替换方块）→ 交回原版 BlockItem 新建。
            return ActionResult.PASS;
        }

        BlockState material = getAcceptedMaterial( world, pos, stack, hit.getSide() );
        if ( material == null ) {
            return ActionResult.PASS;
        }
        return applyMaterial( world, pos, board, material, stack, player, localHit( hit.getPos(), pos ) );
    }

    /**
     * 「目标格里已经有薄板」时的追加入口，方块与物品两条路共用这一份。
     *
     * <p>为什么不能交给原版：方块状态只有 {@code WATERLOGGED}，追加槽位前后 {@code BlockState}
     * 完全相同，原版 {@code BlockItem#place} 里那次 {@code setBlockState} 会返回 {@code false}，
     * 整次放置判 FAIL——物品不消耗、{@code onPlaced} 不触发，occupancy 永远加不上去。
     * 所以只要 {@link #resolveTarget} 定位到的格子当前已经是薄板，就必须由这里直接改方块实体。
     *
     * <p>调用方有两个，语义完全一致：
     * <ul>
     *   <li>{@link #onUse}——玩家点的是薄板自己（这时目标格通常就是命中格）；</li>
     *   <li>{@code LayeredCopycatBoardItem#useOnBlock}——玩家点的是旁边的普通方块，
     *       但命中面外推后的目标格恰好是已有的薄板。方块自己的 {@code onUse} 这时根本不会执行，
     *       所以物品那边必须再问一次。</li>
     * </ul>
     * 分工是：{@link #resolveTarget} 决定「放哪里」，目标格当前是什么决定「怎么执行」。
     *
     * @return 追加成功返回结果；目标格不存在、不是薄板、或槽位已被占（该走原版新建）返回 {@code null}
     */
    @Nullable
    public static ActionResult tryAppendExisting( World world, Vec3d hitPos, Direction side,
                                                  ItemStack stack, @Nullable PlayerEntity player ) {
        PendingSlot target = resolveTarget( world, hitPos, side );
        if ( target == null ) {
            return null;
        }
        if ( !( world.getBlockEntity( target.pos() ) instanceof LayeredCopycatBoardBlockEntity board ) ) {
            return null;
        }
        // resolveTarget 只挑空槽，这里纯属兜底：真撞上就交回原版，不要静默吞掉物品。
        if ( board.hasSlot( target.face(), target.layer() ) ) {
            return null;
        }
        // 客户端不预测：真正的 occupancy 由服务端改完同步过来，这里只负责别让原版再放一次。
        if ( world.isClient ) {
            return ActionResult.SUCCESS;
        }

        board.addSlot( target.face(), target.layer() );
        if ( player == null || !player.isCreative() ) {
            stack.decrement( 1 );
        }
        if ( player != null ) {
            autoCamoNewLayers( world, target.pos(), board, player, Direction.getEntityFacingOrder( player )[0] );
        }
        world.playSound( null, target.pos(),
                world.getBlockState( target.pos() ).getSoundGroup().getPlaceSound(),
                SoundCategory.BLOCKS, 1.0F, 0.75F );
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- 细工凿

    /**
     * 细工凿的形态切换。
     *
     * <p>入口必须同时挂在方块的 {@link #onUse} 和 {@link DetailChisel#useOnBlock} 上：
     * 原版在「潜行 + 手持非空物品」时会整段跳过 {@code BlockState#onUse}，只调用
     * {@code ItemStack#useOnBlock}，Shift + 右键收不到。
     *
     * <ul>
     *   <li>普通右键 BODY / Window 区域 → 切换该面的 {@code window}；</li>
     *   <li>普通右键 Corner → 在相邻的两条边之间切换归属；</li>
     *   <li>普通右键 Edge → 不处理（返回 PASS）；</li>
     *   <li>Shift + 右键 → 把本方块内所有面的 {@code window} 恢复为 false（不清材质、不返还、不动角归属）。</li>
     * </ul>
     */
    public static ActionResult onChisel( World world, BlockPos pos, @Nullable PlayerEntity player,
                                         Vec3d hitPos, Direction side ) {
        if ( !( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) ) {
            return ActionResult.PASS;
        }
        if ( player != null && player.isSneaking() ) {
            if ( !board.clearWindows() ) {
                return ActionResult.PASS;
            }
            if ( !world.isClient ) {
                world.playSound( null, pos, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 0.7F, 1.4F );
            }
            return ActionResult.SUCCESS;
        }

        Vec3d local = localHit( hitPos, pos );
        LayeredBoardParts.Cell cell = LayeredBoardParts.cellAt( board.occupancy(), local );
        if ( cell == null ) {
            return ActionResult.PASS;
        }

        // 交汇点优先：这一格如果是一个物理交汇点，就在它当前候选的 Edge 材质之间循环。
        // 候选是按最终几何算出来的（可能来自别的 Face），所以三面共角时就是 A → B → C → A。
        LayeredBoardParts.Junction junction = LayeredBoardParts.junctionAt(
                board.occupancy(), board.windows(), board.junctionOwners(), local );
        if ( junction != null ) {
            if ( world.isClient ) {
                return ActionResult.SUCCESS;
            }
            List<LayeredBoardParts.Slot> candidates = junction.candidates();
            LayeredBoardParts.Slot next = candidates.get( ( junction.indexOfShown() + 1 ) % candidates.size() );
            board.setJunctionOwner( junction.key(), next.name() );
            world.playSound( null, pos, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 0.7F, 1.0F );
            return ActionResult.SUCCESS;
        }

        // 不是交汇点：维持原行为——中央 BODY / WINDOW 切换窗，普通 Edge 不处理。
        // 这里传 true：不管窗当前开没开，中央 8×8 都解释成「窗区域」，这样关着的时候也能点开它。
        BoardArea area = LayeredBoardParts.areaAt( true, cell.u(), cell.v() );
        if ( area == BoardArea.WINDOW || area == BoardArea.BODY ) {
            if ( world.isClient ) {
                return ActionResult.SUCCESS;
            }
            board.toggleWindow( cell.face() );
            world.playSound( null, pos, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 0.7F, 1.2F );
            return ActionResult.SUCCESS;
        }
        // 落在某条普通边上：按规格不处理
        return ActionResult.PASS;
    }

    // ---------------------------------------------------------------- 扳手

    /** 直接右键：只还原命中区域的那一个材质槽（创造模式不返还）。 */
    @Override
    public ActionResult onWrenched( BlockState state, ItemUsageContext context ) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        if ( !( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) ) {
            return ActionResult.PASS;
        }
        BoardHit hit = hitAt( localHit( context.getHitPos(), pos ), board );
        if ( hit == null ) {
            return ActionResult.PASS;
        }
        String key = hit.area() == BoardArea.WINDOW
                ? LayeredBoardSlots.windowKey( hit.face() )
                : LayeredBoardSlots.materialKey( hit.face(), hit.layer(), hit.area() );
        if ( !board.hasMaterial( key ) ) {
            return ActionResult.PASS;
        }

        if ( !world.isClient ) {
            BlockState material = board.material( key );
            ItemStack returned = board.takeConsumedItemForRemoval( key );
            if ( player != null && !player.isCreative() && !returned.isEmpty() ) {
                // 直接进背包，不是掉在地上——Create 扳手的原生行为
                player.getInventory().offerOrDrop( returned );
            }
            // 播放「被敲掉的那个材质」的破坏粒子
            world.syncWorldEvent( 2001, pos, Block.getRawIdFromState( material ) );
            IWrenchable.playRemoveSound( world, pos );
        }
        return ActionResult.SUCCESS;
    }

    /**
     * 潜行 + 右键：拆掉命中的那一层 1px 板。
     *
     * <p>东西一律<b>直接进背包</b>（{@code offerOrDrop} 就是这个语义：装得下进背包、装不下才落地），
     * 不走战利品表 / {@code Block.dropStack} 那套「扔在地上」的流程——<b>包括拆最后一层</b>：
     * 那一层移除后方块随之消失，但掉落照样先塞进玩家背包。
     *
     * <p>这一层上贴的材质也一起取下来。按「同一 BlockPos 同一种方块只在<b>最后一个引用</b>被移除时
     * 才返还一个」的规则，{@code takeConsumedItemForRemoval} 会自己判断该不该还。
     * 窗材质是面级的、另一层可能还在用，所以这里不动它。
     */
    @Override
    public ActionResult onSneakWrenched( BlockState state, ItemUsageContext context ) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        if ( !( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) ) {
            return IWrenchable.super.onSneakWrenched( state, context );
        }
        LayeredBoardParts.Cell cell = LayeredBoardParts.cellAt(
                board.occupancy(), localHit( context.getHitPos(), pos ) );
        if ( cell == null ) {
            return ActionResult.PASS;
        }
        if ( world.isClient ) {
            return ActionResult.SUCCESS;
        }

        // Copycats+ emits one material break event for the part under the wrench before
        // removing it. Keep the existing layer/payment cleanup below unchanged.
        BlockState feedbackMaterial = null;
        BoardHit feedbackHit = hitAt( localHit( context.getHitPos(), pos ), board );
        if ( feedbackHit != null && feedbackHit.area() != BoardArea.WINDOW
                && feedbackHit.face() == cell.face() && feedbackHit.layer() == cell.layer() ) {
            String feedbackKey = LayeredBoardSlots.materialKey(
                    feedbackHit.face(), feedbackHit.layer(), feedbackHit.area() );
            BlockState candidate = board.material( feedbackKey );
            if ( !candidate.isAir() ) {
                feedbackMaterial = candidate;
            }
        }
        if ( feedbackMaterial == null ) {
            for ( BoardArea area : LayeredBoardSlots.MATERIAL_AREAS ) {
                BlockState candidate = board.material(
                        LayeredBoardSlots.materialKey( cell.face(), cell.layer(), area ) );
                if ( !candidate.isAir() ) {
                    feedbackMaterial = candidate;
                    break;
                }
            }
        }

        // 先把这一层的材质取下来（最后一个引用才真返还）
        for ( BoardArea area : LayeredBoardSlots.MATERIAL_AREAS ) {
            String key = LayeredBoardSlots.materialKey( cell.face(), cell.layer(), area );
            if ( !board.hasMaterial( key ) ) {
                continue;
            }
            ItemStack returned = board.takeConsumedItemForRemoval( key );
            if ( player != null && !player.isCreative() && !returned.isEmpty() ) {
                player.getInventory().offerOrDrop( returned );
            }
        }

        // Window material is shared by both layers of a face. Keep it while the other layer
        // still exists; when this is the face's last layer, settle it through the same
        // offerOrDrop path before block removal can reach onStateReplaced/dropAllMaterials.
        BoardLayer otherLayer = cell.layer() == BoardLayer.OUTER ? BoardLayer.INNER : BoardLayer.OUTER;
        if ( !LayeredBoardSlots.hasSlot( board.occupancy(), cell.face(), otherLayer ) ) {
            String windowKey = LayeredBoardSlots.windowKey( cell.face() );
            if ( board.hasMaterial( windowKey ) ) {
                ItemStack returned = board.takeConsumedItemForRemoval( windowKey );
                if ( player != null && !player.isCreative() && !returned.isEmpty() ) {
                    player.getInventory().offerOrDrop( returned );
                }
            }
        }

        board.removeSlot( cell.face(), cell.layer() );

        if ( player != null && !player.isCreative() ) {
            // 拆一层返还一个薄板，同样直接进背包
            player.getInventory().offerOrDrop( new ItemStack( this ) );
        }

        if ( feedbackMaterial != null ) {
            world.syncWorldEvent( 2001, pos, Block.getRawIdFromState( feedbackMaterial ) );
        }
        if ( board.slotCount() == 0 ) {
            // The last part follows Create's generic wrench removal path. This supplies the
            // whole-block break feedback and break-event hooks without duplicating item returns:
            // occupancy and the layer materials are already empty at this point.
            return IWrenchable.super.onSneakWrenched( state, context );
        }
        IWrenchable.playRemoveSound( world, pos );
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- 掉落

    @Override
    public void onBreak( World world, BlockPos pos, BlockState state, PlayerEntity player ) {
        if ( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) {
            if ( player.isCreative() ) {
                // 创造模式不掉落任何东西，包括伪装材质。
                // onBreak 在方块被移除之前调用，且带着玩家，是唯一能拿到游戏模式的地方；
                // 这里留下一个标记，给紧接着执行的 onStateReplaced 用。
                board.clearConsumedItems();
                brokenInCreative.set( Boolean.TRUE );
            } else {
                brokenInCreative.set( Boolean.FALSE );
            }
        }
        super.onBreak( world, pos, state, player );
    }

    @Override
    public void onStateReplaced( BlockState state, World world, BlockPos pos, BlockState newState, boolean moved ) {
        if ( state.get( WATERLOGGED ) && !newState.isOf( this ) ) {
            world.scheduleFluidTick( pos, Fluids.WATER, Fluids.WATER.getTickRate( world ) );
        }
        Boolean creative = brokenInCreative.get();
        brokenInCreative.remove();

        if ( state.isOf( newState.getBlock() ) || world.isClient || moved ) {
            super.onStateReplaced( state, world, pos, newState, moved );
            return;
        }
        if ( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) {
            // 板材本体：按占用掩码每个 1px 槽掉一个。
            // 刻意不用战利品表——12 个槽在方块状态里根本不存在，loot pool 没法表达。
            // 代价是「战利品表会自动处理创造模式」，所以这里用 onBreak 留下的标记自己判断，
            // 否则创造模式拆一块会掉一地薄板。
            if ( !Boolean.TRUE.equals( creative ) ) {
                int count = board.slotCount();
                if ( count > 0 ) {
                    Block.dropStack( world, pos, new ItemStack( this, count ) );
                }
            }
            board.dropAllMaterials( world, pos );
        }
        super.onStateReplaced( state, world, pos, newState, moved );
    }

    /** 中间键取方块：已伪装时给出伪装用的方块，否则给自己。 */
    @Override
    public ItemStack getPickStack( BlockView world, BlockPos pos, BlockState state ) {
        if ( world.getBlockEntity( pos ) instanceof LayeredCopycatBoardBlockEntity board ) {
            for ( String key : LayeredBoardSlots.allMaterialKeys() ) {
                BlockState material = board.material( key );
                if ( !material.isAir() ) {
                    return new ItemStack( material.getBlock() );
                }
            }
        }
        return super.getPickStack( world, pos, state );
    }
}
