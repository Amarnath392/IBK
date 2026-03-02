package apidemo.stategies;

import org.apache.poi.util.StringUtil;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class PreMarketCloseOrderPanel extends JPanel {
    
    private final TradingStrategies m_parent;
    private final JTabbedPane sheetTabs;
    private final Map<String, SheetTradesPanel> sheetPanels = new LinkedHashMap<>();
    private JLabel statusLabel;
    private File lastImportedFile;
    
    public PreMarketCloseOrderPanel(TradingStrategies parent) {
        this.m_parent = parent;
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        add(createHeaderPanel(), BorderLayout.NORTH);
        
        sheetTabs = new JTabbedPane(JTabbedPane.TOP);
        sheetTabs.setFont(new Font("Arial", Font.BOLD, 13));
        
        JLabel placeholder = new JLabel(
            "<html><div style='text-align:center; padding:60px; color:#9E9E9E;'>" +
            "<h2>No trades loaded</h2>" +
            "Click <b>Import from Excel</b> to load your trade sheets.<br/>" +
            "Each active sheet becomes a tab with its own trade management panel." +
            "</div></html>", SwingConstants.CENTER);
        sheetTabs.addTab("Getting Started", placeholder);
        
        add(sheetTabs, BorderLayout.CENTER);
        
        statusLabel = new JLabel("Ready — Import an Excel file to begin");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        statusLabel.setForeground(new Color(33, 150, 243));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(statusLabel, BorderLayout.SOUTH);
    }
    
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 5));
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        
        JLabel title = new JLabel("Pre-Market Close Order Configuration");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel instructions = new JLabel(
            "<html><div style='text-align:center; padding:4px;'>" +
            "<b>Multi-sheet Excel import with active trade filtering:</b><br/>" +
            "1. Click 'Import from Excel' — only visible sheets and active trades (Active=Y) are loaded<br/>" +
            "2. Each sheet becomes its own tab — reimporting adds only new trades (by Trade ID)<br/>" +
            "3. Alert $: positive = trigger above, negative = trigger below, 0 = no alert" +
            "</div></html>"
        );
        instructions.setFont(new Font("Arial", Font.PLAIN, 12));
        instructions.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        JButton importBtn = createStyledButton("📁 Import from Excel", new Color(255, 152, 0), e -> importFromExcel());
        JButton reimportBtn = createStyledButton("🔄 Reimport Last File", new Color(33, 150, 243), e -> reimportLastFile());
        buttonBar.add(importBtn);
        buttonBar.add(reimportBtn);
        
        header.add(title, BorderLayout.NORTH);
        header.add(instructions, BorderLayout.CENTER);
        header.add(buttonBar, BorderLayout.SOUTH);
        return header;
    }
    
    private JButton createStyledButton(String text, Color bg, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setPreferredSize(new Dimension(200, 35));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createRaisedBevelBorder());
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { button.setBackground(bg.darker()); }
            public void mouseExited(java.awt.event.MouseEvent e) { button.setBackground(bg); }
        });
        button.addActionListener(action);
        return button;
    }
    
    private void reimportLastFile() {
        if (lastImportedFile == null || !lastImportedFile.exists()) {
            statusLabel.setText("No previous file to reimport. Use 'Import from Excel' first.");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }
        doImport(lastImportedFile);
    }
    
    private void importFromExcel() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Excel File with Trade Orders");
        fc.setFileFilter(new FileNameExtensionFilter("Excel Files (*.xlsx, *.xls)", "xlsx", "xls"));
        fc.setAcceptAllFileFilterUsed(false);
        if (lastImportedFile != null) fc.setCurrentDirectory(lastImportedFile.getParentFile());
        
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        doImport(fc.getSelectedFile());
    }
    
    private void doImport(File file) {
        lastImportedFile = file;
        statusLabel.setText("Importing: " + file.getName() + "...");
        statusLabel.setForeground(Color.BLUE);
        
        new SwingWorker<ExcelOrderImporter.ImportResult, Void>() {
            @Override protected ExcelOrderImporter.ImportResult doInBackground() {
                return ExcelOrderImporter.importFromExcel(file);
            }
            @Override protected void done() {
                try {
                    ExcelOrderImporter.ImportResult result = get();
                    handleImportResult(result);
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(PreMarketCloseOrderPanel.this,
                        "Error importing file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    private void handleImportResult(ExcelOrderImporter.ImportResult result) {
        // Validate accounts across all sheets before processing
        List<String> errors = new ArrayList<>(result.errors);
        List<String> availableAccounts = m_parent.getAccountList();
        List<TradeOrder> allTrades = result.sheetTrades.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        validateAccounts(allTrades, availableAccounts, errors);
        
        // Show errors if any
        if (!errors.isEmpty()) {
            showMessageList("Import Errors", errors, JOptionPane.WARNING_MESSAGE);
        }
        
        // Stop import if account validation failed
        if (errors.size() > result.errors.size()) {
            statusLabel.setText("Import failed - account validation errors");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        if (!result.success) {
            statusLabel.setText("No active trades found in Excel file");
            statusLabel.setForeground(Color.RED);
            if (!result.warnings.isEmpty()) {
                showMessageList("Import Warnings", result.warnings, JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        
        // Remove placeholder tab if present
        if (sheetPanels.isEmpty() && sheetTabs.getTabCount() > 0) {
            sheetTabs.removeAll();
        }
        
        int totalAdded = 0;
        int totalSkipped = 0;
        List<String> sheetSummaries = new ArrayList<>();
        
        for (Map.Entry<String, List<TradeOrder>> entry : result.sheetTrades.entrySet()) {
            String name = entry.getKey();
            List<TradeOrder> trades = entry.getValue();
            
            SheetTradesPanel panel = sheetPanels.get(name);
            if (panel == null) {
                panel = new SheetTradesPanel(m_parent, name);
                sheetPanels.put(name, panel);
                sheetTabs.addTab(name, panel);
            }
            
            int added = panel.addTrades(trades);
            int skipped = trades.size() - added;
            
            totalAdded += added;
            totalSkipped += skipped;
            sheetSummaries.add(String.format("  %s: %d added, %d skipped (duplicate)", name, added, skipped));
        }
        
        // Build summary
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Imported %d new trades across %d sheets", totalAdded, result.sheetTrades.size()));
        if (totalSkipped > 0) summary.append(String.format(" (%d duplicates skipped)", totalSkipped));
        if (!result.skippedSheets.isEmpty()) {
            summary.append(String.format("\nInactive sheets skipped: %s", String.join(", ", result.skippedSheets)));
        }
        
        statusLabel.setText(summary.toString().split("\n")[0]);
        statusLabel.setForeground(new Color(0, 128, 0));
        
        // Combine warnings + sheet summaries for detail dialog
        List<String> details = new ArrayList<>();
        details.add("=== Sheet Summary ===");
        details.addAll(sheetSummaries);
        if (!result.skippedSheets.isEmpty()) {
            details.add("");
            details.add("=== Inactive Sheets (skipped) ===");
            details.addAll(result.skippedSheets);
        }
        if (!result.warnings.isEmpty()) {
            details.add("");
            details.add("=== Warnings ===");
            details.addAll(result.warnings);
        }
        
        showMessageList("Import Complete — " + totalAdded + " trades added", details, JOptionPane.INFORMATION_MESSAGE);
        
        // Select the first new sheet tab
        if (!result.sheetTrades.isEmpty()) {
            String firstSheet = result.sheetTrades.keySet().iterator().next();
            int tabIndex = sheetTabs.indexOfTab(firstSheet);
            if (tabIndex >= 0) sheetTabs.setSelectedIndex(tabIndex);
        }
    }
    
    private void showMessageList(String title, List<String> messages, int messageType) {
        StringBuilder sb = new StringBuilder();
        for (String msg : messages) {
            sb.append(msg).append("\n");
        }
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(500, Math.min(300, messages.size() * 20 + 60)));
        JOptionPane.showMessageDialog(this, scroll, title, messageType);
    }
    
    private void validateAccounts(List<TradeOrder> trades, List<String> availableAccounts, List<String> errors) {
        if (availableAccounts == null || availableAccounts.isEmpty()) {
            errors.add("No accounts available from IB. Please ensure you are connected to IB TWS/Gateway.");
            return;
        }

        Set<String> missingAccounts = new HashSet<>();
        Set<String> invalidAccounts = new HashSet<>();

        for (TradeOrder trade : trades) {
            String account = trade.getAccount();
            if (StringUtil.isBlank(account)) {
                missingAccounts.add("Trade ID: " + trade.getTradeId());
                continue;
            }

            account = account.strip();
            if (!availableAccounts.contains(account)) {
                invalidAccounts.add(account);
            }
        }

        if (missingAccounts.isEmpty() && invalidAccounts.isEmpty()) {
            return;
        }

        StringBuilder errorMsg = new StringBuilder("Account Validation Failed!\n\n");
        if (!missingAccounts.isEmpty()) {
            errorMsg.append("Missing account(s) in Excel file:\n")
                    .append(missingAccounts.stream()
                            .map(row -> "• " + row)
                            .collect(Collectors.joining("\n")))
                    .append("\n");
        }

        if (!invalidAccounts.isEmpty()) {
            errorMsg.append("Invalid account(s) not found in IB:\n")
                    .append(invalidAccounts.stream()
                            .map(row -> "• " + row)
                            .collect(Collectors.joining("\n")))
                    .append("\n");
        }

        errors.add(errorMsg.toString());
    }
}
