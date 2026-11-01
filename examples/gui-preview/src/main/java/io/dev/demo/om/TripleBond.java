package io.dev.demo.om;

public final class TripleBond extends OMBonds {
    public static final TripleBond THIRD_1 = new TripleBond("third1", 2);
    public static final TripleBond THIRD_2 = new TripleBond("third2", 4);
    public static final TripleBond THIRD_3 = new TripleBond("third3", 6);
    public static final TripleBond THIRD_4 = new TripleBond("third4", 8);
    public static final TripleBond THIRD_5 = new TripleBond("third5", 10);
    public static final TripleBond THIRD_6 = new TripleBond("third6", 12);
    public static final TripleBond THIRD_7 = new TripleBond("third7", 14);

    private TripleBond(String name, int code) {
        super(name, (byte) code);
    }
}
