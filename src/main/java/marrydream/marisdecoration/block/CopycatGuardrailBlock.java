package marrydream.marisdecoration.block;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import marrydream.marisdecoration.block.utils.GuardrailParts.Slot;
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
import java.util.Map;

/**
 * 伪装护栏。
 *
 * <p>放置方式参考 Macaw's Fences and Walls：朝某个方向放置，就在该方向出现一段护栏；
 * 同一个方块位置可以对不同方向重复放置，每放一次消耗一个物品，最多东南西北四面。
 * 破坏时每个已存在的面掉回一个护栏方块。实现上沿用 Create: Copycats+ 的
 * {@code copycat_board} 范式——每个面一个 {@link BooleanProperty}，由
 * {@link #canReplace} 放行，交给原版消耗物品并重新进入放置流程。
 *
 * <p>伪装部分参照 Create 的 copycat：整块共享「柱」和「横梁」两个材质槽，
 * 副手持方块放置时自动伪装，右键可以按命中部位单独更换其中一个槽位。
 * 扳手交互实现 Create 的 {@link IWrenchable}：直接右键还原伪装并返还材质，
 * 潜行右键拆掉命中的那一面。
 *
 * <p>与 {@code black_steel_guardrail} 的关系仅限于借用其单面模型；
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
        Direction facing = context.getHorizontalPlayerFacing();
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(facing);
        if (property == null) {
            return null;
        }
        BlockPos pos = context.getBlockPos();
        BlockState existing = context.getWorld().getBlockState(pos);
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
     */
    @Nullable
    public BlockState getAcceptedMaterial(World world, BlockPos pos, ItemStack item, @Nullable Direction face) {
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

        GuardrailParts.Hit part = GuardrailParts.partAt(state, localHit(hit, pos));
        if (part == null) {
            return ActionResult.PASS;
        }
        String key = GuardrailParts.key(part.face(), part.slot());

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
            // 沿用 Create 的规则：整块里只要有部件为同种物品付过账，之后就不再扣物品
            boolean freeToApply = blockEntity.alreadyPaidWith(stack);
            blockEntity.setMaterial(key, material, freeToApply ? ItemStack.EMPTY : stack);
            world.playSound(null, pos, material.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.75F);
            if (!player.isCreative() && !freeToApply) {
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
        // 副手只填横梁，柱子留空。
        // 与 Copycats+ 的 setPlacedBy 一样：遍历所有存在的部件，跳过已经有材质的，
        // 每个部件消耗一个物品。放置瞬间通常只有一个面存在，所以实际就是「填这一面的横梁」。
        for (Direction dir : FACES) {
            if (!hasFace(state, dir)) {
                continue;
            }
            String key = GuardrailParts.key(dir, Slot.ROW);
            if (blockEntity.hasMaterial(key)) {
                continue;
            }
            boolean freeToApply = blockEntity.alreadyPaidWith(offhand);
            blockEntity.setMaterial(key, material, freeToApply ? ItemStack.EMPTY : offhand);
            if (player.isCreative() || freeToApply) {
                continue;
            }
            offhand.decrement(1);
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
        GuardrailParts.Hit part = GuardrailParts.partAt(state, localHit(context));
        if (part == null) {
            return ActionResult.PASS;
        }
        String key = GuardrailParts.key(part.face(), part.slot());
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

        if (faceCount(state) <= 1) {
            // 最后一面交给默认实现：破坏方块 -> 护栏方块走战利品表，伪装材质走 onStateReplaced
            return IWrenchable.super.onSneakWrenched(state, context);
        }

        GuardrailParts.Hit hitPart = GuardrailParts.partAt(state, localHit(context));
        Direction target = hitPart == null ? null : hitPart.face();
        if (target == null || !hasFace(state, target)) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            world.setBlockState(pos, state.with(PROPERTY_BY_DIRECTION.get(target), false));
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
