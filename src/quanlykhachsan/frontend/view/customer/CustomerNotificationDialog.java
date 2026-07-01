package quanlykhachsan.frontend.view.customer;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import quanlykhachsan.frontend.utils.ThemeManager;

public class CustomerNotificationDialog extends JDialog {

    public CustomerNotificationDialog(Window owner) {
        super(owner, "Thông báo", Dialog.ModalityType.APPLICATION_MODAL);
        initUI();
        setSize(420, 360);
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(ThemeManager.getBgPanel());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(13, 148, 136));
        header.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("THÔNG BÁO");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JLabel icon = new JLabel("🔔");
        icon.setFont(new Font("Segoe UI", Font.PLAIN, 22));
        icon.setForeground(Color.WHITE);
        header.add(icon, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        content.add(createNoticeCard("🔔 Đơn đặt phòng #125 đang chờ xác nhận", "Hệ thống đang xử lý đơn đặt và sẽ gửi email xác nhận ngay khi hoàn tất."));
        content.add(createNoticeCard("🏷️ Bạn nhận được voucher 10%", "Ưu đãi áp dụng cho tất cả phòng Deluxe và Family trong tuần này."));
        content.add(createNoticeCard("⭐ Đánh giá phòng để nhận 100 điểm", "Chia sẻ trải nghiệm của bạn và nhận thêm ưu đãi cho lần đặt sau."));
        content.add(createNoticeCard("📢 Khuyến mãi cuối tuần", "Giảm giá 15% cho khách đặt trước 3 ngày.") );

        JScrollPane scrollPane = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(ThemeManager.getBgPanel());
        scrollPane.getViewport().setBackground(ThemeManager.getBgPanel());
        scrollPane.setPreferredSize(new Dimension(400, 220));
        add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 0, 15, 15));

        JButton btnClose = new JButton("Đóng");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnClose.setBackground(ThemeManager.getBorderColor());
        btnClose.setForeground(ThemeManager.getTextMain());
        btnClose.setFocusPainted(false);
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);

        add(footer, BorderLayout.SOUTH);
    }

    private JPanel createNoticeCard(String title, String detail) {
        JPanel card = new JPanel(new BorderLayout(10, 6));
        card.setOpaque(true);
        card.setBackground(ThemeManager.getCardBg());
        card.setBorder(new CompoundBorder(new LineBorder(ThemeManager.getBorderColor(), 1, true), new EmptyBorder(14, 14, 14, 14)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(ThemeManager.getTextMain());

        JLabel lblDetail = new JLabel(detail);
        lblDetail.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDetail.setForeground(ThemeManager.getTextMuted());

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(lblDetail, BorderLayout.CENTER);

        return card;
    }
}
