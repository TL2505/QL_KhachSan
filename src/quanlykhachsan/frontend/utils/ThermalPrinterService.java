package quanlykhachsan.frontend.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.hotelservice.ServiceUsage;
import quanlykhachsan.backend.room.Room;

public class ThermalPrinterService implements Printable {

    private Booking booking;
    private Customer customer;
    private Room room;
    private List<ServiceUsage> usages;
    private int days;
    private double totalAmount;

    private final DecimalFormat nf = new DecimalFormat("#,###");
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public ThermalPrinterService(Booking booking, Customer customer, Room room, List<ServiceUsage> usages, int days, double totalAmount) {
        this.booking = booking;
        this.customer = customer;
        this.room = room;
        this.usages = usages;
        this.days = days;
        this.totalAmount = totalAmount;
    }

    public void printReceipt() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(this, getPageFormat(job));
        
        // Hiển thị hộp thoại chọn máy in, nếu người dùng có nhiều máy in.
        // Thực tế hệ thống POS thường set máy in mặc định và in thẳng: job.print()
        boolean doPrint = job.printDialog();
        if (doPrint) {
            try {
                job.print();
            } catch (PrinterException e) {
                e.printStackTrace();
            }
        }
    }

    private PageFormat getPageFormat(PrinterJob job) {
        PageFormat pf = job.defaultPage();
        Paper paper = new Paper();
        
        // Khổ giấy K80 (80mm) ~ 226 điểm (points) width
        // Độ dài tùy thuộc vào nội dung, thường đặt rất dài rồi máy in tự động cắt
        double width = 226.77; 
        double height = 800.0; 
        double margin = 10.0;
        
        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - (margin * 2), height - (margin * 2));
        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);
        return pf;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        g2d.setColor(Color.BLACK);

        int y = 10;
        int width = (int) pageFormat.getImageableWidth();

        // LOGO / TÊN KHÁCH SẠN
        g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
        drawCenteredString(g2d, "HE THONG QL KHACH SAN", width, y);
        y += 15;
        
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 9));
        drawCenteredString(g2d, "Dang cap - Tien nghi - Hien dai", width, y);
        y += 20;

        g2d.setFont(new Font("Monospaced", Font.BOLD, 14));
        drawCenteredString(g2d, "HOA DON THANH TOAN", width, y);
        y += 20;

        // THÔNG TIN CHUNG
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2d.drawString("Ma Booking: BK" + booking.getId(), 0, y); y += 12;
        g2d.drawString("Khach hang: " + (customer != null ? removeAccents(customer.getFullName()) : "N/A"), 0, y); y += 12;
        g2d.drawString("Phong: " + (room != null ? room.getRoomNumber() : "N/A") + " (" + days + " dem)", 0, y); y += 12;
        g2d.drawString("Ngay in: " + sdf.format(new Date()), 0, y); y += 20;

        // ĐƯỜNG KẺ
        drawDashedLine(g2d, width, y); y += 15;

        // CHI TIẾT
        g2d.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2d.drawString("Khoan thu", 0, y); 
        drawRightAlignedString(g2d, "Thanh tien", width, y);
        y += 15;
        
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 10));
        double roomSub = days * (room != null ? room.getPrice() : 0);
        g2d.drawString("Tien phong", 0, y); 
        drawRightAlignedString(g2d, nf.format(roomSub), width, y);
        y += 15;

        double serviceSub = 0;
        if (usages != null && !usages.isEmpty()) {
            g2d.drawString("Dich vu an uong:", 0, y); y += 12;
            for (ServiceUsage u : usages) {
                g2d.drawString(" - DV " + u.getServiceId() + " (x" + u.getQuantity() + ")", 0, y);
                drawRightAlignedString(g2d, nf.format(u.getTotalPrice()), width, y);
                serviceSub += u.getTotalPrice();
                y += 12;
            }
        }
        y += 5;
        drawDashedLine(g2d, width, y); y += 15;

        // TỔNG CỘNG
        g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
        g2d.drawString("TONG CONG:", 0, y);
        drawRightAlignedString(g2d, nf.format(totalAmount) + " VND", width, y);
        y += 25;

        // FOOTER
        g2d.setFont(new Font("Monospaced", Font.ITALIC, 9));
        drawCenteredString(g2d, "Cam on QUY KHACH. Hen gap lai!", width, y);
        
        return PAGE_EXISTS;
    }
    
    // Hàm phụ trợ bỏ dấu tiếng Việt để in máy in nhiệt an toàn (nhiều máy in TQ không hỗ trợ UTF-8 chuẩn)
    private String removeAccents(String str) {
        if (str == null) return "";
        String nfdNormalizedString = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD); 
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    private void drawCenteredString(Graphics2D g, String text, int width, int y) {
        int stringLen = (int) g.getFontMetrics().getStringBounds(text, g).getWidth();
        int start = width / 2 - stringLen / 2;
        g.drawString(text, start, y);
    }

    private void drawRightAlignedString(Graphics2D g, String text, int width, int y) {
        int stringLen = (int) g.getFontMetrics().getStringBounds(text, g).getWidth();
        int start = width - stringLen;
        g.drawString(text, start, y);
    }
    
    private void drawDashedLine(Graphics2D g, int width, int y) {
        StringBuilder dashes = new StringBuilder();
        int charWidth = g.getFontMetrics().charWidth('-');
        int numChars = width / charWidth;
        for (int i = 0; i < numChars; i++) {
            dashes.append("-");
        }
        g.drawString(dashes.toString(), 0, y);
    }
}
