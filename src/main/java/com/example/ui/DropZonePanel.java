package com.example.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

final class DropZonePanel extends JPanel {
    DropZonePanel(Runnable chooseAction) {
        super(new GridBagLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(300, 100));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.insets = new Insets(2, 10, 2, 10);

        JLabel title = new JLabel("＋  添加文件");
        title.setFont(Theme.NORMAL_FONT.deriveFont(Font.BOLD, 16f));
        title.setForeground(Theme.PRIMARY);
        add(title, constraints);

        constraints.gridy = 1;
        JLabel subtitle = new JLabel("点击选择，或将文件拖放到这里（支持多选）");
        subtitle.setFont(Theme.SMALL_FONT);
        subtitle.setForeground(Theme.MUTED_TEXT);
        add(subtitle, constraints);

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                chooseAction.run();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setColor(new Color(248, 250, 255));
        graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        graphics2D.setColor(new Color(172, 185, 240));
        graphics2D.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 0, new float[]{7, 6}, 0));
        graphics2D.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
        graphics2D.dispose();
        super.paintComponent(graphics);
    }
}
