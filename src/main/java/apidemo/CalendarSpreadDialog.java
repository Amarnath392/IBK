package apidemo.custom_v1;

import com.ib.client.Contract;
import com.ib.client.Order;

import javax.swing.*;

public class CalendarSpreadDialog extends JDialog {
    private final Contract m_contract;
    private final Order m_order;
    CalendarSpreadDialog(Contract contract, Order order) {
        m_contract = contract;
        m_order = order;
   }


}
