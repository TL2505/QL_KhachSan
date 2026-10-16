package quanlykhachsan.frontend.view;

import com.google.gson.JsonObject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Base64;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import quanlykhachsan.frontend.api.Phase3UiApi;

public final class AiCheckinDialog extends JDialog {
    private final CameraPreviewPanel camera=new CameraPreviewPanel();
    private final JLabel status=new JLabel("Đối chiếu thủ công luôn khả dụng"),cardPreview=new JLabel("Ảnh CCCD",SwingConstants.CENTER),livePreview=new JLabel("Ảnh trực tiếp",SwingConstants.CENTER);
    private final JTextField name=new JTextField(),identity=new JTextField(),dob=new JTextField(),address=new JTextField();
    private BufferedImage card;
    private JsonObject extracted;
    private boolean pending;
    public AiCheckinDialog(Window owner,Consumer<JsonObject> autofill){
        super(owner,"AI Check-in · CCCD & đối chiếu khuôn mặt",ModalityType.APPLICATION_MODAL);setLayout(new BorderLayout(10,10));
        add(camera,BorderLayout.CENTER);var side=new JPanel(new GridLayout(0,1,6,6));side.setPreferredSize(new Dimension(300,360));
        for(var entry:java.util.List.of(new Object[]{"Họ và tên",name},new Object[]{"CCCD",identity},new Object[]{"Ngày sinh (yyyy-MM-dd)",dob},new Object[]{"Địa chỉ",address})){
            side.add(new JLabel((String)entry[0]));side.add((JTextField)entry[1]);
        }
        var images=new JPanel(new GridLayout(1,2,4,4));images.add(cardPreview);images.add(livePreview);side.add(images);add(side,BorderLayout.EAST);
        var actions=new JPanel();JButton capture=new JButton("Chụp & Nhận diện CCCD"),sample=new JButton("Chọn ảnh JPEG/PNG"),verify=new JButton("Đối chiếu khuôn mặt"),apply=new JButton("Điền vào hồ sơ");
        for(var button:java.util.List.of(capture,sample,verify,apply))actions.add(button);actions.add(status);add(actions,BorderLayout.SOUTH);
        capture.addActionListener(e->{if(pending)return;BufferedImage image=camera.snapshot();if(image==null){status.setText("Chưa có khung hình; dùng ảnh mẫu hoặc đối chiếu thủ công.");return;}recognize(image);});
        sample.addActionListener(e->{
            if(pending)return;var chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;var path=chooser.getSelectedFile().toPath();pending=true;
            new SwingWorker<BufferedImage,Void>(){
                protected BufferedImage doInBackground() throws IOException {if(Files.size(path)>5*1024*1024)throw new IOException("Image too large");try(var input=ImageIO.createImageInputStream(path.toFile())){
                    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid image");var reader=readers.next();try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>12_000_000)throw new IOException("Image too large");return reader.read(0);}finally{reader.dispose();}
                }}
                protected void done(){pending=false;try{recognize(get());}catch(InterruptedException ex){Thread.currentThread().interrupt();status.setText("Đã dừng đọc ảnh.");}catch(java.util.concurrent.ExecutionException ex){status.setText("Ảnh không hợp lệ hoặc lớn hơn 5 MB.");}}
            }.execute();
        });
        verify.addActionListener(e->{
            BufferedImage live=camera.snapshot(),saved=card;if(pending||saved==null||live==null){status.setText("Cần ảnh CCCD và ảnh camera trực tiếp.");return;}pending=true;status.setText("Đang đối chiếu…");
            preview(saved,live);
            Phase3UiApi.request("POST","v1/ai/face-verify",()->{var body=new JsonObject();body.addProperty("id_photo",encode(saved));body.addProperty("live_face",encode(live));return body.toString();},envelope->{
                if(!isDisplayable())return;pending=false;var data=envelope.getAsJsonObject("data");boolean matched=data.get("match").getAsBoolean();status.setForeground(matched?new Color(0,145,65):new Color(200,120,0));status.setText(matched?"Khớp khuôn mặt · "+data.get("confidence").getAsString():"Cần đối chiếu bằng mắt thường");
            },error->{pending=false;status.setText("AI không sẵn sàng. Chuyển đối chiếu thủ công.");});
        });
        apply.addActionListener(e->{if(extracted==null||pending)return;var data=new JsonObject();data.addProperty("full_name",name.getText());data.addProperty("id_card",identity.getText());data.addProperty("dob",dob.getText());data.addProperty("address",address.getText());autofill.accept(data);dispose();});
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1000,560);setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){camera.close();card=null;extracted=null;identity.setText("");}});
    }
    private void recognize(BufferedImage image){
        card=image;pending=true;status.setText("Đang nhận diện…");
        preview(image,null);
        Phase3UiApi.request("POST","v1/ai/id-ocr",()->{var body=new JsonObject();body.addProperty("image",encode(image));return body.toString();},envelope->{
            if(!isDisplayable())return;pending=false;extracted=envelope.getAsJsonObject("data");name.setText(extracted.get("full_name").getAsString());identity.setText(extracted.get("id_card").getAsString());dob.setText(extracted.get("dob").getAsString());address.setText(extracted.get("address").getAsString());status.setText("Kiểm tra lại các trường trước khi lưu hồ sơ.");
        },error->{pending=false;status.setText("AI không sẵn sàng. Chuyển đối chiếu thủ công.");});
    }
    private void preview(BufferedImage cardImage,BufferedImage liveImage){
        new SwingWorker<Icon[],Void>(){
            protected Icon[] doInBackground(){return new Icon[]{thumbnail(cardImage),thumbnail(liveImage)};}
            protected void done(){if(!isDisplayable())return;try{var icons=get();cardPreview.setIcon(icons[0]);cardPreview.setText(icons[0]==null?"Ảnh CCCD":null);livePreview.setIcon(icons[1]);livePreview.setText(icons[1]==null?"Ảnh trực tiếp":null);}
                catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(java.util.concurrent.ExecutionException ex){status.setText("Không thể hiển thị ảnh xem trước.");}}
        }.execute();
    }
    private static Icon thumbnail(BufferedImage image){if(image==null)return null;var thumb=new BufferedImage(135,85,BufferedImage.TYPE_INT_RGB);var g=thumb.createGraphics();try{g.drawImage(image,0,0,135,85,null);}finally{g.dispose();}return new ImageIcon(thumb);}
    private static String encode(BufferedImage image){
        try{var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);if(out.size()>5*1024*1024)throw new IOException("Image too large");return Base64.getEncoder().encodeToString(out.toByteArray());}
        catch(IOException ex){throw new java.io.UncheckedIOException("Image encoding failed",ex);}
    }
}
