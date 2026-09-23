package marrydream.marisdecoration.placement;

/**
 * 一次放置请求为什么没有成功。
 *
 * <p>做成枚举而不是 {@code boolean}，是因为「放置器为什么没反应」在游戏里几乎无法从画面上诊断：
 * 预设里没方块、方块不是伪装方块、目标格被占、结构方块不够——四种情况玩家看到的都是「什么都没发生」。
 * 这些状态会直接被下一阶段的工具物品翻译成动作栏提示，也会被测试断言。
 *
 * <p><b>注意这里没有「材质不够」</b>：按规格，缺材质<b>不阻止</b>结构放置，缺的槽留空即可。
 * 所以它不是一个失败原因。
 */
public enum PlacementFailure {

    /** 预设里根本没指定方块（或者指定的是空气）。 */
    NO_BLOCK("no_block"),
    /** 预设指定的方块不是本 mod 认识的伪装方块。 */
    NOT_COPYCAT("not_copycat"),
    /** 是伪装方块，但没有 adapter 认领它（正常情况下不该发生，GenericCopycatAdapter 兜底）。 */
    NO_ADAPTER("no_adapter"),
    /** 预设与方块对不上（例如预设里的属性值在这个方块上不存在），做不出合法状态。 */
    INVALID_STATE("invalid_state"),
    /** 目标位置被占用 / 这个状态在这个位置放不下（原版 {@code canPlaceAt} 不成立）。 */
    BLOCKED("blocked"),
    /**
     * 这份配置形不成任何可见结构（零部件的幽灵方块）。
     *
     * <p>护栏一个方向都没有、分层薄板占用为 0、Copycats+ multistate 一个 part 都不存在时都是它。
     * 这类状态本身是合法 BlockState、也能真的放进世界，但占着格子却什么都看不见，
     * 再想放别的东西还会提示「这里放不下」。所以服务端在 {@code setBlockState} 之前就挡住。
     */
    INVALID_STRUCTURE("invalid_structure"),
    /** 结构方块数量不够：<b>不放置、也绝不扣任何材质</b>。 */
    NO_STRUCTURE_ITEM("no_structure_item"),
    /**
     * 预设里的方块根本没有物品形态，生存模式无法为它付账。
     *
     * <p>正常流程里不会出现：能放进世界的方块都有对应的 BlockItem。
     * 留着它是为了在「第三方方块确实没有物品」时给出一个说得清的原因，而不是误报成库存不足。
     */
    NO_STRUCTURE_BLOCK_ITEM("no_structure_block_item");

    private final String id;

    PlacementFailure(String id) {
        this.id = id;
    }

    /** 稳定的短名字，给翻译键 / 日志 / 测试用。 */
    public String id() {
        return id;
    }
}
