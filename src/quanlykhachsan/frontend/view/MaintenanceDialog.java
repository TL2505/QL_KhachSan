package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import com.google.gson.JsonObject;
import quanlykhachsan.frontend.api.*;

public final class MaintenanceDialog extends JDialog {
    private final JTextField reason=new JTextField(),finish=new JTextField(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).withNano(0).toString());
    private final JPasswordField pin=new JPasswordField();private String key,lastBody;
    public MaintenanceDialog(Window owner,int room,Runnable refresh) {
        super(owner,"Bảo trì / Sửa chữa",ModalityType.APPLICATION_MODAL);JPanel form=new JPanel(new GridLayout(0,2,12,12));form.setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
        JComboBox<String> state=new JComboBox<>(new String[]{"out_of_order","out_of_service","vacant_clean"});JLabel status=new JLabel("Nhập lý do và thời điểm hoàn tất.");JButton submit=new JButton("Xác nhận bảo trì");
        form.add(new JLabel("Trạng thái"));form.add(state);form.add(new JLabel("Lý do"));form.add(reason);form.add(new JLabel("Dự kiến hoàn tất (ISO)"));form.add(finish);form.add(new JLabel("PIN quản lý (khi có đặt phòng)"));form.add(pin);form.add(status);form.add(submit);add(form);setSize(620,320);setLocationRelativeTo(owner);
        submit.addActionListener(e->{if(reason.getText().isBlank()){status.setText("Vui lòng nhập lý do.");return;}JsonObject body=new JsonObject();body.addProperty("status",String.valueOf(state.getSelectedItem()));body.addProperty("reason",reason.getText().trim());if(!state.getSelectedItem().equals("vacant_clean")){try{LocalDateTime.parse(finish.getText().trim());}catch(DateTimeException ex){status.setText("Thời điểm hoàn tất chưa hợp lệ.");return;}body.addProperty("estimated_finish",finish.getText().trim());}char[] password=pin.getPassword();if(password.length>0)body.addProperty("manager_pin",new String(password));Arrays.fill(password,'\0');String payload=body.toString();if(!payload.equals(lastBody)){key=UUID.randomUUID().toString();lastBody=payload;}submit.setEnabled(false);Phase2UiApi.request("PUT","/v1/rooms/"+room+"/maintenance",payload,key,data->{if(isDisplayable()){pin.setText("");refresh.run();dispose();}},error->{if(!isDisplayable())return;submit.setEnabled(true);if(error instanceof ApiClient.ApiException api && api.getStatusCode()==409){status.setForeground(new Color(180,120,20));status.setText("Có đặt phòng xung đột. Cần PIN quản lý.");pin.requestFocusInWindow();}else status.setText("Không thể cập nhật. Kiểm tra quyền và PIN.");});});
    }
}
