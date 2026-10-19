package quanlykhachsan.frontend.view;

import com.google.gson.*;
import java.awt.*;
import java.time.LocalDate;
import javax.swing.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.*;
import quanlykhachsan.frontend.api.Phase3UiApi;

public final class RevenueForecastPanel extends JPanel {
    private record Category(int id,String name){public String toString(){return name;}}
    private final JComboBox<Category> category=new JComboBox<>();
    private final YieldRecommendationTable table=new YieldRecommendationTable();
    private final JPanel chartHost=new JPanel(new BorderLayout());
    private final JLabel status=new JLabel("Dự báo hỗ trợ quản trị; giá chỉ thay đổi sau khi duyệt.");
    private final JButton refresh=new JButton("Tải dự báo 30 ngày"),apply=new JButton("Áp dụng khuyến nghị hạng phòng");
    private JsonArray forecast;
    public RevenueForecastPanel(){
        setLayout(new BorderLayout(8,8));var top=new JPanel();top.add(refresh);top.add(category);top.add(apply);top.add(new AiGuestSwitchPanel());add(top,BorderLayout.NORTH);
        var split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,chartHost,new JScrollPane(table));split.setResizeWeight(.55);add(split,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);apply.setEnabled(false);
        category.addActionListener(e->{if(forecast!=null&&category.getSelectedItem() instanceof Category selected)table.showForecast(forecast,selected.id());});
        refresh.addActionListener(e->load());apply.addActionListener(e->preview());load();
    }
    private void load(){
        refresh.setEnabled(false);apply.setEnabled(false);status.setText("Đang tải dự báo…");
        Phase3UiApi.request("GET","v1/revenue/forecast?days=30",null,result->{forecast=result.getAsJsonObject("data").getAsJsonArray("forecast");
            new SwingWorker<JFreeChart,Void>(){
                protected JFreeChart doInBackground(){
                    var predicted=new TimeSeries("Công suất dự báo");var previous=new TimeSeries("Cùng kỳ năm trước");
                    for(var item:forecast){var row=item.getAsJsonObject();var date=LocalDate.parse(row.get("date").getAsString());var day=new Day(date.getDayOfMonth(),date.getMonthValue(),date.getYear());if(row.has("predicted_occupancy")&&!row.get("predicted_occupancy").isJsonNull())predicted.add(day,row.get("predicted_occupancy").getAsDouble());if(row.has("last_year_occupancy")&&!row.get("last_year_occupancy").isJsonNull())previous.add(day,row.get("last_year_occupancy").getAsDouble());}
                    var chart=ChartFactory.createTimeSeriesChart("Công suất 30 ngày","Ngày","Dự báo (%)",new TimeSeriesCollection(predicted));
                    chart.setBackgroundPaint(new Color(30,30,30));chart.getTitle().setPaint(Color.WHITE);chart.getLegend().setBackgroundPaint(new Color(30,30,30));chart.getLegend().setItemPaint(Color.WHITE);
                    var plot=chart.getXYPlot();plot.setBackgroundPaint(new Color(40,40,40));plot.getDomainAxis().setLabelPaint(Color.WHITE);plot.getDomainAxis().setTickLabelPaint(Color.LIGHT_GRAY);plot.getRangeAxis().setLabelPaint(Color.WHITE);plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);plot.getRangeAxis().setRange(0,100);var axis=new NumberAxis("Cùng kỳ (%)");axis.setRange(0,100);axis.setLabelPaint(Color.WHITE);axis.setTickLabelPaint(Color.LIGHT_GRAY);plot.setRangeAxis(1,axis);plot.setDataset(1,new TimeSeriesCollection(previous));plot.mapDatasetToRangeAxis(1,1);plot.setRenderer(1,new XYLineAndShapeRenderer(true,false));return chart;
                }
                protected void done(){try{chartHost.removeAll();chartHost.add(new ChartPanel(get()));chartHost.revalidate();category.removeAllItems();if(!forecast.isEmpty())for(var item:forecast.get(0).getAsJsonObject().getAsJsonArray("room_types")){var row=item.getAsJsonObject();category.addItem(new Category(row.get("id").getAsInt(),row.get("name").getAsString()));}refresh.setEnabled(true);apply.setEnabled(category.getItemCount()>0&&!forecast.get(0).getAsJsonObject().get("source").getAsString().equals("INSUFFICIENT_HISTORY"));status.setText("Nguồn: "+forecast.get(0).getAsJsonObject().get("source").getAsString()+" · Ô trống cùng kỳ nghĩa là chưa có dữ liệu.");}
                    catch(InterruptedException ex){Thread.currentThread().interrupt();status.setText("Đã dừng tải biểu đồ.");refresh.setEnabled(true);}catch(java.util.concurrent.ExecutionException ex){status.setText("Không thể dựng biểu đồ.");refresh.setEnabled(true);}}
            }.execute();
        },error->{status.setText("Chưa thể tải dữ liệu dự báo. Vui lòng thử lại.");refresh.setEnabled(true);});
    }
    private void preview(){
        final JsonObject body;
        try{body=table.approval(true);}catch(IllegalArgumentException|SecurityException ex){JOptionPane.showMessageDialog(this,"Kiểm tra hệ số 0.70–2.50; dưới 0.80 cần lý do ngoại lệ.");return;}
        apply.setEnabled(false);
        Phase3UiApi.request("POST","v1/revenue/apply-rates",body::toString,result->{
            String scenario=result.getAsJsonObject("data").get("message").getAsString();
            int choice=JOptionPane.showConfirmDialog(this,scenario+"\nÁp dụng "+body.getAsJsonArray("rates").size()+" hệ số đã kiểm tra cho hạng phòng đã chọn?","Duyệt giá · Không phải cam kết doanh thu",JOptionPane.YES_NO_OPTION);
            if(choice!=JOptionPane.YES_OPTION){apply.setEnabled(true);return;}body.addProperty("preview",false);
            Phase3UiApi.request("POST","v1/revenue/apply-rates",body::toString,response->{status.setText("Đã duyệt giá và ghi audit.");load();},error->{status.setText("Chưa áp dụng giá. Kiểm tra quyền/version và tải lại trước khi duyệt.");apply.setEnabled(true);});
        },error->{status.setText("Chưa thể kiểm tra khuyến nghị.");apply.setEnabled(true);});
    }
}
