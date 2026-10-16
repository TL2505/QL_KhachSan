package quanlykhachsan.frontend.view;

import com.google.gson.*;
import java.awt.*;
import javax.swing.*;
import quanlykhachsan.frontend.api.Phase3UiApi;

/** Authorized staff conversation; every request runs in the shared SwingWorker facade. */
public final class TicketChatDialog extends JDialog {
    private final JTextArea transcript=new JTextArea();
    private final JTextField input=new JTextField();
    private final JLabel status=new JLabel("Đang kết nối…");
    private long after;
    private boolean polling;
    private final Timer timer;
    public TicketChatDialog(Window owner,int ticket){
        super(owner,"Hỗ trợ trực tiếp · #"+ticket,ModalityType.MODELESS);setLayout(new BorderLayout(8,8));
        transcript.setEditable(false);transcript.setLineWrap(true);transcript.setWrapStyleWord(true);add(new JScrollPane(transcript),BorderLayout.CENTER);
        var bottom=new JPanel(new BorderLayout());var send=new JButton("Gửi");bottom.add(input,BorderLayout.CENTER);bottom.add(send,BorderLayout.EAST);bottom.add(status,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        String path="v1/ai/concierge/tickets/"+ticket+"/messages";
        Runnable poll=()->{if(polling||!isShowing())return;polling=true;Phase3UiApi.request("GET",path+"?after="+after,null,envelope->{
            polling=false;if(!isDisplayable())return;for(var row:envelope.getAsJsonArray("data")){var message=row.getAsJsonObject();after=Math.max(after,message.get("id").getAsLong());transcript.append(message.get("sender").getAsString()+": "+message.get("message").getAsString()+"\n");}status.setText("Đã kết nối");
        },error->{polling=false;status.setText("Chưa thể tải tin nhắn; sẽ thử lại.");});};
        send.addActionListener(e->{String message=input.getText().trim();if(message.isEmpty()||message.length()>2000)return;send.setEnabled(false);
            Phase3UiApi.request("POST",path,()->{var body=new JsonObject();body.addProperty("message",message);return body.toString();},envelope->{if(!isDisplayable())return;input.setText("");send.setEnabled(true);poll.run();},error->{send.setEnabled(true);status.setText("Chưa gửi được tin nhắn. Vui lòng thử lại.");});
        });
        timer=new Timer(1000,e->poll.run());timer.start();setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(560,420);setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){timer.stop();transcript.setText("");input.setText("");}});
    }
}
