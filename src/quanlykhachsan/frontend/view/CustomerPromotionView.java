package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import quanlykhachsan.backend.model.Promotion;
import quanlykhachsan.frontend.api.PromotionAPI;

public class CustomerPromotionView extends JPanel {

    private JPanel voucherPanel;
    private JLabel lblStatus;
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");

    private static final Color PRIMARY   = new Color(37, 99, 235);
    private static final Color SUCCESS   = new Color(5, 150, 105);
    private static final Color BG_PANEL = new Color(248, 250, 252);
    private static final Color BORDER_CLR = new Color(226, 232, 240);

    public CustomerPromotionView() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_PANEL);
        initUI();
        loadActivePromotions();
    }

    private void initUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_CLR),
                new EmptyBorder(20, 25, 20, 25)));
        
        JLabel title = new JLabel("Ưu đãi & Khuyến mãi dành riêng cho bạn");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(15, 23, 42));
        
        lblStatus = new JLabel("Đang tải các ưu đãi mới nhất...");
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        
        header.add(title, BorderLayout.WEST);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Content
        voucherPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 20, 20));
        voucherPanel.setBackground(BG_PANEL);
        
        JScrollPane scroll = new JScrollPane(voucherPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_PANEL);
        add(scroll, BorderLayout.CENTER);
    }

    private void loadActivePromotions() {
        voucherPanel.removeAll();
        SwingWorker<List<Promotion>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Promotion> doInBackground() {
                return PromotionAPI.getActivePromotions();
            }

            @Override
            protected void done() {
                try {
                    List<Promotion> list = get();
                    if (list != null && !list.isEmpty()) {
                        for (Promotion p : list) {
                            voucherPanel.add(makeVoucherCard(p));
                        }
                        lblStatus.setText("Chúng tôi tìm thấy " + list.size() + " ưu đãi hấp dẫn!");
                        lblStatus.setForeground(SUCCESS);
                    } else {
                        lblStatus.setText("Hiện chưa có chương trình ưu đãi nào.");
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Lỗi khi kết nối hệ thống.");
                }
                voucherPanel.revalidate();
                voucherPanel.repaint();
            }
        };
        worker.execute();
    }

    private JPanel makeVoucherCard(Promotion p) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Shadow
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fillRoundRect(5, 5, getWidth()-10, getHeight()-10, 20, 20);
                
                // Body
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth()-10, getHeight()-10, 20, 20);
                
                // Border
                g2.setColor(new Color(241, 245, 249));
                g2.drawRoundRect(0, 0, getWidth()-10, getHeight()-10, 20, 20);

                // Side Dash Line (Voucher effect)
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0));
                g2.setColor(new Color(226, 232, 240));
                g2.drawLine(80, 15, 80, getHeight()-25);
            }
        };
        card.setPreferredSize(new Dimension(350, 120));
        card.setLayout(new BorderLayout());
        card.setOpaque(false);

        // Left Section (Icon/Value)
        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(85, 120));
        
        String tagText = p.getDiscountType().equals("percentage") ? "% OFF" : "VND";
        JLabel lblTag = new JLabel(tagText);
        lblTag.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTag.setForeground(Color.WHITE);
        lblTag.setOpaque(true);
        lblTag.setBackground(p.getDiscountType().equals("percentage") ? SUCCESS : PRIMARY);
        lblTag.setBorder(new EmptyBorder(4, 8, 4, 8));
        lblTag.setHorizontalAlignment(SwingConstants.CENTER);
        
        left.add(lblTag);
        card.add(left, BorderLayout.WEST);

        // Right Section (Details)
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(15, 20, 15, 15));

        JLabel title = new JLabel(p.getName());
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(15, 23, 42));

        String valueStr = p.getDiscountType().equals("percentage") 
            ? p.getDiscountValue() + "% Giảm toàn bộ"
            : String.format("%,.0fđ Giảm ngay", p.getDiscountValue());
        JLabel val = new JLabel(valueStr);
        val.setFont(new Font("Segoe UI", Font.BOLD, 14));
        val.setForeground(PRIMARY);

        JLabel cond = new JLabel("Hạn sử dụng: " + df.format(p.getEndDate()));
        cond.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cond.setForeground(new Color(100, 116, 139));

        right.add(title);
        right.add(Box.createVerticalStrut(5));
        right.add(val);
        right.add(Box.createVerticalGlue());
        right.add(cond);

        card.add(right, BorderLayout.CENTER);

        return card;
    }
}
