package app.archiverecovery.ui;

import app.archiverecovery.model.RenameItem;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class RenameTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"原文件", "新文件", "识别结果", "状态", "说明"};
    private List<RenameItem> items = new ArrayList<>();

    public void setItems(List<RenameItem> items) {
        this.items = new ArrayList<>(items);
        fireTableDataChanged();
    }

    public List<RenameItem> items() {
        return items;
    }

    @Override
    public int getRowCount() {
        return items.size();
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
    public Object getValueAt(int rowIndex, int columnIndex) {
        RenameItem item = items.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> item.source().toString();
            case 1 -> item.source().equals(item.target()) ? "—" : item.target().toString();
            case 2 -> item.detection();
            case 3 -> item.status().displayName();
            case 4 -> item.message();
            default -> "";
        };
    }
}
