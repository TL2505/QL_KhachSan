package quanlykhachsan;


public class Quanlykhachsan {

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(()->{
            var splash=new quanlykhachsan.frontend.view.SplashScreen();splash.setVisible(true);
            new javax.swing.SwingWorker<Void,Void>(){
                protected Void doInBackground(){initialize();quanlykhachsan.frontend.api.ApiClient.getInstance();return null;}
                protected void done(){
                    try{get();new quanlykhachsan.frontend.view.LoginForm().setVisible(true);}
                    catch(InterruptedException ex){Thread.currentThread().interrupt();}
                    catch(java.util.concurrent.ExecutionException ex){javax.swing.JOptionPane.showMessageDialog(null,"Không thể khởi động ứng dụng. Kiểm tra cấu hình và thử lại.");}
                    finally{splash.dispose();}
                }
            }.execute();
        });
    }

    private static void initialize() {
        // Kích hoạt FlatLaf - giao diện hiện đại cho Java Swing
        System.setProperty("flatlaf.useWindowDecorations", "true");

        // Initialize theme and global UI
        quanlykhachsan.frontend.utils.ThemeManager.applyGlobalUI();

        // Specific overrides after setup
        javax.swing.UIManager.put("Component.arc", 8);
        javax.swing.UIManager.put("Button.arc", 8);
        javax.swing.UIManager.put("TextComponent.arc", 8);
        javax.swing.UIManager.put("Button.margin", new java.awt.Insets(6, 16, 6, 16));
        javax.swing.UIManager.put("defaultFont", new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));

    }
}
