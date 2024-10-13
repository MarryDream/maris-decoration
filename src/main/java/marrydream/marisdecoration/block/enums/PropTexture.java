package marrydream.marisdecoration.block.enums;

import net.minecraft.util.StringIdentifiable;

public enum PropTexture implements StringIdentifiable {
    NONE( "none" ),
    LEFT( "left" ),
    RIGHT( "right" ),
    SIDE( "side" );

    private final String name;

    private PropTexture( String name ) {
        this.name = name;
    }

    public String toString( ) {
        return this.name;
    }

    @Override
    public String asString( ) {
        return this.name;
    }
}
