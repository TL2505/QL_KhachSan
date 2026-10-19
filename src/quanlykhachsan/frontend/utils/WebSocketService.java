package quanlykhachsan.frontend.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import javax.swing.SwingUtilities;

public class WebSocketService {
    
    private static WebSocket currentWebSocket;
    
    public static void connect() {
        String base = quanlykhachsan.frontend.api.ApiConfig.getBaseUrl();
        String host = "localhost";
        int port = quanlykhachsan.frontend.api.ApiConfig.DEFAULT_BACKEND_PORT;
        try {
            URI uri = URI.create(base);
            if (uri.getHost() != null && !uri.getHost().isBlank()) host = uri.getHost();
            if (uri.getPort() > 0) port = uri.getPort();
        } catch (Exception ignored) {}
        String wsUrl = (base != null && base.startsWith("https") ? "wss://" : "ws://") + host + ":" + port + "/ws"; 
        
        try {
            System.out.println("[WebSocket] Đang thử kết nối tới " + wsUrl + "...");
            HttpClient client = HttpClient.newHttpClient();
            client.newWebSocketBuilder()
                  .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                      @Override
                      public void onOpen(WebSocket webSocket) {
                          System.out.println("[WebSocket] Đã kết nối thành công!");
                          currentWebSocket = webSocket;
                          
                          // Hiển thị thử 1 thông báo chào mừng
                          SwingUtilities.invokeLater(() -> {
                              ToastNotification.showToast("KẾT NỐI REAL-TIME", "Đã kết nối trực tiếp đến Máy chủ. Đang lắng nghe thông báo mới...");
                          });
                          
                          WebSocket.Listener.super.onOpen(webSocket);
                      }

                      @Override
                      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                          System.out.println("[WebSocket] Nhận tin nhắn: " + data);
                          
                          // Lấy dữ liệu gửi về từ Server
                          String message = data.toString();
                          
                          // Nếu Server gửi JSON {"message": "..."} thì bạn có thể dùng Gson để tách lấy chữ
                          // Ở đây ta hiển thị thẳng để test
                          
                          SwingUtilities.invokeLater(() -> {
                              // Gọi Giao diện hiển thị (Toast)
                              ToastNotification.showToast("CẬP NHẬT TỪ HỆ THỐNG", message);
                          });
                          
                          return WebSocket.Listener.super.onText(webSocket, data, last);
                      }

                      @Override
                      public void onError(WebSocket webSocket, Throwable error) {
                          System.out.println("[WebSocket] Lỗi kết nối: " + error.getMessage());
                      }
                      
                      @Override
                      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                          System.out.println("[WebSocket] Kết nối bị ngắt: " + reason);
                          return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                      }
                  });
        } catch (Exception e) {
            System.err.println("[WebSocket] Lỗi khởi tạo: " + e.getMessage());
        }
    }
    
    public static void disconnect() {
        if (currentWebSocket != null) {
            currentWebSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client đóng ứng dụng");
        }
    }
}
