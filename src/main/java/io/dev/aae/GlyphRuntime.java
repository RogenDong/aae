package io.dev.aae;

import io.dev.aae.glyph.GlyphBehavior;
import io.dev.aae.glyph.GlyphBoard;
import io.dev.aae.glyph.GlyphDefinition;
import io.dev.aae.glyph.GlyphInstance;
import io.dev.aae.glyph.GlyphPhase;
import io.dev.aae.glyph.GlyphSpec;

import java.util.*;
import java.util.function.Supplier;

/**
 * 模拟器内部的符文运行时：实例状态、执行上下文、半周期棋盘视图与延迟产物队列的持有者。
 *
 * <p>它把“符文相关”的运行态从 {@link OrthogonalSimulator} 里分离出来，同时不复制任何棋盘
 * 数据：构造时拿到的是模拟器自己的 {@link AtomGrid}、实体索引、抓取计数和两个供应器，因此
 * 视图上的读写仍然直接落在模拟器的状态上。拆出来的是编排逻辑，不是状态。</p>
 *
 * <h2>半周期与视图寿命</h2>
 *
 * <p>每个半周期新建<b>一个</b>棋盘视图，计划里声明的全部符文依次使用它，符文阶段结束后
 * 才交由模拟器失效。因此后一个符文能看到前一个符文在同一半周期提交的变更，而“刚解除键”
 * 的临时标记按方向写在棋盘上、参与周期快照，并由模拟器在下一个周期开始时清除。</p>
 *
 * <h2>延迟产物</h2>
 *
 * <p>{@link #pendingSpawns()} 是所有尚未落盘的符文产物，按登记顺序。产物的目标格点被占用
 * 时会留在当前整数周期的队列里，最多跨越两个半周期。模拟器在每个半周期的机构运动之后、
 * 输入生产之前调用 {@link #flushPendingSpawns()} 重试落盘；尚未落盘的格点由
 * {@link #reservedCells()} 投影成预留集合，用于运动检查与输入阻塞。</p>
 *
 * <p>延迟产物队列进入 {@link OrthogonalSimulator} 的周期快照，因此“延迟产物与机构运动
 * 冲突导致整周期回滚”能完整还原队列。</p>
 */
final class GlyphRuntime {

    private static final List<GlyphBoardView.PendingSpawn> EMPTY_PENDING_SPAWNS = Collections.emptyList();

    private final AtomGrid board;
    private final Map<GridPoint, Long> entityAtPosition;
    private final Map<Long, GridPoint> positionOfEntity;
    private final Map<Long, Integer> grabCounts;
    private final Supplier<Set<Long>> heldEntities;
    private final Supplier<Long> nextEntityIdSupplier;

    /** 每个符文实例的当前状态会在运行中被替换，因此这里必须持有可变列表。 */
    private final List<GlyphInstance<?>> instances;

    private final GlyphContextImpl context;

    /** 当前半周期的符文棋盘视图；只在对应半周期的符文阶段内有效。 */
    private GlyphBoardView boardView;

    /** 当前整数周期内尚未落盘的符文产物，最多跨越两个半周期。 */
    private List<GlyphBoardView.PendingSpawn> pendingSpawns = List.of();

    /** 本周期已经因输出格被占用而跳过的符文。 */
    private final boolean[] skippedForCycle;

    /**
     * 本半周期刚刚落盘的延迟产物格点。
     *
     * <p>用于把“机构运动落到刚落盘的延迟产物上”识别成延迟产物一致性错误（抛异常回滚），
     * 而不是普通同格冲突：这些格点在本半周期的运动检查之前就已经被产物预留了。</p>
     */
    private Set<GridPoint> justClaimedCells = Set.of();

    GlyphRuntime(
            SimulationPlan plan,
            AtomGrid board,
            Map<GridPoint, Long> entityAtPosition,
            Map<Long, GridPoint> positionOfEntity,
            Map<Long, Integer> grabCounts,
            Supplier<Set<Long>> heldEntities,
            Supplier<Long> nextEntityIdSupplier) {
        Objects.requireNonNull(plan, "plan");
        this.board = Objects.requireNonNull(board, "board");
        this.entityAtPosition = Objects.requireNonNull(entityAtPosition, "entityAtPosition");
        this.positionOfEntity = Objects.requireNonNull(positionOfEntity, "positionOfEntity");
        this.grabCounts = Objects.requireNonNull(grabCounts, "grabCounts");
        this.heldEntities = Objects.requireNonNull(heldEntities, "heldEntities");
        this.nextEntityIdSupplier = Objects.requireNonNull(nextEntityIdSupplier, "nextEntityIdSupplier");
        this.instances = new ArrayList<>(createInstances(plan));
        this.skippedForCycle = new boolean[instances.size()];
        this.boardView = createBoardView();
        this.context = new GlyphContextImpl(0, GlyphPhase.FIRST_HALF);
    }

    /**
     * 开始一个新周期：同步周期号，并清掉上一周期留下的临时键。
     *
     * <p>临时键由 {@link GlyphBoard#unbond} 写在棋盘上，因此“清除”就是按方向的键位还原；
     * 清除之后本周期新写下的标记会一直保留到下一个周期开始。</p>
     */
    void beginCycle(long cycle) {
        context.bindToCycle(cycle);
        clearTemporaryBonds();
        // 延迟产物不能跨整数周期；这是异常或回滚后的防御性边界。
        pendingSpawns = EMPTY_PENDING_SPAWNS;
        Arrays.fill(skippedForCycle, false);
    }

    /** 计划里的符文数量。 */
    int count() {
        return instances.size();
    }

    /** 计划里的符文声明，按声明顺序。 */
    List<GlyphSpec> specs() {
        List<GlyphSpec> specs = new ArrayList<>(instances.size());
        for (GlyphInstance<?> instance : instances) {
            specs.add(instance.spec());
        }
        return specs;
    }

    /** 按计划声明下标读取实例状态；越界抛出 {@link IndexOutOfBoundsException}。 */
    Object state(int glyphIndex) {
        return instances.get(Objects.checkIndex(glyphIndex, instances.size())).state();
    }

    /**
     * 按定义重新创建全部实例状态，并丢弃延迟产物队列。
     */
    void reset() {
        for (int index = 0; index < instances.size(); index++) {
            GlyphInstance<?> instance = instances.get(index);
            instances.set(index,
                    withState(instance.spec(), instance.definition(),
                            newState(instance.definition())));
        }
        pendingSpawns = EMPTY_PENDING_SPAWNS;
        Arrays.fill(skippedForCycle, false);
        justClaimedCells = Set.of();
        boardView.invalidate();
        boardView = createBoardView();
    }

    /**
     * 执行一个半周期的符文阶段。
     *
     * <p>顺序：先换用本半周期的新视图；按声明顺序执行全部符文；成功结束时把本半周期登记的
     * 延迟产物并入运行时的队列。无论成功还是抛出，视图都在 {@code finally} 里失效——回调
     * 窗口之外任何棋盘方法都会拒绝执行。</p>
     */
    void runGlyphStage(GlyphPhase phase) {
        boardView = createBoardView();
        try {
            if (!instances.isEmpty()) {
                runGlyphs(phase);
            }
            mergePendingSpawns(boardView.handOverPendingSpawns());
            if (phase.isSecondHalf()) {
                // 第二半周期结束后不得把延迟产物带到下一个整数周期。
                pendingSpawns = EMPTY_PENDING_SPAWNS;
            }
        } finally {
            boardView.invalidate();
        }
    }

    /**
     * 尝试落盘延迟产物。
     *
     * <p>由模拟器在每个半周期的机构变换之后、运动检查与输入生产之前调用：机构让开的格点上
     * 的产物落盘，仍然被机构或其它原子压住的产物留在队列里等待下一个半周期。本半周期新登记
     * 的产物要等到下一个半周期才会尝试落盘。</p>
     *
     * <p>落盘只占用当时确实空闲的格点；同一批里重复指向同一格点的产物按登记顺序只落盘一次，
     * 其余留队。成功落盘的格点会被记入 {@link #wasJustClaimed(GridPoint)}，供运动检查把它
     * 识别成“延迟产物优先于机构运动”的冲突。</p>
     *
     * @return 本次落盘产生的新实体标识，按登记顺序
     */
    List<Long> flushPendingSpawns() {
        if (pendingSpawns.isEmpty()) {
            justClaimedCells = Set.of();
            return List.of();
        }
        Set<GridPoint> claimed = new LinkedHashSet<>();
        Set<GridPoint> stillBlocked = new LinkedHashSet<>();
        List<GlyphBoardView.PendingSpawn> remaining = new ArrayList<>();
        List<GlyphBoardView.PendingSpawn> placed = new ArrayList<>();
        for (GlyphBoardView.PendingSpawn spawn : pendingSpawns) {
            if (board.contains(spawn.position()) || !claimed.add(spawn.position())) {
                if (stillBlocked.add(spawn.position())) {
                    remaining.add(spawn);
                }
                continue;
            }
            placed.add(spawn);
        }
        pendingSpawns = remaining.isEmpty() ? List.of() : List.copyOf(remaining);
        justClaimedCells = placed.isEmpty() ? Set.of() : Set.copyOf(claimed);
        return placeAll(placed);
    }

    /**
     * 本半周期是否有延迟产物刚刚落盘到该格点。
     *
     * <p>这类格点在本半周期的运动检查之前就已被产物预留，因此机构运动落到它上面属于延迟
     * 产物一致性错误：产物优先，整周期回滚。已经落盘、但落在<em>上一个</em>半周期的产物则
     * 只是普通棋盘原子，机构压到它时走普通的同格冲突警告停机路径。</p>
     */
    boolean wasJustClaimed(GridPoint position) {
        return justClaimedCells.contains(position);
    }

    /** 清掉“刚刚落盘”的标记；由模拟器在进入下一阶段前调用。 */
    void clearJustClaimed() {
        justClaimedCells = Set.of();
    }

    /** 把一批已经确认格点空闲的产物落盘，返回新实体标识。 */
    private List<Long> placeAll(List<GlyphBoardView.PendingSpawn> batch) {
        List<Long> spawned = new ArrayList<>(batch.size());
        for (GlyphBoardView.PendingSpawn spawn : batch) {
            spawned.add(placeProduct(spawn.position(), spawn.atom()));
        }
        return spawned;
    }

    /**
     * 把一个新原子放到格点上，并清理邻居指向该格点的反向键。
     *
     * <p>不走棋盘视图：落盘发生在符文阶段之外，这时的视图已经失效。占用判定已经由
     * {@link #flushPendingSpawns()} 完成，因此这里不再做覆盖检查。</p>
     */
    private long placeProduct(GridPoint position, Atom atom) {
        long entity = nextEntityIdSupplier.get();
        board.unbondNeighbor(position);
        board.put(position, atom);
        entityAtPosition.put(position, entity);
        positionOfEntity.put(entity, position);
        return entity;
    }

    /** 全部尚未落盘的延迟产物，按登记顺序。 */
    List<GlyphBoardView.PendingSpawn> pendingSpawns() {
        return pendingSpawns;
    }

    /** 恢复一份延迟产物队列；供周期回滚使用。 */
    void restorePendingSpawns(List<GlyphBoardView.PendingSpawn> restored) {
        pendingSpawns = List.copyOf(Objects.requireNonNull(restored, "restored"));
    }

    /**
     * 尚未落盘的延迟产物格点，供运动检查与输入生产使用。
     *
     * <p>它是 {@link #pendingSpawns()} 的位置投影：产物落盘或被回滚后集合立即变化，因此
     * 每次调用返回的都是当下这一刻的快照。</p>
     */
    Set<GridPoint> reservedCells() {
        if (pendingSpawns.isEmpty()) {
            return Set.of();
        }
        Set<GridPoint> cells = new LinkedHashSet<>(pendingSpawns.size());
        for (GlyphBoardView.PendingSpawn spawn : pendingSpawns) {
            cells.add(spawn.position());
        }
        return cells;
    }

    /** 周期回滚：丢弃延迟产物、让当前视图失效并换上一个全新的视图。 */
    void rewindAfterRollback() {
        pendingSpawns = EMPTY_PENDING_SPAWNS;
        justClaimedCells = Set.of();
        Arrays.fill(skippedForCycle, false);
        boardView.invalidate();
        boardView = createBoardView();
    }

    /** 快照全部实例状态；供 {@link OrthogonalSimulator} 的周期事务回滚使用。 */
    List<GlyphInstance<?>> captureStates() {
        List<GlyphInstance<?>> copy = new ArrayList<>(instances.size());
        for (GlyphInstance<?> instance : instances) {
            copy.add(withState(instance.spec(), instance.definition(), instance.state()));
        }
        return copy;
    }

    /** 恢复快照里的实例状态；状态数量与顺序由 {@link #captureStates()} 保证。 */
    void restoreStates(List<GlyphInstance<?>> states) {
        for (int index = 0; index < instances.size(); index++) {
            instances.set(index, states.get(index));
        }
    }

    /**
     * 把新登记的产物并入队列；同一格点上的旧产物被新产物替换，其余保持登记顺序。
     */
    private void mergePendingSpawns(List<GlyphBoardView.PendingSpawn> registered) {
        if (registered.isEmpty()) {
            return;
        }
        List<GlyphBoardView.PendingSpawn> merged =
                new ArrayList<>(pendingSpawns.size() + registered.size());
        merged.addAll(pendingSpawns);
        for (GlyphBoardView.PendingSpawn spawn : registered) {
            boolean replaced = false;
            for (int index = 0; index < merged.size(); index++) {
                if (merged.get(index).position().equals(spawn.position())) {
                    merged.set(index, spawn);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                merged.add(spawn);
            }
        }
        pendingSpawns = Collections.unmodifiableList(merged);
    }

    /**
     * 清掉棋盘上全部“刚解除键”的临时键位。
     *
     * <p>只清键字节 {@link Atom#TEMPORARY_BOND}，不碰任何长期键。</p>
     */
    private void clearTemporaryBonds() {
        if (board.isEmpty()) {
            return;
        }
        List<GridPoint> positions = new ArrayList<>(board.positions());
        for (GridPoint position : positions) {
            Atom atom = board.get(position);
            if (atom != null && atom.hasTemporaryBond()) {
                board.put(position, atom.withoutTemporaryBonds());
            }
        }
    }

    /**
     * 为一个半周期新建棋盘视图。
     *
     * <p>视图不复制棋盘数据，只承载本半周期的预留格点；因此每个半周期都必须重新创建。</p>
     */
    private GlyphBoardView createBoardView() {
        return new GlyphBoardView(
                board,
                entityAtPosition,
                positionOfEntity,
                grabCounts,
                heldEntities,
                nextEntityIdSupplier,
                reservedCells());
    }

    /** 按声明顺序执行全部符文；后一个能看到前一个已提交的变更。 */
    private void runGlyphs(GlyphPhase phase) {
        context.bindToPhase(phase);
        context.bind(boardView);
        for (int index = 0; index < instances.size(); index++) {
            GlyphInstance<?> instance = instances.get(index);
            if (skippedForCycle[index]) {
                continue;
            }
            boardView.bind(instance);
            context.bind(index, instance);
            if (!outputPreconditionsHold(instance)) {
                skippedForCycle[index] = true;
                continue;
            }
            if (!inputPreconditionsHold(instance)) {
                continue;
            }
            Object nextState = applyGlyph(instance);
            instances.set(index,
                    withState(instance.spec(), instance.definition(), nextState));
        }
    }

    /**
     * 检查一个符文的输入/输出前置条件。
     *
     * <p>输入格点必须存在并符合期望，输出格点必须空闲；任一项不满足就整条符文跳过——
     * 不执行行为、不产生副作用、也不抛异常。</p>
     */
    private boolean inputPreconditionsHold(GlyphInstance<?> instance) {
        GlyphDefinition<?> definition = instance.definition();
        for (GlyphDefinition.InputSlot slot : definition.inputs()) {
            Atom atom = boardView.atomAt(slot.position()).orElse(null);
            if (!slot.matches(atom)
                    || (slot.bondType() != 0 && !boardView.isBonded(slot.position(), slot.direction()))) {
                return false;
            }
        }
        return true;
    }

    private boolean outputPreconditionsHold(GlyphInstance<?> instance) {
        GlyphDefinition<?> definition = instance.definition();
        for (GlyphDefinition.OutputSlot slot : definition.outputs()) {
            if (!slot.isFree(boardView)) {
                return false;
            }
        }
        return true;
    }

    private <S> S applyGlyph(GlyphInstance<S> instance) {
        GlyphBehavior<S> behavior = instance.definition().behavior();
        return behavior.apply(context, instance.state());
    }

    private static List<GlyphInstance<?>> createInstances(SimulationPlan plan) {
        List<GlyphInstance<?>> created = new ArrayList<>(plan.glyphs().size());
        for (GlyphSpec spec : plan.glyphs()) {
            GlyphDefinition<?> definition = plan.glyphDefinition(spec.typeId());
            created.add(withState(spec, definition, newState(definition)));
        }
        return created;
    }

    /**
     * 给定义创建一份新的实例状态。
     *
     * <p>定义的状态类型是通配符，无法在调用点具名；这里集中做一次未经检查的转换，
     * 转换本身是安全的因为状态只由同一份定义的工厂创建、也只传回同一份定义的行为。</p>
     */
    @SuppressWarnings("unchecked")
    private static Object newState(GlyphDefinition<?> definition) {
        GlyphDefinition<Object> typed = (GlyphDefinition<Object>) definition;
        return typed.newState();
    }

    /** 用一份状态重建符文实例；状态类型与定义匹配由调用点保证。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static GlyphInstance<?> withState(
            GlyphSpec spec, GlyphDefinition<?> definition, Object state) {
        return new GlyphInstance(spec, definition, state);
    }
}
