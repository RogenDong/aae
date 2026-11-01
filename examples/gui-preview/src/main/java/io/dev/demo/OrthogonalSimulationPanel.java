package io.dev.demo;

import io.dev.aae.OrthogonalSimulator;
import io.dev.aae.SimulationPlan;
import io.dev.demo.render.FloatWorld;
import io.dev.demo.render.OrthogonalRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * 动态渲染 GUI 面板组件 (Swing / AWT)。
 * 驱动模拟器与动态 FloatWorld 进行平滑插值动画，并响应用户交互与周期推进。
 */
public final class OrthogonalSimulationPanel extends JPanel {

    private final SimulationPlan plan;
    private final OrthogonalSimulator simulator;
    private final FloatWorld floatWorld;
    private final OrthogonalRenderer renderer;

    private double originX = 350.0;
    private double originY = 300.0;
    private int dragStartX;
    private int dragStartY;
    private double dragStartOriginX;
    private double dragStartOriginY;

    private double currentPortion = 0.0;
    private final double portionSpeed = 0.05; // 每一动画帧推进的 portion 增量 (约20帧走完一个周期)
    private boolean isPlaying = false;
    private boolean isStepByStep = false;
    private boolean preStepRunning = false;
    private Timer animationTimer;

    public OrthogonalSimulationPanel(SimulationPlan plan) {
        this(new OrthogonalSimulator(plan));
    }

    public OrthogonalSimulationPanel(OrthogonalSimulator simulator) {
        this.plan = Objects.requireNonNull(simulator.plan(), "plan");
        this.simulator = Objects.requireNonNull(simulator, "simulator");
        this.floatWorld = new FloatWorld();
        this.renderer = new OrthogonalRenderer(plan.tracks(), plan.glyphs());

        setPreferredSize(new Dimension(800, 650));
        setDoubleBuffered(true);

        // 初始生成第 0 周期
        floatWorld.regenerate(this.simulator, this.plan, 0.0);

        setupMouseControls();
        setupAnimationLoop();
    }

    public OrthogonalSimulator simulator() {
        return simulator;
    }

    public FloatWorld floatWorld() {
        return floatWorld;
    }

    public OrthogonalRenderer renderer() {
        return renderer;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setOrigin(double x, double y) {
        this.originX = x;
        this.originY = y;
        repaint();
    }

    public long getCycleCount() {
        return simulator.cycle();
    }

    public long getProductCount() {
        return simulator.getSubmitCount();
    }

    public void stepCycle() {
        this.isPlaying = true;
        this.isStepByStep = true;
    }

    public void reset() {
        simulator.init();
        currentPortion = 0.0;
        floatWorld.regenerate(simulator, plan, currentPortion);
        repaint();
    }

    private void setupMouseControls() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartX = e.getX();
                dragStartY = e.getY();
                dragStartOriginX = originX;
                dragStartOriginY = originY;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                originX = dragStartOriginX + (e.getX() - dragStartX);
                originY = dragStartOriginY + (e.getY() - dragStartY);
                repaint();
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    private void pause() {
        isPlaying = false;
        floatWorld.generateStatic(simulator, plan);
        repaint();
    }

    private void setupAnimationLoop() {
        // ~60 FPS 动画定时器 (16ms)
        animationTimer = new Timer(16, e -> {
            if (!isPlaying) return;
            if (simulator.isHalted()) {
                preStepRunning = false;
                isStepByStep = false;
                pause();
                return;
            }
//            if (simulator.isComplete()) {
//                // 完成后保持在终态
//                pause();
//                return;
//            }

            currentPortion += portionSpeed;
            if (currentPortion >= 1.0) {
                // 当前周期的插值动作完成，提交模拟周期步进
                simulator.step();
                currentPortion = 0.0;
                preStepRunning = false;
            } else {
                preStepRunning = true;
            }
            floatWorld.regenerate(simulator, plan, currentPortion);
            repaint();

            // 如果是步进模式，完成一步就停止
            if (isStepByStep && !preStepRunning) {
                isStepByStep = false;
                isPlaying = false;
            }
        });
        animationTimer.start();
    }

    public void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        renderer.render(g2, getWidth(), getHeight(), originX, originY, floatWorld);
    }
}
