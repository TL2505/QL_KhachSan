package quanlykhachsan.frontend.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import quanlykhachsan.backend.model.Customer;
import quanlykhachsan.backend.model.LoyaltyHistory;
import quanlykhachsan.frontend.api.LoyaltyAPI;

public class LoyaltyForm extends JPanel {

    // ─── Color Palette ────────────────────────────────────────────────────
    private final Color BG = quanlykhachsan.frontend.utils.ThemeManager.getBgPanel();
    private final Color WHITE = quanlykhachsan.frontend.utils.ThemeManager.getCardBg();
    private final Color BORDER_CLR = quanlykhachsan.frontend.utils.ThemeManager.getBorderColor();
    private final Color TEXT_DARK = quanlykhachsan.frontend.utils.ThemeManager.getTextMain();
    private final Color TEXT_MUTED = new Color(107, 114, 128);
    private final Color PRIMARY = new Color(37, 99, 235);
    private final Color SUCCESS = new Color(5, 150, 105);
    private final Color DANGER = new Color(220, 38, 38);
private final Color SILVER_BG = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(30, 41, 59) : new Color(241, 245, 249);
    private final Color SILVER_FG = quanlykhachsan.frontend.utils.ThemeManager.getTextMuted();
    private final Color GOLD_BG = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(146, 64, 14) : new Color(255, 251, 235);
    private final Color GOLD_FG = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(253, 230, 138) : new Color(180, 83, 9);
    private final Color VIP_BG = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(76, 29, 149) : new Color(245, 243, 255);
    private final Color VIP_FG = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(221, 214, 254) : new Color(109, 40, 217);
    private final Color ROW_EVEN = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(249, 250, 251);
    private final Color EARN_CLR = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(6, 78, 59) : new Color(220, 252, 231);
    private final Color REDEEM_CLR = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode() ? new Color(120, 53, 15) : new Color(255, 237, 213);

    private final DecimalFormat nf = new DecimalFormat("#,###");
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    // ─── State ────────────────────────────────────────────────────────────
    private List<Customer> customers = new ArrayList<>();
    private Customer selectedCustomer = null;

    // ─── UI ───────────────────────────────────────────────────────────────
    private JLabel lblStatus;
    private JTable tblCustomers;
    private DefaultTableModel tblCustomersModel;
    private JTable tblHistory;
    private DefaultTableModel tblHistoryModel;

    // Stats labels
    private JLabel lblTotalSilver, lblTotalGold, lblTotalVip, lblTotalCustomers;
    // Customer detail
    private JLabel lblDetName, lblDetPoints, lblDetLevel, lblDetTotal;
    private JPanel detailPanel;

    // Search
    private JTextField txtSearch;

    public LoyaltyForm() {
        setLayout(new BorderLayout());
        setBackground(BG);
        buildUI();
        loadData();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUILD UI
    // ═══════════════════════════════════════════════════════════════════════
    private void buildUI() {
        add(buildHeader(), BorderLayout.NORTH);

        // Bọc nội dung chính trong JScrollPane để cuộn xuống phần Lịch sử tích điểm
        JScrollPane mainScroll = new JScrollPane(buildBody(),
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainScroll.setBorder(null);
        mainScroll.getVerticalScrollBar().setUnitIncrement(20); // Cuộn nhanh hơn
        add(mainScroll, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_CLR),
                new EmptyBorder(12, 22, 12, 22)));

        // Left: dùng BoxLayout để tránh chồng chữ
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleRow.setOpaque(false);
        JLabel icon = new JLabel("⭐");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        JLabel title = new JLabel("Chương Trình Khách Hàng Thân Thiết");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(TEXT_DARK);
        titleRow.add(icon);
        titleRow.add(title);

        JLabel sub = new JLabel("Loyalty Program Management — Quản lý hạng thành viên điểm tích lũy");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(TEXT_MUTED);

        left.add(titleRow);
        left.add(sub);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        lblStatus = new JLabel("Đang tải...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(TEXT_MUTED);
        JButton btnRefresh = makeFlatButton("↻  Làm mới", PRIMARY);
        btnRefresh.addActionListener(e -> loadData());
        right.add(lblStatus);
        right.add(btnRefresh);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(18, 18, 18, 18));

        // Row 1: Stats cards
        body.add(buildStatsRow(), BorderLayout.NORTH);

        // Row 2: Tier benefits + center content (table + detail)
        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(16, 0, 0, 0));

        // Left: tier benefits card - bọc vào scroll pane để tránh cắt khi có nhiều hạng
        JScrollPane tierScroll = new JScrollPane(buildTierBenefitsCard(),
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        tierScroll.setBorder(null);
        tierScroll.setPreferredSize(new Dimension(230, 0));
        tierScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(tierScroll, BorderLayout.WEST);

        // Middle: customer table + history table
        center.add(buildMainContent(), BorderLayout.CENTER);

        body.add(center, BorderLayout.CENTER);
        return body;
    }

    // ─── Stats Cards ───────────────────────────────────────────────────────
    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 14, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        lblTotalCustomers = new JLabel("0");
        lblTotalSilver = new JLabel("0");
        lblTotalGold = new JLabel("0");
        lblTotalVip = new JLabel("0");

        row.add(buildStatCard("Tổng khách hàng", lblTotalCustomers, PRIMARY, new Color(219, 234, 254)));
        row.add(buildStatCard("Silver", lblTotalSilver, SILVER_FG, SILVER_BG));
        row.add(buildStatCard("Gold", lblTotalGold, GOLD_FG, GOLD_BG));
        row.add(buildStatCard("VIP", lblTotalVip, VIP_FG, VIP_BG));
        return row;
    }

    private JPanel buildStatCard(String title, JLabel valueLabel, Color fgColor, Color bgColor) {
        JPanel card = new JPanel(new BorderLayout(0, 5)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Background tint
                g2.setColor(new Color(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue(), 25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                // Border
                g2.setColor(new Color(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue(), 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel lTitle = new JLabel(title);
        lTitle.setFont(new Font("Segoe UI Emoji", Font.BOLD, 12));
        lTitle.setForeground(new Color(71, 85, 105));

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(fgColor);

        card.add(lTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // ─── Tier Benefits Card ────────────────────────────────────────────────
    private JPanel buildTierBenefitsCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(WHITE);
        card.setPreferredSize(new Dimension(220, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0,0,0,0),
                new EmptyBorder(18, 16, 18, 16)));

        JLabel t = new JLabel("🏆 Hạng thành viên");
        t.setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.setForeground(TEXT_DARK);
        t.setAlignmentX(LEFT_ALIGNMENT);
        card.add(t);
        card.add(Box.createVerticalStrut(16));

        card.add(buildTierBlock("🥈 Member", "0 – 499 điểm", SILVER_FG, SILVER_BG, new String[] {
                "✔ Tích 1đ / 1.000đ",
                "✔ Ưu tiên đặt phòng",
                "✔ Giảm 5% dịch vụ"
        }));
        card.add(Box.createVerticalStrut(12));
        card.add(buildTierBlock("🥈 Silver", "500 – 1.999 điểm", new Color(71, 85, 105), quanlykhachsan.frontend.utils.ThemeManager.getBorderColor(), new String[] {
                "✔ Tích 1đ / 1.000đ",
                "✔ Check-in sớm",
                "✔ Giảm 8% dịch vụ"
        }));
        card.add(Box.createVerticalStrut(12));
        card.add(buildTierBlock("🥇 Gold", "2.000 – 4.999 điểm", GOLD_FG, GOLD_BG, new String[] {
                "✔ Tích 1đ / 1.000đ",
                "✔ Check-in sớm",
                "✔ Giảm 10% dịch vụ",
                "✔ Free breakfast"
        }));
        card.add(Box.createVerticalStrut(12));
        card.add(buildTierBlock("💎 VIP", "≥ 5.000 điểm", VIP_FG, VIP_BG, new String[] {
                "✔ Tích 1đ / 1.000đ",
                "✔ Check-in/out linh hoạt",
                "✔ Giảm 20% toàn bộ",
                "✔ Late check-out",
                "✔ Phòng nâng hạng"
        }));
        card.add(Box.createVerticalStrut(16));

        // Redemption rules
        JLabel rTitle = new JLabel("🎁 Đổi điểm");
        rTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        rTitle.setForeground(TEXT_DARK);
        rTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(rTitle);
        card.add(Box.createVerticalStrut(8));

        card.add(buildRedeemRule("100 điểm", "→ Giảm 50.000 VNĐ", new Color(59, 130, 246)));
        card.add(Box.createVerticalStrut(6));
        card.add(buildRedeemRule("500 điểm", "→ Giảm 300.000 VNĐ", VIP_FG));
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JPanel buildTierBlock(String name, String range, Color fg, Color bg, String[] benefits) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBackground(bg);
        block.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(fg, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        block.setAlignmentX(LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel lName = new JLabel(name);
        lName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lName.setForeground(fg);
        lName.setAlignmentX(LEFT_ALIGNMENT);
        block.add(lName);

        JLabel lRange = new JLabel(range);
        lRange.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lRange.setForeground(fg);
        lRange.setAlignmentX(LEFT_ALIGNMENT);
        block.add(lRange);
        block.add(Box.createVerticalStrut(4));

        for (String b : benefits) {
            JLabel lBenefit = new JLabel(b);
            lBenefit.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lBenefit.setForeground(TEXT_DARK);
            lBenefit.setAlignmentX(LEFT_ALIGNMENT);
            block.add(lBenefit);
        }
        return block;
    }

    private JPanel buildRedeemRule(String pts, String desc, Color color) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel lPts = new JLabel(pts);
        lPts.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lPts.setForeground(color);
        lPts.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color, 1, true),
                new EmptyBorder(1, 6, 1, 6)));

        JLabel lDesc = new JLabel(desc);
        lDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lDesc.setForeground(TEXT_DARK);

        row.add(lPts, BorderLayout.WEST);
        row.add(lDesc, BorderLayout.CENTER);
        return row;
    }

    // ─── Main Content (Customer Table + History) ──────────────────────────
    private JPanel buildMainContent() {
        JPanel container = new JPanel(new BorderLayout(0, 12));
        container.setOpaque(false);

        // Top: bảng khách hàng (chiếm 60% chiều cao)
        JPanel topWrapper = new JPanel(new BorderLayout());
        topWrapper.setOpaque(false);
        topWrapper.setPreferredSize(new Dimension(0, 340));
        topWrapper.add(buildCustomerTableCard(), BorderLayout.CENTER);

        // Bottom: bảng lịch sử - luôn hiển thị, không bao giờ bị ẩn
        JPanel bottomWrapper = new JPanel(new BorderLayout());
        bottomWrapper.setOpaque(false);
        bottomWrapper.add(buildHistoryCard(), BorderLayout.CENTER);

        container.add(topWrapper, BorderLayout.NORTH);
        container.add(bottomWrapper, BorderLayout.CENTER);
        return container;
    }

    private JPanel buildCustomerTableCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(WHITE);
        card.setBorder(null);

        // Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setBackground(WHITE);
        toolbar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lTitle = new JLabel("📋 Danh sách khách hàng thành viên");
        lTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lTitle.setForeground(TEXT_DARK);

        txtSearch = new JTextField();
        txtSearch.setPreferredSize(new Dimension(200, 32));
        txtSearch.putClientProperty("JTextField.placeholderText", "🔍 Tìm tên / SĐT...");
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                filterTable();
            }
        });

        toolbar.add(lTitle, BorderLayout.WEST);
        toolbar.add(txtSearch, BorderLayout.EAST);
        card.add(toolbar, BorderLayout.NORTH);

        // Table
        tblCustomersModel = new DefaultTableModel(
                new String[] { "ID", "Họ tên", "SĐT", "Điểm hiện có", "Tổng điểm", "Hạng" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblCustomers = new JTable(tblCustomersModel);
        styleTable(tblCustomers);
        tblCustomers.setRowHeight(42);
        tblCustomers.getColumnModel().getColumn(0).setPreferredWidth(40);
        tblCustomers.getColumnModel().getColumn(1).setPreferredWidth(160);
        tblCustomers.getColumnModel().getColumn(2).setPreferredWidth(110);
        tblCustomers.getColumnModel().getColumn(3).setPreferredWidth(110);
        tblCustomers.getColumnModel().getColumn(4).setPreferredWidth(110);
        tblCustomers.getColumnModel().getColumn(5).setPreferredWidth(90);

        // Custom renderer for "Hạng" column
        tblCustomers.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = new JLabel(value != null ? value.toString() : "Silver");
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lbl.setOpaque(true);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                String lvl = value != null ? value.toString() : "Member";
                switch (lvl) {
                    case "VIP":
                        lbl.setBackground(VIP_BG); lbl.setForeground(VIP_FG); break;
                    case "Gold":
                        lbl.setBackground(GOLD_BG); lbl.setForeground(GOLD_FG); break;
                    case "Silver":
                        lbl.setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBorderColor());
                        lbl.setForeground(new Color(71, 85, 105)); break;
                    case "Diamond": case "Platinum":
                        lbl.setBackground(new Color(237, 233, 254));
                        lbl.setForeground(new Color(109, 40, 217)); break;
                    default: // Member
                        lbl.setBackground(SILVER_BG); lbl.setForeground(SILVER_FG); break;
                }
                if (isSelected) {
                    lbl.setBackground(new Color(219, 234, 254));
                    lbl.setForeground(PRIMARY);
                }
                return lbl;
            }
        });

        // Custom renderer for points columns
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        tblCustomers.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        tblCustomers.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);

        tblCustomers.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                onCustomerSelected();
        });

        JScrollPane scroll = new JScrollPane(tblCustomers);
        scroll.setBorder(null);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(WHITE);
        card.setBorder(null);

        // Header
        JPanel histHeader = new JPanel(new BorderLayout());
        histHeader.setBackground(WHITE);
        histHeader.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lhTitle = new JLabel("📜 Lịch sử tích điểm");
        lhTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lhTitle.setForeground(TEXT_DARK);
        histHeader.add(lhTitle, BorderLayout.WEST);

        // Detail chip (customer info)
        detailPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        detailPanel.setOpaque(false);
        boolean isDark = quanlykhachsan.frontend.utils.ThemeManager.isDarkMode();
        lblDetName = makeChipLabel("---", isDark ? new Color(30,58,138) : new Color(219, 234, 254), PRIMARY);
        lblDetLevel = makeChipLabel("---", SILVER_BG, SILVER_FG);
        lblDetPoints = makeChipLabel("0 điểm", isDark ? new Color(6,78,59) : new Color(220, 252, 231), SUCCESS);
        lblDetTotal = makeChipLabel("Tổng: 0đ", isDark ? new Color(113,63,18) : new Color(254, 243, 199), GOLD_FG);
        detailPanel.add(lblDetName);
        detailPanel.add(lblDetLevel);
        detailPanel.add(lblDetPoints);
        detailPanel.add(lblDetTotal);
        histHeader.add(detailPanel, BorderLayout.EAST);

        card.add(histHeader, BorderLayout.NORTH);

        // Table
        tblHistoryModel = new DefaultTableModel(
                new String[] { "#", "Thời gian", "Loại", "Điểm", "Mô tả" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblHistory = new JTable(tblHistoryModel);
        styleTable(tblHistory);
        tblHistory.setRowHeight(38);
        tblHistory.getColumnModel().getColumn(0).setPreferredWidth(40);
        tblHistory.getColumnModel().getColumn(1).setPreferredWidth(140);
        tblHistory.getColumnModel().getColumn(2).setPreferredWidth(80);
        tblHistory.getColumnModel().getColumn(3).setPreferredWidth(80);
        tblHistory.getColumnModel().getColumn(4).setPreferredWidth(400);

        // Renderer for "Loại" and "Điểm"
        tblHistory.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = new JLabel(value != null ? value.toString() : "");
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setOpaque(true);
                if ("earn".equals(value)) {
                    lbl.setText("✅ Tích điểm");
                    lbl.setBackground(EARN_CLR);
                    lbl.setForeground(SUCCESS);
                } else {
                    lbl.setText("🎁 Đổi điểm");
                    lbl.setBackground(REDEEM_CLR);
                    lbl.setForeground(GOLD_FG);
                }
                if (isSelected) {
                    lbl.setBackground(new Color(219, 234, 254));
                    lbl.setForeground(PRIMARY);
                }
                return lbl;
            }
        });

        tblHistory.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = new JLabel(value != null ? value.toString() : "");
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                lbl.setHorizontalAlignment(SwingConstants.RIGHT);
                lbl.setOpaque(true);
                int pts = 0;
                try {
                    pts = Integer.parseInt(value.toString());
                } catch (Exception ignored) {
                }
                lbl.setBackground(pts > 0 ? EARN_CLR : REDEEM_CLR);
                lbl.setForeground(pts > 0 ? SUCCESS : DANGER);
                lbl.setText((pts > 0 ? "+" : "") + pts);
                if (isSelected) {
                    lbl.setBackground(new Color(219, 234, 254));
                    lbl.setForeground(PRIMARY);
                }
                return lbl;
            }
        });

        JScrollPane scrollH = new JScrollPane(tblHistory);
        scrollH.setBorder(null);
        card.add(scrollH, BorderLayout.CENTER);

        // Placeholder
        JLabel placeholder = new JLabel("← Chọn khách hàng để xem lịch sử tích điểm", SwingConstants.CENTER);
        placeholder.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        placeholder.setForeground(TEXT_MUTED);
        placeholder.setBorder(new EmptyBorder(20, 0, 0, 0));
        card.add(placeholder, BorderLayout.SOUTH);

        return card;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGIC
    // ═══════════════════════════════════════════════════════════════════════

    private void loadData() {
        lblStatus.setText("⏳ Đang tải...");
        lblStatus.setForeground(TEXT_MUTED);
        SwingWorker<List<Customer>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Customer> doInBackground() {
                return LoyaltyAPI.getAllLoyaltyCustomers();
            }

            @Override
            protected void done() {
                try {
                    customers = get();
                    refreshTable(customers);
                    updateStats();
                    int total = customers.size();
                    lblStatus.setText("✅ " + total + " khách hàng trong chương trình.");
                    lblStatus.setForeground(SUCCESS);
                } catch (Exception e) {
                    lblStatus.setText("❌ Lỗi tải dữ liệu!");
                    lblStatus.setForeground(DANGER);
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void refreshTable(List<Customer> list) {
        tblCustomersModel.setRowCount(0);
        for (Customer c : list) {
            tblCustomersModel.addRow(new Object[] {
                    c.getId(), c.getFullName(), c.getPhone(),
                    nf.format(c.getLoyaltyPoints()) + " điểm",
                    nf.format(c.getTotalLoyaltyPoints()) + " điểm",
                    c.getLoyaltyLevel() != null ? c.getLoyaltyLevel() : "Silver"
            });
        }
    }

    private void updateStats() {
        int silver = 0, gold = 0, vip = 0;
        for (Customer c : customers) {
            String lvl = c.getLoyaltyLevel();
            if ("VIP".equals(lvl))
                vip++;
            else if ("Gold".equals(lvl))
                gold++;
            else
                silver++;
        }
        lblTotalCustomers.setText(String.valueOf(customers.size()));
        lblTotalSilver.setText(String.valueOf(silver));
        lblTotalGold.setText(String.valueOf(gold));
        lblTotalVip.setText(String.valueOf(vip));
    }

    private void filterTable() {
        String keyword = txtSearch.getText().trim().toLowerCase();
        List<Customer> filtered = new ArrayList<>();
        for (Customer c : customers) {
            if (c.getFullName().toLowerCase().contains(keyword)
                    || (c.getPhone() != null && c.getPhone().contains(keyword))) {
                filtered.add(c);
            }
        }
        refreshTable(filtered);
    }

    private void onCustomerSelected() {
        int row = tblCustomers.getSelectedRow();
        if (row < 0)
            return;
        int customerId = (int) tblCustomersModel.getValueAt(row, 0);
        selectedCustomer = null;
        for (Customer c : customers)
            if (c.getId() == customerId) {
                selectedCustomer = c;
                break;
            }
        if (selectedCustomer == null)
            return;

        // Update detail chips
        lblDetName.setText("  " + selectedCustomer.getFullName() + "  ");
        String lvl = selectedCustomer.getLoyaltyLevel() != null ? selectedCustomer.getLoyaltyLevel() : "Silver";
        lblDetLevel.setText("  " + getLevelEmoji(lvl) + " " + lvl + "  ");
        decorateLevel(lblDetLevel, lvl);
        lblDetPoints.setText("  " + nf.format(selectedCustomer.getLoyaltyPoints()) + " điểm  ");
        lblDetTotal.setText("  Tổng: " + nf.format(selectedCustomer.getTotalLoyaltyPoints()) + " đ  ");

        // Load history
        loadHistory(customerId);
    }

    private void loadHistory(int customerId) {
        tblHistoryModel.setRowCount(0);
        SwingWorker<List<LoyaltyHistory>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<LoyaltyHistory> doInBackground() {
                return LoyaltyAPI.getHistory(customerId);
            }

            @Override
            protected void done() {
                try {
                    List<LoyaltyHistory> history = get();
                    int idx = 1;
                    for (LoyaltyHistory h : history) {
                        String dateStr = h.getCreatedAt() != null ? sdf.format(h.getCreatedAt()) : "---";
                        tblHistoryModel.addRow(new Object[] {
                                idx++, dateStr, h.getType(),
                                h.getPointsChange(), h.getDescription()
                        });
                    }
                    if (history.isEmpty()) {
                        tblHistoryModel.addRow(new Object[] { "", "", "", "", "—— Chưa có lịch sử tích điểm ——" });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(TEXT_DARK);
        table.setFillsViewportHeight(true);

        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(quanlykhachsan.frontend.utils.ThemeManager.getBgPanel());
        table.getTableHeader().setForeground(TEXT_MUTED);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setReorderingAllowed(false);

        // Alternating row colors
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                setOpaque(true);
                if (!sel)
                    setBackground(row % 2 == 0 ? WHITE : ROW_EVEN);
                setForeground(TEXT_DARK);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        });
    }

    private JLabel makeChipLabel(String text, Color bg, Color fg) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(fg);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(fg, 1, true),
                new EmptyBorder(3, 8, 3, 8)));
        return lbl;
    }

    private void decorateLevel(JLabel lbl, String lvl) {
        if ("VIP".equals(lvl)) {
            lbl.setBackground(VIP_BG);
            lbl.setForeground(VIP_FG);
            lbl.setBorder(
                    BorderFactory.createCompoundBorder(new LineBorder(VIP_FG, 1, true), new EmptyBorder(3, 8, 3, 8)));
        } else if ("Gold".equals(lvl)) {
            lbl.setBackground(GOLD_BG);
            lbl.setForeground(GOLD_FG);
            lbl.setBorder(
                    BorderFactory.createCompoundBorder(new LineBorder(GOLD_FG, 1, true), new EmptyBorder(3, 8, 3, 8)));
        } else {
            lbl.setBackground(SILVER_BG);
            lbl.setForeground(SILVER_FG);
            lbl.setBorder(BorderFactory.createCompoundBorder(new LineBorder(SILVER_FG, 1, true),
                    new EmptyBorder(3, 8, 3, 8)));
        }
    }

    private String getLevelEmoji(String lvl) {
        if ("VIP".equals(lvl))
            return "💎";
        if ("Gold".equals(lvl))
            return "🥇";
        return "🥈";
    }

    private JButton makeFlatButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI Emoji", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
