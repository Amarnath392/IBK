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

public class CalendarSpreadStrategyPanel extends JPanel {
    private final TradingStrategies m_parent;
    private JDateChooser m_currentExpiryDate;
    private JDateChooser m_nextExpiryDate;
    private final UpperField m_spotPrice = new UpperField();
    private UpperField m_sellLegLengthFromSpotPrice = new UpperField();
    private UpperField m_quantity = new UpperField();
    private JCheckBox m_useSellStikesForBuying = new JCheckBox();
    private UpperField m_buyLegLengthFromSellPrice = new UpperField();
    private final JLabel m_status = new JLabel();
    HtmlButton placeOrder;
    private int numberOfContractsLoaded = 0;
    private static final int TOTAL_CONTRACTS = 4;

    private Contract m_putSellContract, m_callSellContract, m_putBuyContract, m_callBuyContract;

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

    private void createAndPopulateContracts() {
        createContracts();
        numberOfContractsLoaded = 0;
        populateContractDetails(m_callSellContract, ContractType.CALL_SELL);
        populateContractDetails(m_putSellContract, ContractType.PUT_SELL);
        populateContractDetails(m_callBuyContract, ContractType.CALL_BUY);
        populateContractDetails(m_putBuyContract, ContractType.PUT_BUY);
    }

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
        m_useSellStikesForBuying.addActionListener(new Action() {
            @Override
            public Object getValue(String key) {
                return m_useSellStikesForBuying.isSelected();
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
                    m_buyLegLengthFromSellPrice.setVisible(!m_useSellStikesForBuying.isSelected());
                });
            }
        });
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

    private VerticalPanel getInputPanel() {
        VerticalPanel p = new VerticalPanel();
        p.add("Current expiry", m_currentExpiryDate);
        p.add("Next expiry", m_nextExpiryDate);
        p.add("Spot price", m_spotPrice);
        p.add("Sell legs length from spot", m_sellLegLengthFromSpotPrice);
        p.add("Use Sell strikes for buying", m_useSellStikesForBuying);
        p.add("Buy legs length from sell", m_buyLegLengthFromSellPrice);
        p.add("Quantity", m_quantity);
        return p;
    }

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

    private void populateDefaults(double spotPrice) {
        SwingUtilities.invokeLater(() -> {
            m_spotPrice.setText("" + customRound(spotPrice));
            populateDates();
            m_sellLegLengthFromSpotPrice.setText(5);
            m_useSellStikesForBuying.setSelected(false);
            m_buyLegLengthFromSellPrice.setVisible(true);
            m_buyLegLengthFromSellPrice.setText(3);
            m_quantity.setText("1");
            m_status.setText("Default values loaded successfully.");
        });
    }

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

    private void fetchCurrentSPYPrice() {
        Contract spyContract = new Contract();
        spyContract.symbol("SPY");
        spyContract.secType("STK");
        spyContract.exchange("SMART");
        spyContract.currency("USD");
        m_parent.controller().reqTopMktData(spyContract, "", false, false, m_stockListener);
    }

    private void createContracts() {
        double spotPrice = m_spotPrice.getDouble();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateAfter7Days = sdf.format(m_currentExpiryDate.getDate());
        String dateAfter14Days = sdf.format(m_nextExpiryDate.getDate());

        double sellDelta = getPositiveOrZero(m_sellLegLengthFromSpotPrice.getDouble());
        m_callSellContract = createOptionContract("C", spotPrice + sellDelta, dateAfter7Days);
        m_putSellContract = createOptionContract("P", spotPrice - sellDelta, dateAfter7Days);

        double callDelta = m_useSellStikesForBuying.isSelected() ? sellDelta
                : sellDelta + getPositiveOrZero(m_buyLegLengthFromSellPrice.getDouble());
        m_callBuyContract = createOptionContract("C", spotPrice + callDelta, dateAfter14Days);
        m_putBuyContract = createOptionContract("P", spotPrice - callDelta, dateAfter14Days);
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

    public static double customRound(double value) {
        double fractionalPart = value - Math.floor(value);
        if (fractionalPart > 0.5) {
            return Math.ceil(value);
        } else {
            return Math.floor(value);
        }
    }

    public static double getPositiveOrZero(double value) {
        return Math.max(value, 0);
    }
}