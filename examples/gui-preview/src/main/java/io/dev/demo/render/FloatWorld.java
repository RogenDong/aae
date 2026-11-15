package io.dev.demo.render;

import io.dev.aae.mech.*;

import io.dev.aae.track.TrackDirection;

import io.dev.aae.*;

import java.util.*;

/**
 * 提供正交直角坐标系下的视图投影（View Model），支持在两个模拟周期之间基于 portion (0.0..1.0) 进行动态平滑插值，
 * 或生成静态当前周期的正交浮点视图。
 * <p>
 * 包含：
 * - 动态运行中的原子（彩色圆形）
 * - 动态运行中的机械臂（黑色朴素矩形）
 * - 生产输入位点（半透明分子模板）
 * - 提交输出位点（半透明分子模板）
 */
public final class FloatWorld {

    private double portion;
    private final List<FloatAtom> dynamicAtoms = new ArrayList<>();
    private final List<FloatArm> dynamicArms = new ArrayList<>();
    private final List<FloatAtom> templateAtoms = new ArrayList<>();

    public FloatWorld() {
        this.portion = 0.0;
    }

    public double portion() {
        return portion;
    }

    public List<FloatAtom> dynamicAtoms() {
        return Collections.unmodifiableList(dynamicAtoms);
    }

    public List<FloatArm> dynamicArms() {
        return Collections.unmodifiableList(dynamicArms);
    }

    public List<FloatAtom> templateAtoms() {
        return Collections.unmodifiableList(templateAtoms);
    }

    /**
     * 生成静态状态投影（无需插值过渡，portion = 0.0）。
     */
    public void generateStatic(OrthogonalSimulator simulator, SimulationPlan plan) {
        regenerate(simulator, plan, 0.0);
    }

    /**
     * 根据当前模拟器状态及当前周期的指令运动，对机械臂和原子进行平滑插值。
     *
     * @param simulator 当前周期的模拟器
     * @param plan      模拟计划
     * @param portion   插值因子 [0.0, 1.0]
     */
    public void regenerate(OrthogonalSimulator simulator, SimulationPlan plan, double portion) {
        this.portion = Math.max(0.0, Math.min(1.0, portion));
        this.dynamicAtoms.clear();
        this.dynamicArms.clear();
        this.templateAtoms.clear();

        // 1. 生成模板分子（生产输入、提交输出位置），设置半透明度 alpha = 0.35f
        for (InputSpec input : plan.inputs()) {
            for (AtomPlacement placement : input.atoms()) {
                GridPoint pos = placement.position();
                templateAtoms.add(FloatAtom.fromAtom(pos.x(), pos.y(), placement.atom(), 0.35f));
            }
        }
        for (OutputSpec output : plan.outputs()) {
            for (AtomPlacement placement : output.atoms()) {
                GridPoint pos = placement.position();
                templateAtoms.add(FloatAtom.fromAtom(pos.x(), pos.y(), placement.atom(), 0.35f));
            }
        }

        // 2. 收集机械臂的运动插值
        long currentCycle = simulator.cycle();
        for (int i = 0; i < plan.mechanisms().size(); i++) {
            var spec = plan.mechanisms().get(i);
            MechanismView view = simulator.mechanism(i);
            Instruction inst = spec.tape().instructionAt(currentCycle);

            double baseX = view.position().x();
            double baseY = view.position().y();
            double baseAngle = FloatArm.directionToAngle(view.direction());

            // 插值位移与旋转
            double currentX = baseX;
            double currentY = baseY;
            double currentAngle = baseAngle;
            double currentLength = view.length();

            switch (inst) {
                case MOVE_FORWARD -> {
                    GridPoint motion = trackMotion(plan, view, TrackDirection.PLUS);
                    currentX += motion.x() * this.portion;
                    currentY += motion.y() * this.portion;
                }
                case MOVE_BACKWARD -> {
                    GridPoint motion = trackMotion(plan, view, TrackDirection.MINUS);
                    currentX += motion.x() * this.portion;
                    currentY += motion.y() * this.portion;
                }
                case ROTATE_CW -> {
                    currentAngle += (Math.PI / 2.0) * this.portion;
                }
                case ROTATE_CCW -> {
                    currentAngle -= (Math.PI / 2.0) * this.portion;
                }
                case EXTEND_PISTON -> {
                    if (view.length() < MechanismSpec.MAX_LENGTH) {
                        currentLength += this.portion;
                    }
                }
                case RETRACT_PISTON -> {
                    if (view.length() > MechanismSpec.MIN_LENGTH) {
                        currentLength -= this.portion;
                    }
                }
                default -> {
                    // 枢轴旋转、抓取、释放与等待不改变机构自身投影。
                }
            }

            // 所有分支共享机构状态；局部偏移决定分支角度及对角杆的实际长度。
            for (int bit : view.combination().enabledBits()) {
                GridPoint offset = view.combination().localOffset(bit);
                double angle = currentAngle + Math.atan2(offset.y(), offset.x());
                double length = currentLength * Math.hypot(offset.x(), offset.y());
                dynamicArms.add(new FloatArm(i, bit, currentX, currentY,
                        angle, length, view.grabbing()));
            }
        }

        // 3. 为每个实体解析唯一的插值运动，再收集棋盘上的原子。
        //    多个机构可能同时持有不同分子，格点上的抓取计数并不说明当前原子属于哪个机构。
        Map<Long, EntityIntent> entityMotions = collectEntityMotions(simulator, plan);
        for (GridPoint pos : simulator.atomPositions()) {
            Atom atom = simulator.atomAt(pos);
            if (atom == null) continue;

            double atomX = pos.x();
            double atomY = pos.y();
            double atomRot = 0.0;

            long entity = simulator.entityAt(pos);
            EntityIntent motion = entity == 0 ? null : entityMotions.get(entity);
            if (motion != null) {
                if (motion.kind() == EntityIntent.Kind.MOVE) {
                    atomX += motion.deltaX() * this.portion;
                    atomY += motion.deltaY() * this.portion;
                } else if (motion.kind() == EntityIntent.Kind.ROTATE) {
                    // 意图使用模四转数；逆时针四分之一圈应沿短弧插值而非顺时针转三圈。
                    int turns = motion.quarterTurns() > 2
                            ? motion.quarterTurns() - 4 : motion.quarterTurns();
                    double dAngle = (Math.PI / 2.0) * turns * this.portion;
                    GridPoint center = motion.rotationCenter();
                    double rx = pos.x() - center.x();
                    double ry = pos.y() - center.y();
                    // 2D 顺时针旋转矩阵 [cos, -sin; sin, cos]
                    double newRx = rx * Math.cos(dAngle) - ry * Math.sin(dAngle);
                    double newRy = rx * Math.sin(dAngle) + ry * Math.cos(dAngle);
                    atomX = center.x() + newRx;
                    atomY = center.y() + newRy;
                    atomRot += dAngle;
                }
            }

            dynamicAtoms.add(new FloatAtom(atomX, atomY, atomRot, atom.type(), atom.bondTypes(), 1.0f));
        }
    }

    /**
     * 收集每个实体在当前周期的插值运动。
     *
     * <p>同一实体可被多个机构同时持有。与模拟器的意图兼容规则一致，完全相同
     * 的运动只应用一次；若持有者给出冲突运动，当前周期没有可安全插值的目标，
     * 因而让该实体保持在已提交位置，等待模拟器在提交周期报告停机。</p>
     */
    private static Map<Long, EntityIntent> collectEntityMotions(
            OrthogonalSimulator simulator, SimulationPlan plan) {
        Map<Long, EntityIntent> resolved = new LinkedHashMap<>();
        Set<Long> conflicts = new HashSet<>();
        for (int i = 0; i < plan.mechanisms().size(); i++) {
            for (EntityIntent intent : simulator.motionIntents(i)) {
                long entity = intent.entity();
                if (conflicts.contains(entity)) continue;
                EntityIntent previous = resolved.putIfAbsent(entity, intent);
                if (previous != null && !previous.compatibleWith(intent)) {
                    // 冲突身份单独保留，防止后续兼容意图覆盖冲突，确保声明顺序无关。
                    conflicts.add(entity);
                    resolved.remove(entity);
                }
            }
        }
        return resolved;
    }

    private static GridPoint trackMotion(
            SimulationPlan plan, MechanismView mechanism, TrackDirection direction) {
        GridPoint origin = mechanism.position();
        GridPoint destination = plan.trackDestination(origin, direction);
        return new GridPoint(
                Math.subtractExact(destination.x(), origin.x()),
                Math.subtractExact(destination.y(), origin.y()));
    }

}
