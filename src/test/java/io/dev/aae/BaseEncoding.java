package io.dev.aae;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 符文系统的原子类型 / 化学键类型编码表。
 *
 * <p>模拟器本身不认识元素名或键名：{@link Atom} 使用范围为 {@code 0..0xFFFF} 的
 * {@code int} 原子类型，键类型仍为 {@code byte}。本类独立提供调用方的“名字 → 字节”
 * 编码体系，原子类型字节自动扩宽后传入引擎，由本类保证以下编码
 * 规则：</p>
 *
 * <ol>
 *   <li>字节 {@code 0} 表示“没有原子类型 / 没有键”，任何原子类型或键类型都不能占用；</li>
 *   <li>字节 {@code 0xFF} 表示“刚解除键”的临时键标记，任何键类型都不能占用；</li>
 *   <li><b>原子类型之间</b>全局互异——同一个字节不能对应两个原子名字；</li>
 *   <li><b>键类型之间</b>全局互异——同一个字节不能对应两个键名字。</li>
 * </ol>
 *
 * <p>{@link #global()} 是进程级编码表：只要所有符文都从这里取码，互异性就是全局成立的。
 * 需要隔离的调用方与测试可以 {@code new GlyphEncoding()} 建一张独立编码表。</p>
 *
 * <p>本类是线程安全的：写操作整体串行化，读路径读的是不可变快照。</p>
 */
public final class BaseEncoding {

    private static final BaseEncoding GLOBAL = new BaseEncoding();

    /**
     * 等级原子。“类型名 → 字节”
     */
    private final CodeMap atomCodes = new CodeMap(255);

    /**
     * 等级键类型。“类型名 → 字节”
     */
    private final CodeMap bondCodes = new CodeMap(254);

    /**
     * 建一张独立的编码表；进程级共享表请用 {@link #global()}。
     */
    public BaseEncoding() {
    }

    /**
     * 进程级全局编码表。
     */
    public static BaseEncoding global() {
        return GLOBAL;
    }

    /**
     * 登记一个原子类型名字，返回它的类型字节。
     *
     * <p>同名重复调用返回同一个字节。</p>
     *
     * @throws NullPointerException     名字为 {@code null}
     * @throws IllegalArgumentException 名字为空
     */
    public byte atomType(String name) {
        return atomCodes.add(name);
    }

    /**
     * 登记一个化学键类型名和字节，返回是否有效登记；若冲突则返回 {@code false}。
     *
     * <p>指定的字节一定不是 {@code 0}。同名重复时返回字节是否相同。</p>
     *
     * @throws NullPointerException     名字为 {@code null}
     * @throws IllegalArgumentException 名字为空，或类型字节为0
     */
    public boolean atomType(String name, byte code) {
        if (code == 0) {
            throw new IllegalArgumentException("type code must not be 0");
        }
        return atomCodes.add(name, code);
    }

    /**
     * 登记一个键类型名字，返回它的类型字节。
     *
     * <p>同名重复调用返回同一个字节。</p>
     *
     * @throws NullPointerException     名字为 {@code null}
     * @throws IllegalArgumentException 名字为空，或该名字已作为键类型登记
     */
    public byte bondType(String name) {
        return bondCodes.add(name);
    }

    /**
     * 登记一个化学键类型名和字节，返回是否有效登记；若冲突则返回 {@code false}。
     *
     * <p>指定的字节一定不是 {@code 0} 也不是 {@link Atom#TEMPORARY_BOND}，因此可以直接当作长期键写入棋盘。</p>
     * 同名重复时返回字节是否相同。
     *
     * @throws NullPointerException     名字为 {@code null}
     * @throws IllegalArgumentException 名字为空，或该名字已作为原子类型登记
     */
    public boolean bondType(String name, byte code) {
        Atom.requireBondCode(code);
        return bondCodes.add(name, code);
    }

    /**
     * 该原子类型名字是否已登记。
     */
    public boolean hasAtomType(String name) {
        return atomCodes.containsKey(name);
    }

    /**
     * 该键类型名字是否已登记。
     */
    public boolean hasBondType(String name) {
        return bondCodes.containsKey(name);
    }

    /**
     * 全部原子类型编码，按登记顺序。
     */
    public Map<String, Byte> atomTypes() {
        return atomCodes.all();
    }

    /**
     * 全部键类型编码，按登记顺序。
     */
    public Map<String, Byte> bondTypes() {
        return bondCodes.all();
    }

    /**
     * 校验多个原子类型字节两两互异且都不是 {@code 0}。
     */
    public static void requireDistinctAtomCodes(byte... codes) {
        requireDistinctCodes(true, codes);
    }

    /**
     * 校验多个键类型字节两两互异且都不是 {@code 0/0xFF}。
     */
    public static void requireDistinctBondCodes(byte... codes) {
        requireDistinctCodes(false, codes);
    }

    /**
     * 校验多个类型字节两两互异且都不是 {@code 0/0xFF}。
     */
    private static void requireDistinctCodes(boolean atom, byte... codes) throws IllegalArgumentException {
        Objects.requireNonNull(codes, "codes");
        Set<Byte> seen = new LinkedHashSet<>();
        for (byte code : codes) {
            if (atom) {
                if (code == 0) {
                    throw new IllegalArgumentException("type code must not be 0");
                }
            } else {
                Atom.requireBondCode(code);
            }
            if (!seen.add(code)) {
                throw new IllegalArgumentException(
                        "atom types must be globally distinct, duplicated byte: " + (code & 0xFF));
            }
        }
    }

    @Override
    public String toString() {
        return "GlyphEncoding[atoms=" + atomCodes.size() + ", bonds=" + bondCodes.size() + "]";
    }
}
