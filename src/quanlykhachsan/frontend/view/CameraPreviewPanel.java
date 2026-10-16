package quanlykhachsan.frontend.view;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import com.github.sarxos.webcam.*;

/** Native camera work stays on one daemon worker. EDT painting reads a completed frame only. */
public final class CameraPreviewPanel extends JPanel implements AutoCloseable {
    public interface FrameSource {void open() throws IOException;BufferedImage read() throws IOException;void close();}
    private final FrameSource source;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"hotel-camera");t.setDaemon(true);return t;});
    private final AtomicReference<BufferedImage> frame=new AtomicReference<>();
    private volatile String status="Đang mở camera…";
    private volatile boolean closed;
    private final Timer repaintTimer=new Timer(33,e->repaint());
    public CameraPreviewPanel(){this(new WebcamSource());}
    public CameraPreviewPanel(FrameSource source){
        this.source=source;setPreferredSize(new Dimension(560,350));setBackground(Color.BLACK);repaintTimer.start();
        executor.execute(()->{
            try{source.open();status="";
                executor.scheduleAtFixedRate(()->{
                    if(closed)return;
                    try{BufferedImage next=source.read();if(!closed)frame.set(next);}
                    catch(IOException|WebcamException|LinkageError ex){status="Camera không sẵn sàng. Dùng ảnh mẫu hoặc đối chiếu thủ công.";closed=true;frame.set(null);source.close();}
                },0,33,TimeUnit.MILLISECONDS);
            }catch(IOException|WebcamException|LinkageError ex){status="Camera không sẵn sàng. Dùng ảnh mẫu hoặc đối chiếu thủ công.";source.close();}
        });
    }
    public BufferedImage snapshot(){return frame.get();}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);var g=(Graphics2D)graphics.create();try{
            BufferedImage current=frame.get();if(current!=null)g.drawImage(current,0,0,getWidth(),getHeight(),null);
            int width=(int)(getWidth()*.78),height=(int)(width*53.98/85.6);int x=(getWidth()-width)/2,y=(getHeight()-height)/2;
            g.setColor(new Color(50,220,140));g.setStroke(new BasicStroke(2));g.drawRoundRect(x,y,width,height,12,12);
            g.setColor(Color.WHITE);g.drawString(status.isEmpty()?"Đặt CCCD vào khung căn chỉnh":status,12,getHeight()-15);
        }finally{g.dispose();}
    }
    @Override public synchronized void close(){
        if(executor.isShutdown())return;
        closed=true;repaintTimer.stop();frame.set(null);
        executor.execute(source::close);executor.shutdown();
    }
    private static final class WebcamSource implements FrameSource {
        private Webcam camera;
        public void open() throws IOException {camera=Webcam.getDefault();if(camera==null)throw new IOException("Camera unavailable");camera.setViewSize(new Dimension(640,480));if(!camera.open())throw new IOException("Camera unavailable");}
        public BufferedImage read() throws IOException {var image=camera.getImage();if(image==null)throw new IOException("Camera frame unavailable");return image;}
        public void close(){if(camera!=null&&camera.isOpen())camera.close();}
    }
}
