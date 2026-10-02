package marrydream.marisdecoration.platform;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
public abstract class RenderDataBlockEntity extends SmartBlockEntity implements net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity {
 protected RenderDataBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type,pos,state); }
 protected void renderDataChanged() {  }
}
