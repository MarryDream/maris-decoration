package marrydream.marisdecoration.mixin.client;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the first chunk mesh in step with Copycats+ material ModelData. */
@Pseudo
@Mixin(targets = {
        "com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity",
        "com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateCopycatBlockEntity",
        "com.copycatsplus.copycats.content.copycat.fluid_pipe.CopycatFluidPipeBlockEntity",
        "com.copycatsplus.copycats.content.copycat.fluid_pipe.CopycatStraightPipeBlockEntity",
        "com.copycatsplus.copycats.content.copycat.shaft.CopycatShaftBlockEntity",
        "com.copycatsplus.copycats.content.copycat.sliding_door.CopycatSlidingDoorBlockEntity",
        "com.copycatsplus.copycats.content.copycat.cogwheel.CopycatCogWheelBlockEntity"
}, remap = false, priority = 900)
abstract class CopycatsModelDataLoadMixin {
    // Apply after Copycats+' own NeoForge onLoad mixins (priority 1000).
    @Inject(method = "onLoad", at = @At("TAIL"), remap = false)
    private void maris$refreshLoadedModelData(CallbackInfo ci) {
        BlockEntity entity = (BlockEntity) (Object) this;
        var level = entity.getLevel();
        if (level == null || !level.isClientSide) return;
        // Loading ModelData alone does not invalidate a mesh already compiled before
        // this BE was available. Empty presets do not trigger a later material change.
        entity.requestModelDataUpdate();
        var state = entity.getBlockState();
        level.sendBlockUpdated(entity.getBlockPos(), state, state, Block.UPDATE_ALL);
    }
}
