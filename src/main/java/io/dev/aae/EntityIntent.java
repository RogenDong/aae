package io.dev.aae;

import java.util.Objects;

/**
 * 机构在一个周期内向一个实体投递的意图。
 *
 * <p>意图先收集成批次，再按实体分组做兼容检查，最后统一提交。兼容规则：</p>
 *
 * <ul>
 *   <li>{@link Kind#STAY 保持不动} 表示机构仍持有实体但本周期不移动；它只与
 *       其它保持不动、抓取或释放兼容；</li>
 *   <li>{@link Kind#GRAB 抓取} 与平移、旋转不兼容，因此被抓取的实体不能被
 *       其它机构在同一周期移动；多个抓取或抓取+释放可以合并；</li>
 *   <li>{@link Kind#RELEASE 释放} 与平移、旋转兼容（先放手再随动，或由其它
 *       持有者继续携带）；</li>
 *   <li>平移只与完全相同的位移兼容，旋转只与相同中心、相同转向兼容。</li>
 * </ul>
 */
public record EntityIntent(
        Kind kind,
        long entity,
        String mechanismId,
        long deltaX,
        long deltaY,
        GridPoint rotationCenter,
        int quarterTurns) {

    /** 意图类型：保持不动、平移、旋转、抓取、释放。 */
    public enum Kind {
        STAY,
        MOVE,
        ROTATE,
        GRAB,
        RELEASE
    }

    public EntityIntent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(mechanismId, "mechanismId");
        switch (kind) {
            case MOVE -> {
                if (rotationCenter != null || quarterTurns != 0) {
                    throw new IllegalArgumentException(
                            "MOVE intent must not carry rotation data");
                }
            }
            case ROTATE -> {
                Objects.requireNonNull(rotationCenter, "rotationCenter");
                if (quarterTurns == 0) {
                    throw new IllegalArgumentException(
                            "ROTATE intent must turn at least once");
                }
                quarterTurns = Math.floorMod(quarterTurns, 4);
            }
            case STAY, GRAB, RELEASE -> {
                if (rotationCenter != null || quarterTurns != 0
                        || deltaX != 0 || deltaY != 0) {
                    throw new IllegalArgumentException(
                            kind + " intent must not carry motion data");
                }
            }
        }
    }

    /** 持有约束：机构仍抓着实体，但本周期不允许它移动。 */
    public static EntityIntent stay(long entity, String mechanismId) {
        return new EntityIntent(Kind.STAY, entity, mechanismId, 0, 0, null, 0);
    }

    /** 平移意图：把实体移动 {@code (deltaX, deltaY)}。 */
    public static EntityIntent move(
            long entity, String mechanismId, long deltaX, long deltaY) {
        return new EntityIntent(Kind.MOVE, entity, mechanismId, deltaX, deltaY, null, 0);
    }

    /** 旋转意图：把实体绕 {@code center} 顺时针旋转 {@code quarterTurns} 个 90 度。 */
    public static EntityIntent rotate(
            long entity, String mechanismId, GridPoint center, int quarterTurns) {
        return new EntityIntent(Kind.ROTATE, entity, mechanismId, 0, 0, center, quarterTurns);
    }

    /** 抓取意图：持有该实体（抓取计数加一）。 */
    public static EntityIntent grab(long entity, String mechanismId) {
        return new EntityIntent(Kind.GRAB, entity, mechanismId, 0, 0, null, 0);
    }

    /** 释放意图：放弃持有该实体（抓取计数减一），实体留在原地。 */
    public static EntityIntent release(long entity, String mechanismId) {
        return new EntityIntent(Kind.RELEASE, entity, mechanismId, 0, 0, null, 0);
    }

    /**
     * 同一实体在同一周期收到的两个意图是否兼容。
     *
     * <p>兼容关系是对称的；调用方保证两个意图指向同一个实体。</p>
     */
    public boolean compatibleWith(EntityIntent other) {
        Objects.requireNonNull(other, "other");
        return switch (kind) {
            case STAY, GRAB -> switch (other.kind) {
                case STAY, GRAB, RELEASE -> true;
                case MOVE, ROTATE -> false;
            };
            case RELEASE -> true;
            case MOVE -> switch (other.kind) {
                case RELEASE -> true;
                case MOVE -> deltaX == other.deltaX && deltaY == other.deltaY;
                case STAY, GRAB, ROTATE -> false;
            };
            case ROTATE -> switch (other.kind) {
                case RELEASE -> true;
                case ROTATE -> quarterTurns == other.quarterTurns
                        && rotationCenter.equals(other.rotationCenter);
                case STAY, GRAB, MOVE -> false;
            };
        };
    }

    /** 用于警告与调试的可读描述。 */
    public String describe() {
        return switch (kind) {
            case STAY -> "STAY";
            case MOVE -> "MOVE(dx=" + deltaX + ", dy=" + deltaY + ")";
            case ROTATE -> "ROTATE(quarterTurns=" + quarterTurns
                    + ", center=" + rotationCenter + ")";
            case GRAB -> "GRAB";
            case RELEASE -> "RELEASE";
        };
    }
}
