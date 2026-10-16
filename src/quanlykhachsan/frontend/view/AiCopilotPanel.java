package quanlykhachsan.frontend.view;

import com.google.gson.*;
import java.awt.*;
import java.util.UUID;
import javax.swing.*;
import quanlykhachsan.frontend.api.Phase3UiApi;

public final class AiCopilotPanel extends JPanel {
    private final JPanel content=new JPanel(new BorderLayout(6,6));
    private final JTextArea answer=new JTextArea();
    private final JTextField query=new JTextField();
    private final EscalationAlertBanner banner;
    private final String session=UUID.randomUUID().toString();
    private final Timer polling;
    private boolean pollPending;
    private final JLabel connectionStatus=new JLabel("Đang kết nối cảnh báo hỗ trợ…");
    public AiCopilotPanel(){
        setLayout(new BorderLayout(4,4));setPreferredSize(new Dimension(320,400));
        var toggle=new JButton("AI Copilot · Thu gọn");add(toggle,BorderLayout.NORTH);add(content,BorderLayout.CENTER);
        toggle.addActionListener(e->{boolean visible=!content.isVisible();content.setVisible(visible);setPreferredSize(new Dimension(visible?320:100,400));toggle.setText(visible?"AI Copilot · Thu gọn":"AI Copilot");revalidate();});
        answer.setEditable(false);answer.setLineWrap(true);answer.setWrapStyleWord(true);answer.setText("Tra cứu SOP đã duyệt. Thông tin thiếu sẽ chuyển lễ tân xác minh.");
        content.add(new JScrollPane(answer),BorderLayout.CENTER);var controls=new JPanel(new BorderLayout());var send=new JButton("Tra cứu");controls.add(query,BorderLayout.CENTER);controls.add(send,BorderLayout.EAST);controls.add(connectionStatus,BorderLayout.SOUTH);content.add(controls,BorderLayout.SOUTH);
        banner=new EscalationAlertBanner(id->Phase3UiApi.request("POST","v1/ai/concierge/tickets/"+id+"/ack",()->"{}",response->{bannerAccepted(id);},error->{answer.setText("Yêu cầu đã được nhân viên khác tiếp nhận hoặc kết nối chưa sẵn sàng.");}));content.add(banner,BorderLayout.NORTH);
        send.addActionListener(e->{String message=query.getText().trim();if(message.isEmpty()||message.length()>4000)return;send.setEnabled(false);answer.setText("Đang tra cứu…");
            Phase3UiApi.request("POST","v1/ai/concierge/chat",()->{var body=new JsonObject();body.addProperty("session_id",session);body.addProperty("message",message);return body.toString();},envelope->{
                send.setEnabled(true);var data=envelope.getAsJsonObject("data");var text=new StringBuilder(data.get("answer").getAsString());if(data.has("citations"))for(var item:data.getAsJsonArray("citations"))text.append("\nNguồn: ").append(item.getAsJsonObject().get("source").getAsString());answer.setText(text.toString());
            },error->{send.setEnabled(true);answer.setText("AI không sẵn sàng. Vui lòng đối chiếu SOP hoặc liên hệ lễ tân.");});
        });
        polling=new Timer(1000,e->{if(!isShowing()||pollPending)return;pollPending=true;Phase3UiApi.request("GET","v1/ai/concierge/tickets",null,envelope->{
            pollPending=false;if(!isDisplayable())return;connectionStatus.setText("Đã kết nối cảnh báo hỗ trợ");for(var item:envelope.getAsJsonArray("data")){var ticket=item.getAsJsonObject();if(ticket.get("status").getAsString().equals("OPEN")&&!ticket.get("issue_summary").getAsString().equals("MONITOR")){banner.showTicket(ticket.get("ticket_id").getAsInt(),ticket.get("issue_summary").getAsString());return;}}banner.clearTicket();
        },error->{pollPending=false;connectionStatus.setText("Mất kết nối cảnh báo; kiểm tra bàn hỗ trợ trực tiếp.");});});
        polling.start();
    }
    private void bannerAccepted(int ticket){banner.clearTicket();new TicketChatDialog(SwingUtilities.getWindowAncestor(this),ticket).setVisible(true);}
    @Override public void removeNotify(){polling.stop();super.removeNotify();}
    @Override public void addNotify(){super.addNotify();if(polling!=null)polling.start();}
}
