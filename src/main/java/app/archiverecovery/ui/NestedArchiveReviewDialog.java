package app.archiverecovery.ui;

import app.archiverecovery.extract.NestedArchiveDecision;
import app.archiverecovery.extract.NestedArchiveInspection;
import app.archiverecovery.service.RenameMode;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class NestedArchiveReviewDialog {
    private NestedArchiveReviewDialog() {
    }

    static NestedArchiveDecision show(Component owner, NestedArchiveInspection inspection) {
        ReviewTableModel model = new ReviewTableModel(inspection);
        JTable table = createTable(model);
        JComboBox<RenameMode> modeSelector = createModeSelector();
        JPanel content = createContent(inspection, model, table, modeSelector);
        Object[] options = {"应用所选并继续", "本层判断完成", "结束全部"};

        while (true) {
            int choice = JOptionPane.showOptionDialog(owner, content,
                    "第 " + inspection.nestedDepth() + " 层：判断伪装压缩文件",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);
            if (choice == 1) {
                return NestedArchiveDecision.finishLayer();
            }
            if (choice != 0) {
                return NestedArchiveDecision.stopAll();
            }

            List<Path> selected = model.selectedPaths();
            if (!selected.isEmpty()) {
                return NestedArchiveDecision.apply(selected,
                        (RenameMode) modeSelector.getSelectedItem());
            }
            JOptionPane.showMessageDialog(owner,
                    "请先勾选属于同一个压缩包或同一组分卷的文件。",
                    "尚未选择文件", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static JPanel createContent(NestedArchiveInspection inspection,
                                        ReviewTableModel model,
                                        JTable table,
                                        JComboBox<RenameMode> modeSelector) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setPreferredSize(new Dimension(940, 500));

        JPanel header = new JPanel(new BorderLayout(0, 5));
        JLabel title = new JLabel("请判断本层解压结果中是否包含伪装后缀的压缩文件");
        title.setFont(Theme.NORMAL_FONT.deriveFont(Font.BOLD, 15f));
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.NORTH);
        JLabel root = new JLabel("当前目录：" + inspection.extractedRoot());
        root.setFont(Theme.SMALL_FONT);
        root.setForeground(Theme.MUTED_TEXT);
        root.setToolTipText(inspection.extractedRoot().toString());
        header.add(root, BorderLayout.CENTER);
        JLabel note = new JLabel("所有文件均可勾选；所选格式优先于当前后缀。分卷模式一次只选择同一组，可跨子文件夹勾选。普通文件不要勾选。");
        note.setFont(Theme.SMALL_FONT);
        note.setForeground(Theme.WARNING);
        header.add(note, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scrollPane.getViewport().setBackground(Color.WHITE);
        content.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        JPanel selectionActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton selectAll = new JButton("全选待判断");
        JButton clear = new JButton("清除选择");
        selectAll.addActionListener(event -> model.selectAllUndecided());
        clear.addActionListener(event -> model.clearSelection());
        selectionActions.add(selectAll);
        selectionActions.add(clear);
        footer.add(selectionActions, BorderLayout.WEST);

        JPanel format = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        format.add(new JLabel("所选文件恢复为："));
        modeSelector.setPreferredSize(new Dimension(150, 30));
        format.add(modeSelector);
        footer.add(format, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
        return content;
    }

    private static JTable createTable(ReviewTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(29);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setGridColor(new Color(235, 238, 245));
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(58);
        table.getColumnModel().getColumn(1).setPreferredWidth(500);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(190);
        return table;
    }

    private static JComboBox<RenameMode> createModeSelector() {
        JComboBox<RenameMode> selector = new JComboBox<>(RenameMode.values());
        selector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof RenameMode mode) {
                    setText(mode.displayName());
                }
                return this;
            }
        });
        return selector;
    }

    private static final class ReviewTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"选择", "解压后文件", "大小", "状态"};
        private final List<Row> rows;

        private ReviewTableModel(NestedArchiveInspection inspection) {
            rows = inspection.files().stream()
                    .sorted(Comparator.comparing(entry -> entry.path().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .map(entry -> new Row(entry,
                            relativePath(inspection.extractedRoot(), entry.path())))
                    .toList();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.selected;
                case 1 -> row.relativePath();
                case 2 -> formatSize(row.entry().size());
                case 3 -> row.entry().recognizedArchive()
                        ? "已按当前后缀识别，可按所选格式覆盖" : "待用户判断";
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && isCellEditable(rowIndex, columnIndex)) {
                rows.get(rowIndex).selected = Boolean.TRUE.equals(value);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        List<Path> selectedPaths() {
            List<Path> result = new ArrayList<>();
            for (Row row : rows) {
                if (row.selected) {
                    result.add(row.entry().path());
                }
            }
            return result;
        }

        void selectAllUndecided() {
            for (Row row : rows) {
                row.selected = !row.entry().recognizedArchive();
            }
            fireTableDataChanged();
        }

        void clearSelection() {
            for (Row row : rows) {
                row.selected = false;
            }
            fireTableDataChanged();
        }

        private static String relativePath(Path root, Path path) {
            try {
                return root.relativize(path).toString();
            } catch (IllegalArgumentException exception) {
                return path.toString();
            }
        }

        private static String formatSize(long bytes) {
            if (bytes < 0) {
                return "未知";
            }
            if (bytes < 1024) {
                return bytes + " B";
            }
            double value = bytes;
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int unit = 0;
            while (value >= 1024 && unit < units.length - 1) {
                value /= 1024;
                unit++;
            }
            return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
        }
    }

    private static final class Row {
        private final NestedArchiveInspection.FileEntry entry;
        private final String relativePath;
        private boolean selected;

        private Row(NestedArchiveInspection.FileEntry entry, String relativePath) {
            this.entry = entry;
            this.relativePath = relativePath;
        }

        NestedArchiveInspection.FileEntry entry() {
            return entry;
        }

        String relativePath() {
            return relativePath;
        }
    }
}
