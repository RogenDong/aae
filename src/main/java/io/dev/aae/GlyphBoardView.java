package io.dev.aae;

import java.util.*;
import java.util.function.Supplier;

/**
 * {@link GlyphBoard} 的模拟器内部实现，一个半周期一个实例，半周期内全部符文共用。
 *
 * <p>它是模拟器棋盘状态之上的一层视图，不复制任何棋盘数据：所有读写都直接落在模拟器的
 * {@link AtomGrid}、实体索引、抓取计数和持有集合上。因此符文看不到内部结构，也无法
 * 破坏 {@code entityId} 稳定性。</p>
 *
 * <h2>延迟生成</h2>
 *
 * <p>{@link #spawn} 与 {@link #queueSpawn} 都遵循“不覆盖占用格、往后延迟直到没有占用”：
 * 目标格点空闲的即时生成立即落盘；被占用的产物进入本视图的队列，并<b>立刻占用</b>该格点
 * （同一半周期内后续符文看到的 {@link #isOccupied} 即为真）。队列在符文阶段结束时交接给
 * {@link GlyphRuntime}，由后者在每个半周期的机构运动之后、输入生产之前按声明顺序重试落盘；
 * 仍然被占用的产物留在本整数周期队列里，最多等待到第二半周期结束。</p>
 *
 * <p>尚未落盘的预留格点不允许被机构运动压到：运动检查会抛出
 * {@link GlyphExecutionException} 回滚整周期。已经落盘的延迟产物就是普通棋盘原子，机构压到
 * 它时走普通的机构同格冲突警告停机路径。输入模板与预留格点相交时整份模板被静默跳过。</p>
 *
 * <h2>临时键</h2>
 *
 * <p>{@link #unbond} 拆键后会在两侧写下键字节 {@link Atom#TEMPORARY_BOND}——“刚解除键”的临时标记。标记直接
 * 写在棋盘上，因此按方向精确、参与周期快照与回滚，并由模拟器在下一个周期开始时清除；
 * 在此之前，{@link #remove} 拒绝删除带标记的原子。</p>
 */
final class GlyphBoardView implements GlyphBoard {

    private final AtomGrid board;
    private final Map<GridPoint, Long> entityAtPosition;
    private final Map<Long, GridPoint> positionOfEntity;
    private final Map<Long, Integer> grabCounts;
    private final Supplier<Set<Long>> heldEntities;
    private final Supplier<Long> nextEntityIdSupplier;

    /**
     * 本次符文阶段登记的待落盘产物，按登记顺序。
     *
     * <p>{@link #spawn} 命中占用格时也会追加到这里；同一格点再次登记会原子地取消旧产物。</p>
     */
    private final List<PendingSpawn> pendingSpawns = new ArrayList<>(4);

    /** 本半周期的预留格点：{@link #pendingSpawns} 的位置投影，用于快速判占用。 */
    private final Set<GridPoint> reservedSpawns = new LinkedHashSet<>();

    private boolean active = true;
    private GlyphInstance<?> instance;

    void bind(GlyphInstance<?> instance) {
        requireActive();
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    private GridPoint resolve(int placeholder) {
        requireActive();
        if (instance == null) {
            throw new GlyphExecutionException("glyph board view is not bound to an instance");
        }
        return instance.worldPoint(instance.definition().placeholderAt(placeholder));
    }

    private boolean containsNeighbor(GridPoint from, Direction direction) {
        Objects.requireNonNull(direction, "direction");
        GridPoint neighbor = from.translate(direction);
        return instance.definition().placeholder().stream()
                .anyMatch(local -> instance.worldPoint(local).equals(neighbor));
    }

    GlyphBoardView(
            AtomGrid board,
            Map<GridPoint, Long> entityAtPosition,
            Map<Long, GridPoint> positionOfEntity,
            Map<Long, Integer> grabCounts,
            Supplier<Set<Long>> heldEntities,
            Supplier<Long> nextEntityIdSupplier,
            Set<GridPoint> inheritedReservations) {
        this.board = Objects.requireNonNull(board, "board");
        this.entityAtPosition = Objects.requireNonNull(entityAtPosition, "entityAtPosition");
        this.positionOfEntity = Objects.requireNonNull(positionOfEntity, "positionOfEntity");
        this.grabCounts = Objects.requireNonNull(grabCounts, "grabCounts");
        this.heldEntities = Objects.requireNonNull(heldEntities, "heldEntities");
        this.nextEntityIdSupplier = Objects.requireNonNull(nextEntityIdSupplier, "nextEntityIdSupplier");
        this.reservedSpawns.addAll(
                Objects.requireNonNull(inheritedReservations, "inheritedReservations"));
    }

    /**
     * 回调窗口是否打开。
     */
    boolean isActive() {
        return active;
    }

    /**
     * 关闭回调窗口；关闭后任何方法都拒绝执行。
     *
     * <p>队列不在这里丢弃——符文阶段成功结束时由 {@link #handOverPendingSpawns()} 交接给
     * {@link GlyphRuntime}；只有回滚路径才会直接丢掉队列。</p>
     */
    void invalidate() {
        active = false;
        reservedSpawns.clear();
    }

    /**
     * 把本半周期登记的待落盘产物交接给运行时，并清空本地队列。
     *
     * <p>由模拟器在符文阶段成功结束时、{@link #invalidate()} 之前调用。</p>
     */
    List<PendingSpawn> handOverPendingSpawns() {
        if (pendingSpawns.isEmpty()) {
            return List.of();
        }
        List<PendingSpawn> handedOver = List.copyOf(pendingSpawns);
        pendingSpawns.clear();
        reservedSpawns.clear();
        return handedOver;
    }

    /** 本半周期登记的待落盘产物，按登记顺序。 */
    List<PendingSpawn> pendingSpawns() {
        return List.copyOf(pendingSpawns);
    }

    @Override
    public Optional<Atom> atomAt(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        return Optional.ofNullable(board.get(position));
    }

    @Override
    public boolean isOccupied(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        return board.contains(position) || reservedSpawns.contains(position);
    }

    @Override
    public long entityAt(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        return entityAtPosition.getOrDefault(position, 0L);
    }

    @Override
    public Molecule moleculeAt(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        return MoleculeBfs.moleculeAt(board, position);
    }

    @Override
    public int grabCountAt(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        long entity = entityAtPosition.getOrDefault(position, 0L);
        return grabCounts.getOrDefault(entity, 0);
    }

    @Override
    public boolean isBonded(int placeholder, Direction direction) throws GlyphExecutionException {
        GridPoint from = resolve(placeholder);
        Objects.requireNonNull(direction, "direction");
        return containsNeighbor(from, direction) && board.isBonded(from, direction);
    }

    @Override
    public Set<Direction> bondedDirections(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        Atom atom = board.get(position);
        if (atom == null) {
            return Set.of();
        }
        Set<Direction> directions = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            if (containsNeighbor(position, direction) && atom.hasRealBond(direction)) {
                directions.add(direction);
            }
        }
        return directions;
    }

    @Override
    public TryResult setType(int placeholder, byte type) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        Atom.requireAtomCode(type);
        Atom atom = board.get(position);
        if (atom == null) {
            return TryResult.SKIPPED;
        }
        board.put(position, atom.withType(type));
        return TryResult.APPLIED;
    }

    @Override
    public TryResult spawn(int placeholder, Atom atom) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        requireRealAtom(atom);
        if (board.contains(position) || reservedSpawns.contains(position)) {
            // 不覆盖占用格：登记为延迟产物并立刻预留该格点。
            reserve(position, atom);
            return TryResult.SKIPPED;
        }
        placeInto(position, atom);
        return TryResult.APPLIED;
    }

    @Override
    public TryResult queueSpawn(int placeholder, Atom atom) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        requireRealAtom(atom);
        reserve(position, atom);
        return TryResult.APPLIED;
    }

    @Override
    public TryResult remove(int placeholder) throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        long entity = entityAtPosition.getOrDefault(position, 0L);
        Atom atom = board.get(position);
        if (atom == null || entity == 0L || grabCounts.getOrDefault(entity, 0) > 0
                || heldEntities.get().contains(entity) || atom.bondCodes() != 0) {
            return TryResult.SKIPPED;
        }
        return removeEntity(entity) ? TryResult.APPLIED : TryResult.SKIPPED;
    }

    @Override
    public TryResult bond(int placeholder, Direction direction, byte bondCode)
            throws GlyphExecutionException {
        GridPoint from = resolve(placeholder);
        Objects.requireNonNull(direction, "direction");
        if (!containsNeighbor(from, direction)) {
            return TryResult.SKIPPED;
        }
        Atom.requireBondCode(bondCode);
        if (board.get(from) == null || board.get(from.translate(direction)) == null) {
            return TryResult.SKIPPED;
        }
        if (hasRealBondMark(from, direction)) {
            // 同键、异键都属于“该方向已经有长期键”，静默跳过。
            return TryResult.SKIPPED;
        }
        // 到这里最多只有单侧残留键（临时标记，或没有反向键的孤键），直接覆盖。
        board.bond(from, direction, bondCode);
        return TryResult.APPLIED;
    }

    @Override
    public TryResult unbond(int placeholder, Direction direction) throws GlyphExecutionException {
        GridPoint from = resolve(placeholder);
        Objects.requireNonNull(direction, "direction");
        if (!containsNeighbor(from, direction)) {
            return TryResult.SKIPPED;
        }
        GridPoint to = from.translate(direction);
        Atom source = board.get(from);
        Atom target = board.get(to);
        if (source == null || target == null) {
            return TryResult.SKIPPED;
        }
        Direction opposite = direction.opposite();
        if (!source.hasRealBond(direction) || !target.hasRealBond(opposite)) {
            // 只处理互惠长期键；单侧残留键不算“可拆的键”。
            return TryResult.SKIPPED;
        }
        board.put(from, source.withBond(direction, Atom.TEMPORARY_BOND));
        board.put(to, target.withBond(opposite, Atom.TEMPORARY_BOND));
        return TryResult.APPLIED;
    }

    @Override
    public boolean wasJustUnbonded(int placeholder, Direction direction)
            throws GlyphExecutionException {
        GridPoint position = resolve(placeholder);
        Objects.requireNonNull(direction, "direction");
        if (!containsNeighbor(position, direction)) {
            return false;
        }
        Atom source = board.get(position);
        Atom target = board.get(position.translate(direction));
        return source != null
                && target != null
                && source.bondType(direction) == Atom.TEMPORARY_BOND
                && target.bondType(direction.opposite()) == Atom.TEMPORARY_BOND;
    }

    private void reserve(GridPoint position, Atom atom) {
        // 同一格点重复登记时后一次覆盖前一次，避免同一格出现两个产物。
        pendingSpawns.removeIf(spawn -> spawn.position().equals(position));
        pendingSpawns.add(new PendingSpawn(position, atom));
        reservedSpawns.add(position);
    }

    /**
     * 该格点方向上是否已经没有可覆盖的键：任一侧存在长期键（同键或异键）时为真。
     */
    private boolean hasRealBondMark(GridPoint from, Direction direction) {
        Atom source = board.get(from);
        if (source != null && source.hasRealBond(direction)) {
            return true;
        }
        Atom target = board.get(from.translate(direction));
        return target != null && target.hasRealBond(direction.opposite());
    }

    /**
     * 删除实体；条件不满足时不改动棋盘并返回 {@code false}。
     */
    private boolean removeEntity(long entity) {
        GridPoint position = positionOfEntity.get(entity);
        if (!positionOfEntity.containsKey(entity)
                || grabCounts.getOrDefault(entity, 0) > 0) {
            return false;
        }

        board.unbondNeighbor(position);
        board.remove(position);
        grabCounts.remove(entity);
        positionOfEntity.remove(entity);
        entityAtPosition.remove(position);
        return true;
    }

    /**
     * 把一个新原子放到格点上。
     *
     * <p>调用方负责保证格点空闲；这里仍然清理残留的旧实体索引与反向键，避免任何路径留下
     * 悬挂键。</p>
     */
    private long placeInto(GridPoint position, Atom atom) {
        long entity = nextEntityIdSupplier.get();
        board.unbondNeighbor(position);
        board.put(position, atom);
        entityAtPosition.put(position, entity);
        positionOfEntity.put(entity, position);
        return entity;
    }

    private void requireActive() throws GlyphExecutionException {
        if (!active) {
            throw new GlyphExecutionException(
                    "glyph board view is only valid during a glyph callback");
        }
    }

    private static void requireRealAtom(Atom atom) {
        Objects.requireNonNull(atom, "atom");
        Atom.requireAtomCode(atom.type());
    }

    /**
     * 一个已经预留、等待落盘的符文产物。
     *
     * <p>它是独立的值对象，可以在半周期视图与 {@link GlyphRuntime} 之间传递。产物只在当前
     * 整数周期的剩余半周期内等待，第二半周期结束仍未落盘即被丢弃。</p>
     *
     * @param position 预留的世界格点
     * @param atom     产物原子
     */
    record PendingSpawn(GridPoint position, Atom atom) {
    }

    /**
     * 供调试与测试使用的只读快照。
     */
    Map<GridPoint, Atom> snapshot() {
        return board.asMap();
    }
}
