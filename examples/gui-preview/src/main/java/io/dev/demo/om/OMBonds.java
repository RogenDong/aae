package io.dev.demo.om;

public sealed class OMBonds permits DoubleBond, TripleBond {
    public static final OMBonds NORMAL = new OMBonds("normal", (byte) 1);

    public final byte code;

    protected OMBonds(String name, byte code) {
//        BaseEncoding.global().bondType(name, code);
        this.code = code;
    }
}
