package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class MandelbrotPanel extends JPanel {
    private static final Color OVERLAY_BACKGROUND = new Color(0, 0, 0, 170);
    private static final Color OVERLAY_TEXT = new Color(255, 240, 160);

    private final MandelbrotExplorer explorer;
    private BufferedImage fractalImage;
    private int[] imageData;

    private Point selectionStart;
    private Point selectionCurrent;
    private boolean isSelecting;
    private boolean isPanning;
    private Point panStartPoint;

    public MandelbrotPanel(MandelbrotExplorer explorer, int preferredWidth, int preferredHeight) {
        this.explorer = explorer;
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        setBackground(Color.BLACK);
        setOpaque(true);
        replaceImage(createImageBuffer(preferredWidth, preferredHeight));

        installMouseControls();
        installKeyBindings();
        installResizeHandling();
    }

    private void installMouseControls() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                if (e.isShiftDown()) {
                    isPanning = true;
                    panStartPoint = e.getPoint();
                } else {
                    isSelecting = true;
                    selectionStart = e.getPoint();
                    selectionCurrent = selectionStart;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isSelecting && SwingUtilities.isLeftMouseButton(e)) {
                    isSelecting = false;
                    if (selectionStart != null && selectionCurrent != null && !selectionStart.equals(selectionCurrent)) {
                        explorer.zoomToRectangle(selectionStart, selectionCurrent);
                    }
                }

                isPanning = false;
                selectionStart = null;
                selectionCurrent = null;
                panStartPoint = null;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isSelecting && SwingUtilities.isLeftMouseButton(e)) {
                    selectionCurrent = constrainSelectionToAspectRatio(selectionStart, e.getPoint());
                    repaint();
                    return;
                }

                if (isPanning && SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
                    Point currentPanPoint = e.getPoint();
                    int dx = currentPanPoint.x - panStartPoint.x;
                    int dy = currentPanPoint.y - panStartPoint.y;
                    explorer.panView(dx, dy);
                    panStartPoint = currentPanPoint;
                }
            }
        });

        addMouseWheelListener(e -> {
            Point mousePos = e.getPoint();
            if (e.getWheelRotation() < 0) {
                explorer.zoomIn(mousePos);
            } else {
                explorer.zoomOut(mousePos);
            }
        });
    }

    private void installKeyBindings() {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        bind(inputMap, actionMap, "ESCAPE", "reset-view", explorer::resetView);
        bind(inputMap, actionMap, "I", "zoom-in", () -> explorer.zoomIn(null));
        bind(inputMap, actionMap, "O", "zoom-out", () -> explorer.zoomOut(null));
        bind(inputMap, actionMap, "T", "threads-up", () -> explorer.changeThreads(1));
        bind(inputMap, actionMap, "shift T", "threads-down", () -> explorer.changeThreads(-1));
        bind(inputMap, actionMap, "C", "iterations-up", () -> explorer.changeIterations(100));
        bind(inputMap, actionMap, "shift C", "iterations-down", () -> explorer.changeIterations(-100));
        bind(inputMap, actionMap, "P", "palette-up", () -> explorer.changePalette(1));
        bind(inputMap, actionMap, "shift P", "palette-down", () -> explorer.changePalette(-1));
        bind(inputMap, actionMap, "A", "toggle-aa", explorer::toggleAntialiasing);
        bind(inputMap, actionMap, "S", "toggle-smooth", explorer::toggleSmoothColoring);
        bind(inputMap, actionMap, "ctrl S", "save-image", explorer::saveCurrentImage);
        bind(inputMap, actionMap, "meta S", "save-image-mac", explorer::saveCurrentImage);
    }

    private void bind(InputMap inputMap, ActionMap actionMap, String key, String actionKey, Runnable action) {
        inputMap.put(KeyStroke.getKeyStroke(key), actionKey);
        actionMap.put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private void installResizeHandling() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() <= 0 || getHeight() <= 0) {
                    return;
                }
                replaceImage(createImageBuffer(getWidth(), getHeight()));
                explorer.triggerComputation();
            }
        });
    }

    public static BufferedImage createImageBuffer(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        return new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_RGB);
    }

    public void replaceImage(BufferedImage image) {
        fractalImage = image;
        imageData = ((DataBufferInt) fractalImage.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < imageData.length; i++) {
            imageData[i] = Color.BLACK.getRGB();
        }
        repaint();
    }

    public int[] getImageDataArray() {
        return imageData;
    }

    public BufferedImage copyCurrentImage() {
        if (fractalImage == null) {
            return null;
        }

        BufferedImage copy = new BufferedImage(
                fractalImage.getWidth(),
                fractalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(fractalImage, 0, 0, null);
        g2d.dispose();
        return copy;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (fractalImage != null) {
            g2d.drawImage(fractalImage, 0, 0, getWidth(), getHeight(), this);
        }

        if (isSelecting && selectionStart != null && selectionCurrent != null) {
            int x = Math.min(selectionStart.x, selectionCurrent.x);
            int y = Math.min(selectionStart.y, selectionCurrent.y);
            int w = Math.abs(selectionStart.x - selectionCurrent.x);
            int h = Math.abs(selectionStart.y - selectionCurrent.y);

            g2d.setColor(new Color(255, 255, 255, 40));
            g2d.fillRect(x, y, w, h);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRect(x, y, w, h);
        }

        drawOverlay(g2d);
        g2d.dispose();
    }

    private void drawOverlay(Graphics2D g2d) {
        Font overlayFont = new Font("Monospaced", Font.PLAIN, 12);
        g2d.setFont(overlayFont);
        FontMetrics metrics = g2d.getFontMetrics();

        String status = explorer.getStatusText();
        String controls = explorer.getControlsText();

        int boxWidth = Math.max(metrics.stringWidth(status), metrics.stringWidth(controls)) + 18;
        int boxHeight = metrics.getHeight() * 2 + 14;
        int boxX = 8;
        int boxY = getHeight() - boxHeight - 8;

        g2d.setColor(OVERLAY_BACKGROUND);
        g2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 14, 14);

        g2d.setColor(OVERLAY_TEXT);
        g2d.drawString(status, boxX + 9, boxY + metrics.getAscent() + 5);
        g2d.drawString(controls, boxX + 9, boxY + metrics.getHeight() + metrics.getAscent() + 5);
    }

    private Point constrainSelectionToAspectRatio(Point anchor, Point rawPoint) {
        int dx = rawPoint.x - anchor.x;
        int dy = rawPoint.y - anchor.y;
        if (dx == 0 || dy == 0 || getHeight() == 0) {
            return rawPoint;
        }

        double panelAspectRatio = (double) getWidth() / getHeight();
        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);

        double draggedAspectRatio = (double) absDx / absDy;
        if (draggedAspectRatio > panelAspectRatio) {
            absDy = Math.max(1, (int) Math.round(absDx / panelAspectRatio));
        } else {
            absDx = Math.max(1, (int) Math.round(absDy * panelAspectRatio));
        }

        return new Point(
                anchor.x + Integer.signum(dx) * absDx,
                anchor.y + Integer.signum(dy) * absDy
        );
    }
}
