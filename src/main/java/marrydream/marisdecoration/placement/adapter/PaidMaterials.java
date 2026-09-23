package marrydream.marisdecoration.placement.adapter;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一次放置里「要替玩家扣掉哪些伪装材质」的账本。
 *
 * <h2>身份是方块种类</h2>
 * 用 {@link Block} 而不是完整 {@link BlockState}，也不是 slot 键名。理由来自本 mod 既有的实现：
 * <ul>
 *   <li>分层伪装薄板（{@code hasPaidFor(material)}）：同一个 BlockPos 上同一种方块只在第一次
 *       使用时消耗一个物品。一块薄板最多 66 个材质槽，逐槽扣一次会让「拆掉整块要还 66 份」对不上；</li>
 *   <li>伪装护栏在放置器这条路径上沿用同一条规则（右键那条老路径仍是逐部件独立付账，没有被改动）；</li>
 *   <li>Create 与 Copycats+ 的一次放置最多产生一个方块实体，所以「每种材质各一份」与它们原本的
 *       「每个方块实体一份」在这一步恰好等价。</li>
 * </ul>
 *
 * <h2>为什么「有没有得付」要在这里判断</h2>
 * 因为 adapter <b>不负责</b>扣物品，但它需要知道「这个材质玩家到底拿不拿得出」来决定
 * 要不要往方块实体里写 {@code consumedItem} 那一份账——写了一份不存在的账，以后拆掉时就会
 * 凭空掉出物品。所以这里提供 {@link #claim}：问「能不能付」，能就记下来、不能就什么都不做。
 * 真正的扣除仍然由 {@link PlacementContext} 的调用方（放置服务）在最后一步统一执行。
 *
 * <h2>创造模式 / 无玩家</h2>
 * {@link #free} 为 {@code true} 时 {@link #claim} 一律返回 {@code true}（不要求库存），
 * 但<b>不记账</b>——没有东西要扣，账本就是空的。
 */
public final class PaidMaterials {

    private final boolean free;
    private final Set<Block> claimed = new LinkedHashSet<>();

    /**
     * @param free 创造模式或无玩家：不要求库存、也不消耗
     */
    public PaidMaterials(boolean free) {
        this.free = free;
    }

    /** 创造模式 / 无玩家。 */
    public boolean free() {
        return free;
    }

    /** 这个材质是不是本次放置里已经记过账了。 */
    public boolean contains(BlockState material) {
        return claimed.contains(material.getBlock());
    }

    /** 已记账的方块种类个数。 */
    public int size() {
        return claimed.size();
    }

    /**
     * 申领一个材质：能付就记账并返回 {@code true}，已经在账上同样返回 {@code true}。
     *
     * @param material 要申领的材质
     * @param affordable 玩家当前拿不拿得出这个材质（创造模式传什么都行，见 {@link #free}）
     * @return 这个材质可不可以被写进方块实体
     */
    public boolean claim(BlockState material, boolean affordable) {
        if (free) {
            return true;
        }
        Block block = material.getBlock();
        if (claimed.contains(block)) {
            return true;
        }
        if (!affordable) {
            return false;
        }
        claimed.add(block);
        return true;
    }

    /** 这次放置要从玩家身上扣掉的物品：每种方块各一个。顺序稳定，便于测试断言。 */
    public Set<Item> requiredItems() {
        Set<Item> items = new LinkedHashSet<>();
        for (Block block : claimed) {
            Item item = block.asItem();
            if (item != null && item != Items.AIR) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public String toString() {
        return free ? "PaidMaterials{free}" : "PaidMaterials" + claimed;
    }
}
