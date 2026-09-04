package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

final class Theme {
    static final Color BACKGROUND = new Color(245, 247, 251);
    static final Color SURFACE = Color.WHITE;
    static final Color PRIMARY = new Color(64, 92, 230);
    static final Color PRIMARY_HOVER = new Color(52, 78, 208);
    static final Color TEXT = new Color(30, 36, 50);
    static final Color MUTED_TEXT = new Color(105, 113, 132);
    static final Color BORDER = new Color(222, 226, 236);
    static final Color SUCCESS = new Color(26, 145, 92);
    static final Color WARNING = new Color(211, 132, 25);
    static final Color ERROR = new Color(210, 65, 65);
    static final Font NORMAL_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
    static final Font SMALL_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 12);
    static final Font TITLE_FONT = new Font("Microsoft YaHei UI", Font.BOLD, 25);

    private Theme() {
    }

    static void installDefaults() {
        UIManager.put("Label.font", NORMAL_FONT);
        UIManager.put("Button.font", NORMAL_FONT);
        UIManager.put("RadioButton.font", NORMAL_FONT);
        UIManager.put("Table.font", NORMAL_FONT);
        UIManager.put("TableHeader.font", NORMAL_FONT.deriveFont(Font.BOLD));
        UIManager.put("OptionPane.messageFont", NORMAL_FONT);
        UIManager.put("OptionPane.buttonFont", NORMAL_FONT);
    }

    static void applyCardStyle(JComponent component) {
        component.setBackground(SURFACE);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));
    }
}
