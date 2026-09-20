package marrydream.marisdecoration.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import marrydream.marisdecoration.block.utils.GuardrailParts.Slot;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code copycat_guardrail} 的方块实体，保存整块共享的两个伪装材质槽：柱与横梁。
 *
 * <p><b>为什么继承 Create 的 {@link SmartBlockEntity} 而不是原版 {@code BlockEntity}</b>：
 * 伪装材质必须同步到客户端才能渲染出来。原版方块实体的 NBT 默认不会随方块更新发给客户端，
 * 结果就是服务端改了材质、客户端依旧渲染成未伪装的样子。Create 的
 * {@code SyncedBlockEntity} 提供了 {@code toInitialChunkDataNbt()} / {@code getUpdatePacket()} /
 * {@code sendData()} 这一整套同步路径，正是伪装板能生效的前提。
 *
 * <p>注意：{@code SmartBlockEntity} 把 {@code writeNbt}/{@code readNbt} 标记为 final，
 * 要覆写的是带 {@code clientPacket} 参数的 {@code write}/{@code read}。
 *
 * <p>无材质的哨兵是空气；渲染层在无材质时回退到 {@code create:block/copycat_base}。
 */
public class CopycatGuardrailBlockEntity extends SmartBlockEntity implements RenderDataBlockEntity {

    private static final String KEY_COLUMN_MATERIAL = "ColumnMaterial";
    private static final String KEY_COLUMN_ITEM = "ColumnItem";
    private static final String KEY_ROW_MATERIAL = "RowMaterial";
    private static final String KEY_ROW_ITEM = "RowItem";

    /** 没有任何材质。 */
    public static final BlockState NO_MATERIAL = Blocks.AIR.getDefaultState();

    private BlockState columnMaterial = NO_MATERIAL;
    private BlockState rowMaterial = NO_MATERIAL;
    private ItemStack columnItem = ItemStack.EMPTY;
    private ItemStack rowItem = ItemStack.EMPTY;

    public CopycatGuardrailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntity.COPYCAT_GUARDRAIL, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 不需要任何 Create 行为组件
    }

    // ---------------------------------------------------------------- 材质读写

    public BlockState material(Slot slot) {
        return slot == Slot.COLUMN ? columnMaterial : rowMaterial;
    }

    public boolean hasMaterial(Slot slot) {
        return !material(slot).isAir();
    }

    public boolean hasAnyMaterial() {
        return hasMaterial(Slot.COLUMN) || hasMaterial(Slot.ROW);
    }

    public ItemStack consumedItem(Slot slot) {
        return slot == Slot.COLUMN ? columnItem : rowItem;
    }

    /** 给某个槽位附着伪装材质；{@code consumed} 是被消耗的那个物品堆。 */
    public void applyMaterial(Slot slot, BlockState material, ItemStack consumed) {
        if (slot == Slot.COLUMN) {
            columnMaterial = material;
            columnItem = consumed.copyWithCount(1);
        } else {
            rowMaterial = material;
            rowItem = consumed.copyWithCount(1);
        }
        sync();
    }

    /** 清空所有槽位并返回其中记录的物品，由调用方决定是否生成掉落物。 */
    public void clearAllMaterials() {
        columnMaterial = NO_MATERIAL;
        rowMaterial = NO_MATERIAL;
        columnItem = ItemStack.EMPTY;
        rowItem = ItemStack.EMPTY;
        sync();
    }

    /** 把两个槽位已消耗的物品都掉出来（整块被破坏时）。 */
    public void dropMaterials(World world, BlockPos pos) {
        for (Slot slot : Slot.values()) {
            ItemStack stack = consumedItem(slot);
            if (!stack.isEmpty()) {
                Block.dropStack(world, pos, stack.copy());
            }
        }
    }

    /**
     * 扳手拆除时把已消耗的物品直接塞进玩家背包。
     *
     * <p>这是 Create 伪装板/扳手的原生行为：扳手拆除返还的材料直接进背包，而不是掉在地上。
     */
    public void giveMaterialsTo(PlayerEntity player) {
        for (Slot slot : Slot.values()) {
            ItemStack stack = consumedItem(slot);
            if (!stack.isEmpty()) {
                player.getInventory().offerOrDrop(stack.copy());
            }
        }
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            // Create 的同步入口：把方块实体数据推给客户端
            notifyUpdate();
        }
    }

    /**
     * 让本方块所在的区块重新烘焙。
     *
     * <p>客户端渲染模型时才去读 {@code getRenderData()}，而区块网格是烘焙后缓存起来的。
     * 只把新材质同步过来、不主动让区块重烘焙的话，画面要等到下一次方块状态变化才会更新——
     * 表现为「材质已经生效（能掉落、能消耗），但外观还是旧的」。Create 的伪装板同样在
     * 客户端收到新材质后调这一步。
     */
    private void redraw() {
        if (world == null || !world.isClient) {
            return;
        }
        // ClientWorld.updateListeners 内部就是 worldRenderer.updateBlock(...)，本身就触发重绘，
        // 所以这里用通用 API 就够了，不需要碰客户端类。
        // 这也是 Create 的伪装板在 Fabric 上的做法（sendBlockUpdated + 光照重新评估）。
        world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        world.getLightingProvider().checkBlock(pos);
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        nbt.put(KEY_COLUMN_MATERIAL, NbtHelper.fromBlockState(columnMaterial));
        nbt.put(KEY_ROW_MATERIAL, NbtHelper.fromBlockState(rowMaterial));
        if (!clientPacket) {
            // 被消耗的物品只在服务端保存，没必要发给客户端
            nbt.put(KEY_COLUMN_ITEM, columnItem.writeNbt(new NbtCompound()));
            nbt.put(KEY_ROW_ITEM, rowItem.writeNbt(new NbtCompound()));
        }
    }

    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        BlockState previousColumn = columnMaterial;
        BlockState previousRow = rowMaterial;
        super.read(nbt, clientPacket);
        columnMaterial = readMaterial(nbt, KEY_COLUMN_MATERIAL);
        rowMaterial = readMaterial(nbt, KEY_ROW_MATERIAL);
        if (!clientPacket) {
            columnItem = readStack(nbt, KEY_COLUMN_ITEM);
            rowItem = readStack(nbt, KEY_ROW_ITEM);
        }
        // 客户端拿到新材质后必须主动重烘焙，否则要等下次方块状态变化才刷新外观
        if (clientPacket && (previousColumn != columnMaterial || previousRow != rowMaterial)) {
            redraw();
        }
    }

    private static BlockState readMaterial(NbtCompound nbt, String key) {
        if (!nbt.contains(key)) {
            return NO_MATERIAL;
        }
        BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound(key));
        return state == null ? NO_MATERIAL : state;
    }

    private static ItemStack readStack(NbtCompound nbt, String key) {
        if (!nbt.contains(key)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.fromNbt(nbt.getCompound(key));
    }

    // ---------------------------------------------------------------- 渲染数据

    /** 交给渲染层的材质数据，通过 Fabric 的 block entity render data 通道传给模型。 */
    public record Materials(BlockState column, BlockState row) {
    }

    @Override
    public @Nullable Object getRenderData() {
        return new Materials(columnMaterial, rowMaterial);
    }
}
