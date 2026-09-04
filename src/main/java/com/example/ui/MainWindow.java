package com.example.ui;

import com.example.model.RenameItem;
import com.example.model.RenameStatus;
import com.example.service.FileRenameService;
import com.example.service.RenameMode;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DropMode;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainWindow extends JFrame {
    private final FileRenameService renameService = new FileRenameService();
    private final RenameTableModel tableModel = new RenameTableModel();
    private final List<Path> selectedFiles = new ArrayList<>();

    private final JLabel summaryLabel = new JLabel("尚未选择文件");
    private final JLabel readyValue = createMetricValue("0", Theme.PRIMARY);
    private final JLabel unchangedValue = createMetricValue("0", Theme.SUCCESS);
    private final JLabel conflictValue = createMetricValue("0", Theme.WARNING);
    private final ModernButton clearButton = new ModernButton("清空列表", false);
    private final ModernButton executeButton = new ModernButton("确认并修改", true);
    private final ExtensionToggleButton zipButton = new ExtensionToggleButton("ZIP", true);
    private final ExtensionToggleButton sevenZipButton = new ExtensionToggleButton("7Z", false);
    private final ExtensionToggleButton rarButton = new ExtensionToggleButton("RAR", false);
    private final ExtensionToggleButton zipMultipartButton = new ExtensionToggleButton("ZIP 分卷", false);
    private final ExtensionToggleButton sevenZipMultipartButton = new ExtensionToggleButton("7Z 分卷", false);
    private final ExtensionToggleButton rarMultipartButton = new ExtensionToggleButton("RAR 分卷", false);

    public MainWindow() {
        super("后缀转换助手");
        Theme.installDefaults();
        configureWindow();
        buildUi();
        bindActions();
        refreshPlan();
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 640));
        setSize(1120, 720);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BACKGROUND);
        setContentPane(root);
        root.add(createHeader(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 22));
        root.add(content, BorderLayout.CENTER);

        JPanel setupArea = new JPanel(new GridLayout(1, 2, 14, 0));
        setupArea.setOpaque(false);
        setupArea.add(createDropCard());
        setupArea.add(createOptionsCard());
        content.add(setupArea, BorderLayout.NORTH);
        content.add(createTableCard(), BorderLayout.CENTER);
        content.add(createFooter(), BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(35, 45, 78));
        header.setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 3));
        text.setOpaque(false);
        JLabel title = new JLabel("后缀转换助手");
        title.setForeground(Color.WHITE);
        title.setFont(Theme.TITLE_FONT);
        JLabel subtitle = new JLabel("只修改文件名，不读取、不压缩，也不改变文件内容");
        subtitle.setForeground(new Color(190, 199, 224));
        subtitle.setFont(Theme.SMALL_FONT);
        text.add(title);
        text.add(subtitle);
        header.add(text, BorderLayout.WEST);

        JLabel badge = new JLabel("JAVA 17", SwingConstants.CENTER);
        badge.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        badge.setForeground(new Color(222, 228, 255));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(91, 109, 174)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private JPanel createDropCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        Theme.applyCardStyle(card);
        JLabel label = sectionTitle("1  选择文件");
        card.add(label, BorderLayout.NORTH);

        DropZonePanel dropZone = new DropZonePanel(this::chooseFiles);
        dropZone.setTransferHandler(new FileDropHandler());
        card.setTransferHandler(new FileDropHandler());
        card.add(dropZone, BorderLayout.CENTER);
        return card;
    }

    private JPanel createOptionsCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        Theme.applyCardStyle(card);
        card.add(sectionTitle("2  选择目标后缀"), BorderLayout.NORTH);

        JPanel choices = new JPanel(new GridLayout(2, 1, 0, 6));
        choices.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(zipButton);
        group.add(sevenZipButton);
        group.add(rarButton);
        group.add(zipMultipartButton);
        group.add(sevenZipMultipartButton);
        group.add(rarMultipartButton);

        JPanel normalChoices = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        normalChoices.setOpaque(false);
        normalChoices.add(new JLabel("普通："));
        normalChoices.add(zipButton);
        normalChoices.add(sevenZipButton);
        normalChoices.add(rarButton);
        choices.add(normalChoices);

        JPanel multipartChoices = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        multipartChoices.setOpaque(false);
        multipartChoices.add(new JLabel("分卷："));
        multipartChoices.add(zipMultipartButton);
        multipartChoices.add(sevenZipMultipartButton);
        multipartChoices.add(rarMultipartButton);
        choices.add(multipartChoices);
        card.add(choices, BorderLayout.CENTER);

        JLabel note = new JLabel("分卷模式会自动避开已有卷号，并优先补最小缺失卷号");
        note.setForeground(Theme.MUTED_TEXT);
        note.setFont(Theme.SMALL_FONT);
        card.add(note, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createTableCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        Theme.applyCardStyle(card);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(sectionTitle("3  修改预览"), BorderLayout.WEST);
        clearButton.setPreferredSize(new Dimension(100, 34));
        titleRow.add(clearButton, BorderLayout.EAST);
        card.add(titleRow, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(31);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(235, 238, 245));
        table.setSelectionBackground(new Color(227, 232, 255));
        table.setSelectionForeground(Theme.TEXT);
        table.setBackground(Theme.SURFACE);
        table.setForeground(Theme.TEXT);
        table.setFillsViewportHeight(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new FileDropHandler());
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setBackground(new Color(239, 242, 249));
        table.getTableHeader().setForeground(Theme.MUTED_TEXT);
        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
        table.getColumnModel().getColumn(4).setPreferredWidth(210);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scrollPane.getViewport().setBackground(Theme.SURFACE);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(14, 0));
        left.setOpaque(false);
        summaryLabel.setForeground(Theme.MUTED_TEXT);
        summaryLabel.setFont(Theme.SMALL_FONT);
        left.add(summaryLabel, BorderLayout.WEST);

        JPanel metrics = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        metrics.setOpaque(false);
        metrics.add(metric("待修改", readyValue));
        metrics.add(metric("已保留", unchangedValue));
        metrics.add(metric("冲突", conflictValue));
        left.add(metrics, BorderLayout.CENTER);
        footer.add(left, BorderLayout.CENTER);

        executeButton.setEnabled(false);
        footer.add(executeButton, BorderLayout.EAST);
        return footer;
    }

    private JPanel metric(String name, JLabel value) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(name);
        label.setFont(Theme.SMALL_FONT);
        label.setForeground(Theme.MUTED_TEXT);
        panel.add(label);
        panel.add(value);
        return panel;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.NORMAL_FONT.deriveFont(Font.BOLD, 15f));
        label.setForeground(Theme.TEXT);
        return label;
    }

    private static JLabel createMetricValue(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.NORMAL_FONT.deriveFont(Font.BOLD));
        label.setForeground(color);
        return label;
    }

    private void bindActions() {
        clearButton.addActionListener(event -> {
            selectedFiles.clear();
            refreshPlan();
        });
        zipButton.addActionListener(event -> refreshPlan());
        sevenZipButton.addActionListener(event -> refreshPlan());
        rarButton.addActionListener(event -> refreshPlan());
        zipMultipartButton.addActionListener(event -> refreshPlan());
        sevenZipMultipartButton.addActionListener(event -> refreshPlan());
        rarMultipartButton.addActionListener(event -> refreshPlan());
        executeButton.addActionListener(event -> executeRename());
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择需要修改后缀的文件");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addFiles(List.of(chooser.getSelectedFiles()));
        }
    }

    private void addFiles(List<File> files) {
        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path path : selectedFiles) {
            uniqueFiles.put(pathKey(path), path);
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            Path path = file.toPath().toAbsolutePath().normalize();
            uniqueFiles.putIfAbsent(pathKey(path), path);
        }
        selectedFiles.clear();
        selectedFiles.addAll(uniqueFiles.values());
        refreshPlan();
    }

    private void refreshPlan() {
        List<RenameItem> plan = renameService.createPlan(selectedFiles, selectedMode());
        tableModel.setItems(plan);
        long ready = count(plan, RenameStatus.READY);
        long unchanged = count(plan, RenameStatus.UNCHANGED);
        long conflict = count(plan, RenameStatus.CONFLICT);
        readyValue.setText(String.valueOf(ready));
        unchangedValue.setText(String.valueOf(unchanged));
        conflictValue.setText(String.valueOf(conflict));
        summaryLabel.setText(plan.isEmpty() ? "尚未选择文件" : "共选择 " + plan.size() + " 个文件");
        executeButton.setEnabled(ready > 0);
    }

    private void executeRename() {
        List<RenameItem> items = tableModel.items();
        long readyCount = count(items, RenameStatus.READY);
        int answer = JOptionPane.showConfirmDialog(this,
                "即将修改 " + readyCount + " 个文件的后缀。\n文件内容不会改变，是否继续？",
                "确认修改", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        setControlsEnabled(false);
        summaryLabel.setText("正在修改，请稍候……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                renameService.execute(items);
                return null;
            }

            @Override
            protected void done() {
                tableModel.fireTableDataChanged();
                updateSelectedFilesAfterRename(items);
                setControlsEnabled(true);
                showResult(items);
            }
        }.execute();
    }

    private void updateSelectedFilesAfterRename(List<RenameItem> items) {
        selectedFiles.clear();
        for (RenameItem item : items) {
            selectedFiles.add(item.status() == RenameStatus.SUCCESS ? item.target() : item.source());
        }
    }

    private void showResult(List<RenameItem> items) {
        long success = count(items, RenameStatus.SUCCESS);
        long unchanged = count(items, RenameStatus.UNCHANGED);
        long conflict = count(items, RenameStatus.CONFLICT);
        long failed = count(items, RenameStatus.FAILED);
        summaryLabel.setText("处理完成：成功 " + success + "，保留 " + unchanged
                + "，冲突 " + conflict + "，失败 " + failed);
        readyValue.setText("0");
        conflictValue.setText(String.valueOf(conflict));
        executeButton.setEnabled(false);
        JOptionPane.showMessageDialog(this, summaryLabel.getText(), "处理完成",
                failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private void setControlsEnabled(boolean enabled) {
        clearButton.setEnabled(enabled);
        zipButton.setEnabled(enabled);
        sevenZipButton.setEnabled(enabled);
        rarButton.setEnabled(enabled);
        zipMultipartButton.setEnabled(enabled);
        sevenZipMultipartButton.setEnabled(enabled);
        rarMultipartButton.setEnabled(enabled);
        executeButton.setEnabled(enabled);
    }

    private RenameMode selectedMode() {
        if (zipMultipartButton.isSelected()) {
            return RenameMode.ZIP_MULTIPART;
        }
        if (sevenZipMultipartButton.isSelected()) {
            return RenameMode.SEVEN_ZIP_MULTIPART;
        }
        if (rarMultipartButton.isSelected()) {
            return RenameMode.RAR_MULTIPART;
        }
        if (sevenZipButton.isSelected()) {
            return RenameMode.SEVEN_ZIP;
        }
        if (rarButton.isSelected()) {
            return RenameMode.RAR;
        }
        return RenameMode.ZIP;
    }

    private long count(List<RenameItem> items, RenameStatus status) {
        return items.stream().filter(item -> item.status() == status).count();
    }

    private String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                addFiles((List<File>) data);
                return true;
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(MainWindow.this, "无法读取拖入的文件：" + exception.getMessage(),
                        "添加失败", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }
}
