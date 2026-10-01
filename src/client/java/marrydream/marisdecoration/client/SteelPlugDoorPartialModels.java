package marrydream.marisdecoration.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import marrydream.marisdecoration.init.ModInfo;

/** Leaf-only models used by the steel plug door block-entity renderer. */
public final class SteelPlugDoorPartialModels {
    public static final PartialModel BOTTOM_LEFT = leaf("bottom_left");
    public static final PartialModel BOTTOM_RIGHT = leaf("bottom_right");
    public static final PartialModel TOP_LEFT = leaf("top_left");
    public static final PartialModel TOP_RIGHT = leaf("top_right");
    public static final PartialModel TOP_CAP_LEFT = leaf("top_cap_left");
    public static final PartialModel TOP_CAP_RIGHT = leaf("top_cap_right");
    public static final PartialModel THRESHOLD_TOP_LEFT = leaf("threshold_top_left");
    public static final PartialModel THRESHOLD_TOP_RIGHT = leaf("threshold_top_right");
    public static final PartialModel LINTEL_BOTTOM_LEFT = leaf("lintel_bottom_left");
    public static final PartialModel LINTEL_BOTTOM_RIGHT = leaf("lintel_bottom_right");

    private SteelPlugDoorPartialModels() {
    }

    private static PartialModel leaf(String name) {
        return PartialModel.of(ModInfo.id("block/steel_plug_door/animated/" + name));
    }

    public static void init() {
        // Class loading registers all partials before model baking.
    }
}
