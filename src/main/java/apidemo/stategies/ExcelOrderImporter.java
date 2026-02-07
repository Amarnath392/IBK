package apidemo.stategies;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExcelOrderImporter {
    
    public static class ImportResult {
        public final List<TradeOrder> trades;
        public final List<String> errors;
        public final List<String> warnings;
        public final boolean success;
        
        public ImportResult(List<TradeOrder> trades, List<String> errors, List<String> warnings) {
            this.trades = trades;
            this.errors = errors;
            this.warnings = warnings;
            this.success = !trades.isEmpty();
        }
    }
    
    private static class ExcelRow {
        String tradeId;
        String account;
        String symbol;
        String expiry;
        String action;
        String optionType;
        String role;
        double strike;
        int quantity;
        double target;
        double alert;
        int rowNumber;
        
        ExcelRow(int rowNumber) {
            this.rowNumber = rowNumber;
        }
    }
    
    public static ImportResult importFromExcel(File file) {
        List<TradeOrder> trades = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(file)) {
            Workbook workbook = createWorkbook(file, fis);
            if (workbook == null) {
                errors.add("Invalid file format. Please use .xlsx or .xls files");
                return new ImportResult(trades, errors, warnings);
            }
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                errors.add("Excel file is empty or has no data rows");
                workbook.close();
                return new ImportResult(trades, errors, warnings);
            }
            
            Map<String, List<ExcelRow>> tradeGroups = parseExcelRows(sheet, errors);
            trades.addAll(createTradeOrders(tradeGroups, errors, warnings));
            
            workbook.close();
            
        } catch (IOException e) {
            errors.add("Error reading Excel file: " + e.getMessage());
        }
        
        return new ImportResult(trades, errors, warnings);
    }
    
    private static Workbook createWorkbook(File file, FileInputStream fis) throws IOException {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xlsx")) {
            return new XSSFWorkbook(fis);
        } else if (fileName.endsWith(".xls")) {
            return new HSSFWorkbook(fis);
        }
        return null;
    }
    
    private static Map<String, List<ExcelRow>> parseExcelRows(Sheet sheet, List<String> errors) {
        Map<String, List<ExcelRow>> tradeGroups = new LinkedHashMap<>();
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            try {
                ExcelRow excelRow = parseRow(row, i + 1);
                if (excelRow.tradeId.isEmpty()) continue;
                
                tradeGroups.computeIfAbsent(excelRow.tradeId, k -> new ArrayList<>()).add(excelRow);
                
            } catch (Exception e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        
        return tradeGroups;
    }
    
    private static ExcelRow parseRow(Row row, int rowNumber) throws Exception {
        ExcelRow excelRow = new ExcelRow(rowNumber);
        
        excelRow.tradeId = getCellValueAsString(row.getCell(0)).trim().toUpperCase();
        excelRow.account = getCellValueAsString(row.getCell(1)).trim();
        excelRow.symbol = getCellValueAsString(row.getCell(2)).trim().toUpperCase();
        excelRow.expiry = parseDateCell(row.getCell(3));
        
        // Parse combined action format (e.g., "CALL BUY", "PUT SELL", "BUY CALL", "SELL PUT")
        String actionCell = getCellValueAsString(row.getCell(4)).trim().toUpperCase();
        parseActionAndType(actionCell, excelRow);
        
        excelRow.role = getCellValueAsString(row.getCell(5)).trim().toUpperCase();
        excelRow.strike = getCellValueAsDouble(row.getCell(6));
        excelRow.quantity = (int) getCellValueAsDouble(row.getCell(7));
        excelRow.target = getCellValueAsDouble(row.getCell(8));
        excelRow.alert = getCellValueAsDouble(row.getCell(9));
        
        validateRow(excelRow);
        
        return excelRow;
    }
    
    private static void parseActionAndType(String actionCell, ExcelRow row) throws Exception {
        if (actionCell.isEmpty()) {
            throw new Exception("Action is required and must be in format 'CALL BUY', 'PUT SELL', 'BUY CALL', or 'SELL PUT'");
        }
        
        String[] parts = actionCell.split("\\s+");
        
        if (parts.length != 2) {
            throw new Exception("Action must be in format 'CALL BUY', 'PUT SELL', 'BUY CALL', or 'SELL PUT'");
        }
        
        String part1 = parts[0];
        String part2 = parts[1];
        
        // Determine which part is the option type and which is the action
        if ((part1.equals("CALL") || part1.equals("PUT") || part1.equals("C") || part1.equals("P")) &&
            (part2.equals("BUY") || part2.equals("SELL"))) {
            // Format: "CALL BUY" or "PUT SELL"
            row.optionType = part1;
            row.action = part2;
        } else if ((part1.equals("BUY") || part1.equals("SELL")) &&
                   (part2.equals("CALL") || part2.equals("PUT") || part2.equals("C") || part2.equals("P"))) {
            // Format: "BUY CALL" or "SELL PUT"
            row.action = part1;
            row.optionType = part2;
        } else {
            throw new Exception("Invalid action format '" + actionCell + "'. Must be 'CALL BUY', 'PUT SELL', 'BUY CALL', or 'SELL PUT'");
        }
    }
    
    private static void validateRow(ExcelRow row) throws Exception {
        if (row.tradeId.isEmpty()) {
            throw new Exception("Trade ID is required");
        }
        
        if (row.symbol.isEmpty()) {
            throw new Exception("Symbol is required");
        }
        
        if (row.expiry.isEmpty()) {
            throw new Exception("Expiry is required");
        }
        
        if (!row.action.equals("BUY") && !row.action.equals("SELL")) {
            throw new Exception("Invalid action '" + row.action + "'. Must be BUY or SELL");
        }
        
        if (!row.optionType.equals("CALL") && !row.optionType.equals("PUT") && !row.optionType.equals("C") && !row.optionType.equals("P")) {
            throw new Exception("Invalid option type '" + row.optionType + "'. Must be CALL/C or PUT/P");
        }
        
        // Normalize option type to single character
        if (row.optionType.equals("CALL")) row.optionType = "C";
        if (row.optionType.equals("PUT")) row.optionType = "P";
        
        // Don't auto-default empty role - leave it empty for non-main legs
        
        if (row.strike <= 0) {
            throw new Exception("Strike must be positive");
        }
        
        if (row.quantity <= 0) {
            throw new Exception("Quantity must be positive");
        }
        
        // Only validate target/alert for explicitly marked MAIN role
        if ("MAIN".equalsIgnoreCase(row.role) && !row.role.isEmpty()) {
            if (row.target <= 0) {
                throw new Exception("Target price must be positive for Main role");
            }
            if (row.alert <= 0) {
                throw new Exception("Alert threshold must be positive for Main role");
            }
        }
    }
    
    private static List<TradeOrder> createTradeOrders(Map<String, List<ExcelRow>> tradeGroups, 
                                                      List<String> errors, List<String> warnings) {
        List<TradeOrder> trades = new ArrayList<>();
        
        for (Map.Entry<String, List<ExcelRow>> entry : tradeGroups.entrySet()) {
            String tradeId = entry.getKey();
            List<ExcelRow> rows = entry.getValue();
            
            try {
                TradeOrder trade = createTradeOrder(tradeId, rows, warnings);
                trades.add(trade);
            } catch (Exception e) {
                errors.add("Trade " + tradeId + ": " + e.getMessage());
            }
        }
        
        return trades;
    }
    
    private static TradeOrder createTradeOrder(String tradeId, List<ExcelRow> rows, List<String> warnings) 
            throws Exception {
        
        if (rows.isEmpty()) {
            throw new Exception("No rows found for trade");
        }
        
        // Get account from first row (all rows should have same account for a trade)
        String account = rows.get(0).account;
        TradeOrder trade = new TradeOrder(tradeId, account);
        
        // Find main leg for target/alert values
        ExcelRow mainRow = findMainRow(rows, tradeId, warnings);
        
        // Set target and alert from main row
        trade.setTargetPrice(mainRow.target);
        trade.setAlertThreshold(mainRow.alert);
        
        // Add all legs
        for (ExcelRow row : rows) {
            TradeOrder.OrderLeg leg = new TradeOrder.OrderLeg(
                row.symbol,
                row.expiry,
                row.action,
                row.optionType,
                row.role,
                row.strike,
                row.quantity,
                row.account,
                row.rowNumber
            );
            trade.addLeg(leg);
        }
        
        // Validate trade consistency
        validateTradeConsistency(trade, rows, warnings);
        
        return trade;
    }
    
    private static ExcelRow findMainRow(List<ExcelRow> rows, String tradeId, List<String> warnings) {
        // Look for explicit MAIN role
        for (ExcelRow row : rows) {
            if ("MAIN".equalsIgnoreCase(row.role)) {
                return row;
            }
        }
        
        // If no MAIN found, use first row and warn
        warnings.add("Trade " + tradeId + ": No MAIN role found, using first row for target/alert");
        return rows.get(0);
    }
    
    private static void validateTradeConsistency(TradeOrder trade, List<ExcelRow> rows, List<String> warnings) {
        String tradeId = trade.getTradeId();
        
        // Check account consistency
        String firstAccount = rows.get(0).account;
        for (ExcelRow row : rows) {
            if (!firstAccount.equals(row.account)) {
                warnings.add("Trade " + tradeId + ": Inconsistent accounts across legs");
                break;
            }
        }
        
        // Check expiry consistency for combo orders
        if (trade.isComboOrder()) {
            String firstExpiry = rows.get(0).expiry;
            for (ExcelRow row : rows) {
                if (!firstExpiry.equals(row.expiry)) {
                    warnings.add("Trade " + tradeId + ": Different expiry dates in combo order");
                    break;
                }
            }
        }
    }
    
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy");
                    return sdf.format(date);
                }
                return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    private static double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;
        
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                String str = cell.getStringCellValue().trim();
                if (str.isEmpty()) return 0.0;
                try {
                    return Double.parseDouble(str);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            default:
                return 0.0;
        }
    }
    
    private static String parseDateCell(Cell cell) {
        if (cell == null) return "";
        
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            return sdf.format(date);
        } else if (cell.getCellType() == CellType.STRING) {
            String dateStr = cell.getStringCellValue().trim();
            if (dateStr.isEmpty()) return "";
            
            try {
                // Try multiple date formats
                String[] formats = {"dd-MMM-yy", "dd/MM/yy", "dd-MM-yy", "yyyyMMdd"};
                for (String format : formats) {
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat(format);
                        Date date = inputFormat.parse(dateStr);
                        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyyMMdd");
                        return outputFormat.format(date);
                    } catch (Exception ignored) {
                        // Try next format
                    }
                }
                
                // If all else fails, try to extract numbers
                return dateStr.replaceAll("[^0-9]", "");
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }
}
