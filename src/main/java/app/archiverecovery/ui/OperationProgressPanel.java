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
import java.awt.GridLayout;
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
    private final JPanel extractionContext = new JPanel(new GridLayout(1, 3, 8, 0));
    private final JLabel layerValue = new JLabel("等待开始");
    private final JLabel archiveValue = new JLabel("—");
    private final JLabel entryValue = new JLabel("—");
    private boolean extractionMode;

    OperationProgressPanel() {
        super(new BorderLayout(0, 10));
        Theme.applyCardStyle(this);
        setPreferredSize(new Dimension(0, 218));

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
        JPanel centerArea = new JPanel();
        centerArea.setOpaque(false);
        centerArea.setLayout(new BoxLayout(centerArea, BoxLayout.Y_AXIS));
        stepIndicator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        centerArea.add(stepIndicator);
        centerArea.add(Box.createVerticalStrut(8));
        configureContextPanel();
        centerArea.add(extractionContext);
        add(centerArea, BorderLayout.CENTER);

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

    private void configureContextPanel() {
        extractionContext.setOpaque(false);
        extractionContext.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        extractionContext.add(createInfoCell("当前层级", layerValue));
        extractionContext.add(createInfoCell("当前压缩包", archiveValue));
        extractionContext.add(createInfoCell("正在处理", entryValue));
        extractionContext.setVisible(false);
    }

    private JPanel createInfoCell(String caption, JLabel value) {
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setBackground(new Color(247, 249, 253));
        cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 233, 241)),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)));
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(Theme.SMALL_FONT.deriveFont(10.5f));
        captionLabel.setForeground(Theme.MUTED_TEXT);
        value.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD, 11.5f));
        value.setForeground(Theme.TEXT);
        value.setToolTipText(value.getText());
        cell.add(captionLabel, BorderLayout.NORTH);
        cell.add(value, BorderLayout.CENTER);
        return cell;
    }

    void showReady(int fileCount) {
        extractionMode = false;
        extractionContext.setVisible(false);
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
        extractionContext.setVisible(shouldExtract);
        layerValue.setText(shouldExtract ? "等待进入解压" : "—");
        archiveValue.setText("—");
        entryValue.setText("—");
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
        if (extractionMode) {
            layerValue.setText("后缀修改阶段");
            archiveValue.setText("等待解压");
            entryValue.setText("—");
        }
    }

    void updateExtraction(ExtractionProgress progress) {
        if (!extractionMode) {
            return;
        }
        stepIndicator.setState(2, false, false);
        extractionContext.setVisible(true);
        layerValue.setText(progress.nestedDepth() == 0
                ? "首层"
                : "嵌套第 " + progress.nestedDepth() + " 层");
        archiveValue.setText(progress.archive() == null
                ? phaseArchiveText(progress)
                : abbreviate(progress.archive().getFileName().toString(), 34));
        archiveValue.setToolTipText(progress.archive() == null
                ? archiveValue.getText()
                : progress.archive().toString());
        entryValue.setText(progress.currentEntry().isBlank()
                ? phaseEntryText(progress)
                : abbreviate(progress.currentEntry(), 38));
        entryValue.setToolTipText(progress.currentEntry().isBlank()
                ? entryValue.getText()
                : progress.currentEntry());
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
                String badge = progress.nestedDepth() == 0
                        ? "正在解压"
                        : "嵌套 " + progress.nestedDepth() + " 层 · 解压中";
                statusBadge.setTextAndColor(badge, Theme.PRIMARY, new Color(232, 236, 255));
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

    private String phaseArchiveText(ExtractionProgress progress) {
        return switch (progress.phase()) {
            case PREPARING_LAYER -> "正在准备本层任务";
            case SCANNING_NESTED -> "正在扫描解压目录";
            case WAITING_FOR_USER -> "等待用户确认";
            case EXTRACTING -> "等待压缩包信息";
        };
    }

    private String phaseEntryText(ExtractionProgress progress) {
        return switch (progress.phase()) {
            case PREPARING_LAYER -> "即将开始";
            case EXTRACTING -> "等待 7-Zip 返回文件名";
            case SCANNING_NESTED -> "查找下一层压缩包";
            case WAITING_FOR_USER -> "密码或嵌套文件选择";
        };
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
        if (extractionMode) {
            entryValue.setText("全部处理完成");
            entryValue.setToolTipText("全部处理完成");
        }
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
                ? "等待 7-Zip 进度  ·  本层 " + completed + " / " + total
                : "等待 7-Zip 进度");
    }

    private void setExactExtractionProgress(int archivePercent, int completed, int total) {
        progressBar.setIndeterminate(false);
        progressBar.setForeground(Theme.PRIMARY);
        progressBar.setValue(archivePercent);
        countLabel.setForeground(Theme.PRIMARY);
        countLabel.setText("当前包 " + archivePercent + "%  ·  本层 "
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
