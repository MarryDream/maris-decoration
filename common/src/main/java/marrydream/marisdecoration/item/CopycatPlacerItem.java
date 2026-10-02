package marrydream.marisdecoration.item;

import marrydream.marisdecoration.placement.PlacementConfig;
import marrydream.marisdecoration.placement.PlacementConfigs;
import marrydream.marisdecoration.placement.PlacementFeedback;
import marrydream.marisdecoration.placement.PlacementResult;
import marrydream.marisdecoration.placement.PlacementService;
import marrydream.marisdecoration.placement.client.PlacerClientBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 伪装放置器。
 *
 * <h2>两个入口，靠原版的命中判定分开</h2>
 * <ul>
 *   <li><b>对空气右键</b>（{@link #use}）→ 打开配置界面（只在客户端真的开）；</li>
 *   <li><b>对方块右键</b>（{@link #useOn}）→ 按携带的预设调
 *       {@link PlacementService} 真正放置。</li>
 * </ul>
 * 不冲突：原版交互管理器先看有没有命中方块，命中走 {@code useOnBlock}，没命中才走 {@code use}。
 *
 * <h2>配置存在哪</h2>
 * 只存在<b>这一把物品自己的 NBT</b> 里（{@link PlacementConfigs}），所以关界面、换快捷栏、
 * 丢在地上再捡起来都还在。不引入任何全局状态——玩家的每一把放置器都可以有不同预设。
 *
 * <h2>为什么放置不交给被点方块</h2>
 * 放置器面对的是「玩家手里有一份想放的东西」，命中什么方块不重要（空气、石头、水都行，
 * 只要那一格放得下）。所以它不走进 {@code BlockState#onUse}，而是自己调服务算落点与形态。
 * 这与「原来的 BlockItem 放置方式」是两条互不影响的路径。
 */
public class CopycatPlacerItem extends Item {

    /** 物品 id 的路径部分。 */
    public static final String ID = "copycat_placer";

    public CopycatPlacerItem() {
        // 工具：不堆叠
        super(new Properties().stacksTo(1));
    }

    /**
     * 对空气右键 → 打开配置界面。
     *
     * <p>服务端也返回 SUCCESS：让手臂挥动有反馈，同时不让这次交互继续往下传。
     * 界面本身由客户端钩子打开（{@link PlacerClientBridge}），专用服务端上那个钩子是空的。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) {
            PlacerClientBridge.openConfigScreen(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * 对方块右键 → 按预设放置。
     *
     * <p>放置只在服务端执行（{@link PlacementService} 自己也会挡住客户端调用），
     * 客户端返回 SUCCESS 只是为了手臂挥动。
     *
     * <h2>落点怎么定</h2>
     * 用标准 BlockItem 的目标格语义（{@link PlacementService#resolveTargetPos}）：
     * 被点那格可替换就放那一格，否则放它沿点击面方向的相邻格。
     *
     * <p><b>点击面只决定「放到哪一格」</b>，绝不参与决定方块形态——朝向、分层薄板的
     * Face/Layer、护栏的方向、以及其它结构属性全部来自预设。这与 {@code BlockItem} 默认的
     * 「按点击面定向」是不同的，正是放置器要绕过的那件事。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos target = PlacementService.resolveTargetPos(world, context.getClickedPos(), context.getClickedFace());
        PlacementConfig config = PlacementConfigs.read(context.getItemInHand());
        PlacementResult result = PlacementService.place(world, target, config, player);
        // 该说的话都在这里统一发出：没选方块 / 伪装板不足会说，成功与「材质不足」保持安静。
        // 对照表见 PlacementFeedback 的类注释。
        PlacementFeedback.report(player, result);
        return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * tooltip：让玩家一眼看出这把工具当前配了什么。
     *
     * <p>只读物品 NBT，不做世界查询，所以不会因为客户端没加载对应区块而显示不一致。
     */
    @Override
    //? if >=1.21 {
/*public void appendHoverText(ItemStack stack, Item.TooltipContext world, List<Component> tooltip, TooltipFlag context) {
*///?} else {
public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
//?}
        PlacementConfig config = PlacementConfigs.read(stack);
        if (config.state() == null) {
            tooltip.add(Component.translatable("item.maris-decoration.copycat_placer.tooltip.empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.maris-decoration.copycat_placer.tooltip.block",
                    config.state().getBlock().getName()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.maris-decoration.copycat_placer.tooltip.materials",
                    config.slots().size()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.maris-decoration.copycat_placer.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
