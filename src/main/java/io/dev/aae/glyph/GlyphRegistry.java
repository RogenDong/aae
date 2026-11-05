package io.dev.aae.glyph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 进程级全局符文类型注册表。
 *
 * <p>自定义符文通过 {@link #register(GlyphDefinition)} 注册；注册返回一个可关闭句柄，
 * 关闭句柄只影响之后构造的计划，已经构造好的计划持有注册当时的快照，不受影响。注册表
 * 线程安全，读路径无锁：定义存放在不可变快照里，写操作整体串行化。</p>
 *
 * <p>自定义行为被视为受信任的 JVM 代码，不提供沙箱；注册表和定义对象必须可并发复用，
 * 实例可变状态只能存在于每个符文的状态值中。</p>
 */
public final class GlyphRegistry {

    private static final GlyphRegistry GLOBAL = new GlyphRegistry();

    private final Object lock = new Object();

    private volatile Map<String, GlyphDefinition<?>> definitions;

    private GlyphRegistry() {
        this.definitions = Map.of();
    }

    /**
     * 进程级全局注册表。
     */
    public static GlyphRegistry global() {
        return GLOBAL;
    }

    /**
     * 注册一个自定义符文定义。
     *
     * @return 可关闭句柄；关闭后该类型不再对之后构造的计划可见
     * @throws NullPointerException     定义为 {@code null}
     * @throws IllegalArgumentException 类型 ID 格式非法，或该类型已注册
     */
    public GlyphHandle register(GlyphDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition");
        String typeId = definition.typeId();
        requireCustomNamespace(typeId);
        synchronized (lock) {
            if (definitions.containsKey(typeId)) {
                throw new IllegalArgumentException("glyph type is already registered: " + typeId);
            }
            Map<String, GlyphDefinition<?>> updated = new LinkedHashMap<>(definitions);
            updated.put(typeId, definition);
            definitions = Map.copyOf(updated);
        }
        return new GlyphHandle(this, typeId);
    }

    /**
     * 该类型当前是否可用。
     */
    public boolean isRegistered(String typeId) {
        Objects.requireNonNull(typeId, "typeId");
        return definitions.containsKey(typeId);
    }

    /**
     * 当前可用定义；未注册时返回 {@code null}。
     */
    public GlyphDefinition<?> definition(String typeId) {
        Objects.requireNonNull(typeId, "typeId");
        return definitions.get(typeId);
    }

    /**
     * 当前全部可用的类型 ID。
     */
    public Set<String> typeIds() {
        return Set.copyOf(definitions.keySet());
    }

    /**
     * 当前全部可用定义。
     */
    public Map<String, GlyphDefinition<?>> definitions() {
        return Map.copyOf(definitions);
    }

    /**
     * 注销一个自定义类型；非法命名空间都会被拒绝。
     */
    void unregister(String typeId) {
        requireCustomNamespace(typeId);
        synchronized (lock) {
            if (!definitions.containsKey(typeId)) {
                return;
            }
            Map<String, GlyphDefinition<?>> updated = new LinkedHashMap<>(definitions);
            updated.remove(typeId);
            definitions = Map.copyOf(updated);
        }
    }

    /** 类型 ID 必须是 {@code namespace:name} 形式 */
    private static void requireCustomNamespace(String typeId) {
        int separator = typeId.indexOf(':');
        if (separator <= 0
                || separator == typeId.length() - 1
                || typeId.lastIndexOf(':') != separator) {
            throw new IllegalArgumentException("glyph type id must use the namespace:name form: " + typeId);
        }
    }

    /**
     * 注册句柄：关闭后只让该类型不再对之后构造的计划可见。
     *
     * <p>关闭是幂等的；已经快照了该类型的计划完全不受影响。</p>
     */
    public static final class GlyphHandle implements AutoCloseable {

        private final GlyphRegistry registry;
        private final String typeId;
        private boolean closed;

        private GlyphHandle(GlyphRegistry registry, String typeId) {
            this.registry = registry;
            this.typeId = typeId;
        }

        /**
         * 句柄对应的类型 ID。
         */
        public String typeId() {
            return typeId;
        }

        /**
         * 句柄是否已关闭。
         */
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            registry.unregister(typeId);
        }

        @Override
        public String toString() {
            return "GlyphHandle[" + typeId + (closed ? ", closed]" : "]");
        }
    }
}
