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
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

/**
 * Panel for executing Strangle option strategies on SPY.
 * A strangle involves selling both a call and put option at different strike prices,
 * both out-of-the-money. This strategy profits when the underlying stays within a range.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic SPY price fetching</li>
 *   <li>Configurable call and put strike distances</li>
 *   <li>Single expiration date for both options</li>
 *   <li>Automatic contract detail population</li>
 *   <li>Simultaneous placement of call and put orders</li>
 * </ul>
 * 
 * @author IBK Trading System
 * @version 1.0
 */
public class StrangleStrategyPanel extends JPanel {
    private final TradingStrategies m_parent;
    private final JDateChooser m_expiryDate;
    private final UpperField m_spotPrice = new UpperField();
    private final UpperField m_callStrikeDistance = new UpperField();
    private final UpperField m_putStrikeDistance = new UpperField();
    private final UpperField m_comboLimitPrice = new UpperField();
    private final JLabel m_status = new JLabel();
    private HtmlButton m_placeOrderButton;
    private Contract m_callContract, m_putContract;
    private int m_callContractId = 0;
    private int m_putContractId = 0;
    private int contractsLoaded = 0;
    private static final int TOTAL_CONTRACTS = 2;

    transient ApiController.TopMktDataAdapter m_stockListener = new ApiController.TopMktDataAdapter() {
        @Override
        public void tickPrice(TickType tickType, double price, TickAttrib attribs) {
            if (tickType == TickType.LAST || tickType == TickType.DELAYED_LAST || tickType == TickType.CLOSE) {
                populateDefaults(CalendarSpreadStrategyPanel.customRound(price));
                m_parent.controller().cancelTopMktData(m_stockListener);
            }
        }
    };

    /**
     * Constructs a new StrangleStrategyPanel.
     * 
     * @param parent the parent TradingStrategies instance for accessing the API controller
     */
    public StrangleStrategyPanel(TradingStrategies parent) {
        m_parent = parent;
        setLayout(new BorderLayout());

        // Initialize date chooser with proper configuration
        m_expiryDate = createDateChooser();

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Strangle Strategy: In progress");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));

        VerticalPanel inputPanel = getInputPanel();
        VerticalPanel buttonPanel = getButtonPanel();
        mainPanel.add(inputPanel);
        mainPanel.add(buttonPanel);
        mainPanel.add(m_status);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Creates the input panel with all strategy parameter fields.
     * 
     * @return VerticalPanel containing input fields for expiry date, spot price, and strike distances
     */
    private VerticalPanel getInputPanel() {
        VerticalPanel p = new VerticalPanel();
        p.add("Expiry date", m_expiryDate);
        p.add("Spot price", m_spotPrice);
        p.add("Call strike distance", m_callStrikeDistance);
        p.add("Put strike distance", m_putStrikeDistance);
        p.add("Combo limit price", m_comboLimitPrice);
        return p;
    }

    /**
     * Creates the button panel with action buttons.
     * 
     * @return VerticalPanel containing action buttons for populating defaults, contracts, and placing orders
     */
    private VerticalPanel getButtonPanel() {
        HtmlButton populateDefaults = new HtmlButton("Populate defaults") {
            @Override
            protected void actionPerformed() {
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

        m_placeOrderButton = new HtmlButton("Place order") {
            @Override
            protected void actionPerformed() {
                m_status.setText("Submitting order... Please wait.");
                onPlaceOrder();
            }
        };
        m_placeOrderButton.setVisible(false);

        VerticalPanel butPanel = new VerticalPanel();
        butPanel.add(populateDefaults);
        butPanel.add(populateContracts);
        butPanel.add(m_placeOrderButton);
        return butPanel;
    }

    /**
     * Creates and configures a date chooser component with calendar popup.
     * Limits selectable dates to between today and one year from today.
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
     * Populates default strategy parameters based on current spot price.
     * Sets default strike distances of 5 points from spot.
     * 
     * @param spotPrice current SPY spot price
     */
    private void populateDefaults(double spotPrice) {
        SwingUtilities.invokeLater(() -> {
            m_spotPrice.setText("" + CalendarSpreadStrategyPanel.customRound(spotPrice));
            populateDates();
            m_callStrikeDistance.setText("5");
            m_putStrikeDistance.setText("5");
            m_comboLimitPrice.setText("1.0");
            m_status.setText("Default values loaded successfully.");
        });
    }

    /**
     * Populates default expiry date to 7 days from today.
     */
    private void populateDates() {
        LocalDate todayDate = LocalDate.now();
        Calendar calendar = Calendar.getInstance();
        calendar.set(todayDate.getYear(), todayDate.getMonthValue() - 1, todayDate.getDayOfMonth());
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        m_expiryDate.setDate(calendar.getTime());
    }

    /**
     * Creates option contracts and populates their details from IB.
     * Resets the contract counter and requests details for both call and put options.
     */
    private void createAndPopulateContracts() {
        createContracts();
        contractsLoaded = 0;
        populateContractDetails(m_callContract, true);
        populateContractDetails(m_putContract, false);
    }

    /**
     * Creates option contracts for both legs of the strangle.
     * Calculates strike prices based on spot price and configured distances.
     */
    private void createContracts() {
        double spotPrice = m_spotPrice.getDouble();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String expiryDate = sdf.format(m_expiryDate.getDate());

        double callStrike = spotPrice + Double.parseDouble(m_callStrikeDistance.getText());
        double putStrike = spotPrice - Double.parseDouble(m_putStrikeDistance.getText());

        m_callContract = createOptionContract("C", callStrike, expiryDate);
        m_putContract = createOptionContract("P", putStrike, expiryDate);
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
     * Requests contract details from IB for a given option contract.
     * Updates the status and enables order placement when both contracts are loaded.
     * 
     * @param contract the option contract to query
     * @param isCall true if this is a call option, false for put option
     */
    private void populateContractDetails(Contract contract, boolean isCall) {
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

            // Store the contract ID for BAG creation
            ContractDetails details = list.get(0);
            if (isCall) {
                m_callContractId = details.contract().conid();
            } else {
                m_putContractId = details.contract().conid();
            }

            contractsLoaded++;
            if (contractsLoaded == TOTAL_CONTRACTS) {
                m_placeOrderButton.setVisible(true);
                m_status.setText("Contracts retrieved successfully. Proceed with order.");
            }
        });
    }

    /**
     * Places a single BAG order containing both call and put options as a strangle strategy.
     * This ensures both legs appear as one combined trade in TWS.
     */
    private void onPlaceOrder() {
        // Create BAG contract for the strangle
        Contract comboContract = new Contract();
        comboContract.symbol("SPY");
        comboContract.secType("BAG");
        comboContract.currency("USD");
        comboContract.exchange("SMART");

        // Create combo leg for call option
        ComboLeg callLeg = new ComboLeg();
        callLeg.conid(m_callContractId);
        callLeg.ratio(1);
        callLeg.action("BUY");
        callLeg.exchange("SMART");

        // Create combo leg for put option
        ComboLeg putLeg = new ComboLeg();
        putLeg.conid(m_putContractId);
        putLeg.ratio(1);
        putLeg.action("BUY");
        putLeg.exchange("SMART");

        // Add legs to combo contract
        java.util.List<ComboLeg> comboLegs = new java.util.ArrayList<>();
        comboLegs.add(callLeg);
        comboLegs.add(putLeg);
        comboContract.comboLegs(comboLegs);

        // Create order for the combo
        Order comboOrder = new Order();
        comboOrder.orderType("LMT");
        comboOrder.lmtPrice(m_comboLimitPrice.getDouble()); // User-defined combo limit price
        comboOrder.action("SELL");
        comboOrder.totalQuantity(Decimal.get(1));
        comboOrder.tif("GTC");
        m_parent.applyAccountToOrder(comboOrder);

        // Place the combo order
        m_parent.controller().placeOrModifyOrder(comboContract, comboOrder,
                new ApiController.IOrderHandler() {
                    @Override
                    public void orderState(OrderState orderState, Order order) {
                        m_status.setText("Strangle order status: " + orderState.getStatus());
                        m_placeOrderButton.setVisible(false);
                    }

                    @Override
                    public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining,
                            double avgFillPrice, int permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {
                    }

                    @Override
                    public void handle(int errorCode, String errorMsg) {
                        m_status.setText("Error placing order: " + errorMsg);
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
}