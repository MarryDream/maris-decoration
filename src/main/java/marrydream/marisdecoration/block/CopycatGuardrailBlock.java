package marrydream.marisdecoration.block;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.utils.GuardrailParts;
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

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 伪装护栏。
 *
 * <p>放置方式参考 Macaw's Fences and Walls：朝某个方向放置，就在该方向出现一段护栏；
 * 同一个方块位置可以对不同方向重复放置，每放一次消耗一个物品，最多东南西北四面。
 * 破坏时每个已存在的面掉回一个护栏方块。实现上沿用 Create: Copycats+ 的
 * {@code copycat_board} 范式——每个面一个 {@link BooleanProperty}，由
 * {@link #canReplace} 放行，交给原版消耗物品并重新进入放置流程。
 *
 * <p>伪装部分参照 Create 的 copycat：<b>横梁按方向、柱子按角点</b>各存一份独立材质
 * （角柱可能被两个方向共用，材质必须跟着角点而不是方向走），副手持方块放置时自动伪装横梁，
 * 右键可以按命中部位单独更换一个槽位的材质，每个槽位独立消耗、独立返还。
 * 扳手交互实现 Create 的 {@link IWrenchable}：直接右键还原伪装并返还材质，
 * 潜行右键拆掉命中的那一面。
 *
 * <p>几何和材质槽完全由伪装护栏自身维护；
 * <b>不使用</b>它的自动连接逻辑与拐角模型。
 */
public class CopycatGuardrailBlock extends Block implements BlockEntityProvider, Waterloggable, IWrenchable {

    /** 方块 id 的路径部分，客户端注册动态模型时要用。 */
    public static final String ID_PATH = "copycat_guardrail";

    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");

    /** 必须复用原版的属性实例，否则原版的流体交互逻辑认不出来。用全限定名以避开本 mod 自己的 Properties 类。 */
    public static final BooleanProperty WATERLOGGED = net.minecraft.state.property.Properties.WATERLOGGED;

    /** 水平四向。顺序与模型生成脚本、位掩码保持一致。 */
    public static final List<Direction> FACES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST);

    /** Create 的伪装允许标签：命中的方块可以无视下面的完整立方体检查。 */
    private static final TagKey<Block> COPYCAT_ALLOW =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("create", "copycat_allow"));
    /** Create 的伪装拒绝标签。 */
    private static final TagKey<Block> COPYCAT_DENY =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("create", "copycat_deny"));

    /**
     * Copycats+ 是否在场。
     *
     * <p>这是<b>可选兼容</b>而不是依赖：build.gradle 里用 {@code modCompileOnly} 引入，
     * 所以没装 Copycats+ 也能正常加载；下面引用它的类之前会先看这个标志。
     */
    private static final boolean COPYCATS_LOADED = FabricLoader.getInstance().isModLoaded("copycats");

    /**
     * 顶面沿长轴三等分的边界，坐标是相对该方块、归一化到 0..1 的<b>局部</b>坐标
     * （即 1/3 = 5.333 像素、2/3 = 10.667 像素处）。
     */
    private static final double STACK_SPLIT_LOW = 1.0 / 3.0;
    private static final double STACK_SPLIT_HIGH = 2.0 / 3.0;

    /** 「贴在某个面上」的距离阈值，沿用 Create: Copycats+ 伪装薄板的 2/16。 */
    private static final double FACE_PROXIMITY = 2.0 / 16.0;

    public CopycatGuardrailBlock(AbstractBlock.Settings settings) {
        super(settings);
        // 默认四面全 false。放置时由 getPlacementState 显式置位，
        // 千万不能在这里预置某个面，否则每次放置都会多出那一个面。
        BlockState state = getDefaultState();
        for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            state = state.with(property, false);
        }
        setDefaultState(state);
    }

    // ---------------------------------------------------------------- 状态工具

    public static int bit(Direction dir) {
        return switch (dir) {
            case NORTH -> 8;
            case EAST -> 4;
            case SOUTH -> 2;
            case WEST -> 1;
            default -> 0;
        };
    }

    public static boolean maskHas(int mask, Direction dir) {
        return (mask & bit(dir)) != 0;
    }

    public static int maskOf(BlockState state) {
        int mask = 0;
        for (Direction dir : FACES) {
            if (state.get(PROPERTY_BY_DIRECTION.get(dir))) {
                mask |= bit(dir);
            }
        }
        return mask;
    }

    public static boolean hasFace(BlockState state, Direction dir) {
        return state.get(PROPERTY_BY_DIRECTION.get(dir));
    }

    public static int faceCount(BlockState state) {
        return Integer.bitCount(maskOf(state));
    }

    /** 把命中点换算到方块本地坐标（0..1）。 */
    private static Vec3d localHit(ItemUsageContext context) {
        BlockPos pos = context.getBlockPos();
        return context.getHitPos().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    /** 把命中点换算到方块本地坐标（0..1）。 */
    private static Vec3d localHit(BlockHitResult hit, BlockPos pos) {
        return hit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    // ---------------------------------------------------------------- 放置

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        BlockState existing = context.getWorld().getBlockState(pos);

        Direction facing = context.getHorizontalPlayerFacing();
        // 点击已有护栏的顶面 = 向上堆叠。上层护栏在哪一侧由点击位置决定，
        // 而不是玩家朝向——参考 Quark 竖半砖的顶面放置。
        if (context.getSide() == Direction.UP) {
            BlockState below = context.getWorld().getBlockState(pos.offset(Direction.DOWN));
            if (below.isOf(this)) {
                facing = directionForStacking(context, below);
            }
        }

        BooleanProperty property = PROPERTY_BY_DIRECTION.get(facing);
        if (property == null) {
            return null;
        }
        if (existing.isOf(this)) {
            // 同一个方块内继续叠加（保留原有的含水状态）。
            // 朝向那一面已经占了就退到对立面：正对着已放好的护栏再右键时应该在对侧补一根，
            // 而不是跑到旁边另起一个新方块。
            if (!existing.get(property)) {
                return existing.with(property, true);
            }
            BooleanProperty opposite = PROPERTY_BY_DIRECTION.get(facing.getOpposite());
            return opposite == null ? null : existing.with(opposite, true);
        }
        // 必须从「四面全 false」出发，否则默认状态里预置的面会一起出现
        BlockState fresh = getDefaultState();
        for (BooleanProperty each : PROPERTY_BY_DIRECTION.values()) {
            fresh = fresh.with(each, false);
        }
        boolean waterlogged = context.getWorld().getFluidState(pos).getFluid() == Fluids.WATER;
        return fresh.with(property, true).with(WATERLOGGED, waterlogged);
    }

    /**
     * 点击已有护栏顶面时，决定上层护栏放在哪一侧。
     *
     * <p>顶面是一条贴着方块边缘的窄条，沿护栏方向的长轴铺开。判定就是沿这条长轴<b>三等分</b>：
     * 中间 1/3 继承下层方向，两端 1/3 各自对应那一侧的垂直方向。
     *
     * <p>为什么不用角度判定：这条窄条离方块中心很远，相对中心的横向偏移恒定接近 0.5，
     * 以中心算 {@code atan2} 时角度被这个恒定偏移主导——只有点到最边角才能落进侧向扇区，
     * 侧向堆叠几乎点不出来。三等分只用长轴上的归一化坐标，与窄条离中心多远无关，
     * 所以两侧各占满 1/3，手感是均匀的。
     *
     * <p>遗留项：下层护栏不止一个面时没有唯一长轴，见方法体内的 TODO。
     */
    private static Direction directionForStacking(ItemPlacementContext context, BlockState below) {
        Direction current = singleFace(below);
        if (current == null) {
            // 下层不止一个面，没有唯一的长轴可言，退回玩家朝向作为参照
            // TODO 多面下层时的参照方向：现在只拿玩家朝向顶替，玩家背对着点、或者从
            //  斜角点的时候，选中的长轴可能不是他眼睛盯着的那条，「中 1/3 继承」就继承到了
            //  一个他没在看的方向。想改成优先取玩家正对的那个面（在下面的面里挑一个最接近
            //  getHorizontalPlayerFacing() 的），都落空时再退到玩家朝向。
            //  顺带一并想清楚：两个垂直面时该以哪条为长轴、以及是否干脆不给继承区。
            current = context.getHorizontalPlayerFacing();
        }

        // 长轴 = 护栏所在轴的另一个水平轴：
        // 朝 NORTH/SOUTH 的护栏，顶面长条沿 X 铺开；朝 EAST/WEST 的，沿 Z 铺开。
        Direction.Axis longAxis = current.getAxis() == Direction.Axis.X
                ? Direction.Axis.Z
                : Direction.Axis.X;

        BlockPos clicked = context.getBlockPos().offset(Direction.DOWN);
        Vec3d hit = context.getHitPos();
        double along = longAxis == Direction.Axis.X
                ? hit.x - clicked.getX()
                : hit.z - clicked.getZ();

        if (along < STACK_SPLIT_LOW) {
            return Direction.from(longAxis, Direction.AxisDirection.NEGATIVE);
        }
        if (along > STACK_SPLIT_HIGH) {
            return Direction.from(longAxis, Direction.AxisDirection.POSITIVE);
        }
        return current;
    }

    /**
     * 点击位置是否几乎贴在「某一侧空闲面所在的平面」上。
     *
     * <p>判定照搬 Create: Copycats+ 的 {@code CopycatBoardBlock#canBeReplaced}。伪装薄板与护栏
     * 的几何是同一种「贴着方块边缘、1 像素厚的板」，所以它用的两个 {@code 2/16} 距离阈值可以直接沿用。
     *
     * <p>思路：薄板/护栏只有 1 像素厚，所以「点在外侧面上」和「点在内侧面上」在<b>方向</b>上无法区分
     * ——两次点击的 {@code getSide()} 都是同一个方向。真正能区分的是<b>点击位置离哪道平面更近</b>：
     * <ul>
     *   <li>点在朝外的那个面上 → 离方块边界 0 像素，离内侧那道空闲平面有 1 格远 → 判为相邻格；</li>
     *   <li>点在朝内的那个面上 → 紧贴内侧那道空闲平面（差 1/16）→ 判为同格追加。</li>
     * </ul>
     */
    private static boolean isFlushWithAFreeFace(BlockState state, ItemPlacementContext context, Direction side) {
        // ① 点击面的反面那一侧还空着，且点击位置贴在那道平面上
        Direction inner = side.getOpposite();
        BooleanProperty innerProperty = PROPERTY_BY_DIRECTION.get(inner);
        if (innerProperty != null && !state.get(innerProperty)) {
            double plane = context.getBlockPos().getComponentAlongAxis(inner.getAxis());
            if (inner.getDirection().offset() > 0) {
                plane += 1;
            }
            double hit = context.getHitPos().getComponentAlongAxis(inner.getAxis());
            if (Math.abs(plane - hit) < FACE_PROXIMITY) {
                return true;
            }
        }

        // ② 点击面那一侧还空着，且点击位置贴在点击面自己所在的平面上
        BooleanProperty sideProperty = PROPERTY_BY_DIRECTION.get(side);
        if (sideProperty != null && !state.get(sideProperty)) {
            double hit = context.getHitPos().getComponentAlongAxis(side.getAxis());
            double offset = hit - Math.round(hit);
            if (Math.signum(side.getDirection().offset()) == Math.signum(offset)
                    && Math.abs(offset) < FACE_PROXIMITY) {
                return true;
            }
        }
        return false;
    }

    /** 该状态只存在一个面时返回那个方向，否则返回 null。 */
    private static @Nullable Direction singleFace(BlockState state) {
        Direction found = null;
        for (Direction dir : FACES) {
            if (!state.get(PROPERTY_BY_DIRECTION.get(dir))) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = dir;
        }
        return found;
    }

    /**
     * 让原版认为「这个位置还可以再放一个」。返回 true 时，原版会消耗一个物品并重新调用
     * {@link #getPlacementState}，这正是多面叠加所需的行为。
     *
     * <p>注意：这个方法由 {@code ItemPlacementContext} 的构造器对<b>命中位置的方块</b>调用，
     * 那个方块未必是本方块（手里拿着护栏右键石头时也会走到这里），所以必须先确认类型，
     * 否则读取不具备的方块属性会抛异常。
     */
    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        if (!state.isOf(this) || !context.getStack().isOf(this.asItem())) {
            return false;
        }
        // 下面这些只在「被问的是命中格自己」时生效。canReplace 会被原版以两种语境调用，
        // 两次的 getSide()/getHitPos() 完全相同，只有 state 不同：
        //   1) ItemPlacementContext 构造时对「命中位置」的方块调用，此时 canReplaceExisting()
        //      为 true（构造器先置位、拿到结果后才覆盖），这次的结果决定目标落在命中格还是相邻格；
        //   2) canPlace() 里对「目标位置」的方块再调一次，此时为 false（canPlace 用 || 短路）。
        // 它的语义是「这个目标格本身能不能接受放置」——护栏必须仍然可被追加新面，
        // 否则点地面顶面时整次放置会直接失败。
        if (context.canReplaceExisting()) {
            Direction side = context.getSide();

            // 顶面 → 向上堆叠（目标算成上方一格）
            if (side == Direction.UP) {
                return false;
            }

            // 竖直面 → 只有点击位置几乎贴在「该侧那道空闲平面」上，才算在同一格继续追加；
            // 否则视为点在护栏的**外侧面**上，应当放到该面相邻的一格。
            if (side.getAxis().isHorizontal() && !isFlushWithAFreeFace(state, context, side)) {
                return false;
            }
        }

        Direction facing = context.getHorizontalPlayerFacing();
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(facing);
        if (property == null) {
            return false;
        }
        if (!state.get(property)) {
            return true;
        }
        // 朝向那一面已占时，允许改放到对立面——仍然是同一个方块
        BooleanProperty opposite = PROPERTY_BY_DIRECTION.get(facing.getOpposite());
        return opposite != null && !state.get(opposite);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CopycatGuardrailBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- 形状

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return GuardrailParts.shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return GuardrailParts.shape(state);
    }

    // ---------------------------------------------------------------- 含水

    /**
     * yarn 的 {@code Waterloggable} <b>不提供</b> {@code getFluidState}（和 Mojang 映射的
     * {@code SimpleWaterloggedBlock} 不同），必须自己实现。没有它的话 {@code WATERLOGGED}
     * 即使置位，方块也不会真的报告有水——不显示水面，也不参与流体逻辑。
     */
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    /**
     * 水桶倒水时的第一道门槛。
     *
     * <p>{@code BucketItem.placeFluid} 的顺序是「先查 canBucketPlace，再查 FluidFillable」，
     * 默认实现对本方块不成立，于是水桶有动作却什么都不会发生。
     */
    @Override
    public boolean canBucketPlace(BlockState state, Fluid fluid) {
        return fluid == Fluids.WATER && !state.get(WATERLOGGED);
    }

    // ---------------------------------------------------------------- 伪装交互

    /**
     * 判断手里的方块能否作为伪装材质，并按点击面定向。
     *
     * <p>规则照搬 Create 的 {@code CopycatBlock#getAcceptedBlockState}：
     * <ul>
     *   <li>必须是方块物品，且本身不能是伪装方块（所以伪装板和本方块不能互相伪装）；</li>
     *   <li>不在 {@code create:copycat_allow} 里的，命中 {@code create:copycat_deny}、
     *       是方块实体方块、是楼梯，一律拒绝；</li>
     *   <li>轮廓必须是<b>完整立方体</b>——栅栏、墙、台阶、半砖等就是在这里被挡掉的；</li>
     *   <li>最后把 FACING / HORIZONTAL_FACING / AXIS / HORIZONTAL_AXIS 按点击面定向。</li>
     * </ul>
     *
     * <p>做成 <b>static</b>：判据本身与方块实例无关，而「伪装放置器」的材质过滤
     * （{@code GuardrailCopycatAdapter#acceptsMaterial}）需要复用同一份规则。
     * 复刻一份出来迟早会与这里分叉。方法体与语义完全未变，既有调用点也不受影响。
     *
     * <p>{@code world} / {@code pos} 允许为 {@code null}（只用于「读方块轮廓形状」这一步），
     * 但<b>调用方应当尽量传真实世界</b>：传 null 会让「完整立方体轮廓 / 碰撞箱非空」这两条
     * 整段被跳过，半砖、玻璃板这类形状不完整的方块就会被误判为可用材质。
     * 正常右键放置时本来就是真实世界；放置器与材质选择界面也一并传真实世界。
     */
    public static BlockState getAcceptedMaterial(@Nullable BlockView world, @Nullable BlockPos pos,
                                                 ItemStack item, @Nullable Direction face) {
        if (!(item.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        if (block instanceof CopycatBlock || block instanceof CopycatGuardrailBlock) {
            return null;
        }
        // Copycats+ 的伪装方块不是 Create CopycatBlock 的子类（它们自己定义了 ICopycatBlock），
        // 所以得单独挡一次，否则一个填满的伪装小方块能通过下面的完整立方体检查被当成材质。
        // 装了就生效，没装时这行不会执行。
        if (COPYCATS_LOADED && block instanceof ICopycatBlock) {
            return null;
        }

        BlockState state = block.getDefaultState();
        if (!state.isIn(COPYCAT_ALLOW)) {
            if (state.isIn(COPYCAT_DENY)) {
                return null;
            }
            if (block instanceof BlockEntityProvider) {
                return null;
            }
            if (block instanceof StairsBlock) {
                return null;
            }
            if (world != null) {
                VoxelShape shape = state.getOutlineShape(world, pos);
                if (shape.isEmpty() || !shape.getBoundingBox().equals(VoxelShapes.fullCube().getBoundingBox())) {
                    return null;
                }
                if (state.getCollisionShape(world, pos).isEmpty()) {
                    return null;
                }
            }
        }

        if (face != null) {
            Direction.Axis axis = face.getAxis();
            if (state.contains(net.minecraft.state.property.Properties.FACING)) {
                state = state.with(net.minecraft.state.property.Properties.FACING, face);
            }
            if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING) && axis != Direction.Axis.Y) {
                state = state.with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, face);
            }
            if (state.contains(net.minecraft.state.property.Properties.AXIS)) {
                state = state.with(net.minecraft.state.property.Properties.AXIS, axis);
            }
            if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y) {
                state = state.with(net.minecraft.state.property.Properties.HORIZONTAL_AXIS, axis);
            }
        }
        return state;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);

        BlockState material = getAcceptedMaterial(world, pos, stack, hit.getSide());
        if (material == null) {
            return ActionResult.PASS;
        }
        if (!(world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity)) {
            return ActionResult.PASS;
        }

        GuardrailParts.Hit part = GuardrailParts.partAt(state, blockEntity.hiddenColumns(), localHit(hit, pos));
        if (part == null) {
            return ActionResult.PASS;
        }
        // 材质槽的身份跟着部件走：横梁按方向，柱子按角点。
        // 柱子按角点意味着两个方向共用的那根角柱只有一个槽位，不会存出两份材质。
        String key = GuardrailParts.materialKey(part);

        // 手里拿的方块和这一槽已有的材质是「同一种方块」时：旋转它的朝向，而不是再贴一次。
        // 判据是方块相同而非状态相同，所以贴了原木之后反复右键就能 X→Y→Z 循环。
        if (blockEntity.material(key).isOf(material.getBlock())) {
            if (!blockEntity.cycleMaterial(key)) {
                // 这个材质没有任何可旋转的属性，交还给原版（手里的方块会被正常放置）
                return ActionResult.PASS;
            }
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.75F, 0.95F);
            return ActionResult.SUCCESS;
        }
        // 已经有材质的部件要先用扳手还原，和 Create 的伪装板一致
        if (blockEntity.hasMaterial(key)) {
            return ActionResult.PASS;
        }

        if (!world.isClient) {
            boolean needsPayment = !blockEntity.hasMaterialBlock(material.getBlock());
            blockEntity.setMaterial(key, material, needsPayment ? stack : null);
            world.playSound(null, pos, material.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.75F);
            if (!player.isCreative() && needsPayment) {
                stack.decrement(1);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient || !(placer instanceof PlayerEntity player)) {
            return;
        }
        ItemStack offhand = player.getOffHandStack();
        BlockState material = getAcceptedMaterial(world, pos, offhand, Direction.getEntityFacingOrder(placer)[0]);
        if (material == null) {
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity)) {
            return;
        }
        // 副手只填横梁，柱子留空——柱子按角点独立付账，不跟着副手一起铺。
        // 与 Copycats+ 的 setPlacedBy 一样：遍历所有存在的部件，跳过已经有材质的，
        // 每个部件消耗一个物品。放置瞬间通常只有一个面存在，所以实际就是「填这一面的横梁」。
        for (Direction dir : FACES) {
            if (!hasFace(state, dir)) {
                continue;
            }
            String key = GuardrailParts.rowKey(dir);
            if (blockEntity.hasMaterial(key)) {
                continue;
            }
            boolean needsPayment = !blockEntity.hasMaterialBlock(material.getBlock());
            blockEntity.setMaterial(key, material, needsPayment ? offhand : null);
            if (player.isCreative()) {
                continue;
            }
            if (needsPayment) {
                offhand.decrement(1);
            }
            if (offhand.isEmpty()) {
                player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
                break;
            }
        }
    }

    // ---------------------------------------------------------------- 扳手

    /** 直接右键：只还原当前对着的那一个部件的材质（创造模式不返还）。 */
    @Override
    public ActionResult onWrenched(BlockState state, ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        if (!(world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity)) {
            return ActionResult.PASS;
        }
        GuardrailParts.Hit part = GuardrailParts.partAt(state, blockEntity.hiddenColumns(), localHit(context));
        if (part == null) {
            return ActionResult.PASS;
        }
        String key = GuardrailParts.materialKey(part);
        if (!blockEntity.hasMaterial(key)) {
            return ActionResult.PASS;
        }

        if (!world.isClient) {
            BlockState material = blockEntity.material(key);
            ItemStack returned = blockEntity.takeConsumedItemForRemoval(key);
            if (player != null && !player.isCreative() && !returned.isEmpty()) {
                // 直接进背包，不是掉在地上——Create 扳手的原生行为
                player.getInventory().offerOrDrop(returned);
            }
            // 播放「被敲掉的那个材质」的破坏粒子，让撞掉的是什么一目了然
            world.syncWorldEvent(2001, pos, Block.getRawIdFromState(material));
            IWrenchable.playRemoveSound(world, pos);
        }
        return ActionResult.SUCCESS;
    }

    /** 潜行 + 右键：拆掉命中的那一面；只剩最后一面时整块移除。 */
    @Override
    public ActionResult onSneakWrenched(BlockState state, ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        // 隐藏的柱子已经不在可见几何里，不该被扳手选中（否则会拆掉一根看不见的柱子的材质）
        Set<String> hidden = world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity
                ? blockEntity.hiddenColumns()
                : Set.of();
        GuardrailParts.Hit hitPart = GuardrailParts.partAt(state, hidden, localHit(context));
        Direction target = hitPart == null ? null : hitPart.face();
        if (target == null || !hasFace(state, target)) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            BlockState nextState = state.with(PROPERTY_BY_DIRECTION.get(target), false);
            if (world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity) {
                // Structural membership deliberately ignores hiddenColumns: hidden columns still exist.
                Set<String> removedKeys = new LinkedHashSet<>(GuardrailParts.visibleKeys(state, Set.of()));
                removedKeys.removeAll(GuardrailParts.visibleKeys(nextState, Set.of()));

                // Copycats+ emits exactly one material break event for the part under the wrench.
                // Other material slots that disappear with this direction are cleared silently.
                String hitKey = GuardrailParts.materialKey(hitPart);
                BlockState hitMaterial = blockEntity.material(hitKey);
                for (String key : removedKeys) {
                    ItemStack returned = blockEntity.takeConsumedItemForRemoval(key);
                    if (player != null && !player.isCreative() && !returned.isEmpty()) {
                        player.getInventory().offerOrDrop(returned);
                    }
                }
                if (!hitMaterial.isAir()) {
                    world.syncWorldEvent(2001, pos, Block.getRawIdFromState(hitMaterial));
                }
            }

            if (faceCount(state) <= 1) {
                // Materials have already followed Copycats+' offerOrDrop path. The default path
                // supplies the structure item, break events and final whole-block feedback.
                return IWrenchable.super.onSneakWrenched(state, context);
            }

            world.setBlockState(pos, nextState);
            if (player != null && !player.isCreative()) {
                // 拆一面返还一个护栏方块，同样直接进背包
                player.getInventory().offerOrDrop(new ItemStack(this));
            }
            IWrenchable.playRemoveSound(world, pos);
        }
        return ActionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- 掉落

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (player.isCreative() && world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity) {
            // 创造模式不掉落任何东西，包括伪装材质。
            // onBreak 在方块被移除之前调用，且带着玩家，是唯一能拿到游戏模式的地方。
            blockEntity.clearConsumedItems();
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.get(WATERLOGGED) && !newState.isOf(this)) {
            // 不再含水时把水放回原位
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        if (!state.isOf(newState.getBlock()) && !world.isClient && !moved) {
            // 护栏方块本体由战利品表按「每个存在的面一个」发放（这样创造模式自动不掉落）；
            // 这里只负责 8 个槽位记录下来的伪装材质。
            if (world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity) {
                blockEntity.dropAllMaterials(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** 中间键取方块：已伪装时给出伪装用的方块，否则给自己。 */
    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof CopycatGuardrailBlockEntity blockEntity) {
            for (String key : GuardrailParts.allKeys()) {
                BlockState material = blockEntity.material(key);
                if (!material.isAir()) {
                    return new ItemStack(material.getBlock());
                }
            }
        }
        return super.getPickStack(world, pos, state);
    }
}
