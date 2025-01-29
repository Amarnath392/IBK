package apidemo.stategies;

import apidemo.util.HtmlButton;
import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;

import javax.swing.*;
import java.awt.*;

public class CalendarSpreadStrategyPanel extends JPanel {
    private UpperField m_spotPrice = new UpperField();
    private UpperField m_sellLegLengthFromSpotPrice = new UpperField();
    private JCheckBox m_useSellStikesForBuying = new JCheckBox();
    private UpperField m_buyLegLengthFromSellPrice = new UpperField();

    public CalendarSpreadStrategyPanel() {
        VerticalPanel p = new VerticalPanel();
        p.add( "Spot price", m_spotPrice);
        p.add( "Sell legs length from spot", m_sellLegLengthFromSpotPrice);
        p.add( "Use Sell strikes for buying", m_useSellStikesForBuying);
        p.add( "Buy legs length from sell", m_buyLegLengthFromSellPrice);

        HtmlButton placeOrder = new HtmlButton( "Place order") {
            @Override protected void actionPerformed() {
                System.out.println("Order placed");
            }
        };


        VerticalPanel butPanel = new VerticalPanel();
        butPanel.add(placeOrder);

        setLayout( new BoxLayout( this, BoxLayout.X_AXIS) );
        add(p);
        add( Box.createHorizontalStrut(20));
        add(butPanel);
    }


}
