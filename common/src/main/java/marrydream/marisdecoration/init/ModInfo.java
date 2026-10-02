package marrydream.marisdecoration.init;

import net.minecraft.resources.ResourceLocation;

public final class ModInfo {
    public static final String MOD_ID = "maris_decoration";
    public static final String NAMESPACE = "maris-decoration";

    private ModInfo() {
    }

    public static ResourceLocation id(String path) {
        return location(NAMESPACE, path);
    }
    public static ResourceLocation location(String namespace, String path) {
        //? if >=1.21 {
/*return ResourceLocation.fromNamespaceAndPath(namespace, path);
*///?} else {
return new ResourceLocation(namespace, path);
//?}
    }
    public static ResourceLocation location(String value) {
        //? if >=1.21 {
/*return ResourceLocation.parse(value);
*///?} else {
return new ResourceLocation(value);
//?}
    }
    public static net.minecraft.world.level.block.state.BlockBehaviour.Properties copyProperties(net.minecraft.world.level.block.state.BlockBehaviour block) {
        //? if >=1.21 {
/*return net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(block);
*///?} else {
return net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(block);
//?}
    }
}
