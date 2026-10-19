package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.UUID;
import com.google.gson.*;
import quanlykhachsan.frontend.api.Phase2UiApi;

public final class WalkInWizardDialog extends JDialog {
    private final JComboBox<Choice> rooms=new JComboBox<>();
    private final JTextField name=new JTextField(),identity=new JTextField(),phone=new JTextField();
    private final JSpinner nights=new JSpinner(new SpinnerNumberModel(1,1,365,1));
    private final JButton submit=new JButton("Xác nhận nhận phòng");
    private final JLabel status=new JLabel("Đang kiểm tra phòng trống…");
    private String key,lastBody;
    public WalkInWizardDialog(Window owner,Runnable refresh) {
        super(owner,"Nhận phòng nhanh · Walk-in",ModalityType.APPLICATION_MODAL);
        JPanel form=new JPanel(new GridLayout(0,2,12,12));form.setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
        form.add(new JLabel("Phòng trống sạch"));form.add(rooms);form.add(new JLabel("Họ và tên"));form.add(name);form.add(new JLabel("CCCD / Hộ chiếu"));form.add(identity);form.add(new JLabel("Số điện thoại"));form.add(phone);form.add(new JLabel("Số đêm"));form.add(nights);
        form.add(status);form.add(submit);add(form);setSize(560,320);setLocationRelativeTo(owner);submit.setEnabled(false);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Phase2UiApi.request("GET","/v1/rooms/availability?check_in="+today+"&check_out="+today.plusDays(1),null,null,data->{
            if(!isDisplayable())return;for(JsonElement row:data.getAsJsonArray())rooms.addItem(new Choice(row.getAsJsonObject()));
            status.setText(rooms.getItemCount()>0?"Chọn phòng để nhận khách":"Không còn phòng sạch");submit.setEnabled(rooms.getItemCount()>0);
        },error->{if(isDisplayable())status.setText("Chưa thể tải phòng. Vui lòng thử lại.");});
        submit.addActionListener(event->{
            Choice choice=(Choice)rooms.getSelectedItem();if(choice==null || name.getText().isBlank() || !identity.getText().trim().matches("[A-Za-z0-9]{5,20}") || !phone.getText().trim().matches("(?:0|\\+84)[35789][0-9]{8}")){status.setText("Vui lòng kiểm tra thông tin khách.");return;}
            JsonObject body=new JsonObject();body.addProperty("customer_name",name.getText().trim());body.addProperty("identity_card",identity.getText().trim());body.addProperty("phone",phone.getText().trim());body.addProperty("room_id",choice.data.get("id").getAsInt());body.addProperty("room_version",choice.data.get("version").getAsInt());body.addProperty("check_in_date",today+" 14:00:00");body.addProperty("check_out_date",today.plusDays(((Number)nights.getValue()).longValue())+" 12:00:00");
            String payload=body.toString();if(!payload.equals(lastBody)){key=UUID.randomUUID().toString();lastBody=payload;}
            submit.setEnabled(false);status.setText("Đang nhận phòng…");
            Phase2UiApi.request("POST","/v1/bookings/walk-in",payload,key,data->{if(!isDisplayable())return;refresh.run();dispose();},error->{if(isDisplayable()){status.setText("Chưa thể nhận phòng. Hãy kiểm tra lại phòng trống.");submit.setEnabled(true);}});
        });
    }
    private static final class Choice {final JsonObject data;Choice(JsonObject data){this.data=data;}@Override public String toString(){return data.get("room_type_name").getAsString()+" · #"+data.get("id").getAsInt();}}
}
