package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link Atom} 只处理数值：下面的类型字节和键字节都是任意的示例数值，
 * 模拟器语义只依赖两条约定——{@code 0} 是无键，{@code 1} 是临时键。
 */
class AtomTest {

    @Test
    void newAtomHasNoBonds() {
        Atom atom = Atom.of((byte) 1);

        assertEquals((byte) 1, atom.type());
        assertEquals(0, atom.bondCodes());
        assertEquals(0, atom.bondCount());
        assertEquals(List.of(), atom.bondedDirections());
        for (Direction direction : Direction.values()) {
            assertEquals((byte) 0, atom.bondType(direction));
            assertFalse(atom.hasBond(direction));
            assertFalse(atom.hasRealBond(direction));
        }
    }

    @Test
    void eachDirectionKeepsItsOwnBondByte() {
        Atom atom = Atom.of((byte) 10)
                .withBond(Direction.NORTH, (byte) 2)
                .withBond(Direction.EAST, (byte) 3)
                .withBond(Direction.SOUTH, (byte) 4)
                .withBond(Direction.WEST, (byte) 5);

        assertEquals((byte) 2, atom.bondType(Direction.NORTH));
        assertEquals((byte) 3, atom.bondType(Direction.EAST));
        assertEquals((byte) 4, atom.bondType(Direction.SOUTH));
        assertEquals((byte) 5, atom.bondType(Direction.WEST));
        assertEquals(4, atom.bondCount());
        assertEquals(
                List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST),
                atom.bondedDirections());
    }

    @Test
    void writingOneDirectionLeavesTheOtherThreeAlone() {
        Atom base = Atom.of((byte) 5)
                .withBond(Direction.NORTH, (byte) 2)
                .withBond(Direction.SOUTH, (byte) 2);

        Atom rewritten = base.withBond(Direction.EAST, (byte) 5);

        assertEquals((byte) 2, rewritten.bondType(Direction.NORTH));
        assertEquals((byte) 2, rewritten.bondType(Direction.SOUTH));
        assertEquals((byte) 0, rewritten.bondType(Direction.WEST));
        // 原实例不受影响：原子是不可变的。
        assertEquals((byte) 0, base.bondType(Direction.EAST));
    }

    @Test
    void temporaryBondIsNotARealBond() {
        Atom atom = Atom.of((byte) 4).withBond(Direction.WEST, Atom.TEMPORARY_BOND);

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
        Atom atom = Atom.of((byte) 7)
                .withBond(Direction.NORTH, (byte) 2)
                .withBond(Direction.EAST, (byte) 2);

        Atom cleared = atom.withoutBond(Direction.NORTH);
        assertEquals((byte) 0, cleared.bondType(Direction.NORTH));
        assertEquals((byte) 2, cleared.bondType(Direction.EAST));

        assertEquals(0, atom.withoutBonds().bondCodes());
        assertEquals(0, cleared.withoutBonds().bondCount());
    }

    @Test
    void withTypeKeepsTheBondBytes() {
        // 只清掉原子类型位
        Atom atom = Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2);

        Atom transformed = atom.withType((byte) 16);

        assertEquals((byte) 16, transformed.type());
        assertEquals(atom.bondCodes(), transformed.bondCodes());
        assertEquals((byte) 2, transformed.bondType(Direction.EAST));
    }

    @Test
    void rotatingBondsMovesThemClockwise() {
        Atom atom = Atom.of((byte) 9)
                .withBond(Direction.NORTH, (byte) 2)
                .withBond(Direction.EAST, (byte) 4);

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
        Atom atom = Atom.of((byte) 11)
                .withBond(Direction.NORTH, (byte) 2)
                .withBond(Direction.SOUTH, (byte) 1);

        assertEquals(atom, atom.rotateBonds(0));
        assertEquals(atom, atom.rotateBonds(4));
        assertEquals(atom, atom.rotateBonds(-4));
        assertEquals(atom.rotateBonds(1), atom.rotateBonds(-3));
        assertEquals(atom.rotateBonds(3), atom.rotateBonds(-1));
        // 旋转不改变原子类型，也不丢失临时键。
        assertEquals((byte) 11, atom.rotateBonds(1).type());
        assertTrue(atom.rotateBonds(1).hasBond(Direction.WEST));
    }

    @Test
    void packedCodesRoundTripAndAnyByteIsAccepted() {
        Atom atom = Atom.of((byte) 12)
                .withBond(Direction.NORTH, (byte) 5)
                .withBond(Direction.SOUTH, (byte) 1);

        assertEquals(atom, Atom.of((byte) 12, atom.bondCodes()));

        // 项目不校验取值范围：调用方给出的字节原样保存与返回。
        int unusual = 0x7F << (Direction.EAST.ordinal() * Byte.SIZE);
        Atom raw = Atom.of((byte) -3, unusual);
        assertEquals((byte) -3, raw.type());
        assertEquals((byte) 0x7F, raw.bondType(Direction.EAST));
        assertTrue(raw.hasBond(Direction.EAST));
        assertTrue(raw.hasRealBond(Direction.EAST));
    }

    @Test
    void equalityIsByTypeAndBondBytes() {
        Atom atom = Atom.of((byte) 14).withBond(Direction.NORTH, (byte) 2);

        assertEquals(atom, Atom.of((byte) 14).withBond(Direction.NORTH, (byte) 2));
        assertEquals(atom.hashCode(),
                Atom.of((byte) 14).withBond(Direction.NORTH, (byte) 2).hashCode());
        assertNotEquals(atom, atom.withType((byte) 13));
        assertNotEquals(atom, atom.withBond(Direction.EAST, (byte) 2));
    }

    @Test
    void toStringUsesRawNumbers() {
        Atom atom = Atom.of((byte) 1).withBond(Direction.WEST, (byte) 2);

        assertEquals("type=1 WEST=2", atom.toString());
    }
}
