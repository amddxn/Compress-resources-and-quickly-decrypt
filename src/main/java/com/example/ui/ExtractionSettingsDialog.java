package com.example.ui;

import com.example.extract.ExtractionPreferences;
import com.example.extract.ExtractionSettings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

final class ExtractionSettingsDialog {
    private static final String BANDIZIP_WEBSITE = "https://www.bandisoft.com/bandizip/";
    private final Component owner;
    private final ExtractionPreferences preferences;
    private final JTextField bzPathField = new JTextField(34);
    private final JTextField outputRootField = new JTextField(34);
    private final JComboBox<Integer> concurrencyBox = new JComboBox<>(
            new Integer[]{1, 2, 3, 4, 5, 6, 7, 8});
    private final JSpinner maxNestedDepthSpinner = new JSpinner(
            new SpinnerNumberModel(10, 1, 50, 1));

    private ExtractionSettingsDialog(Component owner, ExtractionPreferences preferences) {
        this.owner = owner;
        this.preferences = preferences;
        bzPathField.setText(initialBzPath());
        outputRootField.setText(preferences.outputRoot());
        concurrencyBox.setSelectedItem(preferences.concurrency());
        maxNestedDepthSpinner.setValue(preferences.maxNestedDepth());
    }

    static Optional<ExtractionSettings> show(Component owner, ExtractionPreferences preferences) {
        return new ExtractionSettingsDialog(owner, preferences).showDialog();
    }

    private Optional<ExtractionSettings> showDialog() {
        JPanel panel = createPanel();
        while (true) {
            int answer = JOptionPane.showConfirmDialog(owner, panel, "解压设置",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            try {
                ExtractionSettings settings = validateAndCreateSettings();
                preferences.save(settings);
                return Optional.of(settings);
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(owner, exception.getMessage(), "设置有误",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 5, 6, 5);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        addRow(panel, constraints, 0, "bz.exe 路径", bzPathField,
                browseButton("浏览…", this::chooseBzExecutable));
        addRow(panel, constraints, 1, "解压根目录", outputRootField,
                browseButton("选择…", this::chooseOutputRoot));

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        panel.add(new JLabel("并发任务数"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(concurrencyBox, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        JButton autoFind = browseButton("自动查找", this::autoFindBzExecutable);
        panel.add(autoFind, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(new JLabel("最大嵌套层数"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(maxNestedDepthSpinner, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(new JLabel("默认 10，范围 1–50"), constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(createTipPane(), constraints);
        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints constraints, int row,
                        String label, JTextField field, JButton button) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(button, constraints);
    }

    private JButton browseButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JEditorPane createTipPane() {
        JEditorPane tip = new JEditorPane("text/html",
                "<html><body style='font-family:Microsoft YaHei UI;font-size:11px;color:#697184'>"
                        + "小贴士：<a href='" + BANDIZIP_WEBSITE + "'>Bandizip 官网</a><br>"
                        + "默认位置：C:\\Program Files\\Bandizip\\bz.exe<br>"
                        + "部分 32 位安装可能位于 C:\\Program Files (x86)\\Bandizip\\bz.exe"
                        + "</body></html>");
        tip.setEditable(false);
        tip.setOpaque(false);
        tip.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        tip.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openWebsite(event.getURL().toString());
            }
        });
        return tip;
    }

    private void chooseBzExecutable() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 Bandizip 的 bz.exe");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        setCurrentSelection(chooser, bzPathField.getText());
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            bzPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseOutputRoot() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择解压根目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        setCurrentSelection(chooser, outputRootField.getText());
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            outputRootField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void setCurrentSelection(JFileChooser chooser, String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return;
        }
        try {
            File file = Path.of(pathText).toFile();
            chooser.setCurrentDirectory(file.isDirectory() ? file : file.getParentFile());
        } catch (InvalidPathException ignored) {
            // 输入内容无效时使用系统默认目录。
        }
    }

    private void autoFindBzExecutable() {
        Optional<Path> found = ExtractionPreferences.findDefaultBzExecutable();
        if (found.isPresent()) {
            bzPathField.setText(found.get().toString());
        } else {
            JOptionPane.showMessageDialog(owner,
                    "没有在默认安装目录找到 bz.exe，请点击“浏览”手动选择。",
                    "未找到 Bandizip", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private ExtractionSettings validateAndCreateSettings() {
        if (bzPathField.getText().isBlank() || outputRootField.getText().isBlank()) {
            throw new IllegalArgumentException("请选择 bz.exe 并设置解压根目录");
        }
        final Path bzExecutable;
        final Path outputRoot;
        try {
            bzExecutable = Path.of(bzPathField.getText().strip()).toAbsolutePath().normalize();
            outputRoot = Path.of(outputRootField.getText().strip()).toAbsolutePath().normalize();
        } catch (InvalidPathException | NullPointerException exception) {
            throw new IllegalArgumentException("请输入有效的 bz.exe 路径和解压根目录");
        }
        if (!Files.isRegularFile(bzExecutable)
                || !bzExecutable.getFileName().toString().equalsIgnoreCase("bz.exe")) {
            throw new IllegalArgumentException("请选择 Bandizip 安装目录中的 bz.exe");
        }
        if (Files.exists(outputRoot) && !Files.isDirectory(outputRoot)) {
            throw new IllegalArgumentException("解压根目录必须是文件夹");
        }
        return new ExtractionSettings(bzExecutable, outputRoot,
                (Integer) concurrencyBox.getSelectedItem(),
                (Integer) maxNestedDepthSpinner.getValue());
    }

    private String initialBzPath() {
        if (!preferences.bzPath().isBlank()) {
            return preferences.bzPath();
        }
        return ExtractionPreferences.findDefaultBzExecutable()
                .map(Path::toString)
                .orElse("");
    }

    private void openWebsite(String website) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("系统不支持打开浏览器");
            }
            Desktop.getDesktop().browse(URI.create(website));
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(owner, "请在浏览器访问：" + website,
                    "Bandizip 官网", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
