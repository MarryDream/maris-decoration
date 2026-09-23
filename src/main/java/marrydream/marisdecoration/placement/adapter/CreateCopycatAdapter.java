package marrydream.marisdecoration.placement.adapter;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import marrydream.marisdecoration.placement.PlacementConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Create 原生伪装方块的 adapter（{@code copycat_base} / {@code copycat_panel} / {@code copycat_step}
 * / {@code copycat_bars}，以及任何继承 {@link CopycatBlock} 的第三方方块）。
 *
 * <h2>存储模型</h2>
 * Create 的伪装是<b>单材质</b>：{@link CopycatBlockEntity} 里只有一对
 * {@code material} / {@code consumedItem}（见 {@code CopycatBlockEntity} 源码）。所以这里只有一个
 * material slot，键名固定为 {@link #SLOT}。
 *
 * <h2>付账语义：保持 Create 原本的语义</h2>
 * Create 的规则是「<b>每个方块实体</b>记一份被消耗的物品」（{@code setConsumedItem(itemInHand)}），
 * 与「同格同材质只付一次」不同。这里刻意不做合并：
 * <ul>
 *   <li>本来就还没材质的方块实体 → 付一个；</li>
 *   <li>已经有材质的 → 跳过（不覆盖、不付账），与 Create 的 {@code hasCustomMaterial()} 判断一致。</li>
 * </ul>
 * 一次放置最多产生一个方块实体，所以「每种材质各付一次」与「每个方块实体各付一次」在这里
 * 恰好等价，语义没有被改变。
 *
 * <h2>结构</h2>
 * Create 伪装方块的「形态」全在 {@code BlockState} 里（{@code facing} / {@code axis} /
 * {@code half} / {@code shape} 等），没有任何方块实体级的结构属性，所以
 * {@link #stateFrom} 用默认实现原样返回。
 */
public final class CreateCopycatAdapter implements CopycatPlacementAdapter {

    /** 单材质槽的键名。 */
    public static final String SLOT = "create.copycat";

    private static final String LABEL = "maris-decoration.copycat_placer.slot.create_copycat";
    private static final String GROUP = "maris-decoration.copycat_placer.group.main";

    @Override
    public boolean supports(Block block) {
        return block instanceof CopycatBlock;
    }

    @Override
    public int priority() {
        return BuiltinAdapters.PRIORITY_CREATE;
    }

    @Override
    public String name() {
        return "create:copycat";
    }

    @Override
    public List<AdapterSlot> slots(BlockState state, PlacementConfig config) {
        return List.of(AdapterSlot.of(SLOT, LABEL, GROUP, true, config.slots().get(SLOT)));
    }

    /**
     * 材质准入：直接问 Create 自己。
     *
     * <p>{@link CopycatBlock#getAcceptedBlockState} 就是 Create 平铺伪装时用的那一个判据
     * （copycat_allow/deny 标签、方块实体、楼梯、完整立方体轮廓…），这里原样复用，
     * 不自己维护白名单。world/pos 传的是<b>真实世界</b>：那条「轮廓必须是完整立方体」的检查
     * 写在 {@code world != null} 里面，传 null 会让它整段被跳过（半砖、玻璃板就会混进来）。
     */
    @Override
    public boolean acceptsMaterial(BlockState material, String slotKey, PlacementConfig config, BlockView world, BlockPos pos) {
        if (material.isAir() || !(config.block() instanceof CopycatBlock copycat)) {
            return false;
        }
        return copycat.getAcceptedBlockState(CopycatPlacementAdapter.castWorld(world), pos, new ItemStack(material.getBlock()), null) != null;
    }

    @Override
    public void apply(PlacementContext context) {        if (!(context.world().getBlockEntity(context.pos()) instanceof CopycatBlockEntity blockEntity)) {
            return;
        }
        BlockState material = context.material(SLOT);
        if (material == null) {
            return;
        }
        // 已经有材质就不动它：Create 的 onUse 也是用 hasCustomMaterial() 挡住的
        if (blockEntity.hasCustomMaterial()) {
            context.markSkipped(SLOT, material, PlacementContext.SkippedSlot.Reason.ALREADY_CAMOUFLAGED);
            return;
        }
        // 服务端准入校验：不合法的材质留空，不阻止放置。
        // 客户端材质选择界面用的是同一套判据，所以这一步挡的主要是「改过包的客户端」。
        if (!context.acceptsMaterial(material, SLOT)) {
            context.markSkipped(SLOT, material, PlacementContext.SkippedSlot.Reason.REJECTED_MATERIAL);
            return;
        }
        // 拿不出这个材质就留空，不阻止放置
        if (!context.claim(material)) {
            context.markSkipped(SLOT, material, PlacementContext.SkippedSlot.Reason.NO_MATERIAL);
            return;
        }

        blockEntity.setMaterial(material);
        blockEntity.setConsumedItem(consumedStack(material));
        context.markApplied(SLOT);
    }

    /**
     * 要记进 {@code consumedItem} 的物品堆。
     *
     * <p>Create 在放置时会把<b>玩家手里的那一堆</b>记进去（{@code setConsumedItem(itemInHand)}），
     * 它只取一个（内部 {@code copyStackWithSize(stack, 1)}）。放置器没有「手里那一堆」可言，
     * 所以这里造一个只含目标方块的单件堆；效果与 Create 一致，且不携带玩家的 NBT。
     */
    private static ItemStack consumedStack(BlockState material) {
        return new ItemStack(material.getBlock());
    }
}
