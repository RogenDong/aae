package io.dev.aae;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CodeMap {
    private final Object lock = new Object();

    /**
     * 登记原子/键。“类型名 → 字节”
     */
    private final Map<String, Byte> codes;
    private final long[] flags = new long[]{0, 0, 0, 0};
    private final int size;
    private int nextCode = 1;

    public CodeMap(int size) {
        this.codes = new ConcurrentHashMap<>(size);
        this.size = size;
    }

    public byte add(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("type name must not be blank");
        }
        synchronized (lock) {
            byte existing = codes.getOrDefault(name, (byte) 0);
            if (existing != 0) {
                return existing;
            }

            int word, code = nextCode++;
            long mask;
            while (true) {
                if (code >= 0xFF) {
                    throw new IllegalStateException(
                            "no type codes left: a byte can express at most " + size + " distinct codes");
                }
                word = code >>> 6;
                mask = 1L << (code & 63);
                if ((flags[word] & mask) == 0) {
                    flags[word] |= mask;
                    break;
                }
                code++;
            }

            var tmp = (byte) code;
            codes.put(name, tmp);
            return tmp;
        }
    }

    public boolean add(String name, byte code) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("type name must not be blank");
        }

        int word = code >>> 6;
        long mask = 1L << (code & 63);
        if ((flags[word] & mask) != 0) {
            return false;
        }

        synchronized (lock) {
            byte existing = codes.getOrDefault(name, (byte) 0);
            if (existing != 0) {
                return existing != code;
            }

            codes.put(name, code);
            flags[word] |= mask;
            return true;
        }
    }

    public boolean containsKey(String name) {
        return codes.containsKey(name);
    }

    public Map<String, Byte> all() {
        return Map.copyOf(codes);
    }

    public int size() {
        return codes.size();
    }
}
