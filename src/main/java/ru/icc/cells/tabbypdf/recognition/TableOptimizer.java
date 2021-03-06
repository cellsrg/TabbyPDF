package ru.icc.cells.tabbypdf.recognition;

import ru.icc.cells.tabbypdf.entities.table.Cell;
import ru.icc.cells.tabbypdf.entities.table.Row;
import ru.icc.cells.tabbypdf.entities.table.Table;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TableOptimizer {
    public void optimize(Table table) {
        for (int rowNumber = 0; rowNumber < table.getRowsSize(); rowNumber++) {
            List<Cell> rowCells = table.getRow(rowNumber).getCells();
            for (Cell cell : rowCells) {
                if (cell.getColumnWidth() > 1) {
                    for (int i = rowNumber + 1; i < table.getRowsSize(); i++) {
                        span(table.getRow(i), cell);
                    }
                }
            }
        }
    }

    private boolean isColumnStraight(List<Cell> column) {
        return column.size() == column.stream().filter(cell -> cell.getColumnWidth() == getMaxColWidth(column)).count();
    }

    private int getMaxColWidth(List<Cell> column) {
        return Collections.max(column, Comparator.comparingInt(Cell::getColumnWidth)).getColumnWidth();
    }

    private void span(Row row, Cell curr) {
        List<Cell> collect = getCellsToSpan(row, curr);
        if (collect.size() > 1 && collect.stream().filter(c -> !c.getText().isEmpty()).count() <= 1) {
            Cell cell = collect
                .stream()
                .reduce((c1, c2) -> {
                    c1.join(c2);
                    return c1;
                })
                .orElse(collect.get(0));
            int cellIndex = row.getCells().indexOf(collect.get(0));
            row.getCells().removeAll(collect);
            row.getCells().add(cellIndex, cell);
        }
    }

    private List<Cell> getCellsToSpan(Row row, Cell baseCell) {
        return row.getCells().stream()
            .filter(cell -> cell.getId() >= baseCell.getId()
                && cell.getId() + cell.getColumnWidth() <= baseCell.getId() + baseCell.getColumnWidth()
                && cell.getColumnWidth() != baseCell.getColumnWidth()
            )
            .collect(Collectors.toList());
    }
}
