package io.dev.demo.om;

public final class Elem extends OMAtom {
    public static final Elem AIR = new Elem("air", 2);
    public static final Elem WATER = new Elem("water", 3);
    public static final Elem EARTH = new Elem("earth", 4);
    public static final Elem FIRE = new Elem("fire", 5);

    private Elem(String name, int code) {
        super(name, (byte) code);
    }
}
