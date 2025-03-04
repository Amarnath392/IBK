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

public class StrangleStrategyPanel extends JPanel {
    private final TradingStrategies m_parent;
    private JDateChooser m_expiryDate;
    private final UpperField m_spotPrice = new UpperField();
    private final UpperField m_callStrikeDistance = new UpperField();
    private final UpperField m_putStrikeDistance = new UpperField();
    private final JLabel m_status = new JLabel();
    private HtmlButton m_placeOrderButton;
    private Contract m_callContract, m_putContract;
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

    public StrangleStrategyPanel(TradingStrategies parent) {
        m_parent = parent;
        setLayout(new BorderLayout());

        // Initialize date chooser with proper configuration
        m_expiryDate = createDateChooser();

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Strangle Strategy");
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

    private VerticalPanel getInputPanel() {
        VerticalPanel p = new VerticalPanel();
        p.add("Expiry date", m_expiryDate);
        p.add("Spot price", m_spotPrice);
        p.add("Call strike distance", m_callStrikeDistance);
        p.add("Put strike distance", m_putStrikeDistance);
        return p;
    }

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

    private void populateDefaults(double spotPrice) {
        SwingUtilities.invokeLater(() -> {
            m_spotPrice.setText("" + CalendarSpreadStrategyPanel.customRound(spotPrice));
            populateDates();
            m_callStrikeDistance.setText("5");
            m_putStrikeDistance.setText("5");
            m_status.setText("Default values loaded successfully.");
        });
    }

    private void populateDates() {
        LocalDate todayDate = LocalDate.now();
        Calendar calendar = Calendar.getInstance();
        calendar.set(todayDate.getYear(), todayDate.getMonthValue() - 1, todayDate.getDayOfMonth());
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        m_expiryDate.setDate(calendar.getTime());
    }

    private void createAndPopulateContracts() {
        createContracts();
        contractsLoaded = 0;
        populateContractDetails(m_callContract, true);
        populateContractDetails(m_putContract, false);
    }

    private void createContracts() {
        double spotPrice = m_spotPrice.getDouble();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String expiryDate = sdf.format(m_expiryDate.getDate());

        double callStrike = spotPrice + Double.parseDouble(m_callStrikeDistance.getText());
        double putStrike = spotPrice - Double.parseDouble(m_putStrikeDistance.getText());

        m_callContract = createOptionContract("C", callStrike, expiryDate);
        m_putContract = createOptionContract("P", putStrike, expiryDate);
    }

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

            contractsLoaded++;
            if (contractsLoaded == TOTAL_CONTRACTS) {
                m_placeOrderButton.setVisible(true);
                m_status.setText("Contracts retrieved successfully. Proceed with order.");
            }
        });
    }

    private void onPlaceOrder() {
        // Place call order
        Order callOrder = new Order();
        callOrder.orderType("LMT");
        callOrder.lmtPrice(0.50);
        callOrder.action("SELL");
        callOrder.totalQuantity(Decimal.get(1));
        callOrder.tif("GTC");

        m_parent.controller().placeOrModifyOrder(m_callContract, callOrder,
                new ApiController.IOrderHandler() {
                    @Override
                    public void orderState(OrderState orderState, Order order) {
                        m_status.setText("Call order status: " + orderState.getStatus());
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

        // Place put order
        Order putOrder = new Order();
        putOrder.orderType("LMT");
        putOrder.lmtPrice(0.50);
        putOrder.action("SELL");
        putOrder.totalQuantity(Decimal.get(1));
        putOrder.tif("GTC");

        m_parent.controller().placeOrModifyOrder(m_putContract, putOrder,
                new ApiController.IOrderHandler() {
                    @Override
                    public void orderState(OrderState orderState, Order order) {
                        m_status.setText("Put order status: " + orderState.getStatus());
                        m_placeOrderButton.setVisible(false);
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

    private void fetchCurrentSPYPrice() {
        Contract spyContract = new Contract();
        spyContract.symbol("SPY");
        spyContract.secType("STK");
        spyContract.exchange("SMART");
        spyContract.currency("USD");
        m_parent.controller().reqTopMktData(spyContract, "", false, false, m_stockListener);
    }
}