package io.dev.demo.om;

import java.util.Optional;

import io.dev.aae.Atom;

public sealed class OMAtom permits Salt, Elem, Quicksilver, Metal {
    public final byte code;

    protected OMAtom(String name, byte code) {
//        BaseEncoding.global().atomType(name, code);
        this.code = code;
    }

    public static Optional<OMAtom> of(int code) {
        return switch (code) {
            case 1 -> Optional.of(Salt.SALT);
            case 2 -> Optional.of(Elem.AIR);
            case 3 -> Optional.of(Elem.WATER);
            case 4 -> Optional.of(Elem.EARTH);
            case 5 -> Optional.of(Elem.FIRE);
            case 6 -> Optional.of(Quicksilver.QUICKSILVER);
            case 7 -> Optional.of(Metal.GOLD);
            case 8 -> Optional.of(Metal.SILVER);
            case 9 -> Optional.of(Metal.COPPER);
            case 10 -> Optional.of(Metal.IRON);
            case 11 -> Optional.of(Metal.TIN);
            case 12 -> Optional.of(Metal.LEAD);
            default -> Optional.empty();
        };
    }

    public static Atom salt() {
        return Atom.of(Salt.SALT.code);
    }
    
    public static Atom air() {
        return Atom.of(Elem.AIR.code);
    }
    
    public static Atom water() {
        return Atom.of(Elem.WATER.code);
    }
    
    public static Atom earth() {
        return Atom.of(Elem.EARTH.code);
    }
    
    public static Atom fire() {
        return Atom.of(Elem.FIRE.code);
    }
    
}
