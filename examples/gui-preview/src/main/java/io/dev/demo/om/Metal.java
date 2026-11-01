package io.dev.demo.om;

public final class Metal extends OMAtom {
    public static final Metal GOLD = new Metal("gold", 7);
    public static final Metal SILVER = new Metal("silver", 8);
    public static final Metal COPPER = new Metal("copper", 9);
    public static final Metal IRON = new Metal("iron", 10);
    public static final Metal TIN = new Metal("tin", 11);
    public static final Metal LEAD = new Metal("lead", 12);

    public Metal(String name, int code) {
        super(name, (byte) code);
    }
}
