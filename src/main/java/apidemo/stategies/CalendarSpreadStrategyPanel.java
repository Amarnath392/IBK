package apidemo.stategies;

import apidemo.util.HtmlButton;
import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;
import com.ib.client.*;
import com.ib.controller.ApiController;
import com.toedter.calendar.JCalendar;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JMonthChooser;
import com.toedter.calendar.JTextFieldDateEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

/**
 * Panel for executing Calendar Spread option strategies on SPY.
 * A calendar spread involves selling near-term options and buying longer-term options
 * at the same or different strike prices. This strategy profits from time decay.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic SPY price fetching</li>
 *   <li>Configurable call and put spreads</li>
 *   <li>Support for both call and put calendar spreads</li>
 *   <li>Automatic contract detail population</li>
 *   <li>Combo order placement as a single transaction</li>
 * </ul>
 * 
 * @author IBK Trading System
 * @version 1.0
 */
public class CalendarSpreadStrategyPanel extends JPanel {
    private final TradingStrategies m_parent;
    private final JDateChooser m_currentExpiryDate;
    private final JDateChooser m_nextExpiryDate;
    private final UpperField m_spotPrice = new UpperField();
    private final UpperField m_callSellLengthFromSpot = new UpperField();
    private final UpperField m_putSellLengthFromSpot = new UpperField();
    private final UpperField m_callBuyLengthFromSell = new UpperField();
    private final UpperField m_putBuyLengthFromSell = new UpperField();
    private final UpperField m_quantity = new UpperField();
    private final JCheckBox m_useCallSellStrikeForBuying = new JCheckBox();
    private final JCheckBox m_usePutSellStrikeForBuying = new JCheckBox();
    private final JCheckBox m_both = new JCheckBox();
    private final JCheckBox m_call = new JCheckBox();
    private final JCheckBox m_put = new JCheckBox();
    private final JLabel m_status = new JLabel();
    HtmlButton placeOrder;
    private int numberOfContractsLoaded = 0;
    private static final int TOTAL_CONTRACTS = 4;

    private Contract m_putSellContract, m_callSellContract, m_putBuyContract, m_callBuyContract;

    /**
     * Enum representing the type of option contract in the calendar spread.
     */
    enum ContractType {
        PUT_SELL, CALL_SELL, PUT_BUY, CALL_BUY;
    }

    // New member variables for legs
    private ComboLeg m_sellCallLeg, m_sellPutLeg, m_buyCallLeg, m_buyPutLeg;

    transient ApiController.TopMktDataAdapter m_stockListener = new ApiController.TopMktDataAdapter() {
        @Override
        public void tickPrice(TickType tickType, double price, TickAttrib attribs) {
            if (tickType == TickType.LAST || tickType == TickType.DELAYED_LAST || tickType == TickType.CLOSE) {
                populateDefaults(customRound(price));
                m_parent.controller().cancelTopMktData(m_stockListener);
            }
        }
    };

    /**
     * Creates option contracts and populates their details from IB.
     * Resets the contract counter and requests details for all four legs.
     */
    private void createAndPopulateContracts() {
        createContracts();
        numberOfContractsLoaded = 0;
        populateContractDetails(m_callSellContract, ContractType.CALL_SELL);
        populateContractDetails(m_putSellContract, ContractType.PUT_SELL);
        populateContractDetails(m_callBuyContract, ContractType.CALL_BUY);
        populateContractDetails(m_putBuyContract, ContractType.PUT_BUY);
    }

    /**
     * Constructs a new CalendarSpreadStrategyPanel.
     * 
     * @param parent the parent TradingStrategies instance for accessing the API controller
     */
    public CalendarSpreadStrategyPanel(TradingStrategies parent) {
        m_parent = parent;
        setLayout(new BorderLayout());

        // Initialize date choosers with proper configuration
        m_currentExpiryDate = createDateChooser();
        m_nextExpiryDate = createDateChooser();

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Calendar Spread Strategy");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));

        VerticalPanel p = getInputPanel();
        VerticalPanel butPanel = getButtonPanel();
        mainPanel.add(p);
        mainPanel.add(butPanel);
        mainPanel.add(m_status);

        add(mainPanel, BorderLayout.CENTER);

        // Add action listeners for both checkboxes
        m_useCallSellStrikeForBuying.addActionListener(new Action() {
            @Override
            public Object getValue(String key) {
                return m_useCallSellStrikeForBuying.isSelected();
            }

            @Override
            public void putValue(String key, Object value) {
            }

            @Override
            public void setEnabled(boolean b) {
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void addPropertyChangeListener(PropertyChangeListener listener) {
            }

            @Override
            public void removePropertyChangeListener(PropertyChangeListener listener) {
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> {
                    m_callBuyLengthFromSell.setVisible(!m_useCallSellStrikeForBuying.isSelected());
                });
            }
        });

        m_usePutSellStrikeForBuying.addActionListener(new Action() {
            @Override
            public Object getValue(String key) {
                return m_usePutSellStrikeForBuying.isSelected();
            }

            @Override
            public void putValue(String key, Object value) {
            }

            @Override
            public void setEnabled(boolean b) {
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void addPropertyChangeListener(PropertyChangeListener listener) {
            }

            @Override
            public void removePropertyChangeListener(PropertyChangeListener listener) {
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> {
                    m_putBuyLengthFromSell.setVisible(!m_usePutSellStrikeForBuying.isSelected());
                });
            }
        });
    }

    /**
     * Creates and configures a date chooser component with calendar popup.
     * 
     * @return configured JDateChooser instance
     */
    private JDateChooser createDateChooser() {
        JDateChooser dateChooser = new JDateChooser();
        dateChooser.setPreferredSize(new Dimension(150, 25));
        dateChooser.setDateFormatString("yyyy-MM-dd");
        dateChooser.setCalendar(Calendar.getInstance());

        // Configure the text field editor
        JTextFieldDateEditor editor = (JTextFieldDateEditor) dateChooser.getDateEditor();
        editor.setBackground(Color.WHITE);

        // Configure the calendar popup
        JCalendar calendar = dateChooser.getJCalendar();
        calendar.setPreferredSize(new Dimension(300, 300));
        calendar.setMinSelectableDate(new Date()); // Prevent past dates
        calendar.setMaxSelectableDate(new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)); // Limit to 1
                                                                                                          // year

        // Fix narrow month picker
        JMonthChooser monthChooser = calendar.getMonthChooser();
        monthChooser.setPreferredSize(new Dimension(100, 25)); // Increase width
        monthChooser.getComboBox().setPreferredSize(new Dimension(100, 25)); // Ensure combo box is wider

        // Set calendar properties
        calendar.setWeekOfYearVisible(false);
        calendar.setDecorationBackgroundColor(Color.WHITE);
        calendar.setDecorationBordersVisible(true);

        return dateChooser;
    }

    /**
     * Creates the input panel with all strategy parameter fields.
     * 
     * @return VerticalPanel containing input fields
     */
    private VerticalPanel getInputPanel() {
        VerticalPanel p = new VerticalPanel();
        p.add("Current expiry", m_currentExpiryDate);
        p.add("Next expiry", m_nextExpiryDate);
        p.add("Spot price", m_spotPrice);

        p.add("Quantity", m_quantity);

        JPanel hp = new JPanel();
        hp.setLayout(new BoxLayout(hp, BoxLayout.X_AXIS));

        hp.add(new JLabel("Use both ")); // Label before buttons
        hp.add(Box.createRigidArea(new Dimension(10, 0))); // Spacing

        hp.add(m_both);
        hp.add(Box.createRigidArea(new Dimension(10, 0))); // Spacing

        hp.add(new JLabel("Call: "));
        hp.add(m_call);
        hp.add(Box.createRigidArea(new Dimension(10, 0))); // Spacing

        hp.add(new JLabel("Put: "));
        hp.add(m_put);

        p.add(hp);

        JPanel hp2 = new JPanel();
        hp2.setLayout(new BoxLayout(hp2, BoxLayout.X_AXIS));

        VerticalPanel callPanel = new VerticalPanel();
        callPanel.add("Call Sell length from spot", m_callSellLengthFromSpot);
        callPanel.add("Use Call Sell strike for buying", m_useCallSellStrikeForBuying);
        callPanel.add("Call Buy length from sell", m_callBuyLengthFromSell);

        VerticalPanel putPanel = new VerticalPanel();
        putPanel.add("Put Sell length from spot", m_putSellLengthFromSpot);
        putPanel.add("Use Put Sell strike for buying", m_usePutSellStrikeForBuying);
        putPanel.add("Put Buy length from sell", m_putBuyLengthFromSell);
        hp2.add(callPanel);
        hp2.add(putPanel);
        p.add(hp2);
        return p;
    }

    /**
     * Creates the button panel with action buttons.
     * 
     * @return VerticalPanel containing action buttons
     */
    private VerticalPanel getButtonPanel() {
        HtmlButton populateDefaults = new HtmlButton("Populate defaults") {
            @Override
            protected void actionPerformed() {
                placeOrder.setVisible(false);
                m_status.setText("Loading default values... Please wait.");
                fetchCurrentSPYPrice();
            }
        };

        HtmlButton populateContracts = new HtmlButton("Populate contracts") {
            @Override
            protected void actionPerformed() {
                m_status.setText("Fetching contract details...");
                createAndPopulateContracts();
            }
        };

        placeOrder = new HtmlButton("Place order") {
            @Override
            protected void actionPerformed() {
                m_status.setText("Submitting order... Please wait.");
                onPlaceOrder();
            }
        };

        placeOrder.setVisible(false);
        VerticalPanel butPanel = new VerticalPanel();
        butPanel.add(populateDefaults);
        butPanel.add(populateContracts);
        butPanel.add(placeOrder);
        return butPanel;
    }

    /**
     * Populates default strategy parameters based on current spot price.
     * Sets reasonable default values for strikes and quantities.
     * 
     * @param spotPrice current SPY spot price
     */
    private void populateDefaults(double spotPrice) {
        SwingUtilities.invokeLater(() -> {
            m_spotPrice.setText("" + customRound(spotPrice));
            populateDates();
            m_callSellLengthFromSpot.setText(5);
            m_putSellLengthFromSpot.setText(5);
            m_useCallSellStrikeForBuying.setSelected(false);
            m_usePutSellStrikeForBuying.setSelected(false);
            m_callBuyLengthFromSell.setVisible(true);
            m_putBuyLengthFromSell.setVisible(true);
            m_callBuyLengthFromSell.setText(3);
            m_putBuyLengthFromSell.setText(3);
            m_quantity.setText("1");
            m_status.setText("Default values loaded successfully.");
        });
    }

    /**
     * Populates default expiry dates.
     * Sets current expiry to 7 days from today and next expiry to 14 days.
     */
    private void populateDates() {
        LocalDate todayDate = LocalDate.now();
        Calendar calendar = Calendar.getInstance();
        calendar.set(todayDate.getYear(), todayDate.getMonthValue() - 1, todayDate.getDayOfMonth());
        Date today = calendar.getTime();

        calendar.add(Calendar.DAY_OF_MONTH, 7);
        m_currentExpiryDate.setDate(calendar.getTime());

        calendar.setTime(today);
        calendar.add(Calendar.DAY_OF_MONTH, 14);
        m_nextExpiryDate.setDate(calendar.getTime());
    }

    /**
     * Requests contract details from IB for a given option contract.
     * Updates the status and enables order placement when all contracts are loaded.
     * 
     * @param contract the option contract to query
     * @param type the type of contract (CALL_SELL, PUT_SELL, CALL_BUY, PUT_BUY)
     */
    protected void populateContractDetails(Contract contract, final ContractType type) {
        m_parent.controller().reqContractDetails(contract, list -> {
            if (list.size() > 1) {
                m_parent.show("ERROR: More than one contract details found for given contract.");
                m_status.setText("ERROR: More than one contract details found for given contract.");
                return;
            }

            if (list.isEmpty()) {
                m_status.setText("ERROR: Contract details not found. Please check expiry date.");
                return;
            }

            for (ContractDetails details : list) {
                populateLegs(details.contract(), type);
            }

            numberOfContractsLoaded++;
            if (numberOfContractsLoaded == TOTAL_CONTRACTS) {
                placeOrder.setVisible(true);
                m_status.setText("Contracts retrieved successfully. Proceed with order.");
            }
        });
    }

    /**
     * Places the calendar spread order as a combo/BAG order.
     * Creates a BAG contract with all four legs and submits as a limit order.
     */
    protected void onPlaceOrder() {
        Contract comboContract = new Contract();
        comboContract.symbol("SPY");
        comboContract.secType("BAG");
        comboContract.currency("USD");
        comboContract.exchange("SMART");

        comboContract.comboLegs(new ArrayList<>(Arrays.asList(m_sellCallLeg, m_sellPutLeg, m_buyCallLeg, m_buyPutLeg)));

        Order comboOrder = new Order();
        comboOrder.orderType("LMT");
        comboOrder.lmtPrice(0.50);
        comboOrder.action("BUY");
        comboOrder.totalQuantity(Decimal.get(m_quantity.getInt()));
        comboOrder.tif("GTC");
        m_parent.applyAccountToOrder(comboOrder);

        m_parent.controller().placeOrModifyOrder(comboContract, comboOrder,
                new ApiController.IOrderHandler() {
                    @Override
                    public void orderState(OrderState orderState, Order order) {
                        m_status.setText("Order status: " + orderState.getStatus());
                        placeOrder.setVisible(false);
                    }

                    @Override
                    public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining,
                            double avgFillPrice, int permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {
                    }

                    @Override
                    public void handle(int errorCode, String errorMsg) {
                    }
                });
    }

    /**
     * Fetches the current SPY stock price from IB.
     * Uses market data subscription to get the latest price.
     */
    private void fetchCurrentSPYPrice() {
        Contract spyContract = new Contract();
        spyContract.symbol("SPY");
        spyContract.secType("STK");
        spyContract.exchange("SMART");
        spyContract.currency("USD");
        m_parent.controller().reqTopMktData(spyContract, "", false, false, m_stockListener);
    }

    /**
     * Creates option contracts for all four legs of the calendar spread.
     * Calculates strike prices based on spot price and configured deltas.
     */
    private void createContracts() {
        double spotPrice = m_spotPrice.getDouble();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateAfter7Days = sdf.format(m_currentExpiryDate.getDate());
        String dateAfter14Days = sdf.format(m_nextExpiryDate.getDate());

        double callSellDelta = getPositiveOrZero(m_callSellLengthFromSpot.getDouble());
        double putSellDelta = getPositiveOrZero(m_putSellLengthFromSpot.getDouble());
        m_callSellContract = createOptionContract("C", spotPrice + callSellDelta, dateAfter7Days);
        m_putSellContract = createOptionContract("P", spotPrice - putSellDelta, dateAfter7Days);

        double callBuyDelta = m_useCallSellStrikeForBuying.isSelected() ? callSellDelta
                : callSellDelta +  getPositiveOrZero(m_callBuyLengthFromSell.getDouble());

        double putBuyDelta = m_usePutSellStrikeForBuying.isSelected() ? putSellDelta
                :putSellDelta + getPositiveOrZero(m_putBuyLengthFromSell.getDouble());

        m_callBuyContract = createOptionContract("C", spotPrice + callBuyDelta, dateAfter14Days);
        m_putBuyContract = createOptionContract("P", spotPrice - putBuyDelta, dateAfter14Days);
    }

    /**
     * Creates an SPY option contract with specified parameters.
     * 
     * @param right option right - "C" for call, "P" for put
     * @param strike strike price
     * @param date expiration date in yyyyMMdd format
     * @return configured option Contract
     */
    private Contract createOptionContract(String right, double strike, String date) {
        Contract contract = new Contract();
        contract.symbol("SPY");
        contract.secType("OPT");
        contract.exchange("SMART");
        contract.currency("USD");
        contract.lastTradeDateOrContractMonth(date);
        contract.strike(strike);
        contract.right(right);
        contract.multiplier("100");
        return contract;
    }

    /**
     * Populates combo legs for the BAG order based on contract type.
     * 
     * @param contract the option contract with populated conid
     * @param type the type of contract determining BUY or SELL action
     */
    private void populateLegs(Contract contract, ContractType type) {
        ComboLeg leg = new ComboLeg();
        leg.conid(contract.conid());
        leg.ratio(1);
        leg.exchange("SMART");

        switch (type) {
            case CALL_BUY:
                leg.action("BUY");
                m_buyCallLeg = leg;
                break;

            case CALL_SELL:
                leg.action("SELL");
                m_sellCallLeg = leg;
                break;

            case PUT_BUY:
                leg.action("BUY");
                m_buyPutLeg = leg;
                break;

            case PUT_SELL:
                leg.action("SELL");
                m_sellPutLeg = leg;
                break;

            default:
                throw new IllegalArgumentException("Invalid contract type: " + type);
        }
    }

    /**
     * Custom rounding function that rounds to nearest integer.
     * Values > 0.5 round up, otherwise round down.
     * 
     * @param value the value to round
     * @return rounded value
     */
    public static double customRound(double value) {
        double fractionalPart = value - Math.floor(value);
        if (fractionalPart > 0.5) {
            return Math.ceil(value);
        } else {
            return Math.floor(value);
        }
    }

    /**
     * Returns the positive value or zero if negative.
     * 
     * @param value the input value
     * @return max(value, 0)
     */
    public static double getPositiveOrZero(double value) {
        return Math.max(value, 0);
    }
}