package marrydream.marisdecoration.init;

import net.minecraft.resources.ResourceLocation;

public final class ModInfo {
    public static final String MOD_ID = "maris_decoration";
    public static final String NAMESPACE = "maris-decoration";

    private ModInfo() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NAMESPACE, path);
    }
}
