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
    
    // Stock Options column indices (13 import columns + 2 export-only)
    public static final int COL_TRADE_ID = 0;
    public static final int COL_ACCOUNT = 1;
    public static final int COL_SYMBOL = 2;
    public static final int COL_EXPIRY = 3;
    public static final int COL_NET_ACTION = 4;
    public static final int COL_ACTION = 5;
    public static final int COL_ROLE = 6;
    public static final int COL_STRIKE = 7;
    public static final int COL_RATE = 8;
    public static final int COL_QTY = 9;
    public static final int COL_TARGET = 10;
    public static final int COL_ALERT = 11;
    public static final int COL_ACTIVE = 12;
    public static final int COL_STATUS = 13;      // Export only (ignored on import)
    public static final int COL_CURRENT_PRICE = 14; // Export only (ignored on import)
    
    // Futures Options column indices (15 import columns + 2 export-only)
    // 0:Trade ID, 1:Account, 2:Symbol, 3:Futures Month, 4:Exchange, 5:Expiry,
    // 6:Net Action, 7:Action, 8:Role, 9:Strike, 10:Rate, 11:QTY, 12:Target, 13:Alert, 14:Active
    public static final int FUT_COL_TRADE_ID = 0;
    public static final int FUT_COL_ACCOUNT = 1;
    public static final int FUT_COL_SYMBOL = 2;
    public static final int FUT_COL_FUTURES_MONTH = 3;
    public static final int FUT_COL_EXCHANGE = 4;
    public static final int FUT_COL_EXPIRY = 5;
    public static final int FUT_COL_NET_ACTION = 6;
    public static final int FUT_COL_ACTION = 7;
    public static final int FUT_COL_ROLE = 8;
    public static final int FUT_COL_STRIKE = 9;
    public static final int FUT_COL_RATE = 10;
    public static final int FUT_COL_QTY = 11;
    public static final int FUT_COL_TARGET = 12;
    public static final int FUT_COL_ALERT = 13;
    public static final int FUT_COL_ACTIVE = 14;
    public static final int FUT_COL_STATUS = 15;      // Export only
    public static final int FUT_COL_CURRENT_PRICE = 16; // Export only
    
    public static final String[] EXCEL_HEADERS = {
        "Trade ID", "Account", "Symbol", "Expiry", "Net Action", "Action",
        "Role", "Strike", "Rate", "QTY", "Target", "Alert", "Active",
        "Status", "Current Price"
    };
    
    public static final String[] FUTURES_EXCEL_HEADERS = {
        "Trade ID", "Account", "Symbol", "Futures Month", "Exchange", "Expiry",
        "Net Action", "Action", "Role", "Strike", "Rate", "QTY", "Target", "Alert", "Active",
        "Status", "Current Price"
    };
    
    public static class ImportResult {
        public final Map<String, List<TradeOrder>> sheetTrades;
        public final List<String> skippedSheets;
        public final List<String> errors;
        public final List<String> warnings;
        public final boolean success;
        
        public ImportResult(Map<String, List<TradeOrder>> sheetTrades, List<String> skippedSheets,
                           List<String> errors, List<String> warnings) {
            this.sheetTrades = sheetTrades;
            this.skippedSheets = skippedSheets;
            this.errors = errors;
            this.warnings = warnings;
            this.success = !sheetTrades.isEmpty();
        }
        
        public int totalTradeCount() {
            return sheetTrades.values().stream().mapToInt(List::size).sum();
        }
    }
    
    private static class ExcelRow {
        String tradeId;
        String account;
        String symbol;
        String futuresMonth;  // Futures only
        String exchange;      // Futures only
        String expiry;
        String netAction;
        String action;
        String optionType;
        String role;
        double strike;
        int rate;
        int quantity;
        double target;
        double alert;
        boolean active;
        int rowNumber;
        
        ExcelRow(int rowNumber) {
            this.rowNumber = rowNumber;
        }
    }
    
    public static ImportResult importFromExcel(File file) {
        Map<String, List<TradeOrder>> sheetTrades = new LinkedHashMap<>();
        List<String> skippedSheets = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(file)) {
            Workbook workbook = createWorkbook(file, fis);
            if (workbook == null) {
                errors.add("Invalid file format. Please use .xlsx or .xls files");
                return new ImportResult(sheetTrades, skippedSheets, errors, warnings);
            }
            
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName().trim();
                
                if (workbook.isSheetHidden(s) || workbook.isSheetVeryHidden(s)) {
                    skippedSheets.add(sheetName);
                    continue;
                }
                
                if (sheet.getPhysicalNumberOfRows() < 2) {
                    warnings.add("Sheet '" + sheetName + "': Empty or no data rows, skipped");
                    continue;
                }
                
                Map<String, List<ExcelRow>> tradeGroups = parseExcelRows(sheet, sheetName, errors);
                List<TradeOrder> trades = createTradeOrders(tradeGroups, sheetName, errors, warnings);
                
                if (!trades.isEmpty()) {
                    sheetTrades.put(sheetName, trades);
                }
            }
            
            workbook.close();
            
        } catch (IOException e) {
            errors.add("Error reading Excel file: " + e.getMessage());
        }
        
        return new ImportResult(sheetTrades, skippedSheets, errors, warnings);
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
    
    private static Map<String, List<ExcelRow>> parseExcelRows(Sheet sheet, String sheetName, List<String> errors) {
        Map<String, List<ExcelRow>> tradeGroups = new LinkedHashMap<>();
        boolean isFuturesSheet = sheetName.toUpperCase().contains("FUTURES");
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            try {
                ExcelRow excelRow = parseRow(row, i + 1, isFuturesSheet);
                if (excelRow.tradeId.isEmpty()) continue;
                
                tradeGroups.computeIfAbsent(excelRow.tradeId, k -> new ArrayList<>()).add(excelRow);
                
            } catch (Exception e) {
                errors.add("[" + sheetName + "] Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        
        return tradeGroups;
    }
    
    private static ExcelRow parseRow(Row row, int rowNumber, boolean isFutures) throws Exception {
        ExcelRow excelRow = new ExcelRow(rowNumber);
        
        if (isFutures) {
            // Futures options layout: includes Futures Month and Exchange columns
            excelRow.tradeId = getCellValueAsString(row.getCell(FUT_COL_TRADE_ID)).trim().toUpperCase();
            
            String activeStr = getCellValueAsString(row.getCell(FUT_COL_ACTIVE)).trim().toUpperCase();
            excelRow.active = activeStr.equals("Y") || activeStr.equals("YES");
            if (!excelRow.active) {
                return excelRow;
            }
            
            excelRow.account = getCellValueAsString(row.getCell(FUT_COL_ACCOUNT)).trim();
            excelRow.symbol = getCellValueAsString(row.getCell(FUT_COL_SYMBOL)).trim().toUpperCase();
            excelRow.futuresMonth = getCellValueAsString(row.getCell(FUT_COL_FUTURES_MONTH)).trim();
            excelRow.exchange = getCellValueAsString(row.getCell(FUT_COL_EXCHANGE)).trim().toUpperCase();
            excelRow.expiry = parseDateCell(row.getCell(FUT_COL_EXPIRY));
            excelRow.netAction = getCellValueAsString(row.getCell(FUT_COL_NET_ACTION)).trim().toUpperCase();
            
            String actionCell = getCellValueAsString(row.getCell(FUT_COL_ACTION)).trim().toUpperCase();
            parseActionAndType(actionCell, excelRow);
            
            excelRow.role = getCellValueAsString(row.getCell(FUT_COL_ROLE)).trim().toUpperCase();
            excelRow.strike = getCellValueAsDouble(row.getCell(FUT_COL_STRIKE));
            excelRow.rate = (int) getCellValueAsDouble(row.getCell(FUT_COL_RATE));
            excelRow.quantity = (int) getCellValueAsDouble(row.getCell(FUT_COL_QTY));
            excelRow.target = getCellValueAsDouble(row.getCell(FUT_COL_TARGET));
            excelRow.alert = getCellValueAsDouble(row.getCell(FUT_COL_ALERT));
        } else {
            // Stock options layout (backward compatible)
            excelRow.tradeId = getCellValueAsString(row.getCell(COL_TRADE_ID)).trim().toUpperCase();
            
            String activeStr = getCellValueAsString(row.getCell(COL_ACTIVE)).trim().toUpperCase();
            excelRow.active = activeStr.equals("Y") || activeStr.equals("YES");
            if (!excelRow.active) {
                return excelRow;
            }
            
            excelRow.account = getCellValueAsString(row.getCell(COL_ACCOUNT)).trim();
            excelRow.symbol = getCellValueAsString(row.getCell(COL_SYMBOL)).trim().toUpperCase();
            excelRow.expiry = parseDateCell(row.getCell(COL_EXPIRY));
            excelRow.netAction = getCellValueAsString(row.getCell(COL_NET_ACTION)).trim().toUpperCase();
            
            String actionCell = getCellValueAsString(row.getCell(COL_ACTION)).trim().toUpperCase();
            parseActionAndType(actionCell, excelRow);
            
            excelRow.role = getCellValueAsString(row.getCell(COL_ROLE)).trim().toUpperCase();
            excelRow.strike = getCellValueAsDouble(row.getCell(COL_STRIKE));
            excelRow.rate = (int) getCellValueAsDouble(row.getCell(COL_RATE));
            excelRow.quantity = (int) getCellValueAsDouble(row.getCell(COL_QTY));
            excelRow.target = getCellValueAsDouble(row.getCell(COL_TARGET));
            excelRow.alert = getCellValueAsDouble(row.getCell(COL_ALERT));
        }
        
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
        
        String part1 = parts[0].toUpperCase();
        String part2 = parts[1].toUpperCase();
        
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
            throw new Exception("Symbol is required (e.g., SPY, AAPL)");
        }
        
        if (row.expiry.isEmpty()) {
            throw new Exception("Expiry date is required (format: YYYYMMDD or dd-MMM-yy)");
        }
        
        // Futures-specific validations
        if (row.futuresMonth != null && !row.futuresMonth.isEmpty()) {
            validateFuturesMonth(row.futuresMonth);
            validateOptionExpiryForFutures(row.futuresMonth, row.expiry);
        }
        
        if (row.exchange != null && !row.exchange.isEmpty()) {
            // Exchange validation (basic)
            if (!row.exchange.matches("[A-Z]+")) {
                throw new Exception("Exchange must be uppercase letters (e.g., CME, GLOBEX). Found: '" + row.exchange + "'");
            }
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
            throw new Exception("Strike price must be positive (found: " + row.strike + ")");
        }
        
        if (row.rate <= 0) {
            throw new Exception("Rate (contracts per leg) must be positive (found: " + row.rate + ")");
        }
        
        // P2: Better validation messages
        if ("MAIN".equalsIgnoreCase(row.role)) {
            if (row.quantity <= 0) {
                throw new Exception("Quantity must be positive for MAIN role (found: " + row.quantity + ")");
            }
            if (row.target == 0) {
                throw new Exception("Target Price must be non-zero for MAIN role (set your limit order price)");
            }
        }
    }
    
    private static void validateFuturesMonth(String futuresMonth) throws Exception {
        // Format: YYYYMM (e.g., "202609" for Sep 2026)
        if (!futuresMonth.matches("\\d{6}")) {
            throw new Exception("Futures Month must be 6 digits in YYYYMM format (e.g., 202609). Found: '" + futuresMonth + "'");
        }
        
        int year = Integer.parseInt(futuresMonth.substring(0, 4));
        int month = Integer.parseInt(futuresMonth.substring(4, 6));
        
        if (year < 2020 || year > 2050) {
            throw new Exception("Futures Month year must be between 2020-2050. Found: " + year);
        }
        
        if (month < 1 || month > 12) {
            throw new Exception("Futures Month month must be 01-12. Found: " + month + " (format: YYYYMM)");
        }
    }
    
    private static void validateOptionExpiryForFutures(String futuresMonth, String optionExpiry) throws Exception {
        // Option expiry should be within the futures contract month or 1 month prior
        // Example: Sep 2026 futures (202609) → options can expire Aug-Sep 2026
        
        if (optionExpiry.length() != 8 || !optionExpiry.matches("\\d{8}")) {
            // Skip validation if expiry is in non-standard format (will be caught by IB API)
            return;
        }
        
        int futuresYear = Integer.parseInt(futuresMonth.substring(0, 4));
        int futuresMonthNum = Integer.parseInt(futuresMonth.substring(4, 6));
        
        int expiryYear = Integer.parseInt(optionExpiry.substring(0, 4));
        int expiryMonth = Integer.parseInt(optionExpiry.substring(4, 6));
        
        // Convert to comparable format: YYYYMM as integer
        int futuresYYYYMM = futuresYear * 100 + futuresMonthNum;
        int expiryYYYYMM = expiryYear * 100 + expiryMonth;
        
        // Calculate one month before futures month
        int oneMonthBefore = futuresMonthNum == 1 ? (futuresYear - 1) * 100 + 12 : futuresYear * 100 + (futuresMonthNum - 1);
        
        if (expiryYYYYMM < oneMonthBefore) {
            throw new Exception(String.format(
                "Option Expiry (%s) is too early. For Futures Month %s, option must expire in %04d%02d or later",
                optionExpiry, futuresMonth, oneMonthBefore / 100, oneMonthBefore % 100
            ));
        }
        
        if (expiryYYYYMM > futuresYYYYMM) {
            throw new Exception(String.format(
                "Option Expiry (%s) cannot be after Futures Month (%s). Options expire before/with futures contract",
                optionExpiry, futuresMonth
            ));
        }
    }
    
    private static List<TradeOrder> createTradeOrders(Map<String, List<ExcelRow>> tradeGroups,
                                                      String sheetName,
                                                      List<String> errors, List<String> warnings) {
        List<TradeOrder> trades = new ArrayList<>();
        
        for (Map.Entry<String, List<ExcelRow>> entry : tradeGroups.entrySet()) {
            String tradeId = entry.getKey();
            List<ExcelRow> rows = entry.getValue();
            
            boolean anyInactive = rows.stream().anyMatch(r -> !r.active);
            if (anyInactive) {
                warnings.add("[" + sheetName + "] Trade " + tradeId + ": Skipped (inactive)");
                continue;
            }
            
            try {
                TradeOrder trade = createTradeOrder(tradeId, rows, warnings);
                trades.add(trade);
            } catch (Exception e) {
                errors.add("[" + sheetName + "] Trade " + tradeId + ": " + e.getMessage());
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
        
        // Get QTY from main leg
        int mainQty = mainRow.quantity;
        
        // Add all legs (child legs inherit QTY from main)
        // Determine contract type: FUTURES_OPTION if futuresMonth present, else STOCK_OPTION
        TradeOrder.ContractType contractType = (rows.get(0).futuresMonth != null && !rows.get(0).futuresMonth.isEmpty())
            ? TradeOrder.ContractType.FUTURES_OPTION
            : TradeOrder.ContractType.STOCK_OPTION;
        
        for (ExcelRow row : rows) {
            int legQty = "MAIN".equalsIgnoreCase(row.role) ? row.quantity : mainQty;
            TradeOrder.OrderLeg leg = new TradeOrder.OrderLeg(
                row.symbol,
                row.expiry,
                row.netAction,
                row.action,
                row.optionType,
                row.role,
                row.strike,
                row.rate,
                legQty,
                row.account,
                row.rowNumber,
                row.futuresMonth,
                row.exchange,
                contractType
            );
            trade.addLeg(leg);
        }
        
        // Validate trade consistency
        validateTradeConsistency(trade, rows, warnings);
        
        return trade;
    }
    
    private static ExcelRow findMainRow(List<ExcelRow> rows, String tradeId, List<String> warnings) throws Exception {
        // Look for explicit MAIN role
        for (ExcelRow row : rows) {
            if ("MAIN".equalsIgnoreCase(row.role)) {
                // P2: Validate MAIN leg has non-zero target price
                if (row.target == 0) {
                    throw new Exception("MAIN leg must have non-zero Target Price (found 0.0)");
                }
                return row;
            }
        }
        
        // P2: Enforce MAIN role for multi-leg trades (stricter validation)
        if (rows.size() > 1) {
            throw new Exception("Multi-leg combo orders must have one leg with MAIN role. Add MAIN to the primary leg.");
        }
        
        // Single leg trade without explicit MAIN - use first row with validation
        ExcelRow mainRow = rows.get(0);
        if (mainRow.target == 0) {
            throw new Exception("Target Price must be non-zero (found 0.0)");
        }
        warnings.add("Trade " + tradeId + ": Single-leg trade, using as MAIN (consider adding MAIN role)");
        return mainRow;
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
        
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                return sdf.format(date);
            } else {
                // Plain numeric like 20260918 entered directly as a number
                return String.valueOf((long) cell.getNumericCellValue());
            }
        } else if (cell.getCellType() == CellType.STRING) {
            String dateStr = cell.getStringCellValue().trim();
            if (dateStr.isEmpty()) return "";
            
            try {
                // Try multiple date formats
                String[] formats = {"dd/MM/yyyy", "dd-MM-yyyy", "dd-MMM-yyyy", "dd-MMM-yy", "dd/MM/yy", "dd-MM-yy", "yyyyMMdd"};
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
