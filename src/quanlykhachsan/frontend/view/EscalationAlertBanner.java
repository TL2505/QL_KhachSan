package quanlykhachsan.frontend.view;

import java.awt.*;
import java.util.function.IntConsumer;
import javax.swing.*;

public final class EscalationAlertBanner extends JPanel {
    private final JLabel label=new JLabel();
    private int ticket;
    private final Timer blink;
    public EscalationAlertBanner(IntConsumer acknowledge){
        setLayout(new BorderLayout());setBackground(new Color(0xF57C00));setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        label.setForeground(Color.BLACK);add(label,BorderLayout.CENTER);var accept=new JButton("Tiếp nhận hỗ trợ");accept.addActionListener(e->{if(ticket>0)acknowledge.accept(ticket);});add(accept,BorderLayout.SOUTH);
        blink=new Timer(600,e->setBackground(getBackground().equals(new Color(0xF57C00))?new Color(0xFFAD42):new Color(0xF57C00)));setVisible(false);
    }
    public void showTicket(int id,String category){
        if(id==ticket)return;ticket=id;label.setText("#"+id+" · "+category);setVisible(true);blink.start();
        quanlykhachsan.frontend.utils.SoundNotifier.settled();
    }
    public void clearTicket(){ticket=0;blink.stop();setVisible(false);}
    @Override public void removeNotify(){blink.stop();super.removeNotify();}
}
