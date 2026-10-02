package marrydream.marisdecoration.init;

import net.minecraft.resources.ResourceLocation;

public final class ModInfo {
    public static final String MOD_ID = "maris-decoration";

    private ModInfo() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
