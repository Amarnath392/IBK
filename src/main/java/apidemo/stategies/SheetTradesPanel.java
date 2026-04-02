package apidemo.stategies;

import com.ib.client.*;
import com.ib.controller.ApiController;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SheetTradesPanel extends JPanel implements PriceMonitor.PriceAlertListener {
    
    private final TradingStrategies m_parent;
    private final String sheetName;
    private final PriceMonitor priceMonitor;
    private final List<TradeOrder> tradeOrders = new ArrayList<>();
    private final Set<String> tradeIdSet = new HashSet<>();
    private DefaultTableModel tableModel;
    private JTable configTable;
    private JLabel statusLabel;
    private javax.swing.Timer marketPriceUpdateTimer;
    private final Map<String, ApiController.ITopMktDataHandler> marketDataHandlers = new HashMap<>();
    private final Map<String, Map<String, Double>> comboLegPrices = new HashMap<>();
    private final Map<String, Long> lastPriceUpdateTime = new HashMap<>();  // Track staleness
    
    private static final int PAGE_SIZE = 10;
    private int currentPage = 0;
    private int timerTickCount = 0;  // Track timer invocations for periodic re-subscription
    private JLabel pageLabel;
    private static final long STALE_DATA_THRESHOLD_MS = 60000;  // 60 seconds
    
    private static final String[] COLUMN_NAMES = {
        "Select", "Trade ID", "Account", "Symbols", "Expiry", "Action", "Strike", "Rate", "QTY", "Target $", "Alert $", "Market $", "Status"
    };
    
    private static final int COL_SELECT = 0, COL_TRADE_ID = 1, COL_ACCOUNT = 2, COL_SYMBOLS = 3,
        COL_EXPIRY = 4, COL_ACTION = 5, COL_STRIKE = 6, COL_RATE = 7, COL_QTY = 8, COL_TARGET = 9,
        COL_ALERT = 10, COL_MARKET = 11, COL_STATUS = 12;
    
    public SheetTradesPanel(TradingStrategies parent, String sheetName) {
        this.m_parent = parent;
        this.sheetName = sheetName;
        this.priceMonitor = new PriceMonitor(parent.controller());
        this.priceMonitor.addAlertListener(this);
        
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        marketPriceUpdateTimer = new javax.swing.Timer(20000, e -> updateMarketPrices());
        
        add(createConfigPanel(), BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createButtonPanel(), BorderLayout.CENTER);
        statusLabel = new JLabel("Ready — " + sheetName);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 11));
        statusLabel.setForeground(new Color(33, 150, 243));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    // --- Public API for reimport ---
    
    public int addTrades(List<TradeOrder> newTrades) {
        int added = 0;
        for (TradeOrder trade : newTrades) {
            if (!tradeIdSet.contains(trade.getTradeId())) {
                tradeOrders.add(trade);
                tradeIdSet.add(trade.getTradeId());
                added++;
                // Debug: log parsed trade details
                System.out.println("IMPORT Trade " + trade.getTradeId() + ": " + 
                    trade.getDisplaySymbols() + " | isCombo=" + trade.isComboOrder() +
                    " | isCredit=" + trade.isCreditTrade() + " | display=" + trade.getDisplayAction() +
                    " | target=" + trade.getTargetPrice() + " | alert=" + trade.getAlertThreshold());
                for (TradeOrder.OrderLeg leg : trade.getLegs()) {
                    System.out.println("  Leg: " + leg.optionType + " " + leg.action + 
                        " strike=" + leg.strike + " rate=" + leg.rate + " role=" + leg.role);
                }
            }
        }
        if (added > 0) {
            // Sort by Trade ID to preserve original order after reimport
            tradeOrders.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a.getTradeId()), Integer.parseInt(b.getTradeId()));
                } catch (NumberFormatException e) {
                    return a.getTradeId().compareTo(b.getTradeId());
                }
            });
            goToPage(0);
            statusLabel.setText(String.format("Added %d new trades to %s (total: %d)", added, sheetName, tradeOrders.size()));
            statusLabel.setForeground(new Color(0, 128, 0));
            if (!marketPriceUpdateTimer.isRunning()) {
                marketPriceUpdateTimer.start();
            }
        }
        return added;
    }
    
    public Set<String> getExistingTradeIds() {
        return new HashSet<>(tradeIdSet);
    }
    
    public String getSheetName() {
        return sheetName;
    }
    
    // --- UI Creation ---
    
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case COL_SELECT: return Boolean.class;
                    case COL_TARGET: case COL_ALERT: case COL_MARKET: return Double.class;
                    default: return String.class;
                }
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_SELECT || column == COL_TARGET || column == COL_ALERT;
            }
        };
        
        configTable = new JTable(tableModel) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                if (column == COL_SELECT) return new BooleanRenderer();
                if (column == COL_TARGET || column == COL_ALERT) return new EditableNumberRenderer();
                if (column == COL_MARKET) return new MarketPriceRenderer();
                if (column == COL_STATUS) return new StatusRenderer();
                return new DefaultRenderer();
            }
            @Override
            public TableCellEditor getCellEditor(int row, int column) {
                if (column == COL_TARGET || column == COL_ALERT) return new NumberEditor();
                return super.getCellEditor(row, column);
            }
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (col == COL_ACTION && row >= 0) {
                    int tradeIdx = toTradeIndex(row);
                    if (tradeIdx < tradeOrders.size()) {
                        return tradeOrders.get(tradeIdx).getDetailedAction();
                    }
                }
                return super.getToolTipText(e);
            }
        };
        
        configTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        configTable.setRowHeight(36);  // Taller rows for better readability
        configTable.setGridColor(new Color(230, 230, 230));
        configTable.setShowGrid(true);
        configTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        configTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        configTable.getTableHeader().setBackground(new Color(240, 243, 247));
        configTable.getTableHeader().setForeground(new Color(33, 33, 33));
        configTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        configTable.setIntercellSpacing(new Dimension(8, 4));
        
        // Optimized column widths for 1600px resolution
        int[] widths = {60, 80, 100, 140, 100, 100, 90, 60, 60, 90, 90, 90, 180};
        for (int i = 0; i < widths.length; i++) {
            configTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        
        JScrollPane scrollPane = new JScrollPane(configTable);
        scrollPane.setPreferredSize(new Dimension(1500, 520));  // Larger for 1600x900 resolution
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(250, 250, 250));
        
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        topRow.setBackground(new Color(250, 250, 250));
        topRow.add(createStyledButton("☑ Select All", new Color(96, 125, 139), e -> selectAll()));
        topRow.add(createStyledButton("☐ Deselect All", new Color(158, 158, 158), e -> deselectAll()));
        
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        bottomRow.setBackground(new Color(250, 250, 250));
        bottomRow.add(createStyledButton("▶ Start Monitoring", new Color(76, 175, 80), e -> startMonitoringSelected()));
        bottomRow.add(createStyledButton("⏹ Stop Monitoring", new Color(244, 67, 54), e -> stopMonitoringSelected()));
        bottomRow.add(createStyledButton("💼 Place Order", new Color(63, 81, 181), e -> placeOrderSelected()));
        bottomRow.add(createStyledButton("🗑 Remove Selected", new Color(233, 30, 99), e -> removeSelected()));
        
        JPanel pageRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        pageRow.setBackground(new Color(250, 250, 250));
        JButton prevBtn = createStyledButton("◀ Previous", new Color(117, 117, 117), e -> goToPage(currentPage - 1));
        pageLabel = new JLabel("Page 1 / 1  (0 trades)");
        pageLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pageLabel.setForeground(new Color(60, 60, 60));
        JButton nextBtn = createStyledButton("Next ▶", new Color(117, 117, 117), e -> goToPage(currentPage + 1));
        pageRow.add(prevBtn);
        pageRow.add(Box.createHorizontalStrut(15));
        pageRow.add(pageLabel);
        pageRow.add(Box.createHorizontalStrut(15));
        pageRow.add(nextBtn);
        
        panel.add(topRow);
        panel.add(Box.createVerticalStrut(5));
        panel.add(bottomRow);
        panel.add(Box.createVerticalStrut(5));
        panel.add(pageRow);
        return panel;
    }
    
    private JButton createStyledButton(String text, Color bg, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(190, 38));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker(), 1),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { 
                button.setBackground(bg.brighter()); 
            }
            public void mouseExited(java.awt.event.MouseEvent e) { 
                button.setBackground(bg); 
            }
            public void mousePressed(java.awt.event.MouseEvent e) { 
                button.setBackground(bg.darker()); 
            }
        });
        button.addActionListener(action);
        return button;
    }
    
    // --- Table Operations ---
    
    private void refreshTable() {
        tableModel.setRowCount(0);
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, tradeOrders.size());
        for (int i = start; i < end; i++) {
            TradeOrder trade = tradeOrders.get(i);
            // Sign target/alert: negative for credit, positive for debit
            double signedTarget = trade.isCreditTrade() ? -Math.abs(trade.getTargetPrice()) : Math.abs(trade.getTargetPrice());
            double signedAlert = trade.isCreditTrade() ? -Math.abs(trade.getAlertThreshold()) : Math.abs(trade.getAlertThreshold());
            tableModel.addRow(new Object[]{
                Boolean.FALSE,
                trade.getTradeId(),
                trade.getAccount(),
                trade.getDisplaySymbols(),
                formatExpiry(trade.getDisplayExpiry()),
                trade.getDisplayAction(),
                trade.isComboOrder() ? "Combo" : String.format("%.2f", trade.getMainLeg().strike),
                trade.isComboOrder() ? "Combo" : trade.getMainLeg().rate,
                trade.getTotalQuantity(),
                signedTarget,
                signedAlert,
                trade.getCurrentPrice() > 0 ? trade.getCurrentPrice() : 0.0,
                getStatusText(trade)
            });
        }
        pageLabel.setText(String.format("Page %d / %d  (%d trades)", currentPage + 1, totalPages(), tradeOrders.size()));
    }
    
    private int totalPages() { return Math.max(1, (tradeOrders.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    private int toTableRow(int tradeIndex) {
        int row = tradeIndex - currentPage * PAGE_SIZE;
        return (row >= 0 && row < tableModel.getRowCount()) ? row : -1;
    }
    private int toTradeIndex(int tableRow) { return currentPage * PAGE_SIZE + tableRow; }
    
    private void goToPage(int page) {
        currentPage = Math.max(0, Math.min(page, totalPages() - 1));
        cancelAllMarketData();
        refreshTable();
        updateMarketPrices();
        if (!tradeOrders.isEmpty() && !marketPriceUpdateTimer.isRunning()) {
            marketPriceUpdateTimer.start();
        }
    }
    
    private void updateStatusInTable(int tradeIndex, String status) {
        int row = toTableRow(tradeIndex);
        if (row >= 0) {
            tableModel.setValueAt(status, row, COL_STATUS);
        }
    }
    
    private String getStatusText(TradeOrder trade) {
        if (!trade.isActive()) return "Inactive";
        switch (trade.getStatus()) {
            case READY: return "Ready";
            case MONITORING: return "🟢 Monitoring...";
            case ALERTED: return "⚠️ ALERT - Placing Order";
            case PLACED: return "✅ Order Placed";
            case ERROR: return "❌ Error: " + (trade.getErrorMessage() != null ? trade.getErrorMessage() : "Unknown");
            case INACTIVE: return "Inactive";
            default: return trade.getStatus().toString();
        }
    }
    
    private String formatExpiry(String expiry) {
        if (expiry == null || expiry.length() != 8) return expiry != null ? expiry : "";
        try {
            int year = Integer.parseInt(expiry.substring(0, 4));
            int month = Integer.parseInt(expiry.substring(4, 6));
            int day = Integer.parseInt(expiry.substring(6, 8));
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            return String.format("%02d-%s-%02d", day, months[month-1], year % 100);
        } catch (Exception e) { return expiry; }
    }
    
    private List<Integer> getSelectedRows() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean sel = (Boolean) tableModel.getValueAt(i, COL_SELECT);
            if (sel != null && sel) indices.add(toTradeIndex(i));
        }
        return indices;
    }
    
    private void updateTradeFromTable(int tradeIndex, TradeOrder trade) {
        int row = toTableRow(tradeIndex);
        if (row < 0) return;
        Object targetObj = tableModel.getValueAt(row, COL_TARGET);
        Object alertObj = tableModel.getValueAt(row, COL_ALERT);
        if (targetObj != null) {
            double v = parsePrice(targetObj);
            // Store absolute value internally; sign is only for display
            if (v != 0) trade.setTargetPrice(Math.abs(v));
        }
        if (alertObj != null) {
            double v = parsePrice(alertObj);
            trade.setAlertThreshold(Math.abs(v));
        }
    }
    
    private double parsePrice(Object value) {
        if (value instanceof Double) return (Double) value;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).replace("+", "")); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }
    
    // --- Button Actions ---
    
    private void selectAll() {
        for (int i = 0; i < tableModel.getRowCount(); i++) tableModel.setValueAt(Boolean.TRUE, i, COL_SELECT);
        statusLabel.setText("All trades selected");
        statusLabel.setForeground(new Color(33, 150, 243));
    }
    
    private void deselectAll() {
        for (int i = 0; i < tableModel.getRowCount(); i++) tableModel.setValueAt(Boolean.FALSE, i, COL_SELECT);
        statusLabel.setText("All trades deselected");
        statusLabel.setForeground(new Color(33, 150, 243));
    }
    
    private void startMonitoringSelected() {
        List<Integer> selected = getSelectedRows();
        if (selected.isEmpty()) {
            statusLabel.setText("No trades selected for monitoring");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        int started = 0;
        for (int row : selected) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.isActive() && trade.getStatus() == TradeOrder.OrderStatus.READY) {
                startMonitoringTrade(trade, row);
                started++;
            }
        }
        if (started > 0) {
            statusLabel.setText(String.format("Started monitoring %d trades in %s", started, sheetName));
            statusLabel.setForeground(new Color(76, 175, 80));
            // Re-subscribe to market data to ensure fresh subscriptions
            cancelAllMarketData();
            updateMarketPrices();
            if (!marketPriceUpdateTimer.isRunning()) marketPriceUpdateTimer.start();
        } else {
            statusLabel.setText("No active/ready trades found in selection");
            statusLabel.setForeground(Color.ORANGE);
        }
    }
    
    private void stopMonitoringSelected() {
        List<Integer> selected = getSelectedRows();
        if (selected.isEmpty()) {
            statusLabel.setText("No trades selected");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        int stopped = 0;
        for (int row : selected) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
                priceMonitor.stopMonitoring(trade.getMonitoringId());
                trade.setStatus(TradeOrder.OrderStatus.READY);
                trade.setMonitoringId(null);
                updateStatusInTable(row, getStatusText(trade));
                stopped++;
            }
        }
        statusLabel.setText(String.format("Stopped monitoring %d trades", stopped));
        statusLabel.setForeground(new Color(244, 67, 54));
        
        // Keep timer running for market price display even when not monitoring
    }
    
    private void placeOrderSelected() {
        List<Integer> selected = getSelectedRows();
        if (selected.isEmpty()) {
            statusLabel.setText("No trades selected for order placement");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        // Validate accounts before confirming order placement
        List<String> invalidAccounts = new ArrayList<>();
        List<String> availableAccounts = m_parent.getAccountList();
        for (int row : selected) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.isActive() && !trade.getAccount().trim().isEmpty()) {
                if (!availableAccounts.contains(trade.getAccount())) {
                    invalidAccounts.add(trade.getTradeId() + ": " + trade.getAccount());
                }
            }
        }
        
        if (!invalidAccounts.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Invalid accounts found in selected trades:\n" + 
                String.join("\n", invalidAccounts) + 
                "\n\nAvailable accounts: " + String.join(", ", availableAccounts) +
                "\n\nPlease correct account names in Excel and re-import.",
                "Account Validation Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // P2: Build order preview
        StringBuilder preview = new StringBuilder();
        preview.append("<html><body style='width: 500px; font-family: Segoe UI;'>");
        preview.append("<h3>Order Preview - ").append(selected.size()).append(" Trade(s)</h3>");
        preview.append("<table border='1' cellpadding='4' style='border-collapse: collapse; font-size: 11px;'>");
        preview.append("<tr style='background: #f0f0f0;'><th>Trade</th><th>Type</th><th>Action</th><th>Symbol</th><th>Strike</th><th>Qty</th><th>Limit $</th><th>Account</th></tr>");
        
        for (int row : selected) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.isActive()) {
                updateTradeFromTable(row, trade);
                String type = trade.isComboOrder() ? "<b>COMBO</b>" : "Single";
                String action = trade.getDisplayAction();
                String symbols = trade.getDisplaySymbols();
                String strike = trade.isComboOrder() ? "Multi" : String.format("%.2f", trade.getMainLeg().strike);
                int qty = trade.getTotalQuantity();
                double limit = trade.getTargetPrice();
                String acct = trade.getAccount().isEmpty() ? "<i>default</i>" : trade.getAccount();
                String rowColor = trade.isCreditTrade() ? "#e8f5e9" : "#fff3e0";
                preview.append("<tr style='background: ").append(rowColor).append("'>");
                preview.append("<td>").append(trade.getTradeId()).append("</td>");
                preview.append("<td>").append(type).append("</td>");
                preview.append("<td><b>").append(action).append("</b></td>");
                preview.append("<td>").append(symbols).append("</td>");
                preview.append("<td>").append(strike).append("</td>");
                preview.append("<td>").append(qty).append("</td>");
                preview.append("<td><b>$").append(String.format("%.2f", limit)).append("</b></td>");
                preview.append("<td>").append(acct).append("</td>");
                preview.append("</tr>");
            }
        }
        preview.append("</table><br>");
        preview.append("<p style='color: #d32f2f;'><b>⚠️ WARNING:</b> Orders will be submitted to TWS immediately.</p>");
        preview.append("<p>Proceed with order placement?</p>");
        preview.append("</body></html>");
        
        int confirm = JOptionPane.showConfirmDialog(this, preview.toString(),
            "⚡ Confirm Order Placement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        
        int placed = 0;
        for (int row : selected) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.isActive()) {
                placeOrderManually(trade, row);
                placed++;
            }
        }
        statusLabel.setText(String.format("Placing orders for %d trades", placed));
        statusLabel.setForeground(new Color(63, 81, 181));
    }
    
    private void removeSelected() {
        List<Integer> selected = getSelectedRows();
        if (selected.isEmpty()) {
            statusLabel.setText("No trades selected for removal");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Remove %d selected trades from %s?", selected.size(), sheetName),
            "Remove Trades", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        
        selected.sort((a, b) -> b.compareTo(a));
        int removed = 0;
        for (int tradeIdx : selected) {
            TradeOrder trade = tradeOrders.get(tradeIdx);
            if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
                priceMonitor.stopMonitoring(trade.getMonitoringId());
            }
            tradeIdSet.remove(trade.getTradeId());
            tradeOrders.remove(tradeIdx);
            removed++;
        }
        cancelAllMarketData();
        if (currentPage >= totalPages()) currentPage = Math.max(0, totalPages() - 1);
        refreshTable();
        
        statusLabel.setText(String.format("Removed %d trades from %s", removed, sheetName));
        statusLabel.setForeground(new Color(233, 30, 99));
    }
    
    // --- Monitoring ---
    
    private void startMonitoringTrade(TradeOrder trade, int rowIndex) {
        System.out.println("START MONITORING: Trade " + trade.getTradeId() + 
            " currentStatus=" + trade.getStatus() + " isCredit=" + trade.isCreditTrade());
        
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        if (mainLeg == null) {
            statusLabel.setText("Trade " + trade.getTradeId() + " has no main leg");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        updateTradeFromTable(rowIndex, trade);
        trade.setStatus(TradeOrder.OrderStatus.MONITORING);
        updateStatusInTable(rowIndex, getStatusText(trade));
        
        // Unified sign: positive = debit (alert when price drops to/below),
        //               negative = credit (alert when price rises to/above)
        double signedAlert = trade.isCreditTrade()
            ? -Math.abs(trade.getAlertThreshold())
            : Math.abs(trade.getAlertThreshold());
        
        System.out.println("  Alert setup: target=" + trade.getTargetPrice() + 
            " signedAlert=" + signedAlert + " action=" + mainLeg.action);
        
        if (trade.isComboOrder()) {
            // Combo: register without market data subscription; net combo price fed via syncMonitorPrice
            String actualId = priceMonitor.registerOrder(
                trade.getTargetPrice(), signedAlert, mainLeg.action);
            trade.setMonitoringId(actualId);
            System.out.println("  Registered combo: monitorId=" + actualId + " NOW status=" + trade.getStatus());
        } else {
            // Single leg: subscribe to main leg's market data for alert
            Contract contract = createContractFromLeg(mainLeg);
            String monitoringId = trade.getTradeId() + "_" + System.currentTimeMillis();
            trade.setMonitoringId(monitoringId);
            m_parent.controller().reqContractDetails(contract, contractDetailsList -> {
                if (!contractDetailsList.isEmpty()) {
                    Contract validated = contractDetailsList.get(0).contract();
                    String actualId = priceMonitor.startMonitoring(
                        validated, trade.getTargetPrice(), signedAlert, mainLeg.action);
                    trade.setMonitoringId(actualId);
                }
            });
        }
    }
    
    private void updateMarketPrices() {
        timerTickCount++;
        // Periodic re-subscription every 3 minutes (9 ticks × 20s = 180s) to handle IB cancellations
        boolean forceResubscribe = (timerTickCount % 9 == 0);
        
        System.out.println("TIMER FIRED: updateMarketPrices() called | page=" + currentPage + 
            " total=" + tradeOrders.size() + " timerRunning=" + marketPriceUpdateTimer.isRunning() +
            " tick=" + timerTickCount + " forceResub=" + forceResubscribe);
        
        if (forceResubscribe) {
            System.out.println("PERIODIC REFRESH: Clearing and re-subscribing all market data");
            cancelAllMarketData();
        }
        
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, tradeOrders.size());
        System.out.println("  Processing trades " + start + " to " + end);
        for (int i = start; i < end; i++) {
            TradeOrder trade = tradeOrders.get(i);
            if (trade.isActive() && trade.getMainLeg() != null) {
                requestMarketPrice(trade, i);
            }
        }
    }
    
    private void requestMarketPrice(TradeOrder trade, int tradeIndex) {
        System.out.println("TIMER TICK: requestMarketPrice for Trade " + trade.getTradeId() + 
            " isCombo=" + trade.isComboOrder() + " status=" + trade.getStatus());
        if (trade.isComboOrder()) {
            requestComboMarketPrice(trade, tradeIndex);
        } else {
            requestSingleLegMarketPrice(trade, tradeIndex);
        }
    }
    
    private void requestSingleLegMarketPrice(TradeOrder trade, int tradeIndex) {
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        if (mainLeg == null) return;
        
        String key = trade.getTradeId();
        // Already subscribed — skip to avoid redundant re-subscriptions that cause flicker
        if (marketDataHandlers.containsKey(key)) return;
        
        Contract contract = createContractFromLeg(mainLeg);
        
        ApiController.ITopMktDataHandler handler = new ApiController.ITopMktDataHandler() {
            private double lastPrice = 0, bidPrice = 0, askPrice = 0;
            
            @Override public void tickPrice(TickType tickType, double price, TickAttrib attribs) {
                switch (tickType) {
                    case LAST: lastPrice = price; break;
                    case BID: bidPrice = price; break;
                    case ASK: askPrice = price; break;
                    default: return;
                }
                double display = lastPrice > 0 ? lastPrice :
                    (bidPrice > 0 && askPrice > 0) ? (bidPrice + askPrice) / 2 :
                    bidPrice > 0 ? bidPrice : askPrice;
                if (display > 0) {
                    SwingUtilities.invokeLater(() -> {
                        trade.setCurrentPrice(display);
                        syncMonitorPrice(trade, display);
                        int tableRow = toTableRow(tradeIndex);
                        if (tableRow >= 0) {
                            tableModel.setValueAt(display, tableRow, COL_MARKET);
                        }
                    });
                }
            }
            @Override public void tickSize(TickType tickType, Decimal size) {}
            @Override public void tickString(TickType tickType, String value) {}
            @Override public void tickSnapshotEnd() {}
            @Override public void marketDataType(int marketDataType) {}
            @Override public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {}
        };
        
        marketDataHandlers.put(key, handler);
        m_parent.controller().reqTopMktData(contract, "", false, false, handler);
    }
    
    private void requestComboMarketPrice(TradeOrder trade, int tradeIndex) {
        String tradeId = trade.getTradeId();
        // Only subscribe once; subsequent timer ticks reuse existing handlers
        String firstLegKey = tradeId + "_" + trade.getLegs().get(0).strike + "_" + trade.getLegs().get(0).optionType;
        if (marketDataHandlers.containsKey(firstLegKey)) {
            System.out.println("MARKET DATA: Trade " + tradeId + " already subscribed, skipping");
            return;
        }
        
        System.out.println("MARKET DATA: Subscribing combo legs for Trade " + tradeId + " (" + trade.getLegs().size() + " legs)");
        comboLegPrices.put(tradeId, new HashMap<>());
        
        for (TradeOrder.OrderLeg leg : trade.getLegs()) {
            String legKey = tradeId + "_" + leg.strike + "_" + leg.optionType;
            Contract contract = createContractFromLeg(leg);
            
            ApiController.ITopMktDataHandler handler = new ApiController.ITopMktDataHandler() {
                private double lastPrice = 0, bidPrice = 0, askPrice = 0;
                
                @Override public void tickPrice(TickType tickType, double price, TickAttrib attribs) {
                    System.out.println("TICK PRICE: Trade " + tradeId + " leg=" + legKey + 
                        " type=" + tickType + " price=" + price);
                    switch (tickType) {
                        case LAST: lastPrice = price; break;
                        case BID: bidPrice = price; break;
                        case ASK: askPrice = price; break;
                        default: return;
                    }
                    // P1: Use bid for SELL orders, ask for BUY orders (more accurate than last/mid)
                    boolean isSellLeg = leg.action.equalsIgnoreCase("SELL");
                    double display = isSellLeg ? 
                        (bidPrice > 0 ? bidPrice : (askPrice > 0 ? askPrice : lastPrice)) :
                        (askPrice > 0 ? askPrice : (bidPrice > 0 ? bidPrice : lastPrice));
                    
                    if (display > 0) {
                        SwingUtilities.invokeLater(() -> {
                            Map<String, Double> legPrices = comboLegPrices.get(tradeId);
                            if (legPrices != null) {
                                legPrices.put(legKey, display);
                                lastPriceUpdateTime.put(tradeId, System.currentTimeMillis());
                                
                                double netPrice = calculateNetComboPrice(trade, legPrices);
                                if (netPrice != 0) {
                                    long age = System.currentTimeMillis() - lastPriceUpdateTime.getOrDefault(tradeId, 0L);
                                    String staleWarning = age > STALE_DATA_THRESHOLD_MS ? " ⚠️ STALE(" + (age/1000) + "s)" : "";
                                    System.out.println("NET PRICE: Trade " + trade.getTradeId() + 
                                        " net=" + netPrice + " status=" + trade.getStatus() + 
                                        " monitorId=" + trade.getMonitoringId() + staleWarning);
                                    trade.setCurrentPrice(Math.abs(netPrice));
                                    syncMonitorPrice(trade, Math.abs(netPrice));
                                    int tableRow = toTableRow(tradeIndex);
                                    if (tableRow >= 0) {
                                        // Display signed net: positive=debit, negative=credit
                                        double displayNet = trade.isCreditTrade() ? -Math.abs(netPrice) : Math.abs(netPrice);
                                        tableModel.setValueAt(displayNet, tableRow, COL_MARKET);
                                    }
                                }
                            }
                        });
                    }
                }
                @Override public void tickSize(TickType tickType, Decimal size) {}
                @Override public void tickString(TickType tickType, String value) {}
                @Override public void tickSnapshotEnd() {}
                @Override public void marketDataType(int marketDataType) {}
                @Override public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {}
            };
            
            marketDataHandlers.put(legKey, handler);
            m_parent.controller().reqTopMktData(contract, "", false, false, handler);
        }
    }
    
    private double calculateNetComboPrice(TradeOrder trade, Map<String, Double> legPrices) {
        double net = 0.0;
        int legCount = 0;
        List<String> missingLegs = new ArrayList<>();
        
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
            } else {
                missingLegs.add(String.format("%s %.2f%s", leg.symbol, leg.strike, leg.optionType));
            }
        }
        
        // CRITICAL: If ANY leg price missing, return 0 (don't calculate incomplete combo price)
        if (legCount < trade.getLegs().size()) {
            if (!missingLegs.isEmpty()) {
                System.out.println("⚠️  INCOMPLETE PRICE: Trade " + trade.getTradeId() + 
                    " - Missing prices for: " + String.join(", ", missingLegs) + 
                    " (" + legCount + "/" + trade.getLegs().size() + " legs have prices)");
            }
            return 0.0;
        }
        
        return net;
    }
    
    private void syncMonitorPrice(TradeOrder trade, double price) {
        System.out.println("SYNC CALLED: Trade " + trade.getTradeId() + 
            " price=" + price + " status=" + trade.getStatus() + 
            " hasMonitorId=" + (trade.getMonitoringId() != null));
        if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
            System.out.println("MONITOR UPDATE: Trade " + trade.getTradeId() + 
                " price=" + price + " isCredit=" + trade.isCreditTrade());
            // For combo orders, updatePrice also checks alert condition
            priceMonitor.updatePrice(trade.getMonitoringId(), price);
        }
    }
    
    // --- Order Placement ---
    
    private void placeOrderManually(TradeOrder trade, int rowIndex) {
        if (trade.isComboOrder()) placeComboOrder(trade, rowIndex);
        else placeSingleLegOrder(trade, rowIndex);
        trade.setStatus(TradeOrder.OrderStatus.PLACED);
        updateStatusInTable(rowIndex, "Placing Manual Order...");
    }
    
    private void placeOrderInTWS(TradeOrder trade, int rowIndex) {
        if (trade.isComboOrder()) placeComboOrder(trade, rowIndex);
        else placeSingleLegOrder(trade, rowIndex);
    }
    
    private void placeSingleLegOrder(TradeOrder trade, int rowIndex) {
        TradeOrder.OrderLeg leg = trade.getMainLeg();
        if (leg == null) {
            SwingUtilities.invokeLater(() -> {
                trade.setStatus(TradeOrder.OrderStatus.ERROR);
                trade.setErrorMessage("No leg found");
                updateStatusInTable(rowIndex, "Error: No leg");
            });
            return;
        }
        
        Contract contract = createContractFromLeg(leg);
        Order twsOrder = new Order();
        twsOrder.action(leg.action);
        twsOrder.totalQuantity(Decimal.get(leg.getTotalQuantity()));
        twsOrder.orderType("LMT");
        twsOrder.lmtPrice(trade.getTargetPrice());
        twsOrder.tif("GTC");
        twsOrder.outsideRth(false);
        if (!trade.getAccount().trim().isEmpty()) twsOrder.account(trade.getAccount());
        
        placeOrder(trade, contract, twsOrder, rowIndex);
    }
    
    private void placeComboOrder(TradeOrder trade, int rowIndex) {
        // Set timeout for entire validation chain (5 seconds per leg)
        int timeoutMs = trade.getLegs().size() * 5000;
        javax.swing.Timer validationTimeout = new javax.swing.Timer(timeoutMs, e -> {
            SwingUtilities.invokeLater(() -> {
                if (trade.getStatus() != TradeOrder.OrderStatus.PLACED) {
                    trade.setStatus(TradeOrder.OrderStatus.ERROR);
                    trade.setErrorMessage("Contract validation timeout - check IB connection");
                    updateStatusInTable(rowIndex, "❌ Validation Timeout");
                    JOptionPane.showMessageDialog(this, 
                        "Failed to validate combo legs for Trade " + trade.getTradeId() + 
                        "\nCheck IB connection and contract details.",
                        "Order Validation Failed", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        validationTimeout.setRepeats(false);
        validationTimeout.start();
        
        validateComboLegs(trade, rowIndex, 0, new ArrayList<>(), validationTimeout);
    }
    
    private void validateComboLegs(TradeOrder trade, int rowIndex, int legIndex, 
                                   List<Contract> validated, javax.swing.Timer timeout) {
        if (legIndex >= trade.getLegs().size()) {
            timeout.stop();  // Cancel timeout - validation successful
            createAndPlaceBagOrder(trade, rowIndex, validated);
            return;
        }
        
        TradeOrder.OrderLeg leg = trade.getLegs().get(legIndex);
        Contract legContract = createContractFromLeg(leg);
        
        System.out.println("VALIDATING LEG " + (legIndex + 1) + "/" + trade.getLegs().size() + 
            ": " + leg.symbol + " " + leg.strike + leg.optionType + " " + leg.expiry);
        
        m_parent.controller().reqContractDetails(legContract, list -> {
            if (list.isEmpty()) {
                timeout.stop();
                SwingUtilities.invokeLater(() -> {
                    trade.setStatus(TradeOrder.OrderStatus.ERROR);
                    String errorMsg = String.format("Invalid contract: %s %.2f%s %s", 
                        leg.symbol, leg.strike, leg.optionType, leg.expiry);
                    trade.setErrorMessage(errorMsg);
                    updateStatusInTable(rowIndex, "❌ " + errorMsg);
                    JOptionPane.showMessageDialog(this, 
                        "Trade " + trade.getTradeId() + ": " + errorMsg + 
                        "\nCheck symbol, strike, expiry date format.",
                        "Contract Validation Failed", JOptionPane.ERROR_MESSAGE);
                });
                return;
            }
            validated.add(list.get(0).contract());
            validateComboLegs(trade, rowIndex, legIndex + 1, validated, timeout);
        });
    }
    
    private void createAndPlaceBagOrder(TradeOrder trade, int rowIndex, List<Contract> validatedContracts) {
        Contract bag = new Contract();
        bag.symbol(trade.getLegs().get(0).symbol);
        bag.secType("BAG");
        bag.exchange("SMART");
        bag.currency("USD");
        
        List<ComboLeg> comboLegs = new ArrayList<>();
        for (int i = 0; i < trade.getLegs().size(); i++) {
            TradeOrder.OrderLeg leg = trade.getLegs().get(i);
            ComboLeg cl = new ComboLeg();
            cl.conid(validatedContracts.get(i).conid());
            cl.ratio(leg.rate);
            cl.action(leg.action);
            cl.exchange("SMART");
            comboLegs.add(cl);
        }
        bag.comboLegs(comboLegs);
        
        // For combo/BAG orders:
        //  Always Order.action = "BUY" so ComboLeg actions are used as-is (not reversed).
        //  IB limit price convention with BUY action:
        //    Positive lmtPrice = max debit willing to pay
        //    Negative lmtPrice = min credit wanting to receive
        boolean isCredit = trade.isCreditTrade();
        double lmtPrice = isCredit ? -trade.getTargetPrice() : trade.getTargetPrice();
        
        Order twsOrder = new Order();
        twsOrder.action("BUY");
        twsOrder.totalQuantity(Decimal.get(trade.getTotalQuantity()));
        twsOrder.orderType("LMT");
        twsOrder.lmtPrice(lmtPrice);
        twsOrder.tif("GTC");
        twsOrder.outsideRth(false);
        if (!trade.getAccount().trim().isEmpty()) twsOrder.account(trade.getAccount());
        
        // NonGuaranteed param only valid for 2-leg combos per IB API restriction
        if (trade.getLegs().size() == 2) {
            List<TagValue> smartParams = new ArrayList<>();
            smartParams.add(new TagValue("NonGuaranteed", "1"));
            twsOrder.smartComboRoutingParams(smartParams);
        }
        
        System.out.println("📦 COMBO ORDER: Trade " + trade.getTradeId() + 
            " | Quantity=" + trade.getTotalQuantity() + 
            " | LmtPrice=" + lmtPrice + " (isCredit=" + isCredit + ")" +
            " | Account=" + trade.getAccount());
        for (int i = 0; i < trade.getLegs().size(); i++) {
            TradeOrder.OrderLeg leg = trade.getLegs().get(i);
            System.out.println("  Leg " + (i+1) + ": " + leg.action + " " + 
                leg.symbol + " " + leg.strike + leg.optionType + " ratio=" + leg.rate + 
                " (total contracts: " + trade.getTotalQuantity() + " × " + leg.rate + " = " + 
                (trade.getTotalQuantity() * leg.rate) + ")");
        }
        
        System.out.println("COMBO ORDER: Trade " + trade.getTradeId() + 
            " isCredit=" + isCredit + " action=" + twsOrder.action() + 
            " lmtPrice=" + twsOrder.lmtPrice() + " qty=" + twsOrder.totalQuantity() +
            " legs=" + comboLegs.size());
        for (int i = 0; i < trade.getLegs().size(); i++) {
            TradeOrder.OrderLeg leg = trade.getLegs().get(i);
            System.out.println("  Leg " + (i+1) + ": " + leg.optionType + " " + leg.action + 
                " strike=" + leg.strike + " rate=" + leg.rate + " conid=" + validatedContracts.get(i).conid());
        }
        
        placeOrder(trade, bag, twsOrder, rowIndex);
    }
    
    private void placeOrder(TradeOrder trade, Contract contract, Order twsOrder, int rowIndex) {
        m_parent.controller().placeOrModifyOrder(contract, twsOrder,
            new ApiController.IOrderHandler() {
                @Override public void orderState(OrderState orderState, Order order) {
                    SwingUtilities.invokeLater(() -> {
                        trade.setStatus(TradeOrder.OrderStatus.PLACED);
                        updateStatusInTable(rowIndex, "TWS: " + orderState.getStatus());
                        m_parent.show("[" + sheetName + "] Trade " + trade.getTradeId() + " placed: " + orderState.getStatus());
                    });
                }
                @Override public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining,
                        double avgFillPrice, int permId, int parentId, double lastFillPrice,
                        int clientId, String whyHeld, double mktCapPrice) {
                    SwingUtilities.invokeLater(() -> updateStatusInTable(rowIndex, "TWS: " + status.name()));
                }
                @Override public void handle(int errorCode, String errorMsg) {
                    SwingUtilities.invokeLater(() -> {
                        String err = String.format("Error %d: %s", errorCode, errorMsg);
                        trade.setStatus(TradeOrder.OrderStatus.ERROR);
                        trade.setErrorMessage(err);
                        updateStatusInTable(rowIndex, err);
                        statusLabel.setText(err);
                        statusLabel.setForeground(Color.RED);
                    });
                }
            });
    }
    
    // --- Helpers ---
    
    private void cancelAllMarketData() {
        for (ApiController.ITopMktDataHandler h : marketDataHandlers.values()) {
            m_parent.controller().cancelTopMktData(h);
        }
        marketDataHandlers.clear();
        comboLegPrices.clear();
    }
    
    private Contract createContractFromLeg(TradeOrder.OrderLeg leg) {
        Contract c = new Contract();
        c.symbol(leg.symbol);
        c.secType("OPT");
        c.exchange("SMART");
        c.currency("USD");
        c.lastTradeDateOrContractMonth(leg.expiry);
        c.strike(leg.strike);
        c.right(leg.optionType);
        c.multiplier("100");
        return c;
    }
    
    // --- PriceAlertListener ---
    
    @Override
    public void onPriceAlert(PriceMonitor.MonitoredOrder order, double currentPrice, double distance) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < tradeOrders.size(); i++) {
                TradeOrder trade = tradeOrders.get(i);
                if (trade.getMonitoringId() != null && trade.getMonitoringId().equals(order.id)) {
                    trade.setStatus(TradeOrder.OrderStatus.ALERTED);
                    updateStatusInTable(i, "⚠ ALERT - Placing TWS Order");
                    placeOrderInTWS(trade, i);
                    break;
                }
            }
            statusLabel.setText("🔔 ALERT: " + order.id + " threshold reached! Placing in TWS...");
            statusLabel.setForeground(new Color(255, 69, 0));
            
            PriceAlertDialog.showAlert(
                (Frame) SwingUtilities.getWindowAncestor(this), order, currentPrice, distance);
        });
    }
    
    @Override
    public void onPriceUpdate(PriceMonitor.MonitoredOrder order, double currentPrice) {
        for (TradeOrder trade : tradeOrders) {
            if (trade.getMonitoringId() != null && trade.getMonitoringId().equals(order.id)) {
                trade.setCurrentPrice(currentPrice);
                break;
            }
        }
    }
    
    // --- Cell Renderers ---
    
    private Color rowBg(int row) { return row % 2 == 0 ? new Color(248, 249, 250) : Color.WHITE; }
    
    private class DefaultRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!sel) c.setBackground(rowBg(row));
            return c;
        }
    }
    
    private class EditableNumberRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!sel) {
                c.setBackground(new Color(232, 245, 233));
                setBorder(new LineBorder(new Color(76, 175, 80), 1));
            }
            if (value instanceof Double) {
                double v = (Double) value;
                if (v < 0) {
                    setText(String.format("-%.2f", Math.abs(v)));
                    setForeground(new Color(211, 47, 47));
                } else if (v > 0) {
                    setText(String.format("+%.2f", v));
                    setForeground(new Color(46, 125, 50));
                } else {
                    setText("0.00");
                    setForeground(Color.GRAY);
                }
            }
            return c;
        }
    }
    
    private class MarketPriceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!sel) c.setBackground(new Color(227, 242, 253));
            if (value instanceof Double && ((Double) value) != 0.0) {
                double v = (Double) value;
                if (v < 0) {
                    setText(String.format("-%.2f", Math.abs(v)));
                    setForeground(new Color(211, 47, 47)); // red for credit
                } else {
                    setText(String.format("+%.2f", v));
                    setForeground(new Color(33, 150, 243)); // blue for debit
                }
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setText("--");
                setForeground(Color.GRAY);
            }
            return c;
        }
    }
    
    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            String status = (String) value;
            if (!sel && status != null) {
                if (status.contains("Monitoring")) { c.setBackground(new Color(232, 245, 233)); setForeground(new Color(46, 125, 50)); setFont(getFont().deriveFont(Font.BOLD)); }
                else if (status.contains("ALERT")) { c.setBackground(new Color(255, 243, 224)); setForeground(new Color(255, 111, 0)); setFont(getFont().deriveFont(Font.BOLD)); }
                else if (status.contains("Placed") || status.contains("TWS")) { c.setBackground(new Color(227, 242, 253)); setForeground(new Color(33, 150, 243)); setFont(getFont().deriveFont(Font.BOLD)); }
                else if (status.contains("Error")) { c.setBackground(new Color(255, 235, 238)); setForeground(new Color(211, 47, 47)); setFont(getFont().deriveFont(Font.BOLD)); }
                else if (status.equals("Ready")) { c.setBackground(rowBg(row)); setForeground(new Color(97, 97, 97)); }
                else { c.setBackground(rowBg(row)); setForeground(Color.BLACK); }
            } else if (!sel) { c.setBackground(rowBg(row)); }
            return c;
        }
    }
    
    private class BooleanRenderer extends JCheckBox implements TableCellRenderer {
        BooleanRenderer() { setHorizontalAlignment(JLabel.CENTER); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            if (sel) { setForeground(table.getSelectionForeground()); setBackground(table.getSelectionBackground()); }
            else { setForeground(table.getForeground()); setBackground(rowBg(row)); }
            setSelected(value != null && (Boolean) value);
            return this;
        }
    }
    
    private class NumberEditor extends DefaultCellEditor {
        NumberEditor() {
            super(new JTextField());
            ((JTextField) getComponent()).setHorizontalAlignment(SwingConstants.CENTER);
        }
        @Override public boolean stopCellEditing() {
            try { Double.parseDouble((String) super.getCellEditorValue()); }
            catch (NumberFormatException e) { ((JComponent) getComponent()).setBorder(new LineBorder(Color.red)); return false; }
            return super.stopCellEditing();
        }
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean sel, int row, int col) {
            Component c = super.getTableCellEditorComponent(table, value, sel, row, col);
            ((JComponent) c).setBorder(new LineBorder(new Color(76, 175, 80), 2));
            return c;
        }
    }
}
