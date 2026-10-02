package marrydream.marisdecoration.placement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 「放置要付出的代价」这一层的唯一入口。
 *
 * <p>全部放置路径都经过它，所以「创造模式不消耗、生存模式扣一个」这条规则只写了一遍，
 * 不可能出现「结构方块扣了、材质没扣」或者反过来的中间态。
 *
 * <h2>为什么是「先问、后扣」两步</h2>
 * 需求里有一条硬约束：<b>没有结构方块时不放置，也不扣任何材质</b>。如果边放边扣，就会出现
 * 「方块已经放进世界、材质扣到一半发现结构方块不够」的半成品。所以这里把付账拆开：
 * 先用 {@link #has} 把所有要付的东西问一遍，全都有才真正放方块，最后用 {@link #extract}
 * 一次性扣掉。中间任何一步失败都还没有产生任何副作用。
 *
 * <h2>创造模式</h2>
 * {@link #has} 对创造模式恒为 {@code true}，{@link #extract} 直接返回 {@link ItemStack#EMPTY}，
 * 也就是「不要求库存里有、也不消耗」。判断只看 {@link Player#isCreative()}，
 * 与 Create / 原版放置的行为一致。
 *
 * <p>没有玩家（例如区块加载时的自动补全、或测试里的无玩家放置）按<b>创造模式</b>处理：
 * 没有可扣的对象，硬要「消耗」只会失败。
 */
public final class InventoryPayment {

    private final Player player;

    public InventoryPayment(Player player) {
        this.player = player;
    }

    /**
     * 是不是「不用付账」的语境：创造模式，或者根本没有玩家。
     *
     * <p>这个判断是公开的，因为放置服务需要知道「结构方块够不够」这件事在创造模式下恒成立，
     * 从而跳过「预设里没写方块」之外的一切库存检查。
     */
    public boolean free() {
        return player == null || player.isCreative();
    }

    /** 玩家能不能拿出一个这种物品。 */
    public boolean has(Item item) {
        if (free()) {
            return true;
        }
        return count(item) > 0;
    }

    /** 玩家能不能拿出清单上的每一种物品各一个。 */
    public boolean hasAll(Iterable<Item> items) {
        if (free()) {
            return true;
        }
        for (Item item : items) {
            if (!has(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 真正扣掉一个这种物品。
     *
     * @return 被扣掉的那一个（创造模式 / 没有玩家时是空堆）。
     *         调用方<b>必须</b>先用 {@link #has} 确认过，否则可能返回空堆而物品并未被扣。
     */
    public ItemStack extract(Item item) {
        if (free()) {
            return ItemStack.EMPTY;
        }
        int slot = findSlot(item);
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        ItemStack taken = stack.copyWithCount(1);
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        // 客户端不知道服务端改过库存，必须显式同步一次
        player.containerMenu.sendAllDataToRemote();
        return taken;
    }

    /** 扣掉清单上每一种物品各一个，返回实际扣掉的堆（创造模式为空列表）。 */
    public List<ItemStack> extractAll(Iterable<Item> items) {
        if (free()) {
            return List.of();
        }
        List<ItemStack> taken = new ArrayList<>();
        for (Item item : items) {
            ItemStack stack = extract(item);
            if (!stack.isEmpty()) {
                taken.add(stack);
            }
        }
        return taken;
    }

    /** 主手 / 副手 / 快捷栏 / 背包一起算，只看主物品栏。 */
    private int count(Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 从前往后找第一个装着这种物品的格子；没有返回 -1。 */
    private int findSlot(Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }
        return -1;
    }
}
