package quanlykhachsan.frontend.view;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;
import com.google.gson.*;
import quanlykhachsan.frontend.api.Phase2UiApi;
import quanlykhachsan.frontend.utils.SessionManagerUtil;

public final class HousekeepingPanel extends JPanel {
    private final JCheckBox dirtyOnly=new JCheckBox("Phòng bẩn cần dọn",true);
    private final JPanel grid=new JPanel(new GridLayout(0,5,10,10));
    private final JLabel message=new JLabel(" ");
    private JsonArray rooms=new JsonArray();private int generation;
    public HousekeepingPanel() {
        super(new BorderLayout(16,16));setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton refresh=new JButton("Làm mới");tools.add(dirtyOnly);tools.add(refresh);add(tools,BorderLayout.NORTH);add(new JScrollPane(grid),BorderLayout.CENTER);add(message,BorderLayout.SOUTH);
        dirtyOnly.addActionListener(e->render());refresh.addActionListener(e->refresh());refresh();
    }
    public void refresh(){int current=++generation;message.setText("Đang tải phòng…");Phase2UiApi.request("GET","/v1/rooms",null,null,data->{if(current!=generation)return;rooms=data.getAsJsonArray();render();message.setText("Đã cập nhật danh sách phòng");},error->{if(current==generation)message.setText("Chưa thể tải dữ liệu phòng. Vui lòng thử lại.");});}
    private void render() {
        grid.removeAll();boolean mayClean=java.util.Set.of("admin","housekeeping").contains(SessionManagerUtil.getRole());
        for(JsonElement row:rooms){JsonObject room=row.getAsJsonObject();String state=room.get("status").getAsString();boolean dirty=state.equals("vacant_dirty") || state.equals("occupied_dirty");if(dirtyOnly.isSelected() && !dirty)continue;
            JPanel card=new JPanel(new BorderLayout(4,8));card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(dirty?new Color(235,150,50):new Color(75,150,90),2),BorderFactory.createEmptyBorder(12,12,12,12)));
            JTextField duration=new JTextField(),notes=new JTextField();
            String number=room.has("roomNumber")?room.get("roomNumber").getAsString():room.get("room_number").getAsString();card.add(new JLabel("Phòng "+number),BorderLayout.NORTH);card.add(new JLabel(state),BorderLayout.CENTER);
            if(dirty){JPanel inspection=new JPanel(new GridLayout(0,1,4,4));inspection.add(new JLabel(state));inspection.add(new JLabel("Thời gian dọn (giây, nếu đã đo)"));inspection.add(duration);inspection.add(new JLabel("Ghi chú kiểm phòng (tùy chọn)"));inspection.add(notes);card.add(inspection,BorderLayout.CENTER);}
            JPanel buttons=new JPanel(new GridLayout(0,1,4,4));JButton minibar=new JButton("Kiểm kho Minibar");minibar.addActionListener(e->new MinibarChecklistDialog(SwingUtilities.getWindowAncestor(this),room.get("id").getAsInt(),this::refresh).setVisible(true));buttons.add(minibar);
            if(dirty){JButton clean=new JButton("Đã dọn phòng xong");clean.setEnabled(mayClean);clean.addActionListener(e->{String measured=duration.getText().trim();if((!measured.isEmpty() && (!measured.matches("[0-9]{1,5}") || Integer.parseInt(measured)>86400)) || notes.getText().length()>255){message.setText("Kiểm tra thời gian đo và ghi chú (tối đa 255 ký tự).");return;}clean.setEnabled(false);JsonObject body=new JsonObject();if(!measured.isEmpty())body.addProperty("cleaning_duration_seconds",Integer.parseInt(measured));if(!notes.getText().isBlank())body.addProperty("inspection_notes",notes.getText().trim());body.addProperty("status",state.equals("vacant_dirty")?"vacant_clean":"occupied_clean");if(room.has("version"))body.add("room_version",room.get("version"));Phase2UiApi.request("PUT","/v1/rooms/"+room.get("id").getAsInt()+"/housekeeping",body.toString(),UUID.randomUUID().toString(),data->refresh(),error->{message.setText("Phòng đã thay đổi hoặc chưa thể xác nhận. Vui lòng làm mới.");clean.setEnabled(mayClean);});});buttons.add(clean);}
            card.add(buttons,BorderLayout.SOUTH);grid.add(card);
        }grid.revalidate();grid.repaint();
    }
}
