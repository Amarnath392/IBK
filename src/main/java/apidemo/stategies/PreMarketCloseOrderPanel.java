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
import javax.swing.table.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PreMarketCloseOrderPanel extends JPanel implements PriceMonitor.PriceAlertListener {
    
    private final TradingStrategies m_parent;
    private final PriceMonitor priceMonitor;
    private DefaultTableModel tableModel;
    private JTable configTable;
    private JLabel statusLabel;
    
    private static final String[] COLUMN_NAMES = {
        "Select", "Trade ID", "Account", "Symbols", "Expiry", "Action", "Strike", "QTY", "Target $", "Alert $", "Market $", "Status"
    };
    
    private static final int COL_SELECT = 0;
    private static final int COL_TRADE_ID = 1;
    private static final int COL_ACCOUNT = 2;
    private static final int COL_SYMBOLS = 3;
    private static final int COL_EXPIRY = 4;
    private static final int COL_ACTION = 5;
    private static final int COL_STRIKE = 6;
    private static final int COL_QTY = 7;
    private static final int COL_TARGET = 8;
    private static final int COL_ALERT = 9;
    private static final int COL_MARKET = 10;
    private static final int COL_STATUS = 11;
    
    private Timer marketPriceUpdateTimer;
    private boolean[] selectedTrades;
    private Map<String, ApiController.ITopMktDataHandler> marketDataHandlers = new HashMap<>();
    
    private List<TradeOrder> tradeOrders = new ArrayList<>();
    
    public PreMarketCloseOrderPanel(TradingStrategies parent) {
        this.m_parent = parent;
        this.priceMonitor = new PriceMonitor(parent.controller());
        this.priceMonitor.addAlertListener(this);
        
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Initialize market price update timer (updates every 2 seconds)
        marketPriceUpdateTimer = new Timer(2000, e -> updateMarketPrices());
        
        JPanel topPanel = createTopPanel();
        JPanel configPanel = createConfigPanel();
        JPanel buttonPanel = createButtonPanel();
        
        statusLabel = new JLabel("Ready - Import Excel file to load your trades");
        statusLabel.setForeground(new Color(33, 150, 243));
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
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(63, 81, 181), 2), 
            "Trade Order Monitoring", 0, 0, new Font("Arial", Font.BOLD, 14), new Color(63, 81, 181)));
        
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
                if (column == COL_SELECT) {
                    return new BooleanTableCellRenderer();
                } else if (column == COL_TARGET || column == COL_ALERT) {
                    return new EditableNumberRenderer();
                } else if (column == COL_MARKET) {
                    return new MarketPriceRenderer();
                } else if (column == COL_STATUS) {
                    return new StatusCellRenderer();
                } else {
                    return new EnhancedCellRenderer();
                }
            }
            
            @Override
            public TableCellEditor getCellEditor(int row, int column) {
                if (column == COL_TARGET || column == COL_ALERT) {
                    return new NumberCellEditor();
                }
                return super.getCellEditor(row, column);
            }
        };
        
        configTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        configTable.setRowHeight(30);
        configTable.setGridColor(new Color(224, 224, 224));
        configTable.setShowGrid(true);
        configTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        configTable.getTableHeader().setBackground(new Color(245, 245, 245));
        configTable.getTableHeader().setForeground(new Color(33, 33, 33));
        
        // Set column widths for simplified 12-column structure
        configTable.getColumnModel().getColumn(COL_SELECT).setPreferredWidth(50);     // Select
        configTable.getColumnModel().getColumn(COL_TRADE_ID).setPreferredWidth(70);   // Trade ID
        configTable.getColumnModel().getColumn(COL_ACCOUNT).setPreferredWidth(80);    // Account
        configTable.getColumnModel().getColumn(COL_SYMBOLS).setPreferredWidth(120);   // Symbols
        configTable.getColumnModel().getColumn(COL_EXPIRY).setPreferredWidth(80);     // Expiry
        configTable.getColumnModel().getColumn(COL_ACTION).setPreferredWidth(80);     // Action
        configTable.getColumnModel().getColumn(COL_STRIKE).setPreferredWidth(70);     // Strike
        configTable.getColumnModel().getColumn(COL_QTY).setPreferredWidth(50);        // QTY
        configTable.getColumnModel().getColumn(COL_TARGET).setPreferredWidth(70);     // Target $
        configTable.getColumnModel().getColumn(COL_ALERT).setPreferredWidth(70);      // Alert $
        configTable.getColumnModel().getColumn(COL_MARKET).setPreferredWidth(70);     // Market $
        configTable.getColumnModel().getColumn(COL_STATUS).setPreferredWidth(140);    // Status (wider for detailed states)
        
        JScrollPane scrollPane = new JScrollPane(configTable);
        scrollPane.setPreferredSize(new Dimension(1100, 300));
        scrollPane.getViewport().setBackground(Color.WHITE);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    // Custom cell renderers for enhanced UI
    private class EnhancedCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                     boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            
            if (!isSelected) {
                if (row % 2 == 0) {
                    c.setBackground(new Color(248, 249, 250));
                } else {
                    c.setBackground(Color.WHITE);
                }
            }
            return c;
        }
    }
    
    private class EditableNumberRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                     boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            
            if (!isSelected) {
                c.setBackground(new Color(232, 245, 233)); // Light green for editable
                setBorder(new LineBorder(new Color(76, 175, 80), 1));
            }
            
            if (value instanceof Double) {
                setText(String.format("%.2f", (Double) value));
            }
            return c;
        }
    }
    
    private class MarketPriceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                     boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            
            if (!isSelected) {
                c.setBackground(new Color(227, 242, 253)); // Light blue for market price
            }
            
            if (value instanceof Double && ((Double) value) > 0) {
                setText(String.format("%.2f", (Double) value));
                setForeground(new Color(33, 150, 243));
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setText("--");
                setForeground(Color.GRAY);
            }
            return c;
        }
    }
    
    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                     boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            
            String status = (String) value;
            
            if (!isSelected) {
                // Color code status for easy identification
                if (status != null) {
                    if (status.contains("Monitoring")) {
                        c.setBackground(new Color(232, 245, 233)); // Light green
                        setForeground(new Color(46, 125, 50));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("ALERT")) {
                        c.setBackground(new Color(255, 243, 224)); // Light orange
                        setForeground(new Color(255, 111, 0));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("Placed")) {
                        c.setBackground(new Color(227, 242, 253)); // Light blue
                        setForeground(new Color(33, 150, 243));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("Error")) {
                        c.setBackground(new Color(255, 235, 238)); // Light red
                        setForeground(new Color(211, 47, 47));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.equals("Ready")) {
                        c.setBackground(new Color(248, 249, 250)); // Light gray
                        setForeground(new Color(97, 97, 97));
                    } else {
                        c.setBackground(row % 2 == 0 ? new Color(248, 249, 250) : Color.WHITE);
                        setForeground(Color.BLACK);
                    }
                } else {
                    c.setBackground(row % 2 == 0 ? new Color(248, 249, 250) : Color.WHITE);
                }
            }
            return c;
        }
    }
    
    private class BooleanTableCellRenderer extends JCheckBox implements TableCellRenderer {
        public BooleanTableCellRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                     boolean hasFocus, int row, int column) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                if (row % 2 == 0) {
                    setBackground(new Color(248, 249, 250));
                } else {
                    setBackground(Color.WHITE);
                }
            }
            setSelected((value != null && ((Boolean) value).booleanValue()));
            return this;
        }
    }
    
    private class NumberCellEditor extends DefaultCellEditor {
        public NumberCellEditor() {
            super(new JTextField());
            ((JTextField) getComponent()).setHorizontalAlignment(SwingConstants.CENTER);
        }
        
        @Override
        public boolean stopCellEditing() {
            String s = (String) super.getCellEditorValue();
            try {
                Double.parseDouble(s);
            } catch (NumberFormatException e) {
                ((JComponent) getComponent()).setBorder(new LineBorder(Color.red));
                return false;
            }
            return super.stopCellEditing();
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            ((JComponent) c).setBorder(new LineBorder(new Color(76, 175, 80), 2));
            return c;
        }
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        // Top row - Main actions
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        JButton importButton = createStyledButton("📁 Import from Excel", new Color(255, 152, 0), e -> importFromExcel());
        JButton selectAllButton = createStyledButton("☑ Select All", new Color(96, 125, 139), e -> selectAllTrades());
        JButton deselectAllButton = createStyledButton("☐ Deselect All", new Color(158, 158, 158), e -> deselectAllTrades());
        
        topRow.add(importButton);
        topRow.add(selectAllButton);
        topRow.add(deselectAllButton);
        
        // Bottom row - Action buttons
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        JButton startMonitoringButton = createStyledButton("▶ Start Monitoring Selected", new Color(76, 175, 80), e -> startMonitoringSelected());
        JButton stopMonitoringButton = createStyledButton("⏹ Stop Monitoring Selected", new Color(244, 67, 54), e -> stopMonitoringSelected());
        JButton placeOrderButton = createStyledButton("💼 Place Order Selected", new Color(63, 81, 181), e -> placeOrderSelected());
        JButton removeSelectedButton = createStyledButton("🗑 Remove Selected", new Color(233, 30, 99), e -> removeSelectedTrades());
        
        bottomRow.add(startMonitoringButton);
        bottomRow.add(stopMonitoringButton);
        bottomRow.add(placeOrderButton);
        bottomRow.add(removeSelectedButton);
        
        panel.add(topRow);
        panel.add(bottomRow);
        
        return panel;
    }
    
    private JButton createStyledButton(String text, Color backgroundColor, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setPreferredSize(new Dimension(180, 35));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createRaisedBevelBorder());
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Enhanced styling for better visibility
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(backgroundColor.darker());
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(backgroundColor);
            }
        });
        
        button.addActionListener(action);
        return button;
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
                List<String> availableAccounts = m_parent.getAccountList();
                return ExcelOrderImporter.importFromExcel(selectedFile, availableAccounts);
            }
            
            @Override
            protected void done() {
                try {
                    ExcelOrderImporter.ImportResult importResult = get();
                    
                    if (!importResult.errors.isEmpty()) {
                        String errorMsg = "Import Failed:\n\n" + importResult.errors.stream()
                                .map(error -> "• " + error)
                                .collect(Collectors.joining("\n"));
                        JOptionPane.showMessageDialog(
                                PreMarketCloseOrderPanel.this,
                                errorMsg,
                                "Import Errors",
                                JOptionPane.ERROR_MESSAGE
                        );
                        statusLabel.setText("Import failed - check error message");
                        statusLabel.setForeground(Color.RED);
                        return;
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
                    
                    // Start fetching market prices for all imported trades
                    if (!tradeOrders.isEmpty()) {
                        // Start the market price update timer
                        if (!marketPriceUpdateTimer.isRunning()) {
                            marketPriceUpdateTimer.start();
                        }
                        // Immediately fetch market prices
                        updateMarketPrices();
                    }
                    
                    JOptionPane.showMessageDialog(PreMarketCloseOrderPanel.this,
                        String.format("Successfully imported %d trades!\n\nCombo orders: %d\nSingle orders: %d\n\nMarket prices will update automatically.",
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
        // Initialize selectedTrades array if needed
        if (selectedTrades == null || selectedTrades.length < tradeOrders.size()) {
            selectedTrades = new boolean[tradeOrders.size() + 10]; // Add some buffer
        }
        
        tableModel.addRow(new Object[]{
            Boolean.FALSE,                                     // COL_SELECT - checkbox
            trade.getTradeId(),                               // COL_TRADE_ID
            trade.getAccount(),                               // COL_ACCOUNT
            trade.getDisplaySymbols(),                        // COL_SYMBOLS
            formatExpiryDisplay(trade.getDisplayExpiry()),    // COL_EXPIRY
            trade.getDisplayAction(),                         // COL_ACTION
            getDisplayStrike(trade),                          // COL_STRIKE
            trade.getTotalQuantity(),                         // COL_QTY
            trade.getTargetPrice(),                           // COL_TARGET (Double for editing)
            trade.getAlertThreshold(),                        // COL_ALERT (Double for editing)
            0.0,                                             // COL_MARKET (market price)
            getEnhancedStatusDisplay(trade)                   // COL_STATUS (enhanced with monitoring state)
        });
    }
    
    private void updateTradeInTable(int rowIndex, TradeOrder trade) {
        if (rowIndex >= 0 && rowIndex < tableModel.getRowCount()) {
            tableModel.setValueAt(trade.getTradeId(), rowIndex, COL_TRADE_ID);
            tableModel.setValueAt(trade.getAccount(), rowIndex, COL_ACCOUNT);
            tableModel.setValueAt(trade.getDisplaySymbols(), rowIndex, COL_SYMBOLS);
            tableModel.setValueAt(formatExpiryDisplay(trade.getDisplayExpiry()), rowIndex, COL_EXPIRY);
            tableModel.setValueAt(trade.getDisplayAction(), rowIndex, COL_ACTION);
            tableModel.setValueAt(getDisplayStrike(trade), rowIndex, COL_STRIKE);
            tableModel.setValueAt(trade.getTotalQuantity(), rowIndex, COL_QTY);
            tableModel.setValueAt(trade.getTargetPrice(), rowIndex, COL_TARGET);
            tableModel.setValueAt(trade.getAlertThreshold(), rowIndex, COL_ALERT);
            tableModel.setValueAt(getEnhancedStatusDisplay(trade), rowIndex, COL_STATUS);
        }
    }
    
    private void updateTradeStatusInTable(int rowIndex, String status) {
        if (rowIndex >= 0 && rowIndex < tableModel.getRowCount()) {
            tableModel.setValueAt(status, rowIndex, COL_STATUS);
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
    
    private String getEnhancedStatusDisplay(TradeOrder trade) {
        if (!trade.isActive()) {
            return "Inactive";
        }
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
    
    // Legacy method for compatibility
    private String getStatusDisplay(TradeOrder trade) {
        return getEnhancedStatusDisplay(trade);
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
    }
    
    // New enhanced button action methods
    private void selectAllTrades() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(Boolean.TRUE, i, COL_SELECT);
        }
        statusLabel.setText("All trades selected");
        statusLabel.setForeground(new Color(33, 150, 243));
    }
    
    private void deselectAllTrades() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(Boolean.FALSE, i, COL_SELECT);
        }
        statusLabel.setText("All trades deselected");
        statusLabel.setForeground(new Color(33, 150, 243));
    }
    
    private void startMonitoringSelected() {
        List<Integer> selectedRows = getSelectedRows();
        System.out.println("DEBUG: Selected rows count: " + selectedRows.size());
        
        if (selectedRows.isEmpty()) {
            statusLabel.setText("No trades selected. Use checkboxes to select trades for monitoring");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        int started = 0;
        for (int row : selectedRows) {
            TradeOrder trade = tradeOrders.get(row);
            System.out.println("DEBUG: Trade " + trade.getTradeId() + " - isActive: " + trade.isActive() + ", status: " + trade.getStatus());
            
            if (trade.isActive() && trade.getStatus() == TradeOrder.OrderStatus.READY) {
                System.out.println("DEBUG: Starting monitoring for trade " + trade.getTradeId());
                startMonitoringTrade(trade, row);
                started++;
            } else {
                System.out.println("DEBUG: Skipping trade " + trade.getTradeId() + " - not ready to monitor");
            }
        }
        
        if (started > 0) {
            statusLabel.setText(String.format("Started monitoring %d selected trades", started));
            statusLabel.setForeground(new Color(76, 175, 80));
            // Start market price updates when monitoring begins
            if (!marketPriceUpdateTimer.isRunning()) {
                marketPriceUpdateTimer.start();
            }
        } else {
            statusLabel.setText("No active trades found in selection to start monitoring");
            statusLabel.setForeground(Color.ORANGE);
        }
    }
    
    private void stopMonitoringSelected() {
        List<Integer> selectedRows = getSelectedRows();
        if (selectedRows.isEmpty()) {
            statusLabel.setText("No trades selected. Use checkboxes to select trades");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        int stopped = 0;
        for (int row : selectedRows) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
                priceMonitor.stopMonitoring(trade.getMonitoringId());
                trade.setStatus(TradeOrder.OrderStatus.READY);
                trade.setMonitoringId(null);
                updateTradeStatusInTable(row, getEnhancedStatusDisplay(trade));
                stopped++;
            }
        }
        
        statusLabel.setText(String.format("Stopped monitoring %d selected trades", stopped));
        statusLabel.setForeground(new Color(244, 67, 54));
        
        // Stop market price updates if no trades are being monitored
        boolean anyMonitoring = tradeOrders.stream().anyMatch(t -> t.getStatus() == TradeOrder.OrderStatus.MONITORING);
        if (!anyMonitoring && marketPriceUpdateTimer.isRunning()) {
            marketPriceUpdateTimer.stop();
        }
    }
    
    private void placeOrderSelected() {
        List<Integer> selectedRows = getSelectedRows();
        if (selectedRows.isEmpty()) {
            statusLabel.setText("No trades selected. Use checkboxes to select trades for order placement");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        // Confirm manual order placement
        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Place orders for %d selected trades immediately using their Target Price?\\n\\n" +
                "This bypasses monitoring and places orders right away at the specified Target Price.\\n" +
                "Orders will be placed as limit orders.", selectedRows.size()),
            "Manual Order Placement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        int placed = 0;
        for (int row : selectedRows) {
            TradeOrder trade = tradeOrders.get(row);
            if (trade.isActive()) {
                // Get updated target/alert prices from table (in case user edited them)
                updateTradeFromTable(row, trade);
                
                // Place order immediately without monitoring
                placeOrderManually(trade, row);
                placed++;
            }
        }
        
        statusLabel.setText(String.format("Placing orders for %d selected trades", placed));
        statusLabel.setForeground(new Color(63, 81, 181));
    }
    
    private void removeSelectedTrades() {
        List<Integer> selectedRows = getSelectedRows();
        if (selectedRows.isEmpty()) {
            statusLabel.setText("No trades selected. Use checkboxes to select trades for removal");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("Remove %d selected trades?\\n\\nThis will stop monitoring and delete them from the list.", 
                selectedRows.size()),
            "Remove Trades", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Sort in descending order to avoid index shifting issues
        selectedRows.sort((a, b) -> b.compareTo(a));
        
        int removed = 0;
        for (int row : selectedRows) {
            TradeOrder trade = tradeOrders.get(row);
            
            // Stop monitoring if active
            if (trade.getStatus() == TradeOrder.OrderStatus.MONITORING && trade.getMonitoringId() != null) {
                priceMonitor.stopMonitoring(trade.getMonitoringId());
            }
            
            // Remove from list and table
            tradeOrders.remove(row);
            tableModel.removeRow(row);
            removed++;
        }
        
        statusLabel.setText(String.format("Removed %d trades", removed));
        statusLabel.setForeground(new Color(233, 30, 99));
    }
    
    private List<Integer> getSelectedRows() {
        List<Integer> selectedRows = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, COL_SELECT);
            if (selected != null && selected.booleanValue()) {
                selectedRows.add(i);
            }
        }
        return selectedRows;
    }
    
    private void updateTradeFromTable(int row, TradeOrder trade) {
        // Update target and alert prices from table (in case user edited them inline)
        // Handle String, Integer, or Double values
        Object targetPriceObj = tableModel.getValueAt(row, COL_TARGET);
        Object alertPriceObj = tableModel.getValueAt(row, COL_ALERT);
        
        if (targetPriceObj != null) {
            double targetPrice = parsePrice(targetPriceObj);
            if (targetPrice > 0) {
                trade.setTargetPrice(targetPrice);
            }
        }
        if (alertPriceObj != null) {
            double alertPrice = parsePrice(alertPriceObj);
            if (alertPrice > 0) {
                trade.setAlertThreshold(alertPrice);
            }
        }
        // Note: Active status is now managed through business logic, not UI toggle
    }
    
    private double parsePrice(Object value) {
        if (value == null) return 0.0;
        
        if (value instanceof Double) {
            return ((Double) value).doubleValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing price: " + value);
                return 0.0;
            }
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        return 0.0;
    }
    
    private void placeOrderManually(TradeOrder trade, int rowIndex) {
        // Similar to placeOrderInTWS but without monitoring logic
        if (trade.isComboOrder()) {
            placeComboOrder(trade, rowIndex);
        } else {
            placeSingleLegOrder(trade, rowIndex);
        }
        
        trade.setStatus(TradeOrder.OrderStatus.PLACED);
        updateTradeStatusInTable(rowIndex, "Placing Manual Order...");
    }
    
    // Market price update functionality - fetch for ALL trades to show current market conditions
    private void updateMarketPrices() {
        for (int i = 0; i < tradeOrders.size(); i++) {
            TradeOrder trade = tradeOrders.get(i);
            // Fetch market price for all active trades regardless of monitoring status
            if (trade.isActive()) {
                TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
                if (mainLeg != null) {
                    requestMarketPrice(trade, i);
                }
            }
        }
    }
    
    private void requestMarketPrice(TradeOrder trade, int rowIndex) {
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        if (mainLeg == null) return;
        
        String handlerKey = trade.getTradeId() + "_" + rowIndex;
        
        // Cancel existing market data subscription if present
        if (marketDataHandlers.containsKey(handlerKey)) {
            ApiController.ITopMktDataHandler existingHandler = marketDataHandlers.get(handlerKey);
            m_parent.controller().cancelTopMktData(existingHandler);
        }
        
        Contract contract = createContractFromLeg(mainLeg);
        
        // Create market data handler for this trade
        ApiController.ITopMktDataHandler handler = new ApiController.ITopMktDataHandler() {
            private double lastPrice = 0.0;
            private double bidPrice = 0.0;
            private double askPrice = 0.0;
            
            @Override
            public void tickPrice(TickType tickType, double price, TickAttrib attribs) {
                switch (tickType) {
                    case LAST:
                        lastPrice = price;
                        break;
                    case BID:
                        bidPrice = price;
                        break;
                    case ASK:
                        askPrice = price;
                        break;
                    default:
                        return;
                }
                
                // Use best available price: LAST > midpoint of BID/ASK > BID > ASK
                double displayPrice = lastPrice > 0 ? lastPrice : 
                                     (bidPrice > 0 && askPrice > 0) ? (bidPrice + askPrice) / 2 :
                                     bidPrice > 0 ? bidPrice : askPrice;
                
                if (displayPrice > 0) {
                    SwingUtilities.invokeLater(() -> {
                        if (rowIndex < tableModel.getRowCount()) {
                            tableModel.setValueAt(displayPrice, rowIndex, COL_MARKET);
                            trade.setCurrentPrice(displayPrice);
                        }
                    });
                }
            }
            
            @Override
            public void tickSize(TickType tickType, Decimal size) {}
            
            @Override
            public void tickString(TickType tickType, String value) {}
            
            @Override
            public void tickSnapshotEnd() {}
            
            @Override
            public void marketDataType(int marketDataType) {}
            
            @Override
            public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {}
        };
        
        // Store handler and request market data
        marketDataHandlers.put(handlerKey, handler);
        m_parent.controller().reqTopMktData(contract, "", false, false, handler);
    }
    
    private void startMonitoringTrade(TradeOrder trade, int rowIndex) {
        TradeOrder.OrderLeg mainLeg = trade.getMainLeg();
        if (mainLeg == null) {
            statusLabel.setText("Trade " + trade.getTradeId() + " has no main leg for monitoring");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        // Get updated prices from table
        updateTradeFromTable(rowIndex, trade);
        
        Contract contract = createContractFromLeg(mainLeg);
        
        String monitoringId = trade.getTradeId() + "_" + System.currentTimeMillis();
        trade.setMonitoringId(monitoringId);
        trade.setStatus(TradeOrder.OrderStatus.MONITORING);
        
        updateTradeStatusInTable(rowIndex, getEnhancedStatusDisplay(trade));
        
        // Start monitoring with contract details
        m_parent.controller().reqContractDetails(contract, contractDetailsList -> {
            if (!contractDetailsList.isEmpty()) {
                Contract validatedContract = contractDetailsList.get(0).contract();
                String actualMonitoringId = priceMonitor.startMonitoring(
                    validatedContract, trade.getTargetPrice(), trade.getAlertThreshold(), mainLeg.action);
                trade.setMonitoringId(actualMonitoringId);
            }
        });
    }
    
    // Legacy methods updated to work with new structure
    private void startMonitoringAll() {
        if (tradeOrders.isEmpty()) {
            statusLabel.setText("No trades to monitor - please import Excel file first");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        // Select all trades first, then start monitoring selected
        selectAllTrades();
        startMonitoringSelected();
    }
    
    private void stopAllMonitoring() {
        if (tradeOrders.isEmpty()) {
            statusLabel.setText("No trades to stop");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        
        // Select all and stop monitoring
        selectAllTrades();
        stopMonitoringSelected();
    }
    
    private void removeSelectedTrade() {
        // Legacy method - now uses checkbox-based selection
        List<Integer> selectedRows = getSelectedRows();
        if (selectedRows.isEmpty()) {
            // If nothing selected via checkbox, use table row selection
            int selectedRow = configTable.getSelectedRow();
            if (selectedRow >= 0) {
                tableModel.setValueAt(Boolean.TRUE, selectedRow, COL_SELECT);
                removeSelectedTrades();
                return;
            }
        }
        removeSelectedTrades();
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
    
    // Existing onPriceAlert and order placement methods continue below
    
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
