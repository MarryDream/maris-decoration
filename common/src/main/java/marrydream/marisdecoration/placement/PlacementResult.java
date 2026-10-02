package marrydream.marisdecoration.placement;

import marrydream.marisdecoration.placement.adapter.PlacementContext.SkippedSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一次放置的完整结果。
 *
 * <p>不管成功还是失败都返回它（不抛异常、也不返回 {@code null}）：调用方——下一阶段的工具物品、
 * 网络包回执、以及本轮的代码级自检——都需要知道「到底发生了什么」，而不只是「成没成」。
 *
 * @param failure        失败原因；成功时为 {@code null}
 * @param adapter        实际使用的 adapter 名；没走到解析那一步时为 {@code null}
 * @param state          最终写进世界的方块状态（含正确的 {@code WATERLOGGED}）；没放置时为 {@code null}
 * @param appliedSlots   真的写下了材质的 slot 键名，顺序与处理顺序一致
 * @param skippedSlots   有预设但没写下去的 slot 及原因；类型就是 adapter 回报的那一种
 * @param paidMaterials  本次放置消耗掉的物品，每种一个；空表示这一轮什么都没消耗
 * @param structurePaid  结构方块是否被消耗了一个（创造模式为 {@code false}）
 */
public record PlacementResult(@Nullable PlacementFailure failure,
                             @Nullable String adapter,
                             @Nullable BlockState state,
                             List<String> appliedSlots,
                             List<SkippedSlot> skippedSlots,
                             List<Item> paidMaterials,
                             boolean structurePaid) {

    /** 没放置，原因给定。 */
    public static PlacementResult failed(PlacementFailure failure) {
        return new PlacementResult(failure, null, null, List.of(), List.of(), List.of(), false);
    }

    /** 放成功了。 */
    public static PlacementResult placed(String adapter, BlockState state,
                                         List<String> appliedSlots, List<SkippedSlot> skippedSlots,
                                         List<Item> paidMaterials, boolean structurePaid) {
        return new PlacementResult(null, adapter, state, List.copyOf(appliedSlots),
                List.copyOf(skippedSlots), List.copyOf(paidMaterials), structurePaid);
    }

    /** 成功了没有。 */
    public boolean success() {
        return failure == null;
    }
}
