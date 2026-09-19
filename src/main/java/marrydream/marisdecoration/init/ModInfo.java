package marrydream.marisdecoration.init;

import net.minecraft.util.Identifier;

public final class ModInfo {
    public static final String MOD_ID = "maris-decoration";

    private ModInfo() {
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
