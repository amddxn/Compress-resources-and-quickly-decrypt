package com.example.ui;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

final class StatusCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, hasFocus, row, column);
        setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        if (!selected) {
            setForeground(colorFor(String.valueOf(value)));
            setBackground(row % 2 == 0 ? Theme.SURFACE : new Color(249, 250, 253));
        }
        return this;
    }

    private Color colorFor(String status) {
        return switch (status) {
            case "修改成功", "保持不变" -> Theme.SUCCESS;
            case "名称冲突" -> Theme.WARNING;
            case "修改失败" -> Theme.ERROR;
            default -> Theme.PRIMARY;
        };
    }
}
