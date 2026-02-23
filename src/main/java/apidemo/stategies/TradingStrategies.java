package apidemo.stategies;

import apidemo.util.*;
import com.ib.controller.ApiConnection;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IConnectionHandler;
import com.ib.controller.Formats;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application class for Interactive Brokers trading strategies.
 * Provides a GUI interface for connecting to IB TWS/Gateway and executing various trading strategies
 * including Calendar Spreads, Strangles, and Multi-Stock trades.
 * 
 * @author IBK Trading System
 * @version 1.0
 */
public class TradingStrategies implements IConnectionHandler {
    static {
        NewLookAndFeel.register();
    }
    public static TradingStrategies INSTANCE;

    private final IConnectionConfiguration m_connectionConfiguration;
    private final JTextArea m_inLog = new JTextArea();
    private final JTextArea m_outLog = new JTextArea();
    private final Logger m_inLogger = new Logger(m_inLog);
    private final Logger m_outLogger = new Logger(m_outLog);
    private ApiController m_controller;
    private final List<String> m_acctList = new ArrayList<>();
    private String m_selectedAccount = null;
    private final JFrame m_frame = new JFrame();
    private final ConnectionPanel m_connectionPanel;
    private final NewTabbedPanel m_tabbedPanel = new NewTabbedPanel(true);
    private final JTextArea m_msg = new JTextArea();

    // Strategy panels
    private final CalendarSpreadStrategyPanel m_calendarSpreadPanel = new CalendarSpreadStrategyPanel(this);
    private final StrangleStrategyPanel m_stranglePanel = new StrangleStrategyPanel(this);
    private final MultiStockStrategyPanel m_multiStockPanel = new MultiStockStrategyPanel(this);
    private final PreMarketCloseOrderPanel m_preMarketCloseOrderPanel = new PreMarketCloseOrderPanel(this);

    /**
     * Application entry point. Creates and starts the TradingStrategies application
     * with default connection configuration.
     * 
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        start(new TradingStrategies(new IConnectionConfiguration.DefaultConnectionConfiguration()));
    }

    /**
     * Starts the trading strategies application with a given instance.
     * Sets the global INSTANCE reference and initializes the GUI.
     * 
     * @param instance the TradingStrategies instance to start
     */
    public static void start(TradingStrategies instance) {
        INSTANCE = instance;
        instance.run();
    }

    /**
     * Constructs a new TradingStrategies application.
     * 
     * @param connectionConfig configuration for IB connection parameters
     */
    public TradingStrategies(IConnectionConfiguration connectionConfig) {
        m_connectionConfiguration = connectionConfig;
        m_connectionPanel = new ConnectionPanel();
    }

    /**
     * Gets or creates the API controller for communicating with IB TWS/Gateway.
     * Lazily initializes the controller on first access.
     * 
     * @return the ApiController instance
     */
    public ApiController controller() {
        if (m_controller == null) {
            m_controller = new ApiController(this, getInLogger(), getOutLogger());
        }
        return m_controller;
    }

    /**
     * Gets the logger for incoming messages from IB.
     * 
     * @return the incoming message logger
     */
    private ApiConnection.ILogger getInLogger() {
        return m_inLogger;
    }

    /**
     * Gets the logger for outgoing messages to IB.
     * 
     * @return the outgoing message logger
     */
    private ApiConnection.ILogger getOutLogger() {
        return m_outLogger;
    }

    /**
     * Gets the currently selected account.
     * 
     * @return the selected account identifier, or null if none selected
     */
    public String getSelectedAccount() {
        return m_selectedAccount;
    }

    /**
     * Gets the list of all available accounts.
     * 
     * @return list of account identifiers
     */
    public List<String> getAccountList() {
        return new ArrayList<>(m_acctList);
    }

    /**
     * Sets the selected account.
     * 
     * @param account the account identifier to select
     */
    public void setSelectedAccount(String account) {
        m_selectedAccount = account;
        show("Selected account: " + account);
    }

    /**
     * Initializes and displays the main application window.
     * Sets up the tabbed interface with strategy panels and log views.
     * Automatically attempts to connect to IB TWS/Gateway on localhost:7497.
     */
    private void run() {
        m_tabbedPanel.addTab("Connection", m_connectionPanel);
        m_tabbedPanel.addTab("Calendar Spread", m_calendarSpreadPanel);
        m_tabbedPanel.addTab("Strangle", m_stranglePanel);
        m_tabbedPanel.addTab("Multi Stock", m_multiStockPanel);
        m_tabbedPanel.addTab("Pre-Market Close Orders", m_preMarketCloseOrderPanel);

        m_msg.setEditable(false);
        m_msg.setLineWrap(true);

        JScrollPane msgScroll = new JScrollPane(m_msg);
        msgScroll.setPreferredSize(new Dimension(10000, 120));

        JScrollPane outLogScroll = new JScrollPane(m_outLog);
        outLogScroll.setPreferredSize(new Dimension(10000, 120));

        JScrollPane inLogScroll = new JScrollPane(m_inLog);
        inLogScroll.setPreferredSize(new Dimension(10000, 120));

        NewTabbedPanel bot = new NewTabbedPanel();
        bot.addTab("Messages", msgScroll);
        bot.addTab("Log (out)", outLogScroll);
        bot.addTab("Log (in)", inLogScroll);

        m_frame.add(m_tabbedPanel);
        m_frame.add(bot, BorderLayout.SOUTH);
        m_frame.setSize(1024, 768);
        m_frame.setVisible(true);
        m_frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        controller().connect("127.0.0.1", 7497, 0,
                m_connectionConfiguration.getDefaultConnectOptions() != null
                        ? m_connectionConfiguration.getDefaultConnectOptions()
                        : null);
    }

    /**
     * Called when successfully connected to IB TWS/Gateway.
     * Requests server time and bulletin subscriptions.
     */
    @Override
    public void connected() {
        show("connected");
        m_connectionPanel.m_status.setText("connected");

        controller().reqCurrentTime(time -> show("Server date/time is " + Formats.fmtDate(time * 1000)));

        controller().reqBulletins(true, (msgId, newsType, message, exchange) -> {
            String str = String.format("Received bulletin:  type=%s  exchange=%s", newsType, exchange);
            show(str);
            show(message);
        });
    }

    /**
     * Called when disconnected from IB TWS/Gateway.
     */
    @Override
    public void disconnected() {
        show("disconnected");
        m_connectionPanel.m_status.setText("disconnected");
    }

    /**
     * Called when the account list is received from IB.
     * 
     * @param list list of account identifiers
     */
    @Override
    public void accountList(List<String> list) {
        show("Received account list");
        m_acctList.clear();
        m_acctList.addAll(list);
        
        // Update the connection panel dropdown with the account list
        m_connectionPanel.updateAccountList(list);
        
        // Auto-select first account if none selected
        if (m_selectedAccount == null && !list.isEmpty()) {
            setSelectedAccount(list.get(0));
        }
    }

    /**
     * Called when an exception occurs in the API connection.
     * 
     * @param e the exception that occurred
     */
    @Override
    public void error(Exception e) {
        show(e.toString());
    }

    /**
     * Called when a message is received from IB.
     * 
     * @param id request identifier
     * @param errorCode error code (0 if not an error)
     * @param errorMsg error or informational message
     * @param advancedOrderRejectJson JSON string with advanced order rejection details (may be null)
     */
    @Override
    public void message(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        String error = id + " " + errorCode + " " + errorMsg;
        if (advancedOrderRejectJson != null) {
            error += (" " + advancedOrderRejectJson);
        }
        show(error);
    }

    /**
     * Displays a message in the application's message log.
     * Thread-safe - executes on the Swing event dispatch thread.
     * 
     * @param str the message to display
     */
    @Override
    public void show(String str) {
        SwingUtilities.invokeLater(() -> {
            m_msg.append(str);
            m_msg.append("\n\n");

            Dimension d = m_msg.getSize();
            m_msg.scrollRectToVisible(new Rectangle(0, d.height, 1, 1));
        });
    }

    /**
     * Panel for managing the connection to IB TWS/Gateway.
     * Provides UI controls for host, port, client ID, and connection options.
     */
    private class ConnectionPanel extends JPanel {
        private final JTextField m_host = new JTextField(m_connectionConfiguration.getDefaultHost(), 10);
        private final JTextField m_port = new JTextField(m_connectionConfiguration.getDefaultPort(), 7);
        private final JTextField m_connectOptionsTF = new JTextField(
                m_connectionConfiguration.getDefaultConnectOptions(), 30);
        private final JTextField m_clientId = new JTextField("0", 7);
        private final JLabel m_status = new JLabel("Disconnected");
        private final JLabel m_defaultPortNumberLabel = new JLabel(
                "<html>Live Trading ports:<b> TWS: 7496; IB Gateway: 4001.</b><br>"
                        + "Simulated Trading ports for new installations of "
                        + "version 954.1 or newer: "
                        + "<b>TWS: 7497; IB Gateway: 4002</b></html>");
        private final TCombo<String> m_accountCombo = new TCombo<>();
        private final JLabel m_accountLabel = new JLabel("No accounts loaded");

        ConnectionPanel() {
            HtmlButton connect = new HtmlButton("Connect") {
                @Override
                public void actionPerformed() {
                    onConnect();
                }
            };

            HtmlButton disconnect = new HtmlButton("Disconnect") {
                @Override
                public void actionPerformed() {
                    controller().disconnect();
                }
            };

            // Add action listener to account dropdown
            m_accountCombo.addActionListener(e -> {
                String selected = m_accountCombo.getSelectedItem();
                if (selected != null) {
                    setSelectedAccount(selected);
                }
            });

            JPanel p1 = new VerticalPanel();
            p1.add("Host", m_host);
            p1.add("Port", m_port);
            p1.add("Client ID", m_clientId);
            if (m_connectionConfiguration.getDefaultConnectOptions() != null) {
                p1.add("Connect options", m_connectOptionsTF);
            }
            p1.add("", m_defaultPortNumberLabel);

            JPanel p2 = new VerticalPanel();
            p2.add(connect);
            p2.add(disconnect);
            p2.add(Box.createVerticalStrut(20));

            JPanel p3 = new VerticalPanel();
            p3.setBorder(new EmptyBorder(20, 0, 0, 0));
            p3.add("Connection status: ", m_status);
            p3.add("Account: ", m_accountCombo);
            p3.add("", m_accountLabel);

            JPanel p4 = new JPanel(new BorderLayout());
            p4.add(p1, BorderLayout.WEST);
            p4.add(p2);
            p4.add(p3, BorderLayout.SOUTH);

            setLayout(new BorderLayout());
            add(p4, BorderLayout.NORTH);
        }

        /**
         * Handles the connect button action.
         * Parses connection parameters and initiates connection to IB.
         */
        void onConnect() {
            int port = Integer.parseInt(m_port.getText());
            int clientId = Integer.parseInt(m_clientId.getText());
            controller().connect(m_host.getText(), port, clientId, m_connectOptionsTF.getText());
        }

        /**
         * Updates the account dropdown with the list of accounts received from IB.
         * 
         * @param accounts list of account identifiers
         */
        void updateAccountList(List<String> accounts) {
            SwingUtilities.invokeLater(() -> {
                m_accountCombo.removeAllItems();
                for (String account : accounts) {
                    m_accountCombo.addItem(account);
                }
                
                if (!accounts.isEmpty()) {
                    m_accountCombo.setSelectedIndex(0);
                    m_accountLabel.setText(accounts.size() + " account(s) available");
                } else {
                    m_accountLabel.setText("No accounts found");
                }
            });
        }
    }

    /**
     * Logger implementation for API messages.
     * Displays log messages in a JTextArea component.
     */
    private static class Logger implements ApiConnection.ILogger {
        final private JTextArea m_area;

        Logger(JTextArea area) {
            m_area = area;
        }

        @Override
        public void log(final String str) {
            SwingUtilities.invokeLater(() -> {
                // m_area.append(str);
                // Dimension d = m_area.getSize();
                // m_area.scrollRectToVisible(new Rectangle(0, d.height, 1, 1));
            });
        }
    }
}