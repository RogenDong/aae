package io.dev.aae.om;

public final class Quicksilver extends OMAtom {
    public static final Quicksilver QUICKSILVER = new Quicksilver("quicksilver", 6);

    private Quicksilver(String name, int code) {
        super(name, (byte) code);
    }
}
