package quanlykhachsan.frontend.view;

import com.google.gson.*;
import java.math.BigDecimal;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import quanlykhachsan.backend.pricing.YieldRatePolicy;

public final class YieldRecommendationTable extends JTable {
    private final DefaultTableModel rows=new DefaultTableModel(new String[]{"Ngày","Công suất (%)","Hệ số đề xuất","Version","Lý do ngoại lệ"},0){
        public boolean isCellEditable(int row,int column){return column==2||column==4;}
    };
    private int roomType;
    public YieldRecommendationTable(){setModel(rows);setRowHeight(28);}
    public void showForecast(JsonArray forecast,int type){
        roomType=type;rows.setRowCount(0);
        for(var item:forecast){var day=item.getAsJsonObject();var versions=day.getAsJsonObject("approval_versions");int version=versions!=null&&versions.has(Integer.toString(type))?versions.get(Integer.toString(type)).getAsInt():0;
            rows.addRow(new Object[]{day.get("date").getAsString(),day.get("predicted_occupancy").getAsDouble(),day.get("recommended_multiplier").getAsBigDecimal(),version,""});}
    }
    public JsonObject approval(boolean preview){
        if(isEditing()&&!getCellEditor().stopCellEditing())throw new IllegalArgumentException("Hoàn tất chỉnh sửa ô trước khi duyệt.");
        var rates=new JsonArray();
        for(int i=0;i<rows.getRowCount();i++){
            String reason=String.valueOf(rows.getValueAt(i,4));BigDecimal multiplier=new BigDecimal(String.valueOf(rows.getValueAt(i,2)));
            YieldRatePolicy.approve(multiplier,true,reason.isBlank()?null:reason);var row=new JsonObject();row.addProperty("room_type_id",roomType);row.addProperty("date",String.valueOf(rows.getValueAt(i,0)));row.addProperty("multiplier",multiplier);row.addProperty("version",((Number)rows.getValueAt(i,3)).intValue());if(!reason.isBlank())row.addProperty("override_reason",reason);rates.add(row);
        }
        if(rates.isEmpty())throw new IllegalArgumentException("Chưa có khuyến nghị.");
        var body=new JsonObject();body.add("rates",rates);body.addProperty("preview",preview);return body;
    }
}
