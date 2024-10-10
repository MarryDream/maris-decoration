package marrydream.marisdecoration.block.enums;

import net.minecraft.util.StringIdentifiable;

public enum PropHalf implements StringIdentifiable {
    TOP( "top" ),
    BOTTOM( "bottom" );

    private final String name;

    private PropHalf( String name ) {
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
