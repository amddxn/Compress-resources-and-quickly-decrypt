package app.archiverecovery.ui;

import app.archiverecovery.extract.BundledSevenZip;
import app.archiverecovery.extract.ExtractionPreferences;
import app.archiverecovery.extract.ExtractionSettings;

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
    private static final String SEVEN_ZIP_WEBSITE = "https://www.7-zip.org/";
    private final Component owner;
    private final ExtractionPreferences preferences;
    private final JTextField outputRootField = new JTextField(34);
    private final JComboBox<Integer> concurrencyBox = new JComboBox<>(
            new Integer[]{1, 2, 3, 4, 5, 6, 7, 8});
    private final JSpinner maxNestedDepthSpinner = new JSpinner(
            new SpinnerNumberModel(10, 1, 50, 1));

    private ExtractionSettingsDialog(Component owner, ExtractionPreferences preferences) {
        this.owner = owner;
        this.preferences = preferences;
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

        addRow(panel, constraints, 0, "解压根目录", outputRootField,
                browseButton("选择…", this::chooseOutputRoot));

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(new JLabel("并发任务数"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(concurrencyBox, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(new JLabel("范围 1–8"), constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        panel.add(new JLabel("最大嵌套层数"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(maxNestedDepthSpinner, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(new JLabel("默认 10，范围 1–50"), constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 3;
        constraints.weightx = 1;
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
                        + "程序随包提供官方 7-Zip " + BundledSevenZip.VERSION
                        + " x64 组件，并在首次解压时自动释放和校验。<br>"
                        + "7-Zip 采用 GNU LGPL，部分代码采用 BSD 许可，并包含 unRAR 限制。"
                        + "详情请参阅随程序提供的许可文件与 <a href='" + SEVEN_ZIP_WEBSITE
                        + "'>7-Zip 官方网站</a>。"
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

    private ExtractionSettings validateAndCreateSettings() {
        if (outputRootField.getText().isBlank()) {
            throw new IllegalArgumentException("请选择解压根目录");
        }
        final Path outputRoot;
        try {
            outputRoot = Path.of(outputRootField.getText().strip()).toAbsolutePath().normalize();
        } catch (InvalidPathException | NullPointerException exception) {
            throw new IllegalArgumentException("请输入有效的解压根目录");
        }
        if (Files.exists(outputRoot) && !Files.isDirectory(outputRoot)) {
            throw new IllegalArgumentException("解压根目录必须是文件夹");
        }
        return new ExtractionSettings(outputRoot,
                (Integer) concurrencyBox.getSelectedItem(),
                (Integer) maxNestedDepthSpinner.getValue());
    }

    private void openWebsite(String website) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("系统不支持打开浏览器");
            }
            Desktop.getDesktop().browse(URI.create(website));
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(owner, "请在浏览器访问：" + website,
                    "7-Zip 官方网站", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
