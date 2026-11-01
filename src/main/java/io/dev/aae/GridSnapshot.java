package io.dev.aae;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 不可变的占用视图。每个格点会刻意保存全部占用者，
 * 因此同格机构之间不会相互覆盖。
 */
public final class GridSnapshot {

    private final Map<GridPoint, List<MechanismView>> occupantsByPosition;
    private final int mechanismCount;
    private final GridBounds bounds;
    private final long cycle;

    private GridSnapshot(
            Map<GridPoint, List<MechanismView>> occupantsByPosition,
            int mechanismCount,
            GridBounds bounds,
            long cycle) {
        this.occupantsByPosition = occupantsByPosition;
        this.mechanismCount = mechanismCount;
        this.bounds = bounds;
        this.cycle = cycle;
    }

    static GridSnapshot from(long cycle, List<MechanismView> mechanisms) {
        Map<GridPoint, List<MechanismView>> mutable = new LinkedHashMap<>();
        long minX = Long.MAX_VALUE;
        long minY = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long maxY = Long.MIN_VALUE;

        for (MechanismView mechanism : mechanisms) {
            GridPoint position = mechanism.position();
            mutable.computeIfAbsent(position, ignored -> new ArrayList<>()).add(mechanism);
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
        }

        Map<GridPoint, List<MechanismView>> immutable = new LinkedHashMap<>();
        mutable.forEach((position, occupants) ->
                immutable.put(position, List.copyOf(occupants)));

        GridBounds snapshotBounds = mechanisms.isEmpty()
                ? null
                : new GridBounds(minX, minY, maxX, maxY);
        return new GridSnapshot(
                Collections.unmodifiableMap(immutable),
                mechanisms.size(),
                snapshotBounds,
                cycle);
    }

    public List<MechanismView> occupantsAt(GridPoint position) {
        return occupantsByPosition.getOrDefault(position, List.of());
    }

    public Map<GridPoint, List<MechanismView>> cells() {
        return occupantsByPosition;
    }

    public int mechanismCount() {
        return mechanismCount;
    }

    public int occupiedCellCount() {
        return occupantsByPosition.size();
    }

    public long cycle() {
        return cycle;
    }

    public Optional<GridBounds> bounds() {
        return Optional.ofNullable(bounds);
    }
}
