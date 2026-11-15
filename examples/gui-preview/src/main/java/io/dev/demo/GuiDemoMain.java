package io.dev.demo;

import io.dev.aae.mech.InstructionTape;
import io.dev.aae.mech.MechanismSpec;

import io.dev.aae.glyph.GlyphRegistry;
import io.dev.aae.glyph.GlyphSpec;
import io.dev.aae.track.TrackBuilder;

import io.dev.aae.*;
import io.dev.demo.om.OMAtom;
import io.dev.demo.om.OMBonds;
import io.dev.demo.om.OMGlyphs;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 独立的动态渲染 GUI 启动类 / 交互演示器。
 */
public final class GuiDemoMain {

    private static final List<GlyphRegistry.GlyphHandle> GLYPH_HANDLES = new ArrayList<>();

    public static SimulationPlan createSamplePlan() {
        var input1 = InputSpec.of(
                AtomPlacement.of(-3, 1, Atom.of(7).withBond(Direction.NORTH, OMBonds.NORMAL.code)),
                AtomPlacement.of(-3, 0, OMAtom.earth().withBond(Direction.SOUTH, OMBonds.NORMAL.code)));
        var input2 = InputSpec.of(AtomPlacement.of(0, 1, OMAtom.water()));
        var input3 = InputSpec.of(AtomPlacement.of(3, 0, OMAtom.water()));

        // 提交位置
        var output = OutputSpec.of(
                AtomPlacement.of(3, 1, OMAtom.salt().withBond(Direction.EAST, OMBonds.NORMAL.code)),
                AtomPlacement.of(4, 1, OMAtom.water().withBond(Direction.WEST, OMBonds.NORMAL.code)));

        GlyphSpec calcification = OMGlyphs.calcificationSpec(2, 1);
        GlyphSpec bonder = OMGlyphs.bondingSpec(1, 0, Direction.EAST);
        GLYPH_HANDLES.add(GlyphRegistry.global().register(OMGlyphs.CALCIFICATION));
        GLYPH_HANDLES.add(GlyphRegistry.global().register(OMGlyphs.BONDING));

        var track = TrackBuilder.start(5, 0).west().eastSouth().east().loop();

        return new SimulationPlan(
                List.of(// 机械臂
                        new MechanismSpec(point(-1, 0), Direction.WEST, "feqqer", 2),
                        new MechanismSpec(point(0, 0), Direction.EAST, "rdfa"),
                        new MechanismSpec(point(1, 1), Direction.WEST, "fdra"),
                        new MechanismSpec(point(5, 0), Direction.WEST, "fgggrgfgggrg", 2),
                        new MechanismSpec(point(6, 1), Direction.WEST, "rgfgggrgfggg", 2),
                        new MechanismSpec(point(4, 0), Direction.WEST, "ggrgfgggrgfg", 2),
                        // 正向与对角分支分别携带独立原子，共享伸缩，避免同分子伸缩冲突。
                        new MechanismSpec(point(-3, 2), Direction.EAST, 0x11, "fwrs")
                ),
                List.of(track),
                List.of(input1, input2, input3,
                        InputSpec.of(AtomPlacement.of(-2, 2, OMAtom.water())),
                        InputSpec.of(AtomPlacement.of(-2, 3, OMAtom.earth()))),
                List.of(output,
                        OutputSpec.of(AtomPlacement.of(-1, 2, OMAtom.water())),
                        OutputSpec.of(AtomPlacement.of(-1, 4, OMAtom.earth()))),
                List.of(calcification, bonder),
                5);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulationPlan plan = createSamplePlan();
            OrthogonalSimulationPanel panel = new OrthogonalSimulationPanel(plan);
            panel.reset();

            JFrame frame = new JFrame("Orthogonal Simulator - Dynamic GUI Render");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(panel, BorderLayout.CENTER);

            // 控制条
            JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            JButton playPauseBtn = new JButton("Play");
            playPauseBtn.addActionListener(e -> {
                boolean nextState = !panel.isPlaying();
                panel.setPlaying(nextState);
                playPauseBtn.setText(nextState ? "Pause" : "Play");
            });

            JButton stepBtn = new JButton("Step Cycle");
            stepBtn.addActionListener(e -> {
//                panel.setPlaying(false);
                playPauseBtn.setText("Play");
                panel.stepCycle();
            });

            JButton resetBtn = new JButton("Reset");
            resetBtn.addActionListener(e -> {
                panel.reset();
            });

            JLabel infoLabel = new JLabel("products: 0   cycle: 0");

            controlPanel.add(playPauseBtn);
            controlPanel.add(stepBtn);
            controlPanel.add(resetBtn);
            controlPanel.add(infoLabel);

            InstructionTapeView tapeView = new InstructionTapeView(plan);
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(controlPanel, BorderLayout.NORTH);
            bottomPanel.add(tapeView, BorderLayout.CENTER);

            var labelTime = new Timer(32, e -> {
                infoLabel.setText(
                        "products: " + panel.getProductCount() + "   cycle: " + panel.getCycleCount());
                tapeView.showCycle(panel.getCycleCount());
            });
            labelTime.start();

            frame.add(bottomPanel, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static GridPoint point(long x, long y) {
        return new GridPoint(x, y);
    }

    private static final class InstructionTapeView extends JScrollPane {
        private final List<TapeRow> rows = new ArrayList<>();

        private InstructionTapeView(SimulationPlan plan) {
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            for (int index = 0; index < plan.mechanisms().size(); index++) {
                TapeRow row = new TapeRow(index, plan.mechanisms().get(index));
                rows.add(row);
                content.add(row.panel);
            }

            setViewportView(content);
            setBorder(BorderFactory.createTitledBorder("Instruction tapes"));
            setPreferredSize(new Dimension(800, 180));
            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            showCycle(0);
        }

        private void showCycle(long cycle) {
            rows.forEach(row -> row.showCycle(cycle));
        }
    }

    private static final class TapeRow {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        private final InstructionTape tape;
        private final List<JLabel> instructions = new ArrayList<>();
        private final Color normalColor;

        private TapeRow(int mechanismIndex, MechanismSpec mechanism) {
            tape = mechanism.tape();
            JLabel numberLabel = new JLabel("Mechanism " + (mechanismIndex + 1) + ":");
            numberLabel.setPreferredSize(new Dimension(100, numberLabel.getPreferredSize().height));
            panel.add(numberLabel);

            Font tapeFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
            normalColor = UIManager.getColor("Label.foreground");
            for (int index = 0; index < tape.length(); index++) {
                JLabel instruction = new JLabel(String.valueOf(tape.instructionAt(tape.startCycle() + index).symbol()));
                instruction.setFont(tapeFont);
                instruction.setForeground(normalColor);
                instructions.add(instruction);
                panel.add(instruction);
            }
            if (instructions.isEmpty()) {
                panel.add(new JLabel("(empty)"));
            }
        }

        private void showCycle(long cycle) {
            int currentIndex = -1;
            if (cycle >= tape.startCycle() && tape.length() > 0) {
                long elapsed = cycle - tape.startCycle();
                if (tape.playback() == InstructionTape.Playback.LOOPING) {
                    currentIndex = (int) (elapsed % tape.length());
                } else if (elapsed < tape.length()) {
                    currentIndex = (int) elapsed;
                }
            }

            for (int index = 0; index < instructions.size(); index++) {
                instructions.get(index).setForeground(index == currentIndex ? Color.RED : normalColor);
            }
        }
    }
}
