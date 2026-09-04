package com.example.ui;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class ExtensionToggleButton extends JToggleButton {
    ExtensionToggleButton(String text, boolean selected) {
        super(text, selected);
        setFont(Theme.NORMAL_FONT.deriveFont(Font.BOLD));
        setPreferredSize(new Dimension(text.contains("分卷") ? 108 : 82, 38));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setColor(isSelected() ? Theme.PRIMARY : new Color(238, 241, 248));
        graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        graphics2D.dispose();
        setForeground(isSelected() ? Color.WHITE : Theme.MUTED_TEXT);
        super.paintComponent(graphics);
    }
}
