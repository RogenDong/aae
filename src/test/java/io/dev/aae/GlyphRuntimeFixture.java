package io.dev.aae;

import io.dev.aae.glyph.GlyphPhase;
import io.dev.aae.glyph.GlyphSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 符文层测试直接提供棋盘初态，不声明输入生产器或模拟机械臂运输。
 * 棋盘初态不作为静态输入部件声明；符文布局仍由 SimulationPlan 构造器校验。
 */
final class GlyphRuntimeFixture {
    private final AtomGrid board = new AtomGrid();
    private final Map<GridPoint, Long> entities = new LinkedHashMap<>();
    private final Map<Long, GridPoint> positions = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    final GlyphRuntime runtime;
    private long cycle;

    GlyphRuntimeFixture(List<InputSpec> initialAtoms, List<GlyphSpec> glyphs) {
        for (InputSpec template : initialAtoms) {
            for (AtomPlacement placement : template.atoms()) {
                long id = nextId.getAndIncrement();
                board.put(placement.position(), placement.atom());
                entities.put(placement.position(), id);
                positions.put(id, placement.position());
            }
        }
        runtime = new GlyphRuntime(new SimulationPlan(List.of(), glyphs, 1), board,
                entities, positions, new LinkedHashMap<>(), Set::of, nextId::getAndIncrement);
    }

    void step() {
        runtime.beginCycle(cycle++);
        half(GlyphPhase.FIRST_HALF);
        half(GlyphPhase.SECOND_HALF);
    }

    void half(GlyphPhase phase) {
        runtime.flushPendingSpawns();
        runtime.clearJustClaimed();
        runtime.runGlyphStage(phase);
    }

    Atom atomAt(GridPoint point) {
        return board.get(point);
    }

    int boardSize() {
        return board.size();
    }

    Molecule moleculeAt(GridPoint point) {
        return MoleculeBfs.moleculeAt(board, point);
    }

    int pendingGlyphSpawnCount() {
        return runtime.pendingSpawns().size();
    }
}
