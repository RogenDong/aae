package io.dev.demo;

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
                "input_site1",
                new AtomPlacement(point(-3, 1), Atom.of((byte) 7).withBond(Direction.NORTH, OMBonds.NORMAL.code)),
                new AtomPlacement(point(-3, 0), OMAtom.earth().withBond(Direction.SOUTH, OMBonds.NORMAL.code)));
        var input2 = InputSpec.of("input_water_1", new AtomPlacement(point(0, 1), OMAtom.water()));
        var input3 = InputSpec.of("input_water_2", new AtomPlacement(point(3, 0), OMAtom.water()));

        // 提交位置
        var output = OutputSpec.of(
                "output_site",
                new AtomPlacement(point(3, 1), OMAtom.salt().withBond(Direction.EAST, OMBonds.NORMAL.code)),
                new AtomPlacement(point(4, 1), OMAtom.water().withBond(Direction.WEST, OMBonds.NORMAL.code)));

        GlyphSpec calcification = OMGlyphs.calcificationSpec(2, 1);
        GlyphSpec bonder = OMGlyphs.bondingSpec(1, 0, Direction.EAST);
        GLYPH_HANDLES.add(GlyphRegistry.global().register(OMGlyphs.CALCIFICATION));
        GLYPH_HANDLES.add(GlyphRegistry.global().register(OMGlyphs.BONDING));

        var ot1 = new TrackSpec(TrackType.ORTHOGONAL, List.of(point(5, 0), point(4, 0))); // ←
        var ot2 = new TrackSpec(TrackType.ORTHOGONAL, List.of(point(5, 1), point(6, 1))); // →
        var dt1 = new TrackSpec(TrackType.DIAGONAL, List.of(point(4, 0), point(5, 1)));   // ↘
        var dt2 = new TrackSpec(TrackType.DIAGONAL, List.of(point(6, 1), point(5, 0)));   // ↖

        return new SimulationPlan(
                List.of(// 机械臂
                        new MechanismSpec("1", point(-1, 0), Direction.WEST, InstructionTape.looping("feqqerfeqqer"), 2),
                        new MechanismSpec("2", point(0, 0), Direction.EAST, InstructionTape.looping("rdfardfardfa")),
                        new MechanismSpec("3", point(1, 1), Direction.WEST, InstructionTape.looping("fdrafdrafdra")),
                        new MechanismSpec("4", point(5, 0), Direction.WEST, InstructionTape.looping("fgggrgfgggrg"), 2),
                        new MechanismSpec("5", point(6, 1), Direction.WEST, InstructionTape.looping("rgfgggrgfggg"), 2),
                        new MechanismSpec("6", point(4, 0), Direction.WEST, InstructionTape.looping("ggrgfgggrgfg"), 2)
                ),
                List.of(ot1, ot2, dt1, dt2),
                List.of(input1, input2, input3),
                List.of(output),
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

            for (MechanismSpec mechanism : plan.mechanisms()) {
                TapeRow row = new TapeRow(mechanism);
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

        private TapeRow(MechanismSpec mechanism) {
            tape = mechanism.tape();
            JLabel idLabel = new JLabel("Mechanism " + mechanism.id() + ":");
            idLabel.setPreferredSize(new Dimension(100, idLabel.getPreferredSize().height));
            panel.add(idLabel);

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
