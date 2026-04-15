package apidemo.stategies;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for IBK Trading using the user's Excel test data:
 *
 * Sheet "TEST":
 * Row 2: TradeID=1, Account=DU4932144, Symbol=SPY, Expiry=17-Apr, Action="put buy", Role=Main, Strike=640, Rate=1, QTY=1, Target=10, Alert=15, Active=Y
 * Row 3: TradeID=1, Account=DU4932144, Symbol=SPY, Expiry=17-Apr, Action="call buy", Role=(empty), Strike=670, Rate=1, QTY=1, Target=, Alert=, Active=Y
 *
 * This is a debit combo (long strangle): BUY PUT 640 + BUY CALL 670
 */
@TestMethodOrder(OrderAnnotation.class)
public class TradingEndToEndTest {

    private static File testExcelFile;
    private static final String SHEET_NAME = "TEST";

    // ========================================================================
    // Test data setup — creates an Excel file matching the user's screenshot
    // ========================================================================

    @BeforeAll
    static void createTestExcel() throws IOException {
        testExcelFile = File.createTempFile("Model_Excel_Test", ".xlsx");
        testExcelFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(testExcelFile)) {
            Sheet sheet = wb.createSheet(SHEET_NAME);

            // Header row
            Row header = sheet.createRow(0);
            for (int i = 0; i <= ExcelOrderImporter.COL_ACTIVE; i++) {
                header.createCell(i).setCellValue(ExcelOrderImporter.EXCEL_HEADERS[i]);
            }

            // Row 2: PUT BUY (Main leg)
            Row r2 = sheet.createRow(1);
            r2.createCell(ExcelOrderImporter.COL_TRADE_ID).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_ACCOUNT).setCellValue("DU4932144");
            r2.createCell(ExcelOrderImporter.COL_SYMBOL).setCellValue("SPY");
            // Expiry as date "17-Apr-26"
            CellStyle dateStyle = wb.createCellStyle();
            CreationHelper ch = wb.getCreationHelper();
            dateStyle.setDataFormat(ch.createDataFormat().getFormat("dd-MMM-yy"));
            Cell dateCell = r2.createCell(ExcelOrderImporter.COL_EXPIRY);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy");
                dateCell.setCellValue(sdf.parse("17-Apr-26"));
            } catch (Exception e) {
                dateCell.setCellValue("17-Apr-26");
            }
            dateCell.setCellStyle(dateStyle);
            r2.createCell(ExcelOrderImporter.COL_NET_ACTION).setCellValue("BUY");
            r2.createCell(ExcelOrderImporter.COL_ACTION).setCellValue("put buy");
            r2.createCell(ExcelOrderImporter.COL_ROLE).setCellValue("Main");
            r2.createCell(ExcelOrderImporter.COL_STRIKE).setCellValue(640);
            r2.createCell(ExcelOrderImporter.COL_RATE).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_QTY).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_TARGET).setCellValue(10);
            r2.createCell(ExcelOrderImporter.COL_ALERT).setCellValue(15);
            r2.createCell(ExcelOrderImporter.COL_ACTIVE).setCellValue("Y");

            // Row 3: CALL BUY (child leg)
            Row r3 = sheet.createRow(2);
            r3.createCell(ExcelOrderImporter.COL_TRADE_ID).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_ACCOUNT).setCellValue("DU4932144");
            r3.createCell(ExcelOrderImporter.COL_SYMBOL).setCellValue("SPY");
            Cell dateCell2 = r3.createCell(ExcelOrderImporter.COL_EXPIRY);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy");
                dateCell2.setCellValue(sdf.parse("17-Apr-26"));
            } catch (Exception e) {
                dateCell2.setCellValue("17-Apr-26");
            }
            dateCell2.setCellStyle(dateStyle);
            // Net Action left empty (child leg)
            r3.createCell(ExcelOrderImporter.COL_ACTION).setCellValue("call buy");
            // Role left empty (child leg)
            r3.createCell(ExcelOrderImporter.COL_STRIKE).setCellValue(670);
            r3.createCell(ExcelOrderImporter.COL_RATE).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_QTY).setCellValue(1);
            // Target, Alert left empty for child leg
            r3.createCell(ExcelOrderImporter.COL_ACTIVE).setCellValue("Y");

            wb.write(fos);
        }
    }

    @AfterAll
    static void cleanup() {
        if (testExcelFile != null && testExcelFile.exists()) {
            testExcelFile.delete();
        }
    }

    // ========================================================================
    // 1. EXCEL IMPORT / PARSING TESTS
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("1.1 Import succeeds with no errors")
    void testImportSuccess() {
        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testExcelFile);

        assertTrue(result.success, "Import should succeed");
        assertTrue(result.errors.isEmpty(), "No errors expected, got: " + result.errors);
        assertEquals(1, result.sheetTrades.size(), "Should have 1 sheet");
        assertTrue(result.sheetTrades.containsKey(SHEET_NAME));
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Sheet contains exactly 1 trade with 2 legs")
    void testTradeCount() {
        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testExcelFile);
        List<TradeOrder> trades = result.sheetTrades.get(SHEET_NAME);

        assertNotNull(trades);
        assertEquals(1, trades.size(), "Should have exactly 1 trade (two rows share Trade ID=1)");
        assertEquals(1, result.totalTradeCount());

        TradeOrder trade = trades.get(0);
        assertEquals(2, trade.getLegs().size(), "Trade should have 2 legs");
    }

    @Test
    @Order(3)
    @DisplayName("1.3 Leg 1 parsed correctly: PUT BUY 640 Main")
    void testLeg1Parsing() {
        TradeOrder trade = importFirstTrade();
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();

        assertNotNull(mainLeg, "Main leg should exist");
        assertEquals("SPY", mainLeg.symbol);
        assertEquals("BUY", mainLeg.action);
        assertEquals("P", mainLeg.optionType, "PUT should be normalized to P");
        assertEquals("MAIN", mainLeg.role.toUpperCase());
        assertEquals(640.0, mainLeg.strike, 0.01);
        assertEquals(1, mainLeg.rate);
        assertEquals(1, mainLeg.quantity);
    }

    @Test
    @Order(4)
    @DisplayName("1.4 Leg 2 parsed correctly: CALL BUY 670 child")
    void testLeg2Parsing() {
        TradeOrder trade = importFirstTrade();
        List<TradeOrder.OrderLeg> legs = trade.getLegs();

        // Find the non-main leg
        TradeOrder.OrderLeg childLeg = legs.stream()
                .filter(l -> !"MAIN".equalsIgnoreCase(l.role))
                .findFirst().orElse(null);

        assertNotNull(childLeg, "Child leg should exist");
        assertEquals("SPY", childLeg.symbol);
        assertEquals("BUY", childLeg.action);
        assertEquals("C", childLeg.optionType, "CALL should be normalized to C");
        assertEquals(670.0, childLeg.strike, 0.01);
        assertEquals(1, childLeg.rate);
    }

    @Test
    @Order(5)
    @DisplayName("1.5 Target and Alert set from Main leg row")
    void testTargetAlertFromMainLeg() {
        TradeOrder trade = importFirstTrade();

        assertEquals(10.0, trade.getTargetPrice(), 0.01, "Target should be 10 from Main row");
        assertEquals(15.0, trade.getAlertThreshold(), 0.01, "Alert should be 15 from Main row");
    }

    @Test
    @Order(6)
    @DisplayName("1.6 Account, Trade ID, and initial status correct")
    void testTradeMetadata() {
        TradeOrder trade = importFirstTrade();

        assertEquals("1", trade.getTradeId());
        assertEquals("DU4932144", trade.getAccount());
        assertTrue(trade.isActive());
        assertEquals(TradeOrder.OrderStatus.READY, trade.getStatus());
    }

    // ========================================================================
    // 2. CREDIT vs DEBIT CLASSIFICATION TESTS
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("2.1 Two BUY legs → debit trade (not credit)")
    void testDebitClassification() {
        TradeOrder trade = importFirstTrade();

        assertFalse(trade.isCreditTrade(), "PUT BUY + CALL BUY = debit (long strangle)");
    }

    @Test
    @Order(11)
    @DisplayName("2.2 Debit trade display action is BUY")
    void testDebitDisplayAction() {
        TradeOrder trade = importFirstTrade();

        assertEquals("BUY", trade.getDisplayAction(), "Debit combo should display BUY");
    }

    @Test
    @Order(12)
    @DisplayName("2.3 Credit trade: SELL legs → isCreditTrade true")
    void testCreditClassification() {
        // Manually build a credit trade: SELL PUT + SELL CALL (short strangle)
        TradeOrder creditTrade = new TradeOrder("99", "DU4932144");
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "C", "",
                670, 1, 1, "DU4932144", 2));
        creditTrade.setTargetPrice(5.0);
        creditTrade.setAlertThreshold(3.0);

        assertTrue(creditTrade.isCreditTrade(), "SELL PUT + SELL CALL = credit (short strangle)");
        assertEquals("SELL", creditTrade.getDisplayAction());
    }

    @Test
    @Order(13)
    @DisplayName("2.4 Mixed legs: BUY higher PUT + SELL lower PUT → debit (bear put spread)")
    void testMixedLegsDebit() {
        TradeOrder t = new TradeOrder("100", "DU4932144");
        t.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "BUY", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        t.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "",
                630, 1, 1, "DU4932144", 2));

        // Main leg action = BUY (no netAction set) → fallback → debit
        assertFalse(t.isCreditTrade());
        assertEquals("BUY", t.getDisplayAction());
    }

    @Test
    @Order(14)
    @DisplayName("2.5 Mixed legs: SELL higher PUT + BUY lower PUT → credit (bull put spread)")
    void testMixedLegsCredit() {
        TradeOrder t = new TradeOrder("101", "DU4932144");
        t.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        t.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "BUY", "P", "",
                630, 1, 1, "DU4932144", 2));

        // Main leg action = SELL (no netAction set) → fallback → credit
        assertTrue(t.isCreditTrade());
        assertEquals("SELL", t.getDisplayAction());
    }

    @Test
    @Order(15)
    @DisplayName("2.6 Single leg BUY → debit; single leg SELL → credit")
    void testSingleLegClassification() {
        TradeOrder buyTrade = new TradeOrder("102", "DU4932144");
        buyTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "BUY", "C", "MAIN",
                670, 1, 1, "DU4932144", 1));
        assertFalse(buyTrade.isCreditTrade());
        assertFalse(buyTrade.isComboOrder());

        TradeOrder sellTrade = new TradeOrder("103", "DU4932144");
        sellTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        assertTrue(sellTrade.isCreditTrade());
        assertFalse(sellTrade.isComboOrder());
    }

    // --- Excel Trade Type Classification Tests ---

    @Test
    @Order(16)
    @DisplayName("2.7 Trade1: PLTR short strangle (SELL PUT + SELL CALL) → credit")
    void testTrade1ShortStrangle() {
        TradeOrder t = new TradeOrder("1", "DU4932144");
        t.addLeg(new TradeOrder.OrderLeg("PLTR", "20260417", "", "SELL", "P", "MAIN",
                130, 1, 1, "DU4932144", 1));
        t.addLeg(new TradeOrder.OrderLeg("PLTR", "20260417", "", "SELL", "C", "",
                165, 1, 1, "DU4932144", 2));
        assertTrue(t.isCreditTrade(), "Short strangle (all SELL) = credit");
        assertEquals("SELL", t.getDisplayAction());
    }

    @Test
    @Order(17)
    @DisplayName("2.8 Trade2: GOOGL bull put spread (SELL PUT 270 + BUY PUT 260) → credit")
    void testTrade2BullPutSpread() {
        TradeOrder t = new TradeOrder("2", "DU4932144");
        t.addLeg(new TradeOrder.OrderLeg("GOOGL", "20260515", "", "SELL", "P", "MAIN",
                270, 1, 1, "DU4932144", 1));
        t.addLeg(new TradeOrder.OrderLeg("GOOGL", "20260515", "", "BUY", "P", "",
                260, 1, 1, "DU4932144", 2));
        // Main leg action = SELL (no netAction set) → fallback → credit
        assertTrue(t.isCreditTrade(), "Bull put spread = credit (main leg SELL)");
        assertEquals("SELL", t.getDisplayAction());
    }

    @Test
    @Order(18)
    @DisplayName("2.9 Trade3: AAPL iron condor → credit")
    void testTrade3IronCondor() {
        TradeOrder t = new TradeOrder("3", "DU4932144");
        t.addLeg(new TradeOrder.OrderLeg("AAPL", "20260618", "", "SELL", "P", "MAIN",
                230, 1, 1, "DU4932144", 1));
        t.addLeg(new TradeOrder.OrderLeg("AAPL", "20260618", "", "BUY", "P", "",
                220, 1, 1, "DU4932144", 2));
        t.addLeg(new TradeOrder.OrderLeg("AAPL", "20260618", "", "SELL", "C", "",
                260, 1, 1, "DU4932144", 3));
        t.addLeg(new TradeOrder.OrderLeg("AAPL", "20260618", "", "BUY", "C", "",
                270, 1, 1, "DU4932144", 4));
        // Main leg action = SELL (no netAction set) → fallback → credit
        assertTrue(t.isCreditTrade(), "Iron condor = credit (main leg SELL)");
        assertEquals("SELL", t.getDisplayAction());
    }

    @Test
    @Order(19)
    @DisplayName("2.10 Trade4: SPY ratio put spread (BUY PUT 635 r=1 + SELL PUT 620 r=2) → credit")
    void testTrade4RatioPutSpread() {
        TradeOrder t = new TradeOrder("4", "DU4932144");
        t.addLeg(new TradeOrder.OrderLeg("SPY", "20260410", "SELL", "BUY", "P", "MAIN",
                635, 1, 1, "DU4932144", 1));
        t.addLeg(new TradeOrder.OrderLeg("SPY", "20260410", "", "SELL", "P", "",
                620, 2, 1, "DU4932144", 2));
        // Net Action on main leg = "SELL" → credit (explicitly defined by user)
        assertTrue(t.isCreditTrade(), "Ratio put spread: Net Action=SELL on main leg → credit");
        assertEquals("SELL", t.getDisplayAction());
    }

    // ========================================================================
    // 3. COMBO ORDER CONSTRUCTION TESTS
    // ========================================================================

    @Test
    @Order(20)
    @DisplayName("3.1 isComboOrder true for multi-leg trade")
    void testIsComboOrder() {
        TradeOrder trade = importFirstTrade();
        assertTrue(trade.isComboOrder(), "Trade with 2 legs should be combo");
    }

    @Test
    @Order(21)
    @DisplayName("3.2 Display symbols shows both legs")
    void testDisplaySymbols() {
        TradeOrder trade = importFirstTrade();
        assertEquals("SPY + SPY", trade.getDisplaySymbols());
    }

    @Test
    @Order(22)
    @DisplayName("3.3 Detailed action shows leg-by-leg breakdown")
    void testDetailedAction() {
        TradeOrder trade = importFirstTrade();
        String detail = trade.getDetailedAction();
        // Should contain both "PUT BUY" and "CALL BUY"
        assertTrue(detail.contains("PUT") && detail.contains("BUY"), "Should show PUT BUY");
        assertTrue(detail.contains("CALL") && detail.contains("BUY"), "Should show CALL BUY");
    }

    @Test
    @Order(23)
    @DisplayName("3.4 Total quantity from main leg")
    void testTotalQuantity() {
        TradeOrder trade = importFirstTrade();
        assertEquals(1, trade.getTotalQuantity());
    }

    // ========================================================================
    // 4. PRICE MONITOR / ALERT DIRECTION TESTS
    // ========================================================================

    @Test
    @Order(30)
    @DisplayName("4.1 MonitoredOrder shouldAlert: positive threshold (debit) triggers at/below")
    void testPositiveThresholdTriggersBelow() {
        // Positive = debit: alert when price drops to/below threshold
        PriceMonitor.MonitoredOrder order = new PriceMonitor.MonitoredOrder(
                "test1", null, 10.0, 15.0, "BUY");

        // Above threshold → no alert
        order.currentPrice = 16.0;
        assertFalse(order.shouldAlert());

        // At threshold → alert
        order.currentPrice = 15.0;
        assertTrue(order.shouldAlert());

        // Below threshold → alert
        order.currentPrice = 14.0;
        assertTrue(order.shouldAlert());
    }

    @Test
    @Order(31)
    @DisplayName("4.2 MonitoredOrder shouldAlert: negative threshold (credit) triggers at/above")
    void testNegativeThresholdTriggersAbove() {
        // Negative = credit: alert when price rises to/above |threshold|
        PriceMonitor.MonitoredOrder order = new PriceMonitor.MonitoredOrder(
                "test2", null, 10.0, -15.0, "SELL");

        // Below threshold → no alert
        order.currentPrice = 14.0;
        assertFalse(order.shouldAlert());

        // At threshold → alert
        order.currentPrice = 15.0;
        assertTrue(order.shouldAlert());

        // Above threshold → alert
        order.currentPrice = 16.0;
        assertTrue(order.shouldAlert());
    }

    @Test
    @Order(32)
    @DisplayName("4.3 Debit trade monitoring uses positive alert (trigger below)")
    void testDebitTradeAlertDirection() {
        TradeOrder trade = importFirstTrade();

        // Unified: positive = debit (trigger at/below)
        double signedAlert = trade.isCreditTrade()
                ? -Math.abs(trade.getAlertThreshold())
                : Math.abs(trade.getAlertThreshold());

        assertEquals(15.0, signedAlert, 0.01, "Debit trade should use positive alert threshold");
    }

    @Test
    @Order(33)
    @DisplayName("4.4 Credit trade monitoring uses negative alert (trigger above)")
    void testCreditTradeAlertDirection() {
        TradeOrder creditTrade = new TradeOrder("99", "DU4932144");
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "C", "",
                670, 1, 1, "DU4932144", 2));
        creditTrade.setAlertThreshold(5.0);

        double signedAlert = creditTrade.isCreditTrade()
                ? -Math.abs(creditTrade.getAlertThreshold())
                : Math.abs(creditTrade.getAlertThreshold());

        assertEquals(-5.0, signedAlert, 0.01, "Credit trade should use negative alert threshold");
    }

    @Test
    @Order(34)
    @DisplayName("4.5 Alert only fires once (alertTriggered flag)")
    void testAlertFiresOnce() {
        PriceMonitor.MonitoredOrder order = new PriceMonitor.MonitoredOrder(
                "test3", null, 10.0, 15.0, "SELL");

        order.currentPrice = 15.0;
        assertTrue(order.shouldAlert());

        // Mark as triggered
        order.alertTriggered = true;
        assertFalse(order.shouldAlert(), "Should not alert again after triggered");
    }

    @Test
    @Order(35)
    @DisplayName("4.6 Zero threshold or zero price never alerts")
    void testZeroNeverAlerts() {
        PriceMonitor.MonitoredOrder order = new PriceMonitor.MonitoredOrder(
                "test4", null, 10.0, 0.0, "BUY");
        order.currentPrice = 5.0;
        assertFalse(order.shouldAlert(), "Zero threshold should never alert");

        // Positive threshold (debit) with zero price
        PriceMonitor.MonitoredOrder order2 = new PriceMonitor.MonitoredOrder(
                "test5", null, 10.0, 15.0, "BUY");
        order2.currentPrice = 0.0;
        assertFalse(order2.shouldAlert(), "Zero price should never alert");
    }

    // ========================================================================
    // 5. COMBO NET PRICE CALCULATION TESTS
    // ========================================================================

    @Test
    @Order(40)
    @DisplayName("5.1 Net combo price for debit: BUY legs = negative net (cost)")
    void testComboNetPriceDebit() {
        TradeOrder trade = importFirstTrade();

        // Simulate leg prices
        Map<String, Double> legPrices = new HashMap<>();
        legPrices.put(trade.getTradeId() + "_640.0_P", 8.50);   // PUT at 640 = $8.50
        legPrices.put(trade.getTradeId() + "_670.0_C", 9.20);   // CALL at 670 = $9.20

        double netPrice = calculateNetComboPrice(trade, legPrices);

        // Both BUY: net = -8.50*1 - 9.20*1 = -17.70
        assertEquals(-17.70, netPrice, 0.01, "Net combo = -(8.50 + 9.20) for all BUY legs");
    }

    @Test
    @Order(41)
    @DisplayName("5.2 Net combo price for credit: SELL legs = positive net (premium)")
    void testComboNetPriceCredit() {
        TradeOrder creditTrade = new TradeOrder("99", "DU4932144");
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "C", "",
                670, 1, 1, "DU4932144", 2));

        Map<String, Double> legPrices = new HashMap<>();
        legPrices.put("99_640.0_P", 8.50);
        legPrices.put("99_670.0_C", 9.20);

        double netPrice = calculateNetComboPrice(creditTrade, legPrices);

        // Both SELL: net = +8.50*1 + 9.20*1 = +17.70
        assertEquals(17.70, netPrice, 0.01, "Net combo = +(8.50 + 9.20) for all SELL legs");
    }

    @Test
    @Order(42)
    @DisplayName("5.3 Incomplete leg prices return 0 (no partial calculation)")
    void testIncompleteLegsReturnZero() {
        TradeOrder trade = importFirstTrade();

        Map<String, Double> legPrices = new HashMap<>();
        legPrices.put(trade.getTradeId() + "_640.0_P", 8.50); // Only one leg

        double netPrice = calculateNetComboPrice(trade, legPrices);
        assertEquals(0.0, netPrice, 0.01, "Should return 0 when not all legs have prices");
    }

    @Test
    @Order(43)
    @DisplayName("5.4 Mixed combo (bull put spread): SELL P 640 + BUY P 630")
    void testMixedComboNetPrice() {
        TradeOrder spread = new TradeOrder("200", "DU4932144");
        spread.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        spread.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "BUY", "P", "",
                630, 1, 1, "DU4932144", 2));

        Map<String, Double> legPrices = new HashMap<>();
        legPrices.put("200_640.0_P", 10.0);  // SELL at higher strike = more premium
        legPrices.put("200_630.0_P", 7.0);   // BUY at lower strike = less premium

        double netPrice = calculateNetComboPrice(spread, legPrices);
        // SELL 640 → +10*1 = +10, BUY 630 → -7*1 = -7, net = +3
        assertEquals(3.0, netPrice, 0.01, "Bull put spread net = +3 (credit)");
    }

    // ========================================================================
    // 6. DISPLAY FORMATTING TESTS
    // ========================================================================

    @Test
    @Order(50)
    @DisplayName("6.1 Debit trade: signed target positive, signed alert positive")
    void testDebitTradeDisplayValues() {
        TradeOrder trade = importFirstTrade();
        assertFalse(trade.isCreditTrade());

        double signedTarget = trade.isCreditTrade()
                ? -Math.abs(trade.getTargetPrice()) : Math.abs(trade.getTargetPrice());
        double signedAlert = trade.isCreditTrade()
                ? -Math.abs(trade.getAlertThreshold()) : Math.abs(trade.getAlertThreshold());

        assertEquals(10.0, signedTarget, 0.01, "Debit target should be positive");
        assertEquals(15.0, signedAlert, 0.01, "Debit alert should be positive");
    }

    @Test
    @Order(51)
    @DisplayName("6.2 Credit trade: signed target negative, signed alert negative")
    void testCreditTradeDisplayValues() {
        TradeOrder creditTrade = new TradeOrder("99", "DU4932144");
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        creditTrade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "C", "",
                670, 1, 1, "DU4932144", 2));
        creditTrade.setTargetPrice(5.0);
        creditTrade.setAlertThreshold(3.0);

        assertTrue(creditTrade.isCreditTrade());

        double signedTarget = creditTrade.isCreditTrade()
                ? -Math.abs(creditTrade.getTargetPrice()) : Math.abs(creditTrade.getTargetPrice());
        double signedAlert = creditTrade.isCreditTrade()
                ? -Math.abs(creditTrade.getAlertThreshold()) : Math.abs(creditTrade.getAlertThreshold());

        assertEquals(-5.0, signedTarget, 0.01, "Credit target should be negative");
        assertEquals(-3.0, signedAlert, 0.01, "Credit alert should be negative");
    }

    @Test
    @Order(52)
    @DisplayName("6.3 Expiry formatting")
    void testExpiryDisplay() {
        TradeOrder trade = importFirstTrade();
        String expiry = trade.getDisplayExpiry();
        // Expiry should be in yyyyMMdd format from import
        assertNotNull(expiry);
        assertFalse(expiry.isEmpty());
        // Should be 20260417 format
        assertTrue(expiry.matches("\\d{8}"), "Expiry should be yyyyMMdd format, got: " + expiry);
    }

    // ========================================================================
    // 7. INACTIVE TRADE HANDLING TESTS
    // ========================================================================

    @Test
    @Order(60)
    @DisplayName("7.1 Inactive trade rows are skipped during import")
    void testInactiveTradeSkipped() throws IOException {
        File inactiveFile = File.createTempFile("InactiveTest", ".xlsx");
        inactiveFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(inactiveFile)) {
            Sheet sheet = wb.createSheet("TEST2");

            Row header = sheet.createRow(0);
            for (int i = 0; i <= ExcelOrderImporter.COL_ACTIVE; i++) {
                header.createCell(i).setCellValue(ExcelOrderImporter.EXCEL_HEADERS[i]);
            }

            // Active row
            Row r2 = sheet.createRow(1);
            r2.createCell(ExcelOrderImporter.COL_TRADE_ID).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_ACCOUNT).setCellValue("DU4932144");
            r2.createCell(ExcelOrderImporter.COL_SYMBOL).setCellValue("SPY");
            r2.createCell(ExcelOrderImporter.COL_EXPIRY).setCellValue("20260417");
            r2.createCell(ExcelOrderImporter.COL_NET_ACTION).setCellValue("BUY");
            r2.createCell(ExcelOrderImporter.COL_ACTION).setCellValue("put buy");
            r2.createCell(ExcelOrderImporter.COL_ROLE).setCellValue("Main");
            r2.createCell(ExcelOrderImporter.COL_STRIKE).setCellValue(640);
            r2.createCell(ExcelOrderImporter.COL_RATE).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_QTY).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_TARGET).setCellValue(10);
            r2.createCell(ExcelOrderImporter.COL_ALERT).setCellValue(15);
            r2.createCell(ExcelOrderImporter.COL_ACTIVE).setCellValue("Y");

            // Inactive row for SAME trade — should cause entire trade to be skipped
            Row r3 = sheet.createRow(2);
            r3.createCell(ExcelOrderImporter.COL_TRADE_ID).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_ACCOUNT).setCellValue("DU4932144");
            r3.createCell(ExcelOrderImporter.COL_SYMBOL).setCellValue("SPY");
            r3.createCell(ExcelOrderImporter.COL_EXPIRY).setCellValue("20260417");
            r3.createCell(ExcelOrderImporter.COL_ACTION).setCellValue("call buy");
            r3.createCell(ExcelOrderImporter.COL_STRIKE).setCellValue(670);
            r3.createCell(ExcelOrderImporter.COL_RATE).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_QTY).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_ACTIVE).setCellValue("N");  // INACTIVE

            wb.write(fos);
        }

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(inactiveFile);

        // Entire trade should be skipped because one leg is inactive
        assertTrue(result.sheetTrades.isEmpty() || !result.sheetTrades.containsKey("TEST2")
                || result.sheetTrades.get("TEST2").isEmpty(),
                "Trade with any inactive leg should be skipped");

        inactiveFile.delete();
    }

    // ========================================================================
    // 8. EDGE CASE / ACTION PARSING TESTS
    // ========================================================================

    @Test
    @Order(70)
    @DisplayName("8.1 Action format: 'PUT BUY' parsed correctly")
    void testActionFormatPutBuy() throws IOException {
        TradeOrder trade = importTradeWithAction("put buy", 640, "Main", "call buy", 670);
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        assertEquals("BUY", mainLeg.action);
        assertEquals("P", mainLeg.optionType);
    }

    @Test
    @Order(71)
    @DisplayName("8.2 Action format: 'BUY CALL' parsed correctly")
    void testActionFormatBuyCall() throws IOException {
        TradeOrder trade = importTradeWithAction("buy call", 640, "Main", "buy put", 670);
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        assertEquals("BUY", mainLeg.action);
        assertEquals("C", mainLeg.optionType);
    }

    @Test
    @Order(72)
    @DisplayName("8.3 Action format: 'SELL PUT' parsed correctly")
    void testActionFormatSellPut() throws IOException {
        TradeOrder trade = importTradeWithAction("sell put", 640, "Main", "sell call", 670);
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        assertEquals("SELL", mainLeg.action);
        assertEquals("P", mainLeg.optionType);
    }

    @Test
    @Order(73)
    @DisplayName("8.4 Action format: 'CALL SELL' parsed correctly")
    void testActionFormatCallSell() throws IOException {
        TradeOrder trade = importTradeWithAction("call sell", 640, "Main", "put sell", 670);
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        assertEquals("SELL", mainLeg.action);
        assertEquals("C", mainLeg.optionType);
    }

    // ========================================================================
    // 9. PriceMonitor registerOrder / updatePrice TESTS
    // ========================================================================

    @Test
    @Order(80)
    @DisplayName("9.1 PriceMonitor registerOrder and shouldAlert for debit")
    void testPriceMonitorRegisterAndAlert() {
        PriceMonitor monitor = new PriceMonitor(null);

        // Debit trade: positive alert threshold = trigger at/below
        String id = monitor.registerOrder(5.0, 8.0, "BUY");
        assertNotNull(id);

        PriceMonitor.MonitoredOrder order = monitor.getOrder(id);
        assertNotNull(order);

        // Price above threshold → no alert
        order.currentPrice = 9.0;
        assertFalse(order.shouldAlert());

        // Price at threshold → alert
        order.currentPrice = 8.0;
        assertTrue(order.shouldAlert());

        // Price below threshold → alert
        order.currentPrice = 7.0;
        assertTrue(order.shouldAlert());
    }

    @Test
    @Order(81)
    @DisplayName("9.2 PriceMonitor credit: negative alert triggers at/above")
    void testPriceMonitorCreditAlert() {
        PriceMonitor monitor = new PriceMonitor(null);
        // Negative = credit: trigger when price rises to/above |threshold|
        String id = monitor.registerOrder(10.0, -15.0, "SELL");

        PriceMonitor.MonitoredOrder order = monitor.getOrder(id);
        assertNotNull(order);

        // Price below → no alert
        order.currentPrice = 14.0;
        assertFalse(order.shouldAlert());

        // Price at threshold → alert
        order.currentPrice = 15.0;
        assertTrue(order.shouldAlert());

        // Price above → alert
        order.currentPrice = 16.0;
        assertTrue(order.shouldAlert());
    }

    // ========================================================================
    // 10. FULL FLOW SIMULATION (without live IB connection)
    // ========================================================================

    @Test
    @Order(90)
    @DisplayName("10.1 Full flow: Import → Classify → Calculate combo price → Check alert")
    void testFullFlowSimulation() {
        // Step 1: Import
        TradeOrder trade = importFirstTrade();
        assertEquals(TradeOrder.OrderStatus.READY, trade.getStatus());
        assertTrue(trade.isComboOrder());
        assertFalse(trade.isCreditTrade());

        // Step 2: Simulate monitoring setup (unified: positive=debit, negative=credit)
        double signedAlert = trade.isCreditTrade()
                ? -Math.abs(trade.getAlertThreshold())
                : Math.abs(trade.getAlertThreshold());
        assertEquals(15.0, signedAlert, 0.01);

        // Step 3: Register with PriceMonitor
        PriceMonitor monitor = new PriceMonitor(null);
        String monitorId = monitor.registerOrder(trade.getTargetPrice(), signedAlert, "BUY");
        trade.setMonitoringId(monitorId);
        trade.setStatus(TradeOrder.OrderStatus.MONITORING);

        // Step 4: Simulate market data for both legs
        Map<String, Double> legPrices = new HashMap<>();
        legPrices.put(trade.getTradeId() + "_640.0_P", 8.50);
        legPrices.put(trade.getTradeId() + "_670.0_C", 9.20);
        double netPrice = calculateNetComboPrice(trade, legPrices);
        assertEquals(-17.70, netPrice, 0.01);

        // Step 5: Set current price and check alert
        double absPrice = Math.abs(netPrice);
        trade.setCurrentPrice(absPrice);
        monitor.updatePrice(monitorId, absPrice);

        PriceMonitor.MonitoredOrder monOrder = monitor.getOrder(monitorId);
        // absPrice=17.70, threshold=+15 (debit) → alert when price <= 15? 17.70 > 15 → no alert
        assertFalse(monOrder.shouldAlert(),
                "Price 17.70 is above threshold=15, should NOT alert for debit trade");

        // Step 6: Price drops to threshold → updatePrice fires alert and sets alertTriggered=true
        monitor.updatePrice(monitorId, 14.0);
        monOrder = monitor.getOrder(monitorId);
        assertTrue(monOrder.alertTriggered,
                "Price 14.0 is below |threshold|=15, alert should have been triggered for debit trade");
    }

    @Test
    @Order(91)
    @DisplayName("10.2 Full flow: Credit trade → SELL action, positive alert")
    void testFullFlowCreditTrade() {
        // Build short strangle
        TradeOrder trade = new TradeOrder("99", "DU4932144");
        trade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "P", "MAIN",
                640, 1, 1, "DU4932144", 1));
        trade.addLeg(new TradeOrder.OrderLeg("SPY", "20260417", "", "SELL", "C", "",
                670, 1, 1, "DU4932144", 2));
        trade.setTargetPrice(5.0);
        trade.setAlertThreshold(8.0);

        assertTrue(trade.isCreditTrade());
        assertEquals("SELL", trade.getDisplayAction());

        // Alert direction: negative for credit (unified convention)
        double signedAlert = trade.isCreditTrade()
                ? -Math.abs(trade.getAlertThreshold())
                : Math.abs(trade.getAlertThreshold());
        assertEquals(-8.0, signedAlert, 0.01);

        // Simulate monitoring
        PriceMonitor monitor = new PriceMonitor(null);
        String monitorId = monitor.registerOrder(trade.getTargetPrice(), signedAlert, "SELL");

        // Price below threshold → no alert
        monitor.updatePrice(monitorId, 7.0);
        PriceMonitor.MonitoredOrder monOrder = monitor.getOrder(monitorId);
        assertFalse(monOrder.shouldAlert());

        // Price rises to threshold → updatePrice fires alert and sets alertTriggered=true
        monitor.updatePrice(monitorId, 8.0);
        monOrder = monitor.getOrder(monitorId);
        assertTrue(monOrder.alertTriggered,
                "Credit trade: price 8.0 >= threshold 8.0, alert should have been triggered");
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private TradeOrder importFirstTrade() {
        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(testExcelFile);
        assertNotNull(result.sheetTrades.get(SHEET_NAME), "TEST sheet should have trades");
        assertFalse(result.sheetTrades.get(SHEET_NAME).isEmpty(), "Should have at least 1 trade");
        return result.sheetTrades.get(SHEET_NAME).get(0);
    }

    /**
     * Replicates SheetTradesPanel.calculateNetComboPrice logic for testing
     */
    private double calculateNetComboPrice(TradeOrder trade, Map<String, Double> legPrices) {
        double net = 0.0;
        int legCount = 0;
        for (TradeOrder.OrderLeg leg : trade.getLegs()) {
            String legKey = trade.getTradeId() + "_" + leg.strike + "_" + leg.optionType;
            Double price = legPrices.get(legKey);
            if (price != null && price > 0) {
                if (leg.action.toUpperCase().contains("SELL")) {
                    net += price * leg.rate;
                } else {
                    net -= price * leg.rate;
                }
                legCount++;
            }
        }
        return legCount == trade.getLegs().size() ? net : 0.0;
    }

    /**
     * Creates a temp Excel with two legs having specified actions and returns the imported trade.
     */
    private TradeOrder importTradeWithAction(String action1, double strike1, String role1,
                                              String action2, double strike2) throws IOException {
        File f = File.createTempFile("ActionTest", ".xlsx");
        f.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(f)) {
            Sheet sheet = wb.createSheet("TEST");
            Row header = sheet.createRow(0);
            for (int i = 0; i <= ExcelOrderImporter.COL_ACTIVE; i++) {
                header.createCell(i).setCellValue(ExcelOrderImporter.EXCEL_HEADERS[i]);
            }
            String netAction1 = action1.toUpperCase().contains("SELL") ? "SELL" : "BUY";

            Row r2 = sheet.createRow(1);
            r2.createCell(ExcelOrderImporter.COL_TRADE_ID).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_ACCOUNT).setCellValue("DU4932144");
            r2.createCell(ExcelOrderImporter.COL_SYMBOL).setCellValue("SPY");
            r2.createCell(ExcelOrderImporter.COL_EXPIRY).setCellValue("20260417");
            r2.createCell(ExcelOrderImporter.COL_NET_ACTION).setCellValue(netAction1);
            r2.createCell(ExcelOrderImporter.COL_ACTION).setCellValue(action1);
            r2.createCell(ExcelOrderImporter.COL_ROLE).setCellValue(role1);
            r2.createCell(ExcelOrderImporter.COL_STRIKE).setCellValue(strike1);
            r2.createCell(ExcelOrderImporter.COL_RATE).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_QTY).setCellValue(1);
            r2.createCell(ExcelOrderImporter.COL_TARGET).setCellValue(10);
            r2.createCell(ExcelOrderImporter.COL_ALERT).setCellValue(15);
            r2.createCell(ExcelOrderImporter.COL_ACTIVE).setCellValue("Y");

            Row r3 = sheet.createRow(2);
            r3.createCell(ExcelOrderImporter.COL_TRADE_ID).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_ACCOUNT).setCellValue("DU4932144");
            r3.createCell(ExcelOrderImporter.COL_SYMBOL).setCellValue("SPY");
            r3.createCell(ExcelOrderImporter.COL_EXPIRY).setCellValue("20260417");
            // Net Action left empty for child leg
            r3.createCell(ExcelOrderImporter.COL_ACTION).setCellValue(action2);
            r3.createCell(ExcelOrderImporter.COL_STRIKE).setCellValue(strike2);
            r3.createCell(ExcelOrderImporter.COL_RATE).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_QTY).setCellValue(1);
            r3.createCell(ExcelOrderImporter.COL_ACTIVE).setCellValue("Y");

            wb.write(fos);
        }

        ExcelOrderImporter.ImportResult result = ExcelOrderImporter.importFromExcel(f);
        f.delete();

        assertTrue(result.success, "Import should succeed, errors: " + result.errors);
        return result.sheetTrades.get("TEST").get(0);
    }
}
