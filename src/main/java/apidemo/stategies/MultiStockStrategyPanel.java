package apidemo.stategies;

import apidemo.util.HtmlButton;
import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;
import com.ib.client.*;
import com.ib.controller.ApiController;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for executing multi-stock trading strategies.
 * Allows simultaneous placement of up to 4 stock orders with configurable symbols,
 * quantities, prices, and buy/sell actions.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Support for up to 4 simultaneous stock orders</li>
 *   <li>Configurable symbol, quantity, and limit price for each order</li>
 *   <li>Individual buy/sell action selection</li>
 *   <li>Default values for common tech stocks</li>
 *   <li>Batch order placement</li>
 * </ul>
 * 
 * @author IBK Trading System
 * @version 1.0
 */
public class MultiStockStrategyPanel extends JPanel {
    private final TradingStrategies m_parent;
    private final List<StockEntry> m_stockEntries = new ArrayList<>();
    private final JLabel m_status = new JLabel();
    private HtmlButton m_placeOrderButton;
    private static final int MAX_STOCKS = 4;

    /**
     * Data holder for a single stock order entry.
     * Contains fields for symbol, quantity, price, and buy/sell action.
     */
    private static class StockEntry {
        final UpperField symbol = new UpperField();
        final UpperField quantity = new UpperField();
        final UpperField price = new UpperField();
        final JCheckBox isSell = new JCheckBox("Sell");
    }

    /**
     * Constructs a new MultiStockStrategyPanel.
     * 
     * @param parent the parent TradingStrategies instance for accessing the API controller
     */
    public MultiStockStrategyPanel(TradingStrategies parent) {
        m_parent = parent;
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Multi Stock Strategy: In progress");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));

        // Create a panel for stock entries using GridBagLayout
        JPanel stockEntriesPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Add column headers
        gbc.gridx = 0;
        gbc.gridy = 0;
        stockEntriesPanel.add(new JLabel("Stock"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        stockEntriesPanel.add(new JLabel("Quantity"), gbc);
        gbc.gridx = 2;
        gbc.gridy = 0;
        stockEntriesPanel.add(new JLabel("Price"), gbc);
        gbc.gridx = 3;
        gbc.gridy = 0;
        stockEntriesPanel.add(new JLabel("Action"), gbc);

        // Create stock entry fields
        for (int i = 0; i < MAX_STOCKS; i++) {
            StockEntry entry = new StockEntry();
            m_stockEntries.add(entry);

            gbc.gridy = i + 1;

            gbc.gridx = 0;
            stockEntriesPanel.add(new JLabel("Stock " + (i + 1) + ":"), gbc);

            gbc.gridx = 1;
            stockEntriesPanel.add(entry.symbol, gbc);

            gbc.gridx = 2;
            stockEntriesPanel.add(entry.quantity, gbc);

            gbc.gridx = 3;
            stockEntriesPanel.add(entry.price, gbc);

            gbc.gridx = 4;
            stockEntriesPanel.add(entry.isSell, gbc);
        }

        mainPanel.add(stockEntriesPanel);

        // Add buttons
        VerticalPanel buttonPanel = new VerticalPanel();

        HtmlButton populateDefaults = new HtmlButton("Populate defaults") {
            @Override
            protected void actionPerformed() {
                populateDefaultValues();
            }
        };

        m_placeOrderButton = new HtmlButton("Place orders") {
            @Override
            protected void actionPerformed() {
                placeOrders();
            }
        };
        m_placeOrderButton.setVisible(false);

        buttonPanel.add(populateDefaults);
        buttonPanel.add(m_placeOrderButton);

        mainPanel.add(buttonPanel);
        mainPanel.add(m_status);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Populates default values for common tech stocks (AAPL, MSFT, GOOGL, AMZN).
     * Sets default quantities to 100 shares and marks all as SELL orders.
     */
    private void populateDefaultValues() {
        // Example default values
        String[] defaultSymbols = { "AAPL", "MSFT", "GOOGL", "AMZN" };
        String[] defaultQuantities = { "100", "100", "100", "100" };

        for (int i = 0; i < m_stockEntries.size(); i++) {
            StockEntry entry = m_stockEntries.get(i);
            entry.symbol.setText(defaultSymbols[i]);
            entry.quantity.setText(defaultQuantities[i]);
            entry.isSell.setSelected(true);
        }

        m_status.setText("Default values populated");
        m_placeOrderButton.setVisible(true);
    }

    /**
     * Places limit orders for all stocks with non-empty symbols.
     * Each order is submitted independently as a limit order with GTC time in force.
     */
    private void placeOrders() {
        m_status.setText("Placing orders...");

        for (StockEntry entry : m_stockEntries) {
            if (entry.symbol.getText().isEmpty())
                continue;

            Contract contract = new Contract();
            contract.symbol(entry.symbol.getText());
            contract.secType("STK");
            contract.exchange("SMART");
            contract.currency("USD");

            Order order = new Order();
            order.orderType("LMT");
            order.lmtPrice(Double.parseDouble(entry.price.getText()));
            order.action(entry.isSell.isSelected() ? "SELL" : "BUY");
            order.totalQuantity(Decimal.get(Integer.parseInt(entry.quantity.getText())));
            order.tif("GTC");
            m_parent.applyAccountToOrder(order);

            m_parent.controller().placeOrModifyOrder(contract, order, new ApiController.IOrderHandler() {
                @Override
                public void orderState(OrderState orderState, Order order) {
                    m_status.setText("Order status: " + orderState.getStatus());
                }

                @Override
                public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining,
                        double avgFillPrice, int permId, int parentId, double lastFillPrice,
                        int clientId, String whyHeld, double mktCapPrice) {
                }

                @Override
                public void handle(int errorCode, String errorMsg) {
                    if (errorCode != 0) {
                        m_status.setText("Error: " + errorMsg);
                    }
                }
            });
        }
    }
}