/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package quanlykhachsan;

/**
 *
 * @author LEGION
 */
public class Quanlykhachsan {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            // Thiết lập Look and Feel cho mềm mại như bản Native
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Quanlykhachsan.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        // Bắt đầu luồng Swing
        java.awt.EventQueue.invokeLater(() -> {
            new quanlykhachsan.frontend.view.LoginForm().setVisible(true);
        });
    }
}
