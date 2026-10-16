package quanlykhachsan.frontend.view;

import java.awt.*;
import javax.swing.*;

public final class SplashScreen extends JWindow {
    public SplashScreen(){
        var panel=new JPanel(new BorderLayout(10,18));panel.setBorder(BorderFactory.createEmptyBorder(28,28,28,28));panel.setBackground(new Color(18,24,36));
        var title=new JLabel("QL Khách Sạn · Staff Desktop",SwingConstants.CENTER);title.setForeground(Color.WHITE);title.setFont(title.getFont().deriveFont(Font.BOLD,21));panel.add(title,BorderLayout.CENTER);
        var progress=new JProgressBar();progress.setIndeterminate(true);panel.add(progress,BorderLayout.SOUTH);setContentPane(panel);setSize(440,180);setLocationRelativeTo(null);
    }
}
