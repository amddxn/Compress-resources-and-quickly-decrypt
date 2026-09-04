package com.example.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class ModernButton extends JButton {
    private final Color normalColor;
    private final Color hoverColor;
    private final Color textColor;
    private boolean hovered;

    ModernButton(String text, boolean primary) {
        super(text);
        normalColor = primary ? Theme.PRIMARY : new Color(238, 241, 248);
        hoverColor = primary ? Theme.PRIMARY_HOVER : new Color(226, 231, 242);
        textColor = primary ? Color.WHITE : Theme.TEXT;

        setFont(Theme.NORMAL_FONT.deriveFont(java.awt.Font.BOLD));
        setForeground(textColor);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(primary ? 132 : 112, 40));
        setOpaque(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color background = isEnabled() ? (hovered ? hoverColor : normalColor) : new Color(210, 214, 224);
        graphics2D.setColor(background);
        graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        graphics2D.dispose();
        super.paintComponent(graphics);
    }
}
