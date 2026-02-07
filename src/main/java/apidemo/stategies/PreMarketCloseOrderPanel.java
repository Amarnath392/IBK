package apidemo.stategies;

import apidemo.util.HtmlButton;
import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;
import com.ib.client.*;
import com.ib.controller.ApiController;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JCalendar;
import com.toedter.calendar.JMonthChooser;
import com.toedter.calendar.JTextFieldDateEditor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class PreMarketCloseOrderPanel extends JPanel implements PriceMonitor.PriceAlertListener {
    
    private final TradingStrategies m_parent;
    private final PriceMonitor priceMonitor;
    private DefaultTableModel tableModel;
    private JTable configTable;
    private JLabel statusLabel;
    
    private static final String[] COLUMN_NAMES = {
        "Trade ID", "Account", "Symbols", "Expiry", "Action", "Strike", "QTY", "Target $", "Alert $", "Active", "Status"
    };
    
    private List<TradeOrder> tradeOrders = new ArrayList<>();
    
    public PreMarketCloseOrderPanel(TradingStrategies parent) {
        this.m_parent = parent;
        this.priceMonitor = new PriceMonitor(parent.controller());
        this.priceMonitor.addAlertListener(this);
        
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel topPanel = createTopPanel();
        JPanel configPanel = createConfigPanel();
        JPanel buttonPanel = createButtonPanel();
        
        statusLabel = new JLabel("Ready - Import Excel file to load your trades");
        statusLabel.setForeground(Color.BLUE);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(configPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel titleLabel = new JLabel("Pre-Market Close Order Configuration");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel instructionLabel = new JLabel(
            "<html><div style='text-align: center; padding: 10px;'>" +
            "<b>Import Excel file with Trade IDs and monitor orders:</b><br/>" +
            "1. Click 'Import from Excel' to load your trades<br/>" +
            "2. Toggle Active/Inactive for trades you want to monitor<br/>" +
            "3. Click 'Start Monitoring All' - system handles combo and single orders automatically" +
            "</div></html>"
        );
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(instructionLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Trade Order Monitoring"));
        
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        configTable = new JTable(tableModel);
        configTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configTable.setRowHeight(25);
        
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < configTable.getColumnCount(); i++) {
            configTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        
        configTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // Trade ID
        configTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Account
        configTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Symbols
        configTable.getColumnModel().getColumn(3).setPreferredWidth(90);  // Expiry
        configTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Action
        configTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // Strike
        configTable.getColumnModel().getColumn(6).setPreferredWidth(50);  // QTY
        configTable.getColumnModel().getColumn(7).setPreferredWidth(70);  // Target $
        configTable.getColumnModel().getColumn(8).setPreferredWidth(70);  // Alert $
        configTable.getColumnModel().getColumn(9).setPreferredWidth(60);  // Active
        configTable.getColumnModel().getColumn(10).setPreferredWidth(120); // Status
        
        JScrollPane scrollPane = new JScrollPane(configTable);
        scrollPane.setPreferredSize(new Dimension(900, 250));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        
        HtmlButton importButton = new HtmlButton("Import from Excel") {
            @Override
            protected void actionPerformed() {
                importFromExcel();
            }
        };
        importButton.setBackground(new Color(255, 152, 0));
        importButton.setForeground(Color.WHITE);
        importButton.setFont(new Font("Arial", Font.BOLD, 14));
        importButton.setPreferredSize(new Dimension(200, 35));
        
        HtmlButton toggleActiveButton = new HtmlButton("Toggle Active") {
            @Override
            protected void actionPerformed() {
                toggleSelectedTradeActive();
            }
        };
        toggleActiveButton.setBackground(new Color(156, 39, 176));
        toggleActiveButton.setForeground(Color.WHITE);
        toggleActiveButton.setFont(new Font("Arial", Font.BOLD, 14));
        toggleActiveButton.setPreferredSize(new Dimension(150, 35));
        
        HtmlButton startAllButton = new HtmlButton("Start Monitoring All") {
            @Override
            protected void actionPerformed() {
                startMonitoringAll();
            }
        };
        startAllButton.setBackground(new Color(76, 175, 80));
        startAllButton.setForeground(Color.WHITE);
        startAllButton.setFont(new Font("Arial", Font.BOLD, 14));
        startAllButton.setPreferredSize(new Dimension(200, 35));
        
        HtmlButton stopAllButton = new HtmlButton("Stop All Monitoring") {
            @Override
            protected void actionPerformed() {
                stopAllMonitoring();
            }
        };
        stopAllButton.setBackground(new Color(244, 67, 54));
        stopAllButton.setForeground(Color.WHITE);
        stopAllButton.setFont(new Font("Arial", Font.BOLD, 14));
        stopAllButton.setPreferredSize(new Dimension(200, 35));
        
        HtmlButton removeButton = new HtmlButton("Remove Selected") {
            @Override
            protected void actionPerformed() {
                removeSelectedTrade();
            }
        };
        
        panel.add(importButton);
        panel.add(toggleActiveButton);
        panel.add(startAllButton);
        panel.add(stopAllButton);
        panel.add(removeButton);
        
        return panel;
    }
    
    private void importFromExcel() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Excel File with Trade Orders");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Excel Files (*.xlsx, *.xls)", "xlsx", "xls"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File selectedFile = fileChooser.getSelectedFile();
        statusLabel.setText("Importing from Excel: " + selectedFile.getName());
        statusLabel.setForeground(Color.BLUE);
        
        new SwingWorker<ExcelOrderImporter.ImportResult, Void>() {
            @Override
            protected ExcelOrderImporter.ImportResult doInBackground() {
                return ExcelOrderImporter.importFromExcel(selectedFile);
            }
            
            @Override
            protected void done() {
                try {
                    ExcelOrderImporter.ImportResult importResult = get();
                    
                    if (!importResult.errors.isEmpty()) {
                        StringBuilder errorMsg = new StringBuilder("Import completed with errors:\n\n");
                        for (String error : importResult.errors) {
                            errorMsg.append("• ").append(error).append("\n");
                        }
                        JOptionPane.showMessageDialog(PreMarketCloseOrderPanel.this,
                            errorMsg.toString(), "Import Errors", JOptionPane.WARNING_MESSAGE);
                    }
                    
                    if (!importResult.warnings.isEmpty()) {
                        StringBuilder warningMsg = new StringBuilder("Import completed with warnings:\n\n");
                        for (String warning : importResult.warnings) {
                            warningMsg.append("• ").append(warning).append("\n");
                        }
                        JOptionPane.showMessageDialog(PreMarketCloseOrderPanel.this,
                            warningMsg.toString(), "Import Warnings", JOptionPane.INFORMATION_MESSAGE);
                    }
                    
                    if (importResult.trades.isEmpty()) {
                        statusLabel.setText("No valid trades found in Excel file");
                        statusLabel.setForeground(Color.RED);
                        return;
                    }
                    
                    // Clear existing trades
                    tradeOrders.clear();
                    tableModel.setRowCount(0);
                    
                    // Add imported trades
                    for (TradeOrder trade : importResult.trades) {
                        tradeOrders.add(trade);
                        addTradeToTable(trade);
                    }
                    
                    statusLabel.setText(String.format("Successfully imported %d trades (%d single, %d combo orders)",
                        importResult.trades.size(),
                        (int) importResult.trades.stream().filter(t -> !t.isComboOrder()).count(),
                        (int) importResult.trades.stream().filter(TradeOrder::isComboOrder).count()));
                    statusLabel.setForeground(new Color(0, 128, 0));
                    
                    JOptionPane.showMessageDialog(PreMarketCloseOrderPanel.this,
                        String.format("Successfully imported %d trades!\n\nCombo orders: %d\nSingle orders: %d\n\nYou can now toggle Active/Inactive and start monitoring.",
                            importResult.trades.size(),
                            (int) importResult.trades.stream().filter(TradeOrder::isComboOrder).count(),
                            (int) importResult.trades.stream().filter(t -> !t.isComboOrder()).count()),
                        "Import Successful", JOptionPane.INFORMATION_MESSAGE);
                    
                } catch (Exception e) {
                    statusLabel.setText("Error importing Excel: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(PreMarketCloseOrderPanel.this,
                        "Error importing Excel file: " + e.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    private void addTradeToTable(TradeOrder trade) {
        tableModel.addRow(new Object[]{
            trade.getTradeId(),
            trade.getAccount(),
            trade.getDisplaySymbols(),
            formatExpiryDisplay(trade.getDisplayExpiry()),
            trade.getDisplayAction(),
            getDisplayStrike(trade),
            trade.getTotalQuantity(),
            String.format("$%.2f", trade.getTargetPrice()),
            String.format("$%.2f", trade.getAlertThreshold()),
            trade.isActive() ? "✓" : "✗",
            getStatusDisplay(trade)
        });
    }
    
    private void updateTradeInTable(int rowIndex, TradeOrder trade) {
        if (rowIndex >= 0 && rowIndex < tableModel.getRowCount()) {
            tableModel.setValueAt(trade.getTradeId(), rowIndex, 0);
            tableModel.setValueAt(trade.getAccount(), rowIndex, 1);
            tableModel.setValueAt(trade.getDisplaySymbols(), rowIndex, 2);
            tableModel.setValueAt(formatExpiryDisplay(trade.getDisplayExpiry()), rowIndex, 3);
            tableModel.setValueAt(trade.getDisplayAction(), rowIndex, 4);
            tableModel.setValueAt(getDisplayStrike(trade), rowIndex, 5);
            tableModel.setValueAt(String.valueOf(trade.getTotalQuantity()), rowIndex, 6);
            tableModel.setValueAt(String.format("$%.2f", trade.getTargetPrice()), rowIndex, 7);
            tableModel.setValueAt(String.format("$%.2f", trade.getAlertThreshold()), rowIndex, 8);
            tableModel.setValueAt(trade.isActive() ? "✓" : "✗", rowIndex, 9);
            tableModel.setValueAt(getStatusDisplay(trade), rowIndex, 10);
        }
    }
    
    private void updateTradeStatusInTable(int rowIndex, String status) {
        if (rowIndex >= 0 && rowIndex < tableModel.getRowCount()) {
            tableModel.setValueAt(status, rowIndex, 10);
        }
    }
    
    private String getDisplayStrike(TradeOrder trade) {
        if (trade.isComboOrder()) {
            return "Combo";
        } else {
            TradeOrder.OrderLeg leg = trade.getMainLeg();
            return leg != null ? String.format("%.2f", leg.strike) : "N/A";
        }
    }
    
    private String getStatusDisplay(TradeOrder trade) {
        if (!trade.isActive()) {
            return "Inactive";
        }
        switch (trade.getStatus()) {
            case READY: return "Ready";
            case MONITORING: return "Monitoring...";
            case ALERTED: return "⚠ ALERT - Placing Order";
            case PLACED: return "Order Placed";
            case ERROR: return "Error: " + (trade.getErrorMessage() != null ? trade.getErrorMessage() : "Unknown");
            case INACTIVE: return "Inactive";
            default: return trade.getStatus().toString();
        }
    }
    
    private String formatExpiryDisplay(String expiry) {
        if (expiry.length() != 8) return expiry;
        
        try {
            int year = Integer.parseInt(expiry.substring(0, 4));
            int month = Integer.parseInt(expiry.substring(4, 6));
            int day = Integer.parseInt(expiry.substring(6, 8));
            
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            return String.format("%02d-%s-%02d", day, months[month-1], year % 100);
        } catch (Exception e) {
            return expiry;
        }
    }
    
    private void toggleSelectedTradeActive() {
        int selectedRow = configTable.getSelectedRow();
        if (selectedRow < 0) {
            statusLabel.setText("Please select a trade to toggle");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        TradeOrder trade = tradeOrders.get(selectedRow);
        
        // Stop monitoring if currently monitoring
        if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
            priceMonitor.stopMonitoring(trade.getMonitoringId());
            trade.setStatus(TradeOrder.OrderStatus.READY);
        }
        
        // Toggle active state
        trade.setActive(!trade.isActive());
        
        // Update table display
        updateTradeInTable(selectedRow, trade);
        
        statusLabel.setText("Trade " + trade.getTradeId() + " is now " + (trade.isActive() ? "Active" : "Inactive"));
        statusLabel.setForeground(Color.BLUE);
    }
    
    private void startMonitoringAll() {
        if (tradeOrders.isEmpty()) {
            statusLabel.setText("No trades to monitor - please import Excel file first");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        int started = 0;
        for (int i = 0; i < tradeOrders.size(); i++) {
            TradeOrder trade = tradeOrders.get(i);
            if (trade.isActive() && trade.getStatus() == TradeOrder.OrderStatus.READY) {
                startMonitoringTrade(trade, i);
                started++;
            }
        }
        
        statusLabel.setText(String.format("Started monitoring %d active trades. Waiting for price alerts...", started));
        statusLabel.setForeground(new Color(0, 128, 0));
    }
    
    private void startMonitoringTrade(TradeOrder trade, int rowIndex) {
        trade.setStatus(TradeOrder.OrderStatus.MONITORING);
        updateTradeStatusInTable(rowIndex, "Validating...");
        
        // For combo orders, we monitor the main leg
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        if (mainLeg == null) {
            trade.setStatus(TradeOrder.OrderStatus.ERROR);
            trade.setErrorMessage("No main leg found");
            updateTradeStatusInTable(rowIndex, "Error: No main leg");
            return;
        }
        
        Contract contract = createContractFromLeg(mainLeg);
        
        m_parent.controller().reqContractDetails(contract, list -> {
            if (list.isEmpty() || list.size() > 1) {
                SwingUtilities.invokeLater(() -> {
                    trade.setStatus(TradeOrder.OrderStatus.ERROR);
                    trade.setErrorMessage("Contract validation failed");
                    updateTradeStatusInTable(rowIndex, "Contract Error");
                    statusLabel.setText("Error: Contract validation failed for " + trade.getTradeId());
                    statusLabel.setForeground(Color.RED);
                });
                return;
            }
            
            Contract validatedContract = list.get(0).contract();
            
            SwingUtilities.invokeLater(() -> {
                String monitorId = priceMonitor.startMonitoring(
                    validatedContract, trade.getTargetPrice(), trade.getAlertThreshold(), 
                    mainLeg.action);
                
                trade.setMonitoringId(monitorId);
                trade.setStatus(TradeOrder.OrderStatus.MONITORING);
                
                updateTradeStatusInTable(rowIndex, "Monitoring...");
            });
        });
    }
    
    private Contract createContractFromLeg(TradeOrder.OrderLeg leg) {
        Contract contract = new Contract();
        contract.symbol(leg.symbol);
        contract.secType("OPT");
        contract.exchange("SMART");
        contract.currency("USD");
        contract.lastTradeDateOrContractMonth(leg.expiry);
        contract.strike(leg.strike);
        contract.right(leg.optionType); // Use the option type from the leg (C or P)
        contract.multiplier("100");
        return contract;
    }
    
    private void stopAllMonitoring() {
        priceMonitor.stopAllMonitoring();
        
        for (int i = 0; i < tradeOrders.size(); i++) {
            TradeOrder trade = tradeOrders.get(i);
            if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING) {
                trade.setStatus(TradeOrder.OrderStatus.READY);
            }
            updateTradeStatusInTable(i, trade.isActive() ? "Ready" : "Inactive");
        }
        
        statusLabel.setText("All monitoring stopped");
        statusLabel.setForeground(Color.BLUE);
    }
    
    private void removeSelectedTrade() {
        int selectedRow = configTable.getSelectedRow();
        if (selectedRow < 0) {
            statusLabel.setText("Please select a trade to remove");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        TradeOrder trade = tradeOrders.get(selectedRow);
        if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
            priceMonitor.stopMonitoring(trade.getMonitoringId());
        }
        
        tradeOrders.remove(selectedRow);
        tableModel.removeRow(selectedRow);
        
        statusLabel.setText("Trade removed");
        statusLabel.setForeground(Color.BLUE);
    }
    
    @Override
    public void onPriceAlert(PriceMonitor.MonitoredOrder order, double currentPrice, double distance) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < tradeOrders.size(); i++) {
                TradeOrder trade = tradeOrders.get(i);
                if (trade.getMonitoringId() != null && trade.getMonitoringId().equals(order.id)) {
                    trade.setStatus(TradeOrder.OrderStatus.ALERTED);
                    updateTradeStatusInTable(i, "⚠ ALERT - Placing TWS Order");
                    
                    placeOrderInTWS(trade, order, currentPrice, i);
                    break;
                }
            }
            
            statusLabel.setText("🔔 ALERT: Trade " + order.id + " threshold reached! Placing in TWS...");
            statusLabel.setForeground(new Color(255, 69, 0));
            
            showAlertDialog(order, currentPrice, distance);
        });
    }
    
    private void placeOrderInTWS(TradeOrder trade, PriceMonitor.MonitoredOrder order, 
                                 double currentPrice, int rowIndex) {
        if (trade.isComboOrder()) {
            placeComboOrder(trade, rowIndex);
        } else {
            placeSingleLegOrder(trade, rowIndex);
        }
    }
    
    private void placeSingleLegOrder(TradeOrder trade, int rowIndex) {
        TradeOrder.OrderLeg leg = trade.getMainLeg();
        if (leg == null) {
            SwingUtilities.invokeLater(() -> {
                trade.setStatus(TradeOrder.OrderStatus.ERROR);
                trade.setErrorMessage("No leg found for single order");
                updateTradeStatusInTable(rowIndex, "Error: No leg");
            });
            return;
        }
        
        Contract contract = createContractFromLeg(leg);
        
        Order twsOrder = new Order();
        twsOrder.action(leg.action);
        twsOrder.totalQuantity(Decimal.get(leg.quantity));
        twsOrder.orderType("LMT");
        twsOrder.lmtPrice(trade.getTargetPrice());
        twsOrder.tif("GTC");
        twsOrder.outsideRth(false);
        
        if (!trade.getAccount().trim().isEmpty()) {
            twsOrder.account(trade.getAccount());
        }
        
        placeOrder(trade, contract, twsOrder, rowIndex);
    }
    
    private void placeComboOrder(TradeOrder trade, int rowIndex) {
        // Validate all legs first and collect conids
        validateComboLegs(trade, rowIndex, 0, new ArrayList<>());
    }
    
    private void validateComboLegs(TradeOrder trade, int rowIndex, int legIndex, List<Contract> validatedContracts) {
        if (legIndex >= trade.getLegs().size()) {
            // All legs validated, now create and place BAG order
            createAndPlaceBagOrder(trade, rowIndex, validatedContracts);
            return;
        }
        
        TradeOrder.OrderLeg leg = trade.getLegs().get(legIndex);
        Contract legContract = createContractFromLeg(leg);
        
        m_parent.controller().reqContractDetails(legContract, list -> {
            if (list.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    trade.setStatus(TradeOrder.OrderStatus.ERROR);
                    trade.setErrorMessage("Could not validate leg: " + leg.symbol + " " + leg.strike);
                    updateTradeStatusInTable(rowIndex, "Error: Leg validation failed");
                });
                return;
            }
            
            validatedContracts.add(list.get(0).contract());
            // Validate next leg
            validateComboLegs(trade, rowIndex, legIndex + 1, validatedContracts);
        });
    }
    
    private void createAndPlaceBagOrder(TradeOrder trade, int rowIndex, List<Contract> validatedContracts) {
        // Create BAG contract
        Contract bagContract = new Contract();
        bagContract.symbol(trade.getLegs().get(0).symbol);
        bagContract.secType("BAG");
        bagContract.exchange("SMART");
        bagContract.currency("USD");
        
        // Determine order action based on strategy (all legs should have same action for simple strategies)
        String orderAction = trade.getMainLeg() != null ? trade.getMainLeg().action : "BUY";
        
        // Build combo legs from validated contracts
        List<ComboLeg> comboLegs = new ArrayList<>();
        for (int i = 0; i < trade.getLegs().size(); i++) {
            TradeOrder.OrderLeg leg = trade.getLegs().get(i);
            Contract validatedContract = validatedContracts.get(i);
            
            ComboLeg comboLeg = new ComboLeg();
            comboLeg.conid(validatedContract.conid());
            comboLeg.ratio(leg.quantity);
            comboLeg.action("BUY"); // ComboLeg action is always BUY to define the structure
            comboLeg.exchange("SMART");
            
            comboLegs.add(comboLeg);
        }
        
        bagContract.comboLegs(comboLegs);
        
        // Order action determines if we're buying or selling the combo
        // SELL = short strategy (selling options), BUY = long strategy (buying options)
        Order twsOrder = new Order();
        twsOrder.action(orderAction);
        twsOrder.totalQuantity(Decimal.get(1));
        twsOrder.orderType("LMT");
        twsOrder.lmtPrice(trade.getTargetPrice());
        twsOrder.tif("GTC");
        twsOrder.outsideRth(false);
        
        if (!trade.getAccount().trim().isEmpty()) {
            twsOrder.account(trade.getAccount());
        }
        
        placeOrder(trade, bagContract, twsOrder, rowIndex);
    }
    
    private void placeOrder(TradeOrder trade, Contract contract, Order twsOrder, int rowIndex) {
        m_parent.controller().placeOrModifyOrder(contract, twsOrder, 
            new ApiController.IOrderHandler() {
                @Override
                public void orderState(OrderState orderState, Order order) {
                    SwingUtilities.invokeLater(() -> {
                        String status = String.format("TWS: %s", orderState.getStatus());
                        trade.setStatus(TradeOrder.OrderStatus.PLACED);
                        updateTradeStatusInTable(rowIndex, status);
                        m_parent.show("Trade " + trade.getTradeId() + " placed: " + orderState.getStatus());
                    });
                }
                
                @Override
                public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining,
                        double avgFillPrice, int permId, int parentId, double lastFillPrice,
                        int clientId, String whyHeld, double mktCapPrice) {
                    SwingUtilities.invokeLater(() -> {
                        updateTradeStatusInTable(rowIndex, "TWS: " + status.name());
                    });
                }
                
                @Override
                public void handle(int errorCode, String errorMsg) {
                    SwingUtilities.invokeLater(() -> {
                        String fullError = String.format("Error %d: %s", errorCode, errorMsg);
                        trade.setStatus(TradeOrder.OrderStatus.ERROR);
                        trade.setErrorMessage(fullError);
                        updateTradeStatusInTable(rowIndex, fullError);
                        statusLabel.setText(fullError);
                        statusLabel.setForeground(Color.RED);
                        m_parent.show("Error placing order for trade " + trade.getTradeId() + ": " + fullError);
                    });
                }
            });
    }
    
    private void showAlertDialog(PriceMonitor.MonitoredOrder order, double currentPrice, double distance) {
        PriceAlertDialog.showAlert(
            (Frame) SwingUtilities.getWindowAncestor(this),
            order, currentPrice, distance);
    }
    
    @Override
    public void onPriceUpdate(PriceMonitor.MonitoredOrder order, double currentPrice) {
        // Update current price in trade orders
        for (TradeOrder trade : tradeOrders) {
            if (trade.getMonitoringId() != null && trade.getMonitoringId().equals(order.id)) {
                trade.setCurrentPrice(currentPrice);
                break;
            }
        }
    }
}
