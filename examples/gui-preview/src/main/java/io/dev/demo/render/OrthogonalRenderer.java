package io.dev.demo.render;

import io.dev.aae.*;
import io.dev.demo.om.OMGlyphs;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;

/**
 * 正交直角坐标系渲染引擎。
 * 遵循规划要求：
 * - 标准正交直角坐标系的 world (正方格点网格，X向右，Y向下)
 * - 渲染 Arm 用黑色朴素矩形
 * - 渲染原子用彩色朴素圆形
 * - 渲染分子时，原子之间的键用灰色朴素矩形
 * - 生产分子、提交分子的位置用半透明分子 (alpha composite)
 */
public final class OrthogonalRenderer {

    /** 内建符文的底圈半径相对网格单元的比例，略大于原子半径。 */
    private static final double GLYPH_RADIUS_RATIO = 0.38;

    /** 内建符文的基础色：键合（绿）、拆键（橙）、钙化（紫）。 */
    private static final Color BONDING_COLOR = new Color(56, 160, 92);
    private static final Color UNBONDING_COLOR = new Color(214, 138, 46);
    private static final Color CALCIFICATION_COLOR = new Color(146, 92, 190);

    private final RenderConfig config;

    private final List<TrackSpec> tracks;

    private final List<GlyphSpec> glyphs;

    public record RenderConfig(
            int cellSize,        // 网格单元格像素大小，默认 64
            int atomRadius,      // 原子圆形半径，默认 20
            int armWidth,        // 机械臂矩形宽度，默认 14
            int bondWidth,       // 化学键矩形宽度，默认 8
            Color gridColor,     // 网格线颜色
            Color armColor,      // 机械臂颜色（黑色朴素矩形）
            Color bondColor,     // 键颜色（灰色朴素矩形）
            Color backgroundColor// 画布背景色
    ) {
        public static RenderConfig defaultConfig() {
            return new RenderConfig(
                    64,
                    20,
                    10,
                    8,
                    new Color(220, 225, 230),
                    new Color(20, 20, 20),
                    new Color(140, 145, 150),
                    new Color(248, 249, 250)
            );
        }
    }

    public OrthogonalRenderer() {
        this(RenderConfig.defaultConfig(), List.of(), List.of());
    }

    public OrthogonalRenderer(List<TrackSpec> tracks) {
        this(RenderConfig.defaultConfig(), tracks, List.of());
    }

    public OrthogonalRenderer(RenderConfig config, List<TrackSpec> tracks) {
        this(config, tracks, List.of());
    }

    public OrthogonalRenderer(List<TrackSpec> tracks, List<GlyphSpec> glyphs) {
        this(RenderConfig.defaultConfig(), tracks, glyphs);
    }

    /**
     * 完整构造器：固定渲染器持有的轨道与符文声明。
     *
     * <p>符文与轨道一样在渲染器生命周期内不变，因此这里用 {@link List#copyOf} 复制一份
     * 只读列表，避免外部修改影响后续每一帧的绘制。</p>
     */
    public OrthogonalRenderer(RenderConfig config, List<TrackSpec> tracks, List<GlyphSpec> glyphs) {
        this.config = Objects.requireNonNull(config, "config");
        this.tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        this.glyphs = List.copyOf(Objects.requireNonNull(glyphs, "glyphs"));
    }

    public RenderConfig config() {
        return config;
    }

    public List<GlyphSpec> glyphs() {
        return glyphs;
    }

    /**
     * 根据原子类型字节映射其色彩（彩色朴素圆形）。
     */
    public static Color getAtomColor(byte atomType) {
        return switch (atomType) {
            case 1 -> new Color(192, 172, 144); // salt
            case 2 -> Color.WHITE;   // air
            case 3 -> Color.CYAN;    // water
            case 4 -> Color.YELLOW;  // earth
            case 5 -> Color.RED;     // fire
            case 6 -> Color.MAGENTA; // 品红 (如金属/重元素)
            case 7 -> Color.PINK;    // 粉红
            default -> {
                // 动态哈希调色
                int hash = (atomType & 0xFF) * 31;
                yield new Color((hash * 67) % 200 + 40, (hash * 131) % 200 + 40, (hash * 199) % 200 + 40);
            }
        };
    }

    /**
     * 执行全量世界渲染。
     *
     * @param g2         Graphics2D 上下文
     * @param width      视口宽度
     * @param height     视口高度
     * @param originX    网格 (0,0) 点对应的视口像素 X 坐标
     * @param originY    网格 (0,0) 点对应的视口像素 Y 坐标
     * @param floatWorld 当前周期的插值视图模型
     */
    public void render(
            Graphics2D g2,
            int width,
            int height,
            double originX,
            double originY,
            FloatWorld floatWorld) {

        Objects.requireNonNull(g2, "g2");
        Objects.requireNonNull(floatWorld, "floatWorld");

        // 开启抗锯齿与高品质渲染
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 清空背景
        g2.setColor(config.backgroundColor());
        g2.fillRect(0, 0, width, height);

        // 绘制正交直角坐标网格
        renderGrid(g2, width, height, originX, originY);

        // 符文是静态地面设施：位于网格之上、轨道与实体之下
        for (GlyphSpec glyph : glyphs) {
            renderGlyph(g2, originX, originY, glyph);
        }

        // 绘制轨道
        for (TrackSpec track : tracks) {
            List<GridPoint> points = track.points();
            if (points.size() < 2) continue;

            GridPoint last = points.get(points.size() - 1);
            for (int i = 1; i < points.size(); i++) {
                renderTrack(g2, originX, originY, trackColor(points.get(i - 1), points.get(i)),
                        points.get(i - 1), points.get(i));
            }
            if (track.isLoop()) {
                renderTrack(g2, originX, originY, trackColor(last, points.get(0)), last, points.get(0));
            }
            // 绘制轨道 +/- 方向
            renderTrackDirection(g2, originX, originY, trackColor(points.get(0), points.get(1)),
                    trackColor(points.get(points.size() - 2), last), points.get(0), last);
        }

        // 每一层都先画键、再画原子本体，避免后绘制的键压住相邻原子。
        //    模板在动态实体之前，机械臂最后绘制，保持稳定的遮挡层次。
        // TODO: 也许可以缓存位图？降低运算量？
        for (FloatAtom atom : floatWorld.templateAtoms()) {
            renderBonds(g2, originX, originY, atom);
        }
        for (FloatAtom atom : floatWorld.templateAtoms()) {
            renderAtom(g2, originX, originY, atom);
        }

        // 绘制动态实体的键与原子本体
        for (FloatAtom atom : floatWorld.dynamicAtoms()) {
            renderBonds(g2, originX, originY, atom);
        }
        for (FloatAtom atom : floatWorld.dynamicAtoms()) {
            renderAtom(g2, originX, originY, atom);
        }

        // 绘制机械臂（黑色朴素矩形）
        for (FloatArm arm : floatWorld.dynamicArms()) {
            renderArm(g2, originX, originY, arm);
        }
    }

    /**
     * 绘制标准正交直角坐标系网格。
     */
    private void renderGrid(Graphics2D g2, int width, int height, double originX, double originY) {
        int cell = config.cellSize();
        g2.setColor(config.gridColor());
        g2.setStroke(new BasicStroke(1.0f));

        double startX = originX % cell;
        for (double x = startX; x < width; x += cell) {
            g2.drawLine((int) x, 0, (int) x, height);
        }

        double startY = originY % cell;
        for (double y = startY; y < height; y += cell) {
            g2.drawLine(0, (int) y, width, (int) y);
        }

        // 突出显示原点轴线 (0, 0)
        g2.setColor(new Color(180, 190, 200));
        g2.setStroke(new BasicStroke(2.0f));
        if (originX >= 0 && originX <= width) {
            g2.drawLine((int) originX, 0, (int) originX, height);
        }
        if (originY >= 0 && originY <= height) {
            g2.drawLine(0, (int) originY, width, (int) originY);
        }
    }

    /**
     * 绘制一个内建符文；未知类型跳过，不影响其余实体。
     *
     * <p>每个符文使用独立的 {@link Graphics2D} 子上下文，隔离透明度、虚线与字体，
     * 并在结束时 {@code dispose()}。</p>
     */
    private void renderGlyph(Graphics2D g2, double originX, double originY, GlyphSpec glyph) {
        Graphics2D glyphGraphics = (Graphics2D) g2.create();
        try {
            switch (glyph.typeId()) {
                case OMGlyphs.BONDING_ID -> renderPairGlyph(
                        glyphGraphics, originX, originY, glyph, OMGlyphs.BONDING, BONDING_COLOR, true);
                case OMGlyphs.UNBONDING_ID -> renderPairGlyph(
                        glyphGraphics, originX, originY, glyph, OMGlyphs.UNBONDING, UNBONDING_COLOR, false);
                case OMGlyphs.CALCIFICATION_ID -> renderCalcificationGlyph(
                        glyphGraphics, originX, originY, glyph, OMGlyphs.CALCIFICATION, CALCIFICATION_COLOR);
                default -> {
                    // 本轮只提供三种内建样式：其他类型不绘制
                }
            }
        } finally {
            glyphGraphics.dispose();
        }
    }

    /**
     * 双格符文：两个占位各画底圈，中心之间连线；实线表示键合、虚线表示拆键。
     */
    private void renderPairGlyph(
            Graphics2D g2,
            double originX,
            double originY,
            GlyphSpec glyph,
            GlyphDefinition<?> definition,
            Color color,
            boolean solid) {
        List<GridPoint> placeholder = definition.placeholder();
        if (placeholder.size() < 2) {
            return;
        }

        double cs = config.cellSize();
        var gc = glyph.position();
        var twp = glyph.worldPoint(placeholder.get(1));
        Point2D from = new Point2D.Double(originX + gc.x() * cs, originY + gc.y() * cs);
        Point2D to = new Point2D.Double(originX + twp.x() * cs, originY + twp.y() * cs);
        double radius = config.cellSize() * GLYPH_RADIUS_RATIO;
        float lineWidth = glyphLineWidth();

        // 线沿两个世界占位点的中心连线绘制，随后用低透明度底圈压住圆内部分
        g2.setColor(shade(color, 0.72));
        g2.setStroke(solid
                ? new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                : new BasicStroke(
                        lineWidth,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        10f,
                        new float[] {lineWidth * 2f, lineWidth * 2f},
                        0f));
        g2.draw(new Line2D.Double(from, to));

        renderGlyphBase(g2, from.getX(), from.getY(), radius, lineWidth, color);
        renderGlyphBase(g2, to.getX(), to.getY(), radius, lineWidth, color);
    }

    /**
     * 单格钙化符文：锚点底圈内画 {@code Ca} 标记。
     */
    private void renderCalcificationGlyph(
            Graphics2D g2,
            double originX,
            double originY,
            GlyphSpec glyph,
            GlyphDefinition<?> definition,
            Color color) {
        List<GridPoint> placeholder = definition.placeholder();
        if (placeholder.isEmpty()) {
            return;
        }

        double cs = config.cellSize();
        var gc = glyph.position();
        Point2D center = new Point2D.Double(originX + gc.x() * cs, originY + gc.y() * cs);
        double radius = config.cellSize() * GLYPH_RADIUS_RATIO;
        float lineWidth = glyphLineWidth();

        renderGlyphBase(g2, center.getX(), center.getY(), radius, lineWidth, color);

        // 文字始终按屏幕方向绘制，不跟随符文朝向旋转
        int fontSize = Math.max(8, (int) Math.round(config.cellSize() * 0.17));
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics(font);
        String label = "Ca";
        g2.setColor(shade(color, 0.62));
        g2.drawString(
                label,
                (float) (center.getX() - metrics.stringWidth(label) / 2.0),
                (float) (center.getY() + radius * 0.58));
    }

    /** 空格点的符文底圈：低透明度同色填充 + 较清晰的同色轮廓。 */
    private void renderGlyphBase(
            Graphics2D g2, double cx, double cy, double radius, float lineWidth, Color color) {
        Ellipse2D circle = new Ellipse2D.Double(
                cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        g2.setColor(withAlpha(color, 52));
        g2.fill(circle);
        g2.setColor(withAlpha(color, 190));
        g2.setStroke(new BasicStroke(lineWidth));
        g2.draw(circle);
    }

    /** 符文线宽：至少 1 像素，网格变大时同步变粗。 */
    private float glyphLineWidth() {
        return Math.max(1f, config.cellSize() * 0.03f);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color shade(Color color, double factor) {
        return new Color(
                (int) Math.round(color.getRed() * factor),
                (int) Math.round(color.getGreen() * factor),
                (int) Math.round(color.getBlue() * factor),
                color.getAlpha());
    }

    /**
     * 渲染机械臂：黑色朴素矩形。
     */
    private void renderArm(Graphics2D g2, double originX, double originY, FloatArm arm) {
        double px = originX + arm.x() * config.cellSize();
        double py = originY + arm.y() * config.cellSize();

        AffineTransform oldTx = g2.getTransform();
        g2.translate(px, py);
        g2.rotate(arm.angle());

        int cell = config.cellSize();
        int width = config.armWidth();
        int atomRadius = config.atomRadius();
        int length = (int) (arm.length() * cell);

        // 黑色朴素矩形：从基座中心 (0, 0) 延伸向当前朝向 (length, 0)
        g2.setColor(config.armColor());
        g2.fill(new Rectangle2D.Double(0, -width / 2.0, length - atomRadius, width));

        // 臂基座黑色圆形
        int baseSize = (atomRadius - 6) << 1;
        g2.fill(new Ellipse2D.Double(-baseSize / 2.0, -baseSize / 2.0, baseSize, baseSize));

        // 抓手指示
        int clawSize = width >> 1;
        var grabbing = atomRadius + (arm.grabbing() ? 0 : clawSize);
        g2.fill(new Rectangle2D.Double(length - (clawSize >> 1), -grabbing, 5, clawSize));
        g2.fill(new Rectangle2D.Double(length - (clawSize >> 1), grabbing - clawSize, 5, clawSize));

        // 抓手位置圈住原子
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Ellipse2D.Double(length - atomRadius, -atomRadius, atomRadius * 2, atomRadius * 2));

        g2.setTransform(oldTx);

        // 按声明顺序显示从 1 开始的机械臂编号
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        String label = Integer.toString(arm.mechanismIndex() + 1);
        int textWidth = fm.stringWidth(label);
        g2.drawString(label, (int) (px - textWidth / 2.0), (int) (py + fm.getAscent() / 2.0 - 2));
    }

    /** 绘制一个原子向外延伸的化学键。 */
    private void renderBonds(Graphics2D g2, double originX, double originY, FloatAtom atom) {
        Composite oldComposite = g2.getComposite();
        if (atom.alpha() < 1.0f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, atom.alpha()));
        }

        double px = originX + atom.x() * config.cellSize();
        double py = originY + atom.y() * config.cellSize();
        int cell = config.cellSize();
        int r = config.atomRadius();
        int bondW = config.bondWidth();

        // 1. 渲染向外的键（为避免重复绘制，只需按自身坐标与方向延伸；只处理 EAST 和 SOUTH 或按弧度旋转延伸）
        // 按原子自身自转角旋转绘制 4 个方向的键
        g2.setColor(config.bondColor()); // 灰色朴素矩形
        for (int d = 0; d < 4; d++) {
            byte bondCode = atom.bondCodes()[d];
            if (bondCode != 0) { // 存在化学键
                Direction dir = Direction.values()[d];
                double baseAngle = FloatArm.directionToAngle(dir);
                double angle = baseAngle + atom.rotationAngle();

                AffineTransform oldTx = g2.getTransform();
                g2.translate(px, py);
                g2.rotate(angle);

                // 灰色朴素矩形，连接到相邻单元格中心（长度为 cell / 2）
                g2.fill(new Rectangle2D.Double(r, -(bondW >> 1), cell - r * 2, bondW));

                g2.setTransform(oldTx);
            }
        }

        g2.setComposite(oldComposite);
    }

    /** 绘制原子的彩色本体、边框和类型标记。 */
    private void renderAtom(Graphics2D g2, double originX, double originY, FloatAtom atom) {
        Composite oldComposite = g2.getComposite();
        if (atom.alpha() < 1.0f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, atom.alpha()));
        }

        double px = originX + atom.x() * config.cellSize();
        double py = originY + atom.y() * config.cellSize();
        int r = config.atomRadius();

        // 彩色朴素圆形原子
        Color color = getAtomColor(atom.atomType());
        g2.setColor(color);
        g2.fill(new Ellipse2D.Double(px - r, py - r, r * 2.0, r * 2.0));

        // 绘制深色精致边框
        g2.setColor(new Color(40, 40, 40, (int) (180 * atom.alpha())));
        g2.setStroke(new BasicStroke(1.8f));
        g2.draw(new Ellipse2D.Double(px - r, py - r, r * 2.0, r * 2.0));

        // 在原子中心打印类型数字
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        String text = String.valueOf(atom.atomType());
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int textH = fm.getAscent();
        g2.drawString(text, (int) (px - textW / 2.0), (int) (py + textH / 2.0 - 2));

        g2.setComposite(oldComposite);
    }

    private void renderTrack(Graphics2D g2, double originX, double originY, Color color, GridPoint form, GridPoint to) {
        int cellSize = config.cellSize();
        g2.setColor(color);
        float[] dash = { 3f, 3f };
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 3f));
//        g2.setPaint(color);

        var x1 = originX + form.x() * cellSize;
        var y1 = originY + form.y() * cellSize;
        var x2 = originX + to.x() * cellSize;
        var y2 = originY + to.y() * cellSize;
        g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
    }

    private static Color trackColor(GridPoint from, GridPoint to) {
        return from.x() != to.x() && from.y() != to.y() ? Color.BLUE : Color.RED;
    }

    private void renderTrackDirection(Graphics2D g2, double originX, double originY,
            Color firstColor, Color lastColor, GridPoint first, GridPoint last) {
        int cellSize = config.cellSize();
        g2.setColor(firstColor);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));

        var x1 = originX + first.x() * cellSize;
        var y1 = originY + first.y() * cellSize;
        g2.drawString("-", (int) x1, (int) y1);

        g2.setColor(lastColor);
        var x2 = originX + last.x() * cellSize;
        var y2 = originY + last.y() * cellSize;
        g2.drawString("+", (int) x2, (int) y2);
    }
}
