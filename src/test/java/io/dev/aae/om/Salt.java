package io.dev.aae.om;

public final class Salt extends OMAtom {
    public static final Salt SALT = new Salt("salt", 1);

    private Salt(String name, int code) {
        super(name, (byte) code);
    }
}
