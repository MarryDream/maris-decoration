package marrydream.marisdecoration.block;

import com.simibubi.create.content.redstone.RoseQuartzLampBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import marrydream.marisdecoration.init.ModBlock;
import marrydream.marisdecoration.init.ModBlockEntity;
import marrydream.marisdecoration.platform.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import marrydream.marisdecoration.platform.StackData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CopycatLadderBlockEntity extends RenderDataBlockEntity {
    private static final String MATERIAL_DATA = "material_data";
    private static final String MATERIAL = "material";
    private static final String CONSUMED = "consumedItem";
    public static final BlockState NO_MATERIAL = Blocks.AIR.defaultBlockState();

    private final Map<String, BlockState> materials = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> consumedItems = new ConcurrentHashMap<>();
    private volatile RenderData renderData;

    public CopycatLadderBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(ModBlock.COPYCAT_STEEL_FIXED_LADDER)
                ? ModBlockEntity.COPYCAT_STEEL_FIXED_LADDER
                : ModBlockEntity.COPYCAT_STEEL_VERTICAL_LADDER, pos, state);
        for (String slot : slots()) {
            materials.put(slot, NO_MATERIAL);
            consumedItems.put(slot, ItemStack.EMPTY);
        }
    }

    private List<String> slots() {
        return getBlockState().is(ModBlock.COPYCAT_STEEL_FIXED_LADDER)
                ? CopycatLadderParts.fixedSlots() : CopycatLadderParts.verticalSlots();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public BlockState material(String slot) {
        return materials.getOrDefault(slot, NO_MATERIAL);
    }

    public boolean hasMaterial(String slot) {
        return !material(slot).isAir();
    }

    public boolean hasMaterialBlock(Block block) {
        return materials.values().stream().anyMatch(state -> !state.isAir() && state.is(block));
    }

    public void setMaterial(String slot, BlockState material, @Nullable ItemStack consumed) {
        materials.put(slot, material);
        if (consumed != null && !consumed.isEmpty()) {
            consumedItems.put(slot, consumed.copyWithCount(1));
        }
        syncData();
    }

    public boolean cycleMaterial(String slot) {
        BlockState material = material(slot);
        BlockState next;
        if (material.hasProperty(BlockStateProperties.HALF) && material.getOptionalValue(BlockStateProperties.OPEN).orElse(false)) {
            next = material.cycle(BlockStateProperties.HALF);
        } else if (material.hasProperty(BlockStateProperties.FACING)) {
            next = material.cycle(BlockStateProperties.FACING);
        } else if (material.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            next = material.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    material.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
        } else if (material.hasProperty(BlockStateProperties.AXIS)) {
            next = material.cycle(BlockStateProperties.AXIS);
        } else if (material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            next = material.cycle(BlockStateProperties.HORIZONTAL_AXIS);
        } else if (material.hasProperty(BlockStateProperties.LIT)) {
            next = material.cycle(BlockStateProperties.LIT);
        } else if (material.hasProperty(RoseQuartzLampBlock.POWERING)) {
            next = material.cycle(RoseQuartzLampBlock.POWERING);
        } else {
            return false;
        }
        materials.put(slot, next);
        syncData();
        return true;
    }

    public ItemStack takeConsumedItemForRemoval(String slot) {
        ItemStack returned = consumedItems.getOrDefault(slot, ItemStack.EMPTY);
        BlockState removed = material(slot);
        materials.put(slot, NO_MATERIAL);
        consumedItems.put(slot, ItemStack.EMPTY);
        if (!returned.isEmpty() && !removed.isAir()) {
            for (String other : slots()) {
                if (!other.equals(slot) && material(other).is(removed.getBlock())) {
                    if (consumedItems.getOrDefault(other, ItemStack.EMPTY).isEmpty()) {
                        consumedItems.put(other, returned);
                    }
                    returned = ItemStack.EMPTY;
                    break;
                }
            }
        }
        syncData();
        return returned;
    }

    public void clearConsumedItems() {
        for (String slot : slots()) consumedItems.put(slot, ItemStack.EMPTY);
        setChanged();
    }

    public void dropAllMaterials(Level world, BlockPos pos) {
        for (String slot : slots()) {
            ItemStack stack = consumedItems.getOrDefault(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) Block.popResource(world, pos, stack.copy());
        }
    }

    private void syncData() {
        renderData = null;
        renderDataChanged();
        setChanged();
        if (level == null) return;
        if (!level.isClientSide) notifyUpdate();
        else redraw();
    }

    private void redraw() {
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.getLightEngine().checkBlock(worldPosition);
        }
    }

    @Override
    //? if >=1.21 {
/*protected void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
*///?} else {
protected void write(CompoundTag nbt, boolean clientPacket) {
        HolderLookup.Provider registries = null;
//?}
        //? if >=1.21 {
/*super.write(nbt, registries, clientPacket);
*///?} else {
super.write(nbt, clientPacket);
//?}
        CompoundTag data = new CompoundTag();
        for (String slot : slots()) {
            BlockState material = material(slot);
            ItemStack consumed = consumedItems.getOrDefault(slot, ItemStack.EMPTY);
            if (material.isAir() && consumed.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.put(MATERIAL, NbtUtils.writeBlockState(material));
            if (!clientPacket) entry.put(CONSUMED, StackData.save(consumed, registries));
            data.put(slot, entry);
        }
        nbt.put(MATERIAL_DATA, data);
    }

    @Override
    //? if >=1.21 {
/*protected void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
*///?} else {
protected void read(CompoundTag nbt, boolean clientPacket) {
        HolderLookup.Provider registries = null;
//?}
        Map<String, BlockState> before = Map.copyOf(materials);
        //? if >=1.21 {
/*super.read(nbt, registries, clientPacket);
*///?} else {
super.read(nbt, clientPacket);
//?}
        CompoundTag data = nbt.getCompound(MATERIAL_DATA);
        for (String slot : slots()) {
            CompoundTag entry = data.getCompound(slot);
            materials.put(slot, entry.contains(MATERIAL)
                    ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound(MATERIAL))
                    : NO_MATERIAL);
            if (!clientPacket) {
                consumedItems.put(slot, entry.contains(CONSUMED)
                        ? StackData.read(entry.getCompound(CONSUMED), registries) : ItemStack.EMPTY);
            }
        }
        renderData = null;
        renderDataChanged();
        if (clientPacket && !before.equals(materials)) redraw();
    }

    public record RenderData(Map<String, BlockState> materials) {
    }

    @Override
    public @Nullable Object getRenderData() {
        RenderData cached = renderData;
        if (cached != null) return cached;
        RenderData built = new RenderData(Map.copyOf(materials));
        renderData = built;
        return built;
    }
}
