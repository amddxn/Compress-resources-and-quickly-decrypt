package app.archiverecovery.ui;

import app.archiverecovery.extract.ExtractionProgress;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

final class OperationProgressPanel extends JPanel {
    private static final List<String> EXTRACTION_STEPS =
            List.of("准备任务", "修改后缀", "解压与检查", "处理完成");
    private static final List<String> RENAME_STEPS =
            List.of("准备任务", "修改后缀", "处理完成");

    private final StepIndicator stepIndicator = new StepIndicator();
    private final StatusBadge statusBadge = new StatusBadge();
    private final JLabel detailLabel = new JLabel("选择文件后即可开始");
    private final JLabel countLabel = new JLabel("0%");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private boolean extractionMode;

    OperationProgressPanel() {
        super(new BorderLayout(0, 10));
        Theme.applyCardStyle(this);
        setPreferredSize(new Dimension(0, 158));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("处理进度");
        title.setFont(Theme.NORMAL_FONT.deriveFont(Font.BOLD, 15f));
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.WEST);
        statusBadge.setTextAndColor("等待开始", Theme.MUTED_TEXT, new Color(239, 242, 248));
        header.add(statusBadge, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        stepIndicator.setSteps(EXTRACTION_STEPS);
        add(stepIndicator, BorderLayout.CENTER);

        JPanel progressArea = new JPanel();
        progressArea.setOpaque(false);
        progressArea.setLayout(new BoxLayout(progressArea, BoxLayout.Y_AXIS));

        JPanel labels = new JPanel(new BorderLayout(12, 0));
        labels.setOpaque(false);
        detailLabel.setFont(Theme.SMALL_FONT);
        detailLabel.setForeground(Theme.MUTED_TEXT);
        countLabel.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        countLabel.setForeground(Theme.PRIMARY);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        labels.add(detailLabel, BorderLayout.CENTER);
        labels.add(countLabel, BorderLayout.EAST);
        progressArea.add(labels);
        progressArea.add(Box.createVerticalStrut(6));

        progressBar.setPreferredSize(new Dimension(0, 8));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        progressBar.setBorder(BorderFactory.createEmptyBorder());
        progressBar.setBackground(new Color(232, 235, 243));
        progressBar.setForeground(Theme.PRIMARY);
        progressBar.setStringPainted(false);
        progressArea.add(progressBar);
        add(progressArea, BorderLayout.SOUTH);
    }

    void showReady(int fileCount) {
        extractionMode = false;
        stepIndicator.setSteps(EXTRACTION_STEPS);
        stepIndicator.setState(-1, false, false);
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setForeground(Theme.PRIMARY);
        countLabel.setText("0%");
        detailLabel.setText(fileCount == 0
                ? "选择文件后即可开始"
                : "已选择 " + fileCount + " 个文件，等待开始处理");
        statusBadge.setTextAndColor("等待开始", Theme.MUTED_TEXT, new Color(239, 242, 248));
    }

    void begin(boolean shouldExtract, int fileCount) {
        extractionMode = shouldExtract;
        stepIndicator.setSteps(shouldExtract ? EXTRACTION_STEPS : RENAME_STEPS);
        stepIndicator.setState(0, false, false);
        setDeterminateProgress(0, Math.max(1, fileCount));
        detailLabel.setText("正在整理 " + fileCount + " 个文件的处理计划");
        statusBadge.setTextAndColor("准备任务", Theme.PRIMARY, new Color(232, 236, 255));
    }

    void updateRename(int completed, int total, String fileName) {
        stepIndicator.setState(1, false, false);
        if (total == 0) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            countLabel.setText("无需修改");
            detailLabel.setText("文件后缀无需修改，准备进入解压步骤");
        } else {
            setDeterminateProgress(completed, total);
            detailLabel.setText(completed == 0
                    ? "准备修改文件后缀"
                    : "已处理：" + abbreviate(fileName, 58));
        }
        statusBadge.setTextAndColor("修改后缀", Theme.PRIMARY, new Color(232, 236, 255));
    }

    void updateExtraction(ExtractionProgress progress) {
        if (!extractionMode) {
            return;
        }
        stepIndicator.setState(2, false, false);
        switch (progress.phase()) {
            case PREPARING_LAYER -> {
                setDeterminateProgress(0, progress.total());
                statusBadge.setTextAndColor("准备解压", Theme.PRIMARY, new Color(232, 236, 255));
            }
            case EXTRACTING -> {
                if (progress.hasExactPercentage()) {
                    setExactExtractionProgress(progress.archivePercent(),
                            progress.completed(), progress.total());
                } else {
                    setIndeterminateExtractionProgress(progress.completed(), progress.total());
                }
                statusBadge.setTextAndColor("正在解压", Theme.PRIMARY, new Color(232, 236, 255));
            }
            case SCANNING_NESTED -> {
                setIndeterminateProgress();
                statusBadge.setTextAndColor("检查嵌套", Theme.WARNING, new Color(255, 246, 228));
            }
            case WAITING_FOR_USER -> {
                setIndeterminateProgress();
                statusBadge.setTextAndColor("等待确认", Theme.WARNING, new Color(255, 246, 228));
            }
        }
        detailLabel.setText(abbreviate(progress.message(), 76));
    }

    void finish(boolean warning, String detail) {
        int lastStep = (extractionMode ? EXTRACTION_STEPS : RENAME_STEPS).size() - 1;
        stepIndicator.setState(lastStep, true, false);
        progressBar.setIndeterminate(false);
        progressBar.setValue(100);
        progressBar.setForeground(warning ? Theme.WARNING : Theme.SUCCESS);
        countLabel.setText("100%");
        countLabel.setForeground(warning ? Theme.WARNING : Theme.SUCCESS);
        detailLabel.setText(abbreviate(detail, 76));
        if (warning) {
            statusBadge.setTextAndColor("完成 · 有提示", Theme.WARNING, new Color(255, 246, 228));
        } else {
            statusBadge.setTextAndColor("处理完成", Theme.SUCCESS, new Color(231, 248, 239));
        }
    }

    void fail(String detail) {
        stepIndicator.setState(stepIndicator.currentStep(), false, true);
        progressBar.setIndeterminate(false);
        progressBar.setForeground(Theme.ERROR);
        countLabel.setForeground(Theme.ERROR);
        detailLabel.setText(abbreviate(detail, 76));
        statusBadge.setTextAndColor("处理异常", Theme.ERROR, new Color(255, 235, 235));
    }

    private void setDeterminateProgress(int completed, int total) {
        progressBar.setIndeterminate(false);
        progressBar.setForeground(Theme.PRIMARY);
        countLabel.setForeground(Theme.PRIMARY);
        int safeTotal = Math.max(0, total);
        int safeCompleted = Math.max(0, Math.min(completed, safeTotal));
        int percent = safeTotal == 0 ? 0 : (int) Math.round(safeCompleted * 100.0 / safeTotal);
        progressBar.setValue(percent);
        countLabel.setText(safeTotal == 0
                ? "准备中"
                : safeCompleted + " / " + safeTotal + "  ·  " + percent + "%");
    }

    private void setIndeterminateProgress() {
        progressBar.setIndeterminate(true);
        progressBar.setForeground(Theme.WARNING);
        countLabel.setForeground(Theme.WARNING);
        countLabel.setText("处理中");
    }

    private void setIndeterminateExtractionProgress(int completed, int total) {
        progressBar.setIndeterminate(true);
        progressBar.setForeground(Theme.PRIMARY);
        countLabel.setForeground(Theme.MUTED_TEXT);
        countLabel.setText(total > 0
                ? "等待 Bandizip 进度  ·  本层 " + completed + " / " + total
                : "等待 Bandizip 进度");
    }

    private void setExactExtractionProgress(int archivePercent, int completed, int total) {
        progressBar.setIndeterminate(false);
        progressBar.setForeground(Theme.PRIMARY);
        progressBar.setValue(archivePercent);
        countLabel.setForeground(Theme.PRIMARY);
        countLabel.setText("当前压缩包 " + archivePercent + "%  ·  本层 "
                + completed + " / " + total);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "处理中，请稍候……";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    private static final class StatusBadge extends JLabel {
        private Color fill = Color.WHITE;

        private StatusBadge() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        }

        void setTextAndColor(String text, Color foreground, Color background) {
            setText(text);
            setForeground(foreground);
            fill = background;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class StepIndicator extends JComponent {
        private List<String> steps = List.of();
        private int currentStep = -1;
        private boolean finished;
        private boolean error;

        private StepIndicator() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 48));
        }

        void setSteps(List<String> steps) {
            this.steps = List.copyOf(steps);
            revalidate();
            repaint();
        }

        int currentStep() {
            return Math.max(0, currentStep);
        }

        void setState(int currentStep, boolean finished, boolean error) {
            this.currentStep = currentStep;
            this.finished = finished;
            this.error = error;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (steps.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int radius = 10;
            int y = 12;
            int sidePadding = Math.max(42, getWidth() / (steps.size() * 5));
            int available = Math.max(1, getWidth() - sidePadding * 2);

            for (int index = 0; index < steps.size() - 1; index++) {
                int x1 = xFor(index, available, sidePadding);
                int x2 = xFor(index + 1, available, sidePadding);
                boolean segmentDone = finished || index < currentStep;
                g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(segmentDone ? Theme.SUCCESS : new Color(225, 229, 238));
                g2.drawLine(x1 + radius, y, x2 - radius, y);
            }

            Font numberFont = Theme.SMALL_FONT.deriveFont(Font.BOLD, 11f);
            Font labelFont = Theme.SMALL_FONT.deriveFont(Font.BOLD, 11.5f);
            for (int index = 0; index < steps.size(); index++) {
                int x = xFor(index, available, sidePadding);
                boolean done = finished || index < currentStep;
                boolean active = index == currentStep && !finished;
                boolean failed = active && error;
                Color circle = failed ? Theme.ERROR
                        : done ? Theme.SUCCESS
                        : active ? Theme.PRIMARY
                        : new Color(225, 229, 238);
                g2.setColor(circle);
                g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);

                g2.setFont(numberFont);
                String marker = done ? "✓" : String.valueOf(index + 1);
                g2.setColor(done || active ? Color.WHITE : Theme.MUTED_TEXT);
                int markerWidth = g2.getFontMetrics().stringWidth(marker);
                int markerY = y + (g2.getFontMetrics().getAscent()
                        - g2.getFontMetrics().getDescent()) / 2;
                g2.drawString(marker, x - markerWidth / 2, markerY);

                g2.setFont(labelFont);
                g2.setColor(failed ? Theme.ERROR
                        : done ? Theme.SUCCESS
                        : active ? Theme.PRIMARY
                        : Theme.MUTED_TEXT);
                String label = steps.get(index);
                int labelWidth = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, x - labelWidth / 2, y + 28);
            }
            g2.dispose();
        }

        private int xFor(int index, int available, int sidePadding) {
            return steps.size() == 1
                    ? getWidth() / 2
                    : sidePadding + available * index / (steps.size() - 1);
        }
    }
}
