package io.dev.aae.om;

public final class DoubleBond extends OMBonds {
    public static final DoubleBond PAIR_1 = new DoubleBond("pair1", 0x10);
    public static final DoubleBond PAIR_2 = new DoubleBond("pair2", 0x20);
    public static final DoubleBond PAIR_3 = new DoubleBond("pair3", 0x30);

    private DoubleBond(String name, int code) {
        super(name, (byte) code);
    }
}
