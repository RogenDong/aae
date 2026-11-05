package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link Atom} 只处理数值：原子类型为 16 位有效范围的 {@code int}，键类型仍为字节，
 * 模拟器语义只依赖两条键约定——{@code 0} 是无键，{@code 0xFF} 是临时键。
 */
class AtomTest {

    @Test
    void newAtomHasNoBonds() {
        for (int type : new int[]{0, 1, 0xFF, 0x0101, 0x8000, 0xFFFF}) {
            Atom atom = Atom.of(type);

            assertEquals(type, atom.type());
            assertEquals(0, atom.bondCodes());
            assertEquals(0, atom.bondCount());
            assertEquals(List.of(), atom.bondedDirections());
            for (Direction direction : Direction.values()) {
                assertEquals((byte) 0, atom.bondType(direction));
                assertFalse(atom.hasBond(direction));
                assertFalse(atom.hasRealBond(direction));
            }
        }
    }

    @Test
    void eachDirectionKeepsItsOwnBondByte() {
        Atom atom = Atom.of(0xFFFF)
                .withBond(Direction.NORTH, 2)
                .withBond(Direction.EAST, 3)
                .withBond(Direction.SOUTH, 4)
                .withBond(Direction.WEST, 5);

        assertEquals((byte) 2, atom.bondType(Direction.NORTH));
        assertEquals((byte) 3, atom.bondType(Direction.EAST));
        assertEquals((byte) 4, atom.bondType(Direction.SOUTH));
        assertEquals((byte) 5, atom.bondType(Direction.WEST));
        assertEquals(4, atom.bondCount());
        assertEquals(0xFFFF, atom.type());
        assertEquals(
                List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST),
                atom.bondedDirections());
    }

    @Test
    void writingOneDirectionLeavesTheOtherThreeAlone() {
        Atom base = Atom.of(5)
                .withBond(Direction.NORTH, 2)
                .withBond(Direction.SOUTH, 2);

        Atom rewritten = base.withBond(Direction.EAST, 5);

        assertEquals((byte) 2, rewritten.bondType(Direction.NORTH));
        assertEquals((byte) 2, rewritten.bondType(Direction.SOUTH));
        assertEquals((byte) 0, rewritten.bondType(Direction.WEST));
        // 原实例不受影响：原子是不可变的。
        assertEquals((byte) 0, base.bondType(Direction.EAST));
    }

    @Test
    void temporaryBondIsNotARealBond() {
        Atom atom = Atom.of(4).withBond(Direction.WEST, Atom.TEMPORARY_BOND);

        assertTrue(atom.hasBond(Direction.WEST));
        assertFalse(atom.hasRealBond(Direction.WEST));
        assertEquals(0, atom.bondCount());
        assertEquals(List.of(), atom.bondedDirections());
    }

    @Test
    void realBondsAreEveryNonZeroByteExceptTheTemporaryOne() {
        assertFalse(Atom.isRealBond((byte) 0));
        assertFalse(Atom.isRealBond(Atom.TEMPORARY_BOND));
        for (int code = 1; code < 255; code++) {
            assertTrue(Atom.isRealBond((byte) code), "bond byte " + code);
        }
    }

    @Test
    void withoutBondAndWithoutBondsClearExactlyWhatTheySay() {
        Atom atom = Atom.of(0x8000)
                .withBond(Direction.NORTH, 2)
                .withBond(Direction.EAST, 2);

        Atom cleared = atom.withoutBond(Direction.NORTH);
        assertEquals((byte) 0, cleared.bondType(Direction.NORTH));
        assertEquals((byte) 2, cleared.bondType(Direction.EAST));

        assertEquals(0, atom.withoutBonds().bondCodes());
        assertEquals(0, cleared.withoutBonds().bondCount());
        assertEquals(0x8000, cleared.type());
        assertEquals(0x8000, atom.withoutBonds().type());
        Atom temporary = atom.withBond(Direction.WEST, Atom.TEMPORARY_BOND);
        assertEquals(atom, temporary.withoutTemporaryBonds());
        assertEquals(0x8000, new Atom(0x8000, -1).withoutTemporaryBonds().type());
        assertEquals(0, new Atom(0x8000, -1).withoutTemporaryBonds().bondCodes());
    }

    @Test
    void withTypeKeepsTheBondBytes() {
        Atom atom = Atom.of(0x0101).withBond(Direction.EAST, 2);

        for (int type : new int[]{0, 0xFF, 0x8000, 0xFFFF}) {
            Atom transformed = atom.withType(type);

            assertEquals(type, transformed.type());
            assertEquals(atom.bondCodes(), transformed.bondCodes());
            assertEquals((byte) 2, transformed.bondType(Direction.EAST));
        }
        for (int type : new int[]{-1, -3, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> atom.withType(type));
        }
    }

    @Test
    void rotatingBondsMovesThemClockwise() {
        Atom atom = Atom.of(9)
                .withBond(Direction.NORTH, 2)
                .withBond(Direction.EAST, 4);

        Atom once = atom.rotateBonds(1);
        assertEquals((byte) 2, once.bondType(Direction.EAST));
        assertEquals((byte) 4, once.bondType(Direction.SOUTH));
        assertEquals((byte) 0, once.bondType(Direction.NORTH));

        Atom thrice = atom.rotateBonds(3);
        assertEquals((byte) 2, thrice.bondType(Direction.WEST));
        assertEquals((byte) 4, thrice.bondType(Direction.NORTH));
    }

    @Test
    void rotatingByAFullTurnOrNegativeTurnsIsWellDefined() {
        Atom atom = Atom.of(0xFFFF)
                .withBond(Direction.NORTH, 2)
                .withBond(Direction.SOUTH, 1);

        assertEquals(atom, atom.rotateBonds(0));
        assertEquals(atom, atom.rotateBonds(4));
        assertEquals(atom, atom.rotateBonds(-4));
        assertEquals(atom.rotateBonds(1), atom.rotateBonds(-3));
        assertEquals(atom.rotateBonds(3), atom.rotateBonds(-1));
        // 旋转不改变原子类型，也不丢失临时键。
        assertEquals(0xFFFF, atom.rotateBonds(1).type());
        assertTrue(atom.rotateBonds(1).hasBond(Direction.WEST));
    }

    @Test
    void packedCodesRoundTripAndAtomTypeIsValidated() {
        Atom atom = Atom.of(0x8000)
                .withBond(Direction.NORTH, 5)
                .withBond(Direction.SOUTH, 1);

        assertEquals(atom, new Atom(atom.type(), atom.bondCodes()));

        // 原子类型校验范围；键字节仍原样保存与返回。
        int unusual = 0x7F << (Direction.EAST.ordinal() * Byte.SIZE);
        Atom raw = new Atom(0xFFFF, unusual);
        assertEquals(0xFFFF, raw.type());
        assertEquals((byte) 0x7F, raw.bondType(Direction.EAST));
        assertTrue(raw.hasBond(Direction.EAST));
        assertTrue(raw.hasRealBond(Direction.EAST));
        for (int type : new int[]{0, 0xFF, 0x0101, 0x8000, 0xFFFF}) {
            Atom.requireAtomCode(type);
            assertEquals(type, new Atom(type, unusual).type());
        }
        for (int type : new int[]{-1, -3, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new Atom(type, unusual));
            assertThrows(IllegalArgumentException.class, () -> Atom.of(type));
            assertThrows(IllegalArgumentException.class, () -> Atom.requireAtomCode(type));
        }
    }

    @Test
    void equalityIsByTypeAndBondBytes() {
        Atom atom = Atom.of(0x0101).withBond(Direction.NORTH, 2);

        assertEquals(atom, new Atom(0x0101, atom.bondCodes()));
        assertEquals(atom.hashCode(),
                new Atom(0x0101, atom.bondCodes()).hashCode());
        assertNotEquals(atom, atom.withType(0x0201));
        assertNotEquals(atom, atom.withBond(Direction.EAST, 2));
    }

    @Test
    void toStringUsesRawNumbers() {
        Atom atom = Atom.of(0xFFFF).withBond(Direction.WEST, 2);

        assertEquals("type=65535 N=0 E=0 S=0 W=2", atom.toString());
    }
}
