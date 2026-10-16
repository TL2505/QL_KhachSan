package quanlykhachsan.frontend.view;

import com.google.gson.JsonObject;
import javax.swing.*;
import quanlykhachsan.frontend.api.Phase3UiApi;

public final class AiGuestSwitchPanel extends JPanel {
    private final JToggleButton toggle=new JToggleButton("Guest AI · Đang tải…");
    private final JLabel status=new JLabel();
    public AiGuestSwitchPanel(){
        add(toggle);add(status);toggle.setEnabled(false);
        Phase3UiApi.request("GET","v1/ai/concierge/state",null,result->{boolean enabled=result.getAsJsonObject("data").get("guest_enabled").getAsBoolean();toggle.setSelected(enabled);showState();toggle.setEnabled(true);status.setText("Lỗi audit: "+result.getAsJsonObject("data").get("audit_failures").getAsString());},error->{status.setText("Chưa tải được trạng thái AI.");});
        toggle.addActionListener(e->{boolean enabled=toggle.isSelected();toggle.setEnabled(false);
            Phase3UiApi.request("PUT","v1/ai/concierge/state",()->{var body=new JsonObject();body.addProperty("guest_enabled",enabled);return body.toString();},result->{showState();toggle.setEnabled(true);status.setText(enabled?"Đã bật AI cho khách":"Khách được chuyển sang hỗ trợ trực tiếp");},error->{toggle.setSelected(!enabled);showState();toggle.setEnabled(true);status.setText("Chưa lưu được trạng thái. Vui lòng thử lại.");});
        });
    }
    private void showState(){toggle.setText(toggle.isSelected()?"Guest AI · ON":"Guest AI · Live Agent Only");}
}
