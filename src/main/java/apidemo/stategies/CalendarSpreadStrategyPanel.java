package apidemo.stategies;

import apidemo.util.HtmlButton;
import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;
import com.ib.client.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class CalendarSpreadStrategyPanel extends JPanel {
    private final UpperField m_spotPrice = new UpperField();
    private UpperField m_sellLegLengthFromSpotPrice = new UpperField();
    private JCheckBox m_useSellStikesForBuying = new JCheckBox();
    private UpperField m_buyLegLengthFromSellPrice = new UpperField();

    private Contract m_putSellContract;
    private Contract m_callSellContract;
    private Contract m_putBuyContract;
    private Contract m_callBuyContract;

    public CalendarSpreadStrategyPanel() {
        VerticalPanel p = getInputPanel();

        VerticalPanel butPanel = getButtonPanel();
        setLayout(new BorderLayout());
        add(p, BorderLayout.WEST);
        add(butPanel);
    }

    private VerticalPanel getInputPanel() {
        VerticalPanel p = new VerticalPanel();
        p.add( "Spot price", m_spotPrice);
        p.add( "Sell legs length from spot", m_sellLegLengthFromSpotPrice);
        p.add( "Use Sell strikes for buying", m_useSellStikesForBuying);
        p.add( "Buy legs length from sell", m_buyLegLengthFromSellPrice);
        return p;
    }

    private VerticalPanel getButtonPanel() {
        HtmlButton populateContracts = new HtmlButton( "Populate contracts") {
            @Override protected void actionPerformed() {
                onPopulateContractDetails();
            }
        };

        HtmlButton placeOrder = new HtmlButton( "Place order") {
            @Override protected void actionPerformed() {
               onPlaceOrder();
            }
        };

        VerticalPanel butPanel = new VerticalPanel();
        butPanel.add(populateContracts);
        butPanel.add(placeOrder);
        return butPanel;
    }


    protected void onPopulateContractDetails(Contract contract) {
        CalendarSpreadStrategy.INSTANCE.controller().reqContractDetails(contract, list -> {
            for (ContractDetails details : list) {
                System.out.println(details.contract());
            }
        });
    }

    protected void onPopulateContractDetails() {
        onOK();
        onPopulateContractDetails(m_callSellContract);
        onPopulateContractDetails(m_putSellContract);
        onPopulateContractDetails(m_callBuyContract);
        onPopulateContractDetails(m_putBuyContract);
    }

    protected void onPlaceOrder() {
        // Define the Combo Contract for SPY
        Contract comboContract = new Contract();
        comboContract.symbol("SPY");              // Underlying symbol
        comboContract.secType("BAG");             // Combo (BAG) contract
        comboContract.currency("USD");            // Currency
        comboContract.exchange("SMART");          // Smart routing



        // Leg 1: Sell SPY Call Option (near-term expiry)
        ComboLeg leg1 = new ComboLeg();
        leg1.conid(123456789);       // Contract ID for near-term option
        leg1.ratio(1);               // Quantity ratio
        leg1.action("SELL");         // Sell near-term option
        leg1.exchange("SMART");

// Leg 2: Buy SPY Call Option (long-term expiry)
        ComboLeg leg2 = new ComboLeg();
        leg2.conid(987654321);       // Contract ID for long-term option
        leg2.ratio(1);               // Quantity ratio
        leg2.action("BUY");          // Buy long-term option
        leg2.exchange("SMART");

// Add legs to the combo contract
        comboContract.comboLegs(new ArrayList<>(Arrays.asList(leg1, leg2)));



        Order comboOrder = new Order();
        comboOrder.orderType("LMT");                 // Limit order
        comboOrder.lmtPrice(1.50);                   // Limit price for the spread
        comboOrder.action("BUY");                    // Buy the calendar spread
        comboOrder.totalQuantity(Decimal.get(1));                 // Quantity of the spread
        comboOrder.tif("GTC");                       // Good Till Canceled

// Place the combo order
        CalendarSpreadStrategy.INSTANCE.controller().placeOrModifyOrder(comboContract, comboOrder, null);
    }

    private void onOK() {
        m_callSellContract = new Contract();
        m_callSellContract.symbol("SPY");          // Underlying symbol
        m_callSellContract.secType("OPT");         // Security type: Option
        m_callSellContract.exchange("SMART");      // Exchange
        m_callSellContract.currency("USD");        // Currency
        m_callSellContract.lastTradeDateOrContractMonth("20250203");  // Expiry date (YYYYMMDD)
        m_callSellContract.strike(m_spotPrice.getDouble());            // Strike price
        m_callSellContract.right("P");             // Option type: "C" for Call, "P" for Put
        m_callSellContract.multiplier("100");


        m_putSellContract = new Contract();
        m_putSellContract.symbol("SPY");          // Underlying symbol
        m_putSellContract.secType("OPT");         // Security type: Option
        m_putSellContract.exchange("SMART");      // Exchange
        m_putSellContract.currency("USD");        // Currency
        m_putSellContract.lastTradeDateOrContractMonth("20250203");  // Expiry date (YYYYMMDD)
        m_putSellContract.strike(m_spotPrice.getDouble());            // Strike price
        m_putSellContract.right("P");             // Option type: "C" for Call, "P" for Put
        m_putSellContract.multiplier("100");


        m_callBuyContract = new Contract();
        m_callBuyContract.symbol("SPY");          // Underlying symbol
        m_callBuyContract.secType("OPT");         // Security type: Option
        m_callBuyContract.exchange("SMART");      // Exchange
        m_callBuyContract.currency("USD");        // Currency
        m_callBuyContract.lastTradeDateOrContractMonth("20250203");  // Expiry date (YYYYMMDD)
        m_callBuyContract.strike(m_spotPrice.getDouble());            // Strike price
        m_callBuyContract.right("P");             // Option type: "C" for Call, "P" for Put
        m_callBuyContract.multiplier("100");


        m_putBuyContract = new Contract();
        m_putBuyContract.symbol("SPY");          // Underlying symbol
        m_putBuyContract.secType("OPT");         // Security type: Option
        m_putBuyContract.exchange("SMART");      // Exchange
        m_putBuyContract.currency("USD");        // Currency
        m_putBuyContract.lastTradeDateOrContractMonth("20250203");  // Expiry date (YYYYMMDD)
        m_putBuyContract.strike(m_spotPrice.getDouble());            // Strike price
        m_putBuyContract.right("P");             // Option type: "C" for Call, "P" for Put
        m_putBuyContract.multiplier("100");

    }


}
