package apidemo.stategies;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static apidemo.stategies.ExcelOrderImporter.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for Futures Options (ES/MES) functionality
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FuturesOptionsEndToEndTest {

    private static File testFuturesFile;

    @BeforeAll
    static void setup() throws IOException {
        testFuturesFile = File.createTempFile("Futures_Test", ".xlsx");
        testFuturesFile.deleteOnExit();
    }

    @AfterAll
    static void cleanup() {
        if (testFuturesFile != null && testFuturesFile.exists()) {
            testFuturesFile.delete();
        }
    }

    // ===================================================================
    // Test 1: Valid Single-Leg Futures Option (ES Call Sell)
    // ===================================================================
    
    @Test
    @Order(1)
    void testValidSingleLegFuturesOption() throws IOException {
        createFuturesExcel("Futures_ES", new Object[][] {
            {1, "DU4932144", "ES", "202609", "CME", "20260918", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertTrue(result.success, "Import should succeed");
        assertEquals(0, result.errors.size(), "Should have no errors: " + result.errors);
        assertEquals(1, result.sheetTrades.size(), "Should import 1 sheet");
        
        List<TradeOrder> trades = result.sheetTrades.get("Futures_ES");
        assertNotNull(trades);
        assertEquals(1, trades.size(), "Should have 1 trade");
        
        TradeOrder trade = trades.get(0);
        assertEquals("1", trade.getTradeId());
        assertEquals(1, trade.getLegs().size(), "Should be single-leg");
        
        TradeOrder.OrderLeg leg = trade.getMainLeg();
        assertNotNull(leg);
        assertEquals("ES", leg.symbol);
        assertEquals("202609", leg.futuresMonth);
        assertEquals("CME", leg.exchange);
        assertEquals("20260918", leg.expiry);
        assertEquals(TradeOrder.ContractType.FUTURES_OPTION, leg.contractType);
        assertEquals(6600.0, leg.strike);
        assertEquals("C", leg.optionType);
        assertEquals("SELL", leg.action);
        assertEquals(5.0, trade.getTargetPrice());
        assertEquals(7.5, trade.getAlertThreshold());
    }

    // ===================================================================
    // Test 2: Valid Combo Futures Option (ES Call Spread)
    // ===================================================================
    
    @Test
    @Order(2)
    void testValidComboFuturesOption() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {2, "DU4932144", "ES", "202609", "CME", "20260918", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 2.5, 4.0, "Y"},
            {2, "DU4932144", "ES", "202609", "CME", "20260918", "", "CALL BUY", "", 6650.0, 1, 1, 0.0, 0.0, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertTrue(result.success);
        assertEquals(0, result.errors.size(), "Should have no errors: " + result.errors);
        
        List<TradeOrder> trades = result.sheetTrades.get("Futures");
        assertEquals(1, trades.size(), "Should group into 1 combo trade");
        
        TradeOrder trade = trades.get(0);
        assertEquals("2", trade.getTradeId());
        assertTrue(trade.isComboOrder(), "Should be combo order");
        assertEquals(2, trade.getLegs().size(), "Should have 2 legs");
        
        // Verify both legs have futures metadata
        for (TradeOrder.OrderLeg leg : trade.getLegs()) {
            assertEquals("ES", leg.symbol);
            assertEquals("202609", leg.futuresMonth);
            assertEquals("CME", leg.exchange);
            assertEquals(TradeOrder.ContractType.FUTURES_OPTION, leg.contractType);
        }
        
        // Verify it's a credit trade (SELL spread)
        assertTrue(trade.isCreditTrade(), "SELL spread should be credit");
    }

    // ===================================================================
    // Test 3: MES (Micro E-mini) Support
    // ===================================================================
    
    @Test
    @Order(3)
    void testMESFuturesOption() throws IOException {
        createFuturesExcel("Futures_MES", new Object[][] {
            {3, "DU4932144", "MES", "202609", "CME", "20260918", "BUY", "PUT BUY", "MAIN", 6000.0, 1, 1, 3.0, 5.0, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertTrue(result.success);
        List<TradeOrder> trades = result.sheetTrades.get("Futures_MES");
        
        TradeOrder trade = trades.get(0);
        TradeOrder.OrderLeg leg = trade.getMainLeg();
        assertEquals("MES", leg.symbol);
        assertEquals(TradeOrder.ContractType.FUTURES_OPTION, leg.contractType);
    }

    // ===================================================================
    // Test 4: Invalid Futures Month Format
    // ===================================================================
    
    @Test
    @Order(4)
    void testInvalidFuturesMonthFormat() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {1, "DU4932144", "ES", "2026-09", "CME", "20260918", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertFalse(result.success, "Import should fail with invalid futures month");
        assertTrue(result.errors.size() > 0, "Should have errors");
        assertTrue(result.errors.get(0).contains("6 digits in YYYYMM format"), 
            "Error should mention format requirement: " + result.errors.get(0));
    }

    // ===================================================================
    // Test 5: Invalid Futures Month (Month 13)
    // ===================================================================
    
    @Test
    @Order(5)
    void testInvalidFuturesMonthValue() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {1, "DU4932144", "ES", "202613", "CME", "20260918", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertFalse(result.success);
        assertTrue(result.errors.get(0).contains("month must be 01-12"), 
            "Error should mention month range: " + result.errors.get(0));
    }

    // ===================================================================
    // Test 6: Option Expiry After Futures Month (Invalid)
    // ===================================================================
    
    @Test
    @Order(6)
    void testOptionExpiryAfterFuturesMonth() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {1, "DU4932144", "ES", "202609", "CME", "20261015", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertFalse(result.success);
        assertTrue(result.errors.get(0).contains("cannot be after Futures Month"), 
            "Error should mention expiry after futures: " + result.errors.get(0));
    }

    // ===================================================================
    // Test 7: Option Expiry Too Early (Invalid)
    // ===================================================================
    
    @Test
    @Order(7)
    void testOptionExpiryTooEarly() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {1, "DU4932144", "ES", "202609", "CME", "20260515", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertFalse(result.success);
        assertTrue(result.errors.get(0).contains("too early"), 
            "Error should mention expiry too early: " + result.errors.get(0));
    }

    // ===================================================================
    // Test 8: Valid Option Expiry (1 Month Before Futures)
    // ===================================================================
    
    @Test
    @Order(8)
    void testValidOptionExpiryOneMonthBefore() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {1, "DU4932144", "ES", "202609", "CME", "20260820", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertTrue(result.success, "Should accept expiry 1 month before futures. Errors: " + result.errors);
        assertEquals(0, result.errors.size());
    }

    // ===================================================================
    // Test 9: Exchange Auto-Normalization (Lowercase -> Uppercase)
    // ===================================================================
    
    @Test
    @Order(9)
    void testExchangeAutoNormalization() throws IOException {
        createFuturesExcel("Futures", new Object[][] {
            {1, "DU4932144", "ES", "202609", "cme", "20260918", "SELL", "CALL SELL", "MAIN", 6600.0, 1, 1, 5.0, 7.5, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertTrue(result.success, "Should auto-normalize exchange to uppercase. Errors: " + result.errors);
        List<TradeOrder> trades = result.sheetTrades.get("Futures");
        TradeOrder.OrderLeg leg = trades.get(0).getMainLeg();
        assertEquals("CME", leg.exchange, "Exchange should be auto-normalized to uppercase");
    }

    // ===================================================================
    // Test 10: Stock Options Still Work (Backward Compatibility)
    // ===================================================================
    
    @Test
    @Order(10)
    void testStockOptionsBackwardCompatible() throws IOException {
        createStockOptionsExcel("SPY_Options", new Object[][] {
            {1, "DU4932144", "SPY", "20260417", "BUY", "PUT BUY", "MAIN", 640.0, 1, 1, 10.0, 15.0, "Y"}
        });

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testFuturesFile);

        assertTrue(result.success, "Stock options should still work");
        List<TradeOrder> trades = result.sheetTrades.get("SPY_Options");
        
        TradeOrder.OrderLeg leg = trades.get(0).getMainLeg();
        assertEquals("SPY", leg.symbol);
        assertEquals(TradeOrder.ContractType.STOCK_OPTION, leg.contractType);
        assertNull(leg.futuresMonth, "Stock options should not have futures month");
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private void createFuturesExcel(String sheetName, Object[][] rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(testFuturesFile)) {
            Sheet sheet = wb.createSheet(sheetName);
            
            // Header row
            Row header = sheet.createRow(0);
            String[] headers = FUTURES_EXCEL_HEADERS;
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            
            // Data rows
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] data = rows[r];
                for (int c = 0; c < data.length; c++) {
                    Cell cell = row.createCell(c);
                    if (data[c] instanceof Number) {
                        cell.setCellValue(((Number) data[c]).doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(data[c]));
                    }
                }
            }
            
            wb.write(fos);
        }
    }

    private void createStockOptionsExcel(String sheetName, Object[][] rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(testFuturesFile)) {
            Sheet sheet = wb.createSheet(sheetName);
            
            // Header row
            Row header = sheet.createRow(0);
            String[] headers = EXCEL_HEADERS;
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            
            // Data rows
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] data = rows[r];
                for (int c = 0; c < data.length; c++) {
                    Cell cell = row.createCell(c);
                    if (data[c] instanceof Number) {
                        cell.setCellValue(((Number) data[c]).doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(data[c]));
                    }
                }
            }
            
            wb.write(fos);
        }
    }
}
