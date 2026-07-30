package com.company.inventory.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Helpers de lectura de celdas Excel compartidos por los servicios de importación masiva.
 */
final class ExcelCellUtils {

    private ExcelCellUtils() {
    }

    static String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? new SimpleDateFormat("dd/MM/yyyy").format(cell.getDateCellValue())
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    static Date parseDate(Cell cell, String rawStr) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue();
        }
        for (String pattern : new String[]{"dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy"}) {
            try {
                return new SimpleDateFormat(pattern).parse(rawStr.trim());
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
