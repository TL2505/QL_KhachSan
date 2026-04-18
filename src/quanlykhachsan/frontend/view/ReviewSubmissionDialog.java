package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import quanlykhachsan.backend.model.Review;
import quanlykhachsan.backend.model.Booking;
import quanlykhachsan.frontend.api.ReviewAPI;

public class ReviewSubmissionDialog extends JDialog {

    private Booking booking;
    private JSlider sldRating;
    private JTextArea txtComment;

    private static final Color PRIMARY = new Color(13, 148, 136);
    private static final Color BG = new Color(248, 250, 252);

    public ReviewSubmissionDialog(Window owner, Booking booking) {
        super(owner, "Đánh giá dịch vụ", Dialog.ModalityType.APPLICATION_MODAL);
        this.booking = booking;

        initUI();
        
        setSize(400, 450);
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(PRIMARY);
        header.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("Chia sẻ trải nghiệm của bạn");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title);

        JLabel sub = new JLabel("Phòng #" + booking.getRoomId());
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(204, 251, 241));
        header.add(sub);
        add(header, BorderLayout.NORTH);

        // Content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        content.add(new JLabel("Điểm đánh giá (1 - 5 sao):"));
        sldRating = new JSlider(1, 5, 5);
        sldRating.setMajorTickSpacing(1);
        sldRating.setPaintTicks(true);
        sldRating.setPaintLabels(true);
        sldRating.setBackground(BG);
        content.add(sldRating);
        content.add(Box.createVerticalStrut(20));

        content.add(new JLabel("Nhận xét của bạn:"));
        txtComment = new JTextArea(5, 20);
        txtComment.setLineWrap(true);
        txtComment.setWrapStyleWord(true);
        txtComment.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(txtComment);
        scroll.setBorder(new LineBorder(new Color(226, 232, 240)));
        content.add(scroll);

        add(content, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 20, 20, 20));

        JButton btnCancel = new JButton("Bỏ qua");
        btnCancel.addActionListener(e -> dispose());
        footer.add(btnCancel);

        JButton btnSubmit = new JButton("Gửi đánh giá");
        btnSubmit.setBackground(PRIMARY);
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSubmit.addActionListener(e -> submitReview());
        footer.add(btnSubmit);

        add(footer, BorderLayout.SOUTH);
    }

    private void submitReview() {
        Review r = new Review();
        r.setCustomerId(booking.getCustomerId());
        r.setRoomId(booking.getRoomId());
        r.setRating(sldRating.getValue());
        r.setComment(txtComment.getText());

        String res = ReviewAPI.addReview(r);
        if ("Success".equals(res)) {
            JOptionPane.showMessageDialog(this, "Cảm ơn bạn đã đóng góp ý kiến!");
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Lỗi: " + res);
        }
    }
}
