package com.kresdl.swing.palette;

import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import com.kresdl.geometry.Vec2;
import com.kresdl.utilities.Misc;
import com.kresdl.utilities.Mouse;
import com.kresdl.xpanel.VolatileXPanel;

/**
 * Palette
 */
public final class Palette extends JDialog {

    protected static final int SIZE = 256,
            REAL_SIZE = SIZE + 24;
    private Color abs,
            color;
    private final JSlider multiplier = new JSlider();
    private final ColorMap map = new ColorMap();
    private final Paint paint = new Paint();
    private final Vec2 pos;
    private final Path2D cursor = new Path2D.Float();
    
    /**
     * Constant used to indicate that the color has been adjusted.
     */
    public static final String ADJUST_PROP = "adjust";

    private final class ColorMap extends VolatileXPanel {

        private final BufferedImage colors = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_4BYTE_ABGR);

        private ColorMap() {
            super(REAL_SIZE, REAL_SIZE);

            setPreferredSize(new Dimension(REAL_SIZE, REAL_SIZE));

            new Mouse(this)
                    .onPress(0, this::onMouse)
                    .onDrag(0, this::onMouse);
        }

        private void init() {
            byte[] b = ((DataBufferByte) (colors.getRaster().getDataBuffer())).getData();
            int k = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double[] rgba = scan(x, y);
                    Misc.alpha(b, k++, rgba);
                    Misc.blue(b, k++, rgba);
                    Misc.green(b, k++, rgba);
                    Misc.red(b, k++, rgba);
                }
            }
        }

        private void onMouse(MouseEvent e) {
            new Thread(() -> {
                cursor(e);
            }).start();
        }

        @Override
        public void drawImage(Graphics2D g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, REAL_SIZE, REAL_SIZE);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) multiplier.getValue() / 255));
            g.drawImage(colors, 12, 12, null);

            AffineTransform at = new AffineTransform();
            at.translate(pos.x + REAL_SIZE / 2, pos.y + REAL_SIZE / 2);
            Shape s = at.createTransformedShape(cursor);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.Src);
            g.setColor(Color.WHITE);
            g.fill(s);
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.setColor(Color.BLACK);
            g.draw(s);
        }
    }

    private final class Paint extends JPanel {

        private Paint() {
            setOpaque(false);
            setPreferredSize(new Dimension(32, 32));
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, 32, 32);
        }
    }

    private Palette(Window owner, Color def) {
        super(owner, JDialog.ModalityType.DOCUMENT_MODAL);

        Data d = Data.get(def);
        abs = pick(d.x, d.y);
        pos = new Vec2(d.x, d.y);
        color = Misc.colorLerp(Color.BLACK, abs, d.m);

        cursor.moveTo(0, 0);
        cursor.lineTo(12, 5);
        cursor.lineTo(6, 6);
        cursor.lineTo(5, 12);
        cursor.closePath();

        multiplier.setMinimum(0);
        multiplier.setMaximum(255);
        multiplier.setValue((int) (255 * d.m));
        multiplier.addChangeListener(e -> {
            new Thread(() -> {
                multiplier(e);
            }).start();
        });

        JPanel pp = new JPanel(new GridBagLayout());
        pp.setBackground(Color.BLACK);
        pp.add(paint);

        JButton cancel = new JButton(new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                color = null;
                dispose();
            }
        });

        JButton ok = new JButton(new AbstractAction("Affirm") {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        Box box = new Box(BoxLayout.LINE_AXIS);
        box.add(cancel);
        box.add(Box.createRigidArea(new Dimension(5, 0)));
        box.add(ok);

        JPanel b = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        b.add(map, gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.BOTH;
        b.add(pp, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.insets = new Insets(10, 10, 10, 10);
        gc.fill = GridBagConstraints.NONE;
        b.add(multiplier, gc);
        gc.gridx = 1;
        b.add(box, gc);

        setContentPane(b);
    }

    private void cursor(MouseEvent e) {
        double h = SIZE / 2;
        double x = e.getX() - h - 12;
        double y = e.getY() - h - 12;
        double r = Math.sqrt(x * x + y * y);
        if (r > h) {
            x *= h / r;
            y *= h / r;
        }
        pos.x = x;
        pos.y = y;

        abs = pick(x, y);
        Color old = color;
        color = Misc.colorLerp(Color.BLACK, abs, (double) multiplier.getValue() / 255);

        map.redraw();
        paint.repaint();
        firePropertyChange(ADJUST_PROP, old, color);
    }

    private void multiplier(ChangeEvent e) {
        Color old = color;
        color = Misc.colorLerp(
                Color.BLACK,
                abs,
                (double) multiplier.getValue() / 255);
        map.redraw();
        paint.repaint();
        firePropertyChange(ADJUST_PROP, old, color);
    }

    private void init() {
        map.init();
        map.redraw();
    }

    /**
     * Get selected color.
     *
     * @return color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Shows a modal palette dislog.
     *
     * @param owner component which the palette relates to
     * @param x horizontal offset from owner's left side
     * @param y vertical offset from owner's top side
     * @param current start color
     * @param onChange listener for color adjustments
     * @return selected color
     */
    public static Color show(Component owner, int x, int y, Color current, PropertyChangeListener onChange) {
        Window w = SwingUtilities.getWindowAncestor(owner);
        Palette p = new Palette(w, current);
        Point pos = owner != null ? owner.getLocationOnScreen() : new Point();
        pos.x += x;
        pos.y += y;
        p.setLocation(pos);

        new Thread(p::init).start();

        p.addPropertyChangeListener(ADJUST_PROP, onChange);
        p.setTitle("Select a color");
        p.pack();
        p.setVisible(true);
        return p.getColor();
    }

    private Color pick(double x, double y) {
        double[] polar = toPolar(x, y);
        double rad = polar[1];
        return rad == 0
                ? Color.WHITE
                : Misc.rgb(sample(polar[0], rad));
    }

    private static double[] scan(double x, double y) {
        int h = SIZE / 2;
        double[] polar = toPolar(x - h, y - h);
        double rad = polar[1];

        if (rad == 0) {
            return new double[]{1, 1, 1, 1};
        } else if (rad > h) {
            return new double[]{0, 0, 0, 0};
        }

        return sample(polar[0], rad);
    }

    private static double[] sample(double angle, double rad) {
        int r1 = 0,
                g1 = 0,
                b1 = 0,
                r2 = 0,
                g2 = 0,
                b2 = 0;

        double d = 2 * Math.PI / 6;
        double q;

        if ((angle >= 0) && (angle < d)) {
            r1 = 1;
            r2 = 1;
            g2 = 1;
            q = angle;
        } else if ((angle >= d) && (angle < (2 * d))) {
            r1 = 1;
            g1 = 1;
            g2 = 1;
            q = angle - d;
        } else if ((angle >= (2 * d)) && (angle < (3 * d))) {
            g1 = 1;
            g2 = 1;
            b2 = 1;
            q = angle - 2 * d;
        } else if ((angle >= (3 * d)) && (angle < (4 * d))) {
            g1 = 1;
            b1 = 1;
            b2 = 1;
            q = angle - 3 * d;
        } else if ((angle >= (4 * d)) && (angle < (5 * d))) {
            b1 = 1;
            r2 = 1;
            b2 = 1;
            q = angle - 4 * d;
        } else {
            r1 = 1;
            b1 = 1;
            r2 = 1;
            q = angle - 5 * d;
        }

        q /= d;
        double q2 = 1.0d - rad / (SIZE / 2);

        q = 3 * q * q - 2 * q * q * q;
        q2 = 3 * q2 * q2 - 2 * q2 * q2 * q2;

        double[] rgba = new double[4];
        rgba[0] = Misc.lerp(Misc.lerp(r1, r2, q), 1, q2);
        rgba[1] = Misc.lerp(Misc.lerp(g1, g2, q), 1, q2);
        rgba[2] = Misc.lerp(Misc.lerp(b1, b2, q), 1, q2);
        rgba[3] = 1.0d;
        return rgba;
    }

    private static double[] toPolar(double x, double y) {
        double rad = Math.sqrt(x * x + y * y);
        double angle = Math.acos(-y / rad);
        angle = x < 0
                ? 2 * Math.PI - angle
                : angle;

        return new double[]{angle, rad};
    }
}

class Data {

    double x, y, m;

    Data(double x, double y, double multiplier) {
        this.x = x;
        this.y = y;
        m = multiplier;
    }

    static Data get(Color color) {
        double[] rgb = {Misc.red(color), Misc.green(color), Misc.blue(color)};
        double min = Math.min(Math.min(rgb[0], rgb[1]), rgb[2]);

        if (min == 1.0d) {
            return new Data(0, 0, 1);
        }

        double max = Math.max(Math.max(rgb[0], rgb[1]), rgb[2]);

        if (min == max) {
            return new Data(0, 0, max);
        }

        // Saturate
        for (int i = 0; i < 3; i++) {
            rgb[i] = (rgb[i] - min) / (max - min);
        }

        double angle;
        double rad = (1.0d - min) * Palette.SIZE / 2;
        double p = 2 * Math.PI / 6;
        int m = 0;

        for (int i = 0; i < 3; i++) {
            double c1 = rgb[i];
            double c2 = rgb[(i + 1) % 3];

            if (c1 > 0) {
                m = i;
                if (c2 > 0) {
                    if (c1 > c2) {
                        double d = c2;//straighten(c2);
                        angle = p * (2 * i + d);
                    } else {
                        double d = 1 - c1;//straighten(1 - c1);
                        angle = p * (2 * i + 1 + d);

                    }
                    double[] xy = toXY(angle, rad);
                    return new Data(xy[0], xy[1], max);
                }
            }
        }
        double[] xy = toXY(p * 2 * m, rad);
        return new Data(xy[0], xy[1], max);
    }

    private static double[] toXY(double angle, double rad) {
        double[] p = new double[2];
        p[0] = Math.sin(angle) * rad;
        p[1] = -Math.cos(angle) * rad;
        return p;
    }
}
