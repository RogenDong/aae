package io.dev.aae;

import io.dev.aae.glyph.GlyphExecutionException;
import io.dev.aae.glyph.GlyphInstance;
import io.dev.aae.glyph.GlyphPhase;
import io.dev.aae.glyph.GlyphSpec;
import io.dev.aae.track.TrackDirection;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 指令带驱动的方格网格模拟器。
 *
 * <p>每个周期严格分成两个半周期：第一半周期只执行 {@code GRAB}/{@code RELEASE}，第二半
 * 周期执行移动、旋转、枢轴旋转和活塞伸缩。每个半周期的推进顺序：</p>
 *
 * <ol>
 *   <li>解码：所有机构基于同一个当前周期读取指令；</li>
 *   <li>意图：本条指令把意图投递给目标实体——第一半周期的抓取面向格点上的分子，
 *       释放面向已持有的实体；第二半周期的移动与转向携带被持有的实体；</li>
 *   <li>冲突检查：同一实体收到不兼容意图时停机并记录警告；</li>
 *   <li>变换：机构自身与被携带实体的新状态写入暂存区；</li>
 *   <li>提交：所有变换成功后统一替换当前状态，并落盘被抓住的分子；</li>
 *   <li>延迟产物：在机构运动之后按登记顺序落盘目标格点已经让开的符文产物，仍然被占用的
 *       留队到当前整数周期的第二半周期结束；</li>
 *   <li>生产：占位空闲的输入把模板原子放进棋盘，与延迟产物格点相交的模板整份跳过；</li>
 *   <li>符文：换用本半周期唯一的棋盘视图（上一个周期的临时键已在周期开始时清除），
 *       按计划声明顺序执行全部符文，后一个能看到前一个已提交的变更；</li>
 *   <li>消费：输出消费类型、长期键与位置完全一致的未抓取分子。</li>
 * </ol>
 *
 * <p>尚未落盘的延迟产物格点在同一半周期内就算已占用：机构运动落到这些格点时抛出
 * {@link GlyphExecutionException} 并回滚整个周期；已经落盘的产物则是普通棋盘原子，
 * 机构压到它时走普通同格冲突的警告停机路径。输入模板与预留格点相交时静默跳过整份模板。</p>
 *
 * <p>因此输入和输出可能在同一周期的两个半周期分别生产或消费；两个半周期都结束后
 * {@code cycle} 才加一。整个 {@link #step()} 由变更日志保护：任何异常都会回滚本周期开始的
 * 状态（含符文实例状态与延迟产物队列）、把模拟器标记为 {@code halted}，并把同一个异常对象
 * 原样抛给调用方。</p>
 *
 * <p>停机是确定性的业务规则，不是碰撞检测：一旦停机，后续 {@link #step()} 和
 * {@link #run(long)} 不再推进任何状态，{@link #reset()} 才能恢复运行。</p>
 *
 * <p>符文的实例状态、执行上下文与半周期棋盘视图由包级私有的 {@link GlyphRuntime} 持有；
 * 本类保留机构与原子棋盘的运行态、周期事务和输出消费，只在半周期的符文阶段调用它。
 * 两者共享同一份棋盘与实体索引，拆分的是编排逻辑而不是状态。</p>
 */
public final class OrthogonalSimulator {

    private final SimulationPlan plan;
    /** 机械臂的坐标 x */
    private final long[] x;
    /** 机械臂的坐标 y */
    private final long[] y;
    /** 机械臂的朝向 */
    private final byte[] direction;
    /** 机械臂的长度 */
    private final byte[] length;

    /** 下周期机械臂坐标 x */
    private final long[] nextX;
    /** 下周期机械臂坐标 y */
    private final long[] nextY;
    /** 下周期机械臂朝向 */
    private final byte[] nextDirection;
    /** 下周期机械臂长度 */
    private final byte[] nextLength;
    /** 待执行的指令 */
    private final Instruction[] pendingInstructions;
    private final long[] pendingTrackDeltaX;
    private final long[] pendingTrackDeltaY;

    /** 原子棋盘。被抓住的原子留在棋盘上，由抓取计数区分是否被持有。 */
    private final AtomGrid board = new AtomGrid();
    private final Map<GridPoint, Long> entityAtPosition = new LinkedHashMap<>();
    private final Map<Long, GridPoint> positionOfEntity = new LinkedHashMap<>();
    private final Map<Long, Integer> grabCounts = new LinkedHashMap<>();
    private final List<List<Long>> heldByMechanism;
    private final long[] outputCounts;

    /** 符文运行态：实例状态、执行上下文与半周期棋盘视图都由它持有。 */
    private final GlyphRuntime glyphs;

    private long nextEntityId;
    private boolean halted;
    private final List<String> warnings = new ArrayList<>();

    private long cycle;

    public OrthogonalSimulator(SimulationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");

        int size = plan.size();
        this.x = new long[size];
        this.y = new long[size];
        this.direction = new byte[size];
        this.length = new byte[size];
        this.nextX = new long[size];
        this.nextY = new long[size];
        this.nextDirection = new byte[size];
        this.nextLength = new byte[size];
        this.pendingInstructions = new Instruction[size];
        this.pendingTrackDeltaX = new long[size];
        this.pendingTrackDeltaY = new long[size];
        this.heldByMechanism = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.heldByMechanism.add(new ArrayList<>(8));
        }
        this.outputCounts = new long[plan.outputs().size()];
        this.glyphs = new GlyphRuntime(
                plan,
                board,
                entityAtPosition,
                positionOfEntity,
                grabCounts,
                this::heldEntityIds,
                this::getNextEntityId);
        reset();
    }

    /** 全部机构当前持有的实体并集，供符文棋盘视图判断“被抓取”。 */
    private Set<Long> heldEntityIds() {
        return heldByMechanism.stream()
                .flatMap(List::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private long getNextEntityId() {
        return nextEntityId++;
    }

    public SimulationPlan plan() {
        return plan;
    }

    public long cycle() {
        return cycle;
    }

    public int mechanismCount() {
        return x.length;
    }

    /** 计划里的符文数量。 */
    public int glyphCount() {
        return glyphs.count();
    }

    public void init() {
        reset();
        // 预先输入生产：此时没有任何符文预留，预留集合为空。
        spawnInputs(Set.of());
    }

    public void reset() {
        cycle = 0;
        halted = false;
        warnings.clear();
        board.clear();
        entityAtPosition.clear();
        positionOfEntity.clear();
        grabCounts.clear();
        heldByMechanism.forEach(List::clear);
        Arrays.fill(outputCounts, 0);
        nextEntityId = 1;
        List<MechanismSpec> mechanisms = plan.mechanisms();
        for (int i = 0; i < mechanisms.size(); i++) {
            MechanismSpec mechanism = mechanisms.get(i);
            x[i] = mechanism.initialPosition().x();
            y[i] = mechanism.initialPosition().y();
            direction[i] = (byte) mechanism.initialDirection().ordinal();
            length[i] = (byte) mechanism.initialLength();
        }
        glyphs.reset();
    }

    /**
     * 推进一个周期。
     *
     * <p>符文回调抛出的业务异常、{@link GlyphExecutionException}，以及“机构运动落到符文
     * 预留格点”这类延迟产物一致性错误，都会让整个周期回滚、把模拟器标记为 {@code halted}，
     * 然后把同一个异常对象原样重新抛出；这里不吞异常、也不包装异常。</p>
     */
    public void step() {
        if (halted) {
            return;
        }
        if (cycle == Long.MAX_VALUE) {
            throw new IllegalStateException("cycle counter exhausted");
        }
        Snapshot snapshot = captureState();
        glyphs.beginCycle(cycle);
        try {
            runHalfCycle(GlyphPhase.FIRST_HALF);
            if (halted) {
                return;
            }
            runHalfCycle(GlyphPhase.SECOND_HALF);
            if (halted) {
                return;
            }
        } catch (HaltSignal stop) {
            restoreState(snapshot);
            halted = true;
            cycle++;
            return;
        } catch (RuntimeException | Error failure) {
            restoreState(snapshot);
            halted = true;
            warnings.add("cycle " + cycle + ": " + failure.getClass().getSimpleName()
                    + " from a glyph callback or a deferred product conflict: "
                    + failure.getMessage());
            throw failure;
        }
        cycle++;
    }

    /**
     * 执行一个半周期。
     *
     * <p>顺序：机构指令解码、意图批次、按实体的兼容检查、机构变换暂存、延迟产物落盘、
     * 延迟产物一致性检查、统一提交、输入生产、按计划顺序执行符文、消费输出。
     * 临时键由 {@link OrthogonalSimulator#step()} 在本周期开始时清除。</p>
     *
     * <p>延迟产物在运动检查之前落盘，因此“产物优先于机构运动”是确定的：机构或实体运动
     * 落到尚未落盘的预留格点、或本半周期刚落盘的产物格点时抛出
     * {@link GlyphExecutionException} 并回滚整个周期。</p>
     */
    private void runHalfCycle(GlyphPhase phase) {
        List<MechanismSpec> mechanisms = plan.mechanisms();

        // 1. 解码：同一周期、同一条指令带语义。
        for (int i = 0; i < mechanisms.size(); i++) {
            pendingInstructions[i] = mechanisms.get(i).tape().instructionAt(cycle);
            resolveTrackMotion(i, pendingInstructions[i]);
        }

        // 2. 收集本半周期的意图批次。
        List<EntityIntent> intents = new ArrayList<>();
        for (int i = 0; i < mechanisms.size(); i++) {
            collectIntents(i, pendingInstructions[i], phase, intents);
        }

        // 3. 按实体分组，并在生成顺序上做兼容检查。
        Map<Long, List<EntityIntent>> intentsByEntity = new LinkedHashMap<>();
        for (EntityIntent intent : intents) {
            List<EntityIntent> prior = intentsByEntity.get(intent.entity());
            if (prior == null) {
                prior = new ArrayList<>();
                intentsByEntity.put(intent.entity(), prior);
            } else {
                for (EntityIntent other : prior) {
                    if (!intent.compatibleWith(other)) {
                        haltForIncompatibleIntents(intent, other);
                        return;
                    }
                }
            }
            prior.add(intent);
        }

        // 4. 机构与实体变换全部写入暂存区；溢出或冲突时没有任何状态被提交。
        if (phase.isSecondHalf()) {
            for (int i = 0; i < mechanisms.size(); i++) {
                transform(i, pendingInstructions[i]);
            }
        }
        // 5. 延迟产物落盘：按登记顺序把目标格点已经让开的产物写进棋盘，仍然被占用的留队，
        //    只在当前整数周期内等待。落盘早于运动检查，因此“刚落盘的延迟产物”与“机构运动”的
        //    同格优先级是确定的：产物优先。
        glyphs.flushPendingSpawns();

        // 6. 重叠检查：尚未落盘的预留格点与“刚落盘的延迟产物”格点都属于延迟产物一致性范围，
        //    若实体运动落到其上则抛出 GlyphExecutionException 回滚整周期；普通同格冲突
        //    与尚未落盘的延迟产物压到静止原子上则走警告停机路径。
        Set<GridPoint> reservedCells = glyphs.reservedCells();
        List<AtomChange> changes = computeAtomChanges(intentsByEntity);
        if (!checkAtomOverlaps(changes, reservedCells)) {
            glyphs.clearJustClaimed();
            return;
        }
        if (phase.isSecondHalf() && !checkRuntimeOverlaps(changes)) {
            glyphs.clearJustClaimed();
            return;
        }

        // 7. 提交机构状态。
        if (phase.isSecondHalf()) {
            System.arraycopy(nextX, 0, x, 0, x.length);
            System.arraycopy(nextY, 0, y, 0, y.length);
            System.arraycopy(nextDirection, 0, direction, 0, direction.length);
            System.arraycopy(nextLength, 0, length, 0, length.length);
        }

        // 8. 提交实体状态与持有关系。
        commitAtomChanges(changes);
        for (EntityIntent intent : intents) {
            List<Long> held = heldByMechanism.get(intent.mechanismIndex());
            long entity = intent.entity();
            switch (intent.kind()) {
                case GRAB -> held.add(entity);
                case RELEASE -> held.remove(entity);
                case STAY, MOVE, ROTATE -> {
                    // 持有约束与运动不改变持有关系。
                }
            }
        }
        glyphs.clearJustClaimed();

        // 9. 输入生产：预留格点与棋盘格点一样算占用，模板任一格冲突就整份跳过。
        spawnInputs(glyphs.reservedCells());

        // 10. 符文阶段：换用本半周期的棋盘视图（半周期内全部符文共用同一个视图），按计划
        //     顺序执行全部符文；后一个符文能看到前一个在同一半周期提交的变更。
        glyphs.runGlyphStage(phase);

        // 11. 消费输出。
        consumeOutputs();
    }

    public void run(long cycleCount) {
        if (cycleCount < 0) {
            throw new IllegalArgumentException("cycleCount must be non-negative");
        }
        if (cycleCount > Long.MAX_VALUE - cycle) {
            throw new IllegalArgumentException("cycle count exceeds supported range");
        }
        for (long index = 0; index < cycleCount && !halted; index++) {
            step();
        }
    }

    /** 按计划声明顺序返回机构状态的不可变快照。 */
    public List<MechanismView> mechanisms() {
        List<MechanismView> views = new ArrayList<>(mechanismCount());
        for (int i = 0; i < mechanismCount(); i++) {
            views.add(mechanism(i));
        }
        return List.copyOf(views);
    }

    /** 计划里的符文声明，按声明顺序。 */
    public List<GlyphSpec> glyphSpecs() {
        return glyphs.specs();
    }

    /**
     * 尚未落盘的符文产物数量。
     *
     * <p>符文产物在目标格点被占用时会留在当前整数周期的延迟队列里；第二半周期结束仍未落盘
     * 的产物会被丢弃。队列内容
     * 参与周期事务回滚。</p>
     */
    public int pendingGlyphSpawnCount() {
        return glyphs.pendingSpawns().size();
    }

    /** 按计划声明下标（从 0 开始）读取符文状态；越界抛出 {@link IndexOutOfBoundsException}。 */
    public Object glyphState(int glyphIndex) {
        return glyphs.state(glyphIndex);
    }

    /** 棋盘上指定格点的原子；没有原子时返回 {@code null}。 */
    public Atom atomAt(GridPoint position) {
        return board.get(position);
    }

    /**
     * 返回棋盘上指定格点的稳定实体标识；没有原子时返回 {@code 0L}。
     *
     * <p>渲染层用这个标识把原子与当前机构的持有集合关联起来。格点只是实体
     * 的瞬时位置，不能用来判断一个机构到底持有哪一个原子。</p>
     */
    public long entityAt(GridPoint position) {
        return entityAtPosition.getOrDefault(Objects.requireNonNull(position, "position"), 0L);
    }

    /** 棋盘上全部占用格点，按插入顺序。 */
    public Set<GridPoint> atomPositions() {
        return board.positions();
    }

    /** 棋盘上的原子数量。 */
    public int boardSize() {
        return board.size();
    }

    /** 包含指定格点的分子；该格点没有原子时返回 {@link Molecule#empty()}。 */
    public Molecule moleculeAt(GridPoint position) {
        return MoleculeBfs.moleculeAt(board, position);
    }

    /** 把棋盘上的全部原子划分成分子，按首次出现的原子顺序返回。 */
    public List<Molecule> molecules() {
        return MoleculeBfs.molecules(board);
    }

    /** 指定格点上原子的抓取计数（被几个机构持有）；没有原子时返回 0。 */
    public int grabsAt(GridPoint position) {
        long entity = entityAtPosition.getOrDefault(position, 0L);
        return grabCounts.getOrDefault(entity, 0);
    }

    /** 返回计划中指定下标（从 0 开始）的机构当前持有实体的不可变快照。 */
    public List<Long> heldEntities(int index) {
        return List.copyOf(heldByMechanism.get(Objects.checkIndex(index, mechanismCount())));
    }

    /** 按计划声明下标（从 0 开始）读取输出计数；越界抛出 {@link IndexOutOfBoundsException}。 */
    public long outputCount(int outputIndex) {
        return outputCounts[Objects.checkIndex(outputIndex, outputCounts.length)];
    }

    /** 全部输出计数的独立快照，按计划 outputs() 声明顺序；修改返回数组不影响模拟器。 */
    public long[] outputCounts() {
        return Arrays.copyOf(outputCounts, outputCounts.length);
    }

    /** 每个输出必须达到的消费次数。 */
    public long targetOutputs() {
        return plan.targetOutputs();
    }

    /** 是否所有输出都已达到目标消费次数；没有输出时永远不完成。 */
    public boolean isComplete() {
        if (plan.outputs().isEmpty()) {
            return false;
        }
        for (long count : outputCounts) {
            if (count < plan.targetOutputs()) {
                return false;
            }
        }
        return true;
    }

    public long getSubmitCount() {
        return Arrays.stream(outputCounts).sum();
    }

    /** 模拟器是否已因不兼容意图、原子重叠或符文异常而停机。 */
    public boolean isHalted() {
        return halted;
    }

    /** 已记录的警告，按发生顺序排列。 */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** 返回一个稳定值，便于在基准测试中消费模拟结果。 */
    public long checksum() {
        long hash = 0x9e3779b97f4a7c15L ^ cycle;
        for (int i = 0; i < mechanismCount(); i++) {
            hash ^= Long.rotateLeft(x[i] * 0xc2b2ae3d27d4eb4fL, i & 63);
            hash ^= Long.rotateRight(y[i] * 0x165667b19e3779f9L, i & 63);
            hash = Long.rotateLeft(hash, 17)
                    ^ direction[i]
                    ^ ((long) length[i] << Byte.SIZE);
        }
        return hash;
    }

    /** 返回计划中指定下标（从 0 开始）的机构状态。 */
    public MechanismView mechanism(int index) {
        Objects.checkIndex(index, mechanismCount());
        return new MechanismView(
                new GridPoint(x[index], y[index]),
                Direction.fromOrdinal(direction[index]),
                length[index],
                !heldByMechanism.get(index).isEmpty());
    }

    private void haltForIncompatibleIntents(EntityIntent intent, EntityIntent other) {
        haltWithWarning("entity #" + intent.entity()
                + " received incompatible intents "
                + other.describe() + " (from mechanism[" + other.mechanismIndex()
                + "]) and " + intent.describe()
                + " (from mechanism[" + intent.mechanismIndex() + "])");
    }

    private void haltForMultiEntityDestinationConflict(GridPoint pos, long entity1, long entity2) {
        if (entity1 != 0 && entity2 != 0) {
            haltWithWarning("entity #" + entity1
                            + " would move onto cell " + pos
                            + " already occupied by entity #" + entity2);
        } else {
            haltWithWarning("multiple entities would move onto cell " + pos);
        }
    }

    private void haltForMechanismConflict(int mech, int other, GridPoint pos) {
        haltWithWarning("mechanism[" + mech + "] conflicts with mechanism[" + other + "] at " + pos);
    }

    private void haltForMechanismEntityConflict(int mechIndex, long occupant, GridPoint pos) {
        haltWithWarning("mechanism[" + mechIndex + "] overlaps entity #" + occupant + " at " + pos);
    }

    /** 记录停机警告：写入警告列表并输出到标准错误流。 */
    private void haltWithWarning(String warning) {
        warnings.add("cycle " + cycle + ": " + warning + "; simulator halted");
        System.err.println("[orthogonal] WARNING: " + warning);
        halted = true;
        throw new HaltSignal();
    }

    private void collectIntents(
            int mechIndex,
            Instruction instruction,
            GlyphPhase phase,
            List<EntityIntent> intents) {
        List<Long> held = heldByMechanism.get(mechIndex);
        Direction facing = Direction.fromOrdinal(direction[mechIndex]);

        // 第一半周期只执行抓取与释放；第二半周期执行其余指令。
        if (phase.isFirstHalf()) {
            switch (instruction) {
                case GRAB -> {
                    // 已经持有实体时抓取是空操作
                    if (!held.isEmpty()) {
                        return;
                    }
                    GridPoint target = grabPosition(mechIndex, facing);
                    Molecule molecule = moleculeAt(target);
                    for (AtomPlacement ap : molecule.atoms()) {
                        intents.add(EntityIntent.grab(
                                entityAtPosition.getOrDefault(ap.position(), 0L),
                                mechIndex));
                    }
                }
                case RELEASE -> {
                    for (long entity : held) {
                        intents.add(EntityIntent.release(entity, mechIndex));
                    }
                }
                default -> {
                    // 其它指令在第二半周期执行。
                }
            }
            return;
        }

        // 未抓握实体则无需收集意图
        if (held.isEmpty()) {
            return;
        }

        var entities = new HashSet<Long>(held.size() * 2);
        // 检索被抓握的分子
        for (long entity : held) {
            if (entities.contains(entity)) {
                continue;
            }
            GridPoint target =  positionOfEntity.get(entity);
            if (target == null) {
                continue;
            }
            entities.add(entity);
            Molecule molecule = moleculeAt(target);
            for (AtomPlacement atom : molecule.atoms()) {
                entities.add(entityAtPosition.get(atom.position()));
            }
        }

        int firstIntent = intents.size();
        switch (instruction) {
            case MOVE_FORWARD, MOVE_BACKWARD -> {
                long deltaX = pendingTrackDeltaX[mechIndex];
                long deltaY = pendingTrackDeltaY[mechIndex];
                if (deltaX == 0 && deltaY == 0) {
                    break;
                }
                for (long entity : entities) {
                    intents.add(EntityIntent.move(entity, mechIndex, deltaX, deltaY));
                }
            }
            case ROTATE_CCW -> {
                GridPoint center = new GridPoint(x[mechIndex], y[mechIndex]);
                for (long entity : entities) {
                    intents.add(EntityIntent.rotate(entity, mechIndex, center, -1));
                }
            }
            case ROTATE_CW -> {
                GridPoint center = new GridPoint(x[mechIndex], y[mechIndex]);
                for (long entity : entities) {
                    intents.add(EntityIntent.rotate(entity, mechIndex, center, 1));
                }
            }
            case PIVOT_CCW -> {
                GridPoint center = grabPosition(mechIndex, facing);
                for (long entity : entities) {
                    intents.add(EntityIntent.rotate(entity, mechIndex, center, -1));
                }
            }
            case PIVOT_CW -> {
                GridPoint center = grabPosition(mechIndex, facing);
                for (long entity : entities) {
                    intents.add(EntityIntent.rotate(entity, mechIndex, center, 1));
                }
            }
            case EXTEND_PISTON -> {
                if (length[mechIndex] >= MechanismSpec.MAX_LENGTH) {
                    break;
                }
                for (long entity : entities) {
                    intents.add(EntityIntent.move(
                            entity, mechIndex, facing.deltaX(), facing.deltaY()));
                }
            }
            case RETRACT_PISTON -> {
                if (length[mechIndex] <= MechanismSpec.MIN_LENGTH) {
                    break;
                }
                for (long entity : entities) {
                    intents.add(EntityIntent.move(
                            entity, mechIndex, -facing.deltaX(), -facing.deltaY()));
                }
            }
            case GRAB, RELEASE, WAIT -> {
                // 抓取与释放已经在第一半周期执行。
            }
        }
        if (intents.size() == firstIntent) {
            // 等待、重复抓取、轨道端点和活塞边界都必须保留静止持有约束。
            for (long entity : entities) {
                intents.add(EntityIntent.stay(entity, mechIndex));
            }
        }
    }

    private void transform(int index, Instruction instruction) {
        long transformedX = x[index];
        long transformedY = y[index];
        int transformedDirection = direction[index] & 3;
        int transformedLength = length[index];

        switch (instruction) {
            case MOVE_FORWARD, MOVE_BACKWARD -> {
                transformedX = Math.addExact(transformedX, pendingTrackDeltaX[index]);
                transformedY = Math.addExact(transformedY, pendingTrackDeltaY[index]);
            }
            case ROTATE_CCW -> transformedDirection = (transformedDirection + 3) & 3;
            case ROTATE_CW -> transformedDirection = (transformedDirection + 1) & 3;
            case EXTEND_PISTON -> {
                if (transformedLength < MechanismSpec.MAX_LENGTH) {
                    transformedLength++;
                }
            }
            case RETRACT_PISTON -> {
                if (transformedLength > MechanismSpec.MIN_LENGTH) {
                    transformedLength--;
                }
            }
            case PIVOT_CCW, PIVOT_CW, GRAB, RELEASE, WAIT -> {
                // 枢轴旋转、抓取与释放不改变机构基座、朝向或长度。
            }
        }

        nextX[index] = transformedX;
        nextY[index] = transformedY;
        nextDirection[index] = (byte) transformedDirection;
        nextLength[index] = (byte) transformedLength;
    }

    /** 解码当前格点唯一的轨道位移；端点和非轨道格点都解析为原地等待。 */
    private void resolveTrackMotion(int index, Instruction instruction) {
        pendingTrackDeltaX[index] = 0;
        pendingTrackDeltaY[index] = 0;

        TrackDirection trackDirection = switch (instruction) {
            case MOVE_FORWARD -> TrackDirection.PLUS;
            case MOVE_BACKWARD -> TrackDirection.MINUS;
            default -> null;
        };
        if (trackDirection == null) {
            return;
        }

        GridPoint position = new GridPoint(x[index], y[index]);
        GridPoint destination = plan.trackDestination(position, trackDirection);
        pendingTrackDeltaX[index] = Math.subtractExact(destination.x(), position.x());
        pendingTrackDeltaY[index] = Math.subtractExact(destination.y(), position.y());
    }

    private GridPoint grabPosition(int index, Direction facing) {
        return new GridPoint(x[index], y[index]).translate(facing, length[index]);
    }

    private List<AtomChange> computeAtomChanges(
            Map<Long, List<EntityIntent>> intentsByEntity) {
        List<AtomChange> changes = new ArrayList<>(intentsByEntity.size());
        for (Map.Entry<Long, List<EntityIntent>> entry : intentsByEntity.entrySet()) {
            long entity = entry.getKey();
            List<EntityIntent> intents = entry.getValue();
            GridPoint oldPosition = positionOfEntity.get(entity);
            Atom oldAtom = oldPosition == null ? null : board.get(oldPosition);
            if (oldPosition == null || oldAtom == null) {
                // 实体已经不在棋盘上（例如刚被符文消费掉），本半周期不再改动它。
                continue;
            }
            GridPoint newPosition = oldPosition;
            Atom newAtom = oldAtom;
            int grabDelta = 0;
            // 兼容检查保证一个实体最多收到一种运动；相同运动只应用一次。
            boolean moved = false;
            boolean rotated = false;
            for (EntityIntent intent : intents) {
                switch (intent.kind()) {
                    case MOVE -> {
                        if (!moved && !rotated) {
                            newPosition = new GridPoint(
                                    Math.addExact(newPosition.x(), intent.deltaX()),
                                    Math.addExact(newPosition.y(), intent.deltaY()));
                            moved = true;
                        }
                    }
                    case ROTATE -> {
                        if (!moved && !rotated) {
                            newPosition = rotateAround(
                                    newPosition, intent.rotationCenter(), intent.quarterTurns());
                            newAtom = newAtom.rotateBonds(intent.quarterTurns());
                            rotated = true;
                        }
                    }
                    case STAY -> {
                        // 兼容检查已保证本实体没有非零运动。
                    }
                    case GRAB -> grabDelta++;
                    case RELEASE -> grabDelta--;
                }
            }
            changes.add(new AtomChange(
                    entity, oldPosition, newPosition, oldAtom, newAtom, grabDelta));
        }
        return changes;
    }

    /** 顺时针旋转 90 度的坐标变换：{@code (dx, dy) -> (-dy, dx)}。 */
    private static GridPoint rotateAround(
            GridPoint position, GridPoint center, int quarterTurns) {
        long dx = Math.subtractExact(position.x(), center.x());
        long dy = Math.subtractExact(position.y(), center.y());
        for (int turn = 0; turn < quarterTurns; turn++) {
            long rotatedX = Math.negateExact(dy);
            long rotatedY = dx;
            dx = rotatedX;
            dy = rotatedY;
        }
        return new GridPoint(Math.addExact(center.x(), dx), Math.addExact(center.y(), dy));
    }

    /**
     * 提交前的原子重叠检查：两个原子不能落到同一格，也不能落到被静止原子
     * 占据的格点。这是数据完整性规则——棋盘每个格点只保存一个原子，重叠会
     * 导致静默覆盖。
     *
     * <p>这里还检查符文的预留格点：{@code reservedCells} 是尚未落盘的产物格点，任一实际移动的
     * 目标格属于预留集合、或正是本半周期刚落盘的延迟产物时，抛出{@link GlyphExecutionException}
     * ，由 {@link #step()} 回滚整个周期——延迟产物的一致性错误不能用“警告停机”降级处理。
     * 普通的多实体目标重叠和已占用格冲突继续沿用警告停机语义。</p>
     *
     * @param changes       本半周期全部实体的变化
     * @param reservedCells 尚未落盘的延迟产物格点
     */
    private boolean checkAtomOverlaps(List<AtomChange> changes, Set<GridPoint> reservedCells) {
        for (AtomChange change : changes) {
            if (change.newPosition().equals(change.oldPosition())) {
                continue;
            }
            if (reservedCells.contains(change.newPosition())
                    || glyphs.wasJustClaimed(change.newPosition())) {
                throw new GlyphExecutionException(
                        "cycle " + cycle + ": entity #" + change.entity()
                                + " would move onto cell " + change.newPosition()
                                + " claimed by a glyph product in this half cycle");
            }
        }
        Set<GridPoint> destinations = new HashSet<>();
        for (AtomChange change : changes) {
            if (change.newPosition().equals(change.oldPosition())) {
                continue;
            }
            if (!destinations.add(change.newPosition())) {
                haltForMultiEntityDestinationConflict(change.newPosition(), 0, 0);
                return false;
            }
        }
        for (AtomChange change : changes) {
            if (change.newPosition().equals(change.oldPosition())) {
                continue;
            }
            if (!board.contains(change.newPosition())) {
                continue;
            }
            long occupant = entityAtPosition.getOrDefault(change.newPosition(), 0L);
            if (occupant == 0) {
                continue;
            }
            boolean occupantMovesAway = false;
            for (AtomChange other : changes) {
                if (other.entity() == occupant
                        && other.oldPosition().equals(change.newPosition())
                        && !other.newPosition().equals(other.oldPosition())) {
                    occupantMovesAway = true;
                    break;
                }
            }
            if (!occupantMovesAway) {
                haltForMultiEntityDestinationConflict(change.newPosition(), change.entity(), occupant);
                return false;
            }
        }
        return true;
    }

    /**
     * 变换提交前检查变换后的原子位置与机构基座。
     *
     * <p>棋盘本身是一格一个原子的稀疏映射，因此这里把“分子与分子重叠”落实为
     * 变换结果中不能出现两个实体占据同一格；机构基座之间、基座与任一结果原子重叠同样停机。</p>
     */
    private boolean checkRuntimeOverlaps(List<AtomChange> changes) {
        Map<Long, AtomChange> changesByEntity = new LinkedHashMap<>();
        for (AtomChange change : changes) {
            changesByEntity.put(change.entity(), change);
        }
        Map<GridPoint, Long> resultAt = new LinkedHashMap<>();
        for (Map.Entry<Long, GridPoint> entry : positionOfEntity.entrySet()) {
            long entity = entry.getKey();
            AtomChange change = changesByEntity.get(entity);
            GridPoint result = change == null ? entry.getValue() : change.newPosition();
            resultAt.put(result, entity);
        }
        for (int i = 0; i < mechanismCount(); i++) {
            // checking mechanism overlap
            for (int j = i + 1; j < mechanismCount(); j++) {
                if (nextX[i] == nextX[j] && nextY[i] == nextY[j]) {
                    haltForMechanismConflict(
                            i,
                            j,
                            new GridPoint(nextX[i], nextY[i]));
                    return false;
                }
            }
            // checking mechanism-entity overlap
            GridPoint mechBase = new GridPoint(nextX[i], nextY[i]);
            long occupant = resultAt.getOrDefault(mechBase, 0L);
            if (occupant != 0) {
                haltForMechanismEntityConflict(i, occupant, mechBase);
                return false;
            }
        }
        return true;
    }

    private void commitAtomChanges(List<AtomChange> changes) {
        // 先移除全部旧位置，原子交换位置才能原子化完成。
        for (AtomChange change : changes) {
            if (!change.newPosition().equals(change.oldPosition())) {
                board.remove(change.oldPosition());
                positionOfEntity.remove(
                        entityAtPosition.remove(change.oldPosition()));
            }
        }
        for (AtomChange change : changes) {
            long entity = change.entity();
            if (change.newPosition().equals(change.oldPosition())) {
                if (!change.newAtom().equals(change.oldAtom())) {
                    board.put(change.oldPosition(), change.newAtom());
                }
            } else addEntityPositions(entity, change.newPosition(), change.newAtom());
            int delta = change.grabDelta();
            if (delta != 0) {
                int count = grabCounts.getOrDefault(entity, 0) + delta;
                if (count > 0) {
                    grabCounts.put(entity, count);
                } else {
                    grabCounts.remove(entity);
                }
            }
        }
    }

    private void spawnInputs(Set<GridPoint> reservedCells) {
        for (InputSpec input : plan.inputs()) {
            boolean blocked = false;
            for (AtomPlacement placement : input.atoms()) {
                if (board.contains(placement.position())
                        || reservedCells.contains(placement.position())) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                continue;
            }
            for (AtomPlacement placement : input.atoms()) {
                long entity = nextEntityId++;
                addEntityPositions(entity, placement.position(), placement.atom());
            }
        }
    }

    private void consumeOutputs() {
        List<OutputSpec> outputs = plan.outputs();
        for (int index = 0; index < outputs.size(); index++) {
            OutputSpec output = outputs.get(index);
            if (!matchesOutput(output)) {
                continue;
            }
            for (AtomPlacement placement : output.atoms()) {
                board.remove(placement.position());
                long entity = entityAtPosition.remove(placement.position());
                positionOfEntity.remove(entity);
                grabCounts.remove(entity);
            }
            outputCounts[index]++;
        }
    }

    private boolean matchesOutput(OutputSpec output) {
        for (AtomPlacement placement : output.atoms()) {
            long entity = entityAtPosition.getOrDefault(placement.position(), 0L);
            Atom actual = board.get(placement.position());
            if (entity == 0L || actual == null
                    || grabCounts.getOrDefault(entity, 0) > 0
                    || !actual.equals(placement.atom())) {
                return false;
            }
        }
        return true;
    }

    // ---------- 事务 ----------

    /** 本周期开始时的完整状态快照；回滚时用它恢复。 */
    private Snapshot captureState() {
        List<List<Long>> held = new ArrayList<>(heldByMechanism.size());
        for (List<Long> src : heldByMechanism) {
            held.add(List.copyOf(src));
        }
        return new Snapshot(
                Arrays.copyOf(x, x.length),
                Arrays.copyOf(y, y.length),
                Arrays.copyOf(direction, direction.length),
                Arrays.copyOf(length, length.length),
                new LinkedHashMap<>(board.asMap()),
                new LinkedHashMap<>(entityAtPosition),
                new LinkedHashMap<>(positionOfEntity),
                new LinkedHashMap<>(grabCounts),
                held,
                Arrays.copyOf(outputCounts, outputCounts.length),
                nextEntityId,
                glyphs.captureStates(),
                glyphs.pendingSpawns());
    }

    private void addEntityPositions(long id, GridPoint pos, Atom atom) {
        board.put(pos, atom);
        entityAtPosition.put(pos, id);
        positionOfEntity.put(id, pos);
    }

    /** 恢复快照；周期号、停机标志和警告列表都不在快照范围内。 */
    private void restoreState(Snapshot snapshot) {
        System.arraycopy(snapshot.mechanismX(), 0, x, 0, x.length);
        System.arraycopy(snapshot.mechanismY(), 0, y, 0, y.length);
        System.arraycopy(snapshot.direction(), 0, direction, 0, direction.length);
        System.arraycopy(snapshot.length(), 0, length, 0, length.length);
        board.clear();
        board.putAll(snapshot.atoms());
        entityAtPosition.clear();
        entityAtPosition.putAll(snapshot.entityAtPosition());
        positionOfEntity.clear();
        positionOfEntity.putAll(snapshot.positionOfEntity());
        grabCounts.clear();
        grabCounts.putAll(snapshot.grabCounts());
        List<List<Long>> heldSnapshot = snapshot.held();
        int len = Math.max(heldByMechanism.size(), heldSnapshot.size());
        for (int i = 0; i < len; i++) {
            if (i >= heldSnapshot.size()) {
                break;
            }
            if (i >= heldByMechanism.size()) {
                heldByMechanism.add(new ArrayList<>(heldSnapshot.get(i)));
                continue;
            }
            List<Long> held = heldByMechanism.get(i);
            held.clear();
            held.addAll(heldSnapshot.get(i));
        }
        System.arraycopy(snapshot.outputCounts(), 0, outputCounts, 0, outputCounts.length);
        nextEntityId = snapshot.nextEntityId();
        glyphs.restoreStates(snapshot.glyphInstances());
        glyphs.rewindAfterRollback();
        glyphs.restorePendingSpawns(snapshot.pendingSpawns());
    }

    /** 一个周期开始时的状态。 */
    private record Snapshot(
            long[] mechanismX,
            long[] mechanismY,
            byte[] direction,
            byte[] length,
            Map<GridPoint, Atom> atoms,
            Map<GridPoint, Long> entityAtPosition,
            Map<Long, GridPoint> positionOfEntity,
            Map<Long, Integer> grabCounts,
            List<List<Long>> held,
            long[] outputCounts,
            long nextEntityId,
            List<GlyphInstance<?>> glyphInstances,
            List<GlyphBoardView.PendingSpawn> pendingSpawns) {
    }

    /** 一个实体在本周期内的全部变化；全部先计算后提交。 */
    private record AtomChange(
            long entity,
            GridPoint oldPosition,
            GridPoint newPosition,
            Atom oldAtom,
            Atom newAtom,
            int grabDelta) {
    }

    /** 内部控制流：普通业务冲突需要回滚并停机，但不应作为异常暴露给调用方。 */
    private static final class HaltSignal extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private HaltSignal() {
            super(null, null, false, false);
        }
    }
}
