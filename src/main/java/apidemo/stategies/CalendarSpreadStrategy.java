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

public class CalendarSpreadStrategy implements IConnectionHandler {
    static { NewLookAndFeel.register(); }
    public static CalendarSpreadStrategy INSTANCE;
    private final IConnectionConfiguration m_connectionConfiguration;
    private final JTextArea m_inLog = new JTextArea();
    private final JTextArea m_outLog = new JTextArea();
    private final Logger m_inLogger = new Logger( m_inLog);
    private final Logger m_outLogger = new Logger( m_outLog);
    private ApiController m_controller;
    private final List<String> m_acctList = new ArrayList<>();

    private final JFrame m_frame = new JFrame();
    private final ConnectionPanel m_connectionPanel;
    private final CalendarSpreadStrategyPanel calendarSpreadStrategyPanel = new CalendarSpreadStrategyPanel();
    private final NewTabbedPanel m_tabbedPanel = new NewTabbedPanel(true);
    private final JTextArea m_msg = new JTextArea();


    ApiConnection.ILogger getInLogger() {
        return m_inLogger;
    }

    ApiConnection.ILogger getOutLogger() {
        return m_outLogger;
    }

    public static void main(String[] args) {
        start(new CalendarSpreadStrategy( new IConnectionConfiguration.DefaultConnectionConfiguration()));
    }

    public static void start(CalendarSpreadStrategy instance) {
        INSTANCE = instance;
        INSTANCE.run();
    }

    public CalendarSpreadStrategy( IConnectionConfiguration connectionConfig ) {
        m_connectionConfiguration = connectionConfig;
        m_connectionPanel = new ConnectionPanel(); // must be done after connection config is set
    }

    public ApiController controller() {
        if ( m_controller == null ) {
            m_controller = new ApiController( this, getInLogger(), getOutLogger() );
        }
        return m_controller;
    }

    private void run() {
        m_tabbedPanel.addTab("Connection", m_connectionPanel);
        m_tabbedPanel.addTab("Calendar spread strategy", calendarSpreadStrategyPanel);

        m_msg.setEditable( false);
        m_msg.setLineWrap( true);

        JScrollPane msgScroll = new JScrollPane( m_msg);
        msgScroll.setPreferredSize( new Dimension( 10000, 120) );

        JScrollPane outLogScroll = new JScrollPane( m_outLog);
        outLogScroll.setPreferredSize( new Dimension( 10000, 120) );

        JScrollPane inLogScroll = new JScrollPane( m_inLog);
        inLogScroll.setPreferredSize( new Dimension( 10000, 120) );

        NewTabbedPanel bot = new NewTabbedPanel();
        bot.addTab( "Messages", msgScroll);
        bot.addTab( "Log (out)", outLogScroll);
        bot.addTab( "Log (in)", inLogScroll);

        m_frame.add(m_tabbedPanel);
        m_frame.add(bot, BorderLayout.SOUTH);
        m_frame.setSize(1024, 768);
        m_frame.setVisible(true);
        m_frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);


        controller().connect( "127.0.0.1", 7497, 0,
                m_connectionConfiguration.getDefaultConnectOptions() != null ? m_connectionConfiguration.getDefaultConnectOptions() : null);
    }


    @Override
    public void connected() {
        show( "connected");
        m_connectionPanel.m_status.setText( "connected");

        controller().reqCurrentTime(time -> show( "Server date/time is " + Formats.fmtDate(time * 1000) ));

        controller().reqBulletins( true, (msgId, newsType, message, exchange) -> {
            String str = String.format( "Received bulletin:  type=%s  exchange=%s", newsType, exchange);
            show( str);
            show( message);
        });
    }

    @Override
    public void disconnected() {
        show( "disconnected");
        m_connectionPanel.m_status.setText( "disconnected");
    }

    @Override
    public void accountList(List<String> list) {
        show( "Received account list");
        m_acctList.clear();
        m_acctList.addAll( list);
    }

    @Override
    public void error(Exception e) {
        show( e.toString() );
    }

    @Override
    public void message(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        String error = id + " " + errorCode + " " + errorMsg;
        if (advancedOrderRejectJson != null) {
            error += (" " + advancedOrderRejectJson);
        }
        show(error);
    }

    @Override
    public void show(String str) {
        SwingUtilities.invokeLater(() -> {
            m_msg.append(str);
            m_msg.append( "\n\n");

            Dimension d = m_msg.getSize();
            m_msg.scrollRectToVisible( new Rectangle( 0, d.height, 1, 1) );
        });
    }


    private class ConnectionPanel extends JPanel {
        private final JTextField m_host = new JTextField( m_connectionConfiguration.getDefaultHost(), 10);
        private final JTextField m_port = new JTextField( m_connectionConfiguration.getDefaultPort(), 7);
        private final JTextField m_connectOptionsTF = new JTextField( m_connectionConfiguration.getDefaultConnectOptions(), 30);
        private final JTextField m_clientId = new JTextField("0", 7);
        private final JLabel m_status = new JLabel("Disconnected");
        private final JLabel m_defaultPortNumberLabel = new JLabel("<html>Live Trading ports:<b> TWS: 7496; IB Gateway: 4001.</b><br>"
                + "Simulated Trading ports for new installations of "
                + "version 954.1 or newer: "
                + "<b>TWS: 7497; IB Gateway: 4002</b></html>");

        ConnectionPanel() {
            HtmlButton connect = new HtmlButton("Connect") {
                @Override public void actionPerformed() {
                    onConnect();
                }
            };

            HtmlButton disconnect = new HtmlButton("Disconnect") {
                @Override public void actionPerformed() {
                    controller().disconnect();
                }
            };

            JPanel p1 = new VerticalPanel();
            p1.add( "Host", m_host);
            p1.add( "Port", m_port);
            p1.add( "Client ID", m_clientId);
            if ( m_connectionConfiguration.getDefaultConnectOptions() != null ) {
                p1.add( "Connect options", m_connectOptionsTF);
            }
            p1.add( "", m_defaultPortNumberLabel);

            JPanel p2 = new VerticalPanel();
            p2.add( connect);
            p2.add( disconnect);
            p2.add( Box.createVerticalStrut(20));

            JPanel p3 = new VerticalPanel();
            p3.setBorder( new EmptyBorder( 20, 0, 0, 0));
            p3.add( "Connection status: ", m_status);

            JPanel p4 = new JPanel( new BorderLayout() );
            p4.add( p1, BorderLayout.WEST);
            p4.add( p2);
            p4.add( p3, BorderLayout.SOUTH);

            setLayout( new BorderLayout() );
            add( p4, BorderLayout.NORTH);
        }

        void onConnect() {
            int port = Integer.parseInt( m_port.getText() );
            int clientId = Integer.parseInt( m_clientId.getText() );
            controller().connect( m_host.getText(), port, clientId, m_connectOptionsTF.getText());
        }
    }
    private static class Logger implements ApiConnection.ILogger {
        final private JTextArea m_area;

        Logger( JTextArea area) {
            m_area = area;
        }

        @Override public void log(final String str) {
            SwingUtilities.invokeLater(() -> {
//					m_area.append(str);
//
//					Dimension d = m_area.getSize();
//					m_area.scrollRectToVisible( new Rectangle( 0, d.height, 1, 1) );
            });
        }
    }
}
