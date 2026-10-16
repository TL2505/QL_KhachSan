package quanlykhachsan.frontend.view;

import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPreviewPanelTest {
    @Test void slowSyntheticCameraDoesNotBlockEdtAndClosesOnWorker() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var closed=new CountDownLatch(1);var wrongThread=new AtomicBoolean();
        var source=new CameraPreviewPanel.FrameSource(){
            public void open(){if(SwingUtilities.isEventDispatchThread())wrongThread.set(true);}
            public BufferedImage read() throws java.io.IOException {entered.countDown();try{release.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new java.io.IOException(ex);}return new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);}
            public void close(){if(SwingUtilities.isEventDispatchThread())wrongThread.set(true);closed.countDown();}
        };
        var panel=new AtomicReference<CameraPreviewPanel>();SwingUtilities.invokeAndWait(()->panel.set(new CameraPreviewPanel(source)));
        try{
            assertTrue(entered.await(2,TimeUnit.SECONDS));var heartbeat=new CountDownLatch(1);SwingUtilities.invokeLater(heartbeat::countDown);
            assertTrue(heartbeat.await(500,TimeUnit.MILLISECONDS));SwingUtilities.invokeAndWait(()->panel.get().close());
            release.countDown();assertTrue(closed.await(2,TimeUnit.SECONDS));assertFalse(wrongThread.get());assertNull(panel.get().snapshot());
        }finally{release.countDown();SwingUtilities.invokeAndWait(()->panel.get().close());}
    }
}
