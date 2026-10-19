package quanlykhachsan.frontend;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import quanlykhachsan.frontend.worker.ServiceOrderAlertWorker;
import quanlykhachsan.frontend.utils.SessionManagerUtil;

class ServiceOrderDeliveryTest {
    @Test void committedOrderSnapshotReachesDesktopListenerOnEdt()throws Exception {
        var delivered=new CountDownLatch(1);var edt=new AtomicBoolean();var available=new AtomicBoolean();
        SessionManagerUtil.setAccessToken("synthetic-not-a-real-token");
        var worker=new ServiceOrderAlertWorker((count,orders)->{if(count==1){edt.set(SwingUtilities.isEventDispatchThread());assertEquals(42,orders.get(0).get("id").getAsInt());delivered.countDown();}},
            ()->available.get()?"{\"status\":\"success\",\"data\":[{\"id\":42,\"order_status\":\"PENDING\"}]}":"{\"status\":\"success\",\"data\":[]}");
        try{
            worker.start();long started=System.nanoTime();available.set(true);
            assertTrue(delivered.await(3,TimeUnit.SECONDS),"Desktop delivery exceeded three seconds");assertTrue(edt.get());
            System.out.printf(java.util.Locale.ROOT,"Synthetic source-to-desktop latency: %.3f ms%n",(System.nanoTime()-started)/1_000_000.0);
        }finally{worker.stop();SessionManagerUtil.clear();}
    }
}
