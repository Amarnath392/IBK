package apidemo.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility to generate sample Futures Options Excel file for testing
 */
public class GenerateFuturesSampleExcel {
    
    public static void main(String[] args) throws IOException {
        String outputPath = "/Users/amarnath.chadalawada/Public/Futures_Sample_Test.xlsx";
        
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(outputPath)) {
            Sheet sheet = wb.createSheet("Futures");
            
            // Header row with styling
            Row header = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            String[] headers = {
                "Trade ID", "Account", "Symbol", "Futures Month", "Exchange", "Expiry",
                "Net Action", "Action", "Role", "Strike", "Rate", "QTY", "Target", "Alert", "Active"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Sample data
            // NOTE: Strikes must be near current market price for IB to have them listed.
            // ES is currently ~5200-5400 (April 2026). Use strikes within ~10-15% of spot.
            //
            // ⚠️ IMPORTANT: Jun 19 2026 = Juneteenth (CME holiday) → expiry rolls to Jun 18 (Thu)
            // Correct Jun 2026 quarterly expiry = 20260618 (NOT 20260619)
            // Sep 2026 quarterly expiry = 20260918 (normal - no holidays)
            //
            // 2026 ES Quarterly Expiry Calendar:
            //   Mar 2026 → 20260320 (normal 3rd Friday)
            //   Jun 2026 → 20260618 (shifted: Jun 19 = Juneteenth holiday)
            //   Sep 2026 → 20260918 (normal 3rd Friday)
            //   Dec 2026 → 20261218 (normal 3rd Friday)
            Object[][] data = {
                // Trade 1: ES Single Call Sell (Credit)
                {1, "DU4932144", "ES", 202606, "CME", 20260618, "SELL", "CALL SELL", "MAIN", 5500.0, 1, 1, 5.0, 7.5, "Y"},
                
                // Trade 2: ES Call Spread SELL 5500 / BUY 5550 (Credit)
                {2, "DU4932144", "ES", 202606, "CME", 20260618, "SELL", "CALL SELL", "MAIN", 5500.0, 1, 1, 2.5, 4.0, "Y"},
                {2, "DU4932144", "ES", 202606, "CME", 20260618, "", "CALL BUY", "", 5550.0, 1, 1, "", "", "Y"},
                
                // Trade 3: MES Put Buy (Debit) - Micro E-mini, 10x less margin than ES
                {3, "DU4932144", "MES", 202606, "CME", 20260618, "BUY", "PUT BUY", "MAIN", 5100.0, 1, 2, 3.0, 5.0, "Y"},
                
                // Trade 4: ES Put Spread BUY 5100 / SELL 5050 (Debit) - Sep expiry (unchanged, works)
                {4, "DU4932144", "ES", 202609, "CME", 20260918, "BUY", "PUT BUY", "MAIN", 5100.0, 1, 1, 4.0, 6.0, "Y"},
                {4, "DU4932144", "ES", 202609, "CME", 20260918, "", "PUT SELL", "", 5050.0, 1, 1, "", "", "Y"},
                
                // Trade 5: ES Iron Condor (4-leg combo) - Credit
                {5, "DU4932144", "ES", 202606, "CME", 20260618, "SELL", "CALL SELL", "MAIN", 5550.0, 1, 1, 8.0, 12.0, "Y"},
                {5, "DU4932144", "ES", 202606, "CME", 20260618, "", "CALL BUY", "", 5600.0, 1, 1, "", "", "Y"},
                {5, "DU4932144", "ES", 202606, "CME", 20260618, "", "PUT SELL", "", 5150.0, 1, 1, "", "", "Y"},
                {5, "DU4932144", "ES", 202606, "CME", 20260618, "", "PUT BUY", "", 5100.0, 1, 1, "", "", "Y"}
            };
            
            // Create data rows
            CellStyle numberStyle = wb.createCellStyle();
            DataFormat format = wb.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("0.00"));
            
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] rowData = data[r];
                
                for (int c = 0; c < rowData.length; c++) {
                    Cell cell = row.createCell(c);
                    Object value = rowData[c];
                    
                    if (value instanceof Number) {
                        double numValue = ((Number) value).doubleValue();
                        cell.setCellValue(numValue);
                        if (c == 9 || c == 12 || c == 13) { // Strike, Target, Alert
                            cell.setCellStyle(numberStyle);
                        }
                    } else if (value instanceof String) {
                        cell.setCellValue((String) value);
                    } else if (value == null || value.toString().isEmpty()) {
                        cell.setCellValue("");
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 512);
            }
            
            wb.write(fos);
            System.out.println("✅ Successfully created: " + outputPath);
            System.out.println("\nSample trades:");
            System.out.println("  Trade 1: ES Jun 2026 (20260618*) - CALL SELL  Strike 5500  [single-leg credit]");
            System.out.println("  Trade 2: ES Jun 2026 (20260618*) - Call Spread SELL 5500 / BUY 5550  [2-leg credit]");
            System.out.println("  Trade 3: MES Jun 2026 (20260618*) - PUT BUY   Strike 5100  [MES micro, debit]");
            System.out.println("  Trade 4: ES Sep 2026 (20260918)  - Put Spread BUY 5100 / SELL 5050  [2-leg debit]");
            System.out.println("  Trade 5: ES Jun 2026 (20260618*) - Iron Condor SELL 5550C/BUY 5600C/SELL 5150P/BUY 5100P");
            System.out.println("\n* Jun 19 2026 = Juneteenth holiday → CME expiry shifts to Jun 18 (Thu)");
            System.out.println("\n📂 Import via Futures tab → Import Excel button");
        }
    }
}
