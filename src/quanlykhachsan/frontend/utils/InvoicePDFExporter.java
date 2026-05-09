package quanlykhachsan.frontend.utils;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import quanlykhachsan.backend.booking.Booking;
import quanlykhachsan.backend.customer.Customer;
import quanlykhachsan.backend.room.Room;
import java.awt.Desktop;

public class InvoicePDFExporter {

    public static void exportPDF(Booking booking, Customer customer, Room room,
            java.util.List<quanlykhachsan.backend.hotelservice.ServiceUsage> usages, int days, double totalAmount) {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            // Check if folder exists
            File dir = new File("invoices");
            if (!dir.exists()) {
                dir.mkdir();
            }

            String fileName = "invoices/HoaDon_" + booking.getId() + "_" + System.currentTimeMillis() + ".pdf";
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            // Font setting
            BaseFont bf;
            try {
                bf = BaseFont.createFont("c:/windows/fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ex) {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            }

            Font fontHeader = new Font(bf, 20, Font.BOLD, BaseColor.BLUE);
            Font fontNormal = new Font(bf, 13, Font.NORMAL, BaseColor.BLACK);
            Font fontBold = new Font(bf, 13, Font.BOLD, BaseColor.BLACK);
            Font fontTotal = new Font(bf, 16, Font.BOLD, BaseColor.RED);

            // LOGO & HOTEL INFO
            Paragraph hotelName = new Paragraph("HỆ THỐNG QUẢN LÝ KHÁCH SẠN", fontHeader);
            hotelName.setAlignment(Element.ALIGN_CENTER);
            document.add(hotelName);

            Paragraph hotelSub = new Paragraph("Đẳng cấp - Tiện nghi - Hiện đại", fontNormal);
            hotelSub.setAlignment(Element.ALIGN_CENTER);
            document.add(hotelSub);

            document.add(new Paragraph(" "));

            Paragraph title = new Paragraph("HÓA ĐƠN THANH TOÁN (E-INVOICE)",
                    new Font(bf, 18, Font.BOLD, BaseColor.BLACK));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            // INFO SECTION
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            document.add(new Paragraph("Mã đặt phòng (Booking ID): " + booking.getId(), fontBold));
            document.add(
                    new Paragraph("Khách hàng: " + (customer != null ? customer.getFullName() : "N/A"), fontNormal));
            document.add(
                    new Paragraph("Số điện thoại: " + (customer != null ? customer.getPhone() : "N/A"), fontNormal));
            document.add(new Paragraph(
                    "Thời gian Check-in: "
                            + (booking.getCheckInDate() != null ? sdf.format(booking.getCheckInDate()) : "N/A"),
                    fontNormal));
            document.add(new Paragraph("Thời gian xuất hóa đơn: " + sdf.format(new Date()), fontNormal));

            document.add(new Paragraph(" "));

            DecimalFormat nf = new DecimalFormat("#,###");

            // TABLE SECTION: PHÒNG
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            PdfPCell cell1 = new PdfPCell(new Phrase("Phòng", fontBold));
            cell1.setBackgroundColor(BaseColor.LIGHT_GRAY);
            PdfPCell cell2 = new PdfPCell(new Phrase("Số ngày/đêm", fontBold));
            cell2.setBackgroundColor(BaseColor.LIGHT_GRAY);
            PdfPCell cell3 = new PdfPCell(new Phrase("Đơn giá", fontBold));
            cell3.setBackgroundColor(BaseColor.LIGHT_GRAY);
            PdfPCell cell4 = new PdfPCell(new Phrase("Thành tiền", fontBold));
            cell4.setBackgroundColor(BaseColor.LIGHT_GRAY);

            table.addCell(cell1);
            table.addCell(cell2);
            table.addCell(cell3);
            table.addCell(cell4);

            double roomSub = days * (room != null ? room.getPrice() : 0);
            table.addCell(new Phrase((room != null ? room.getRoomNumber() : "N/A"), fontNormal));
            table.addCell(new Phrase(String.valueOf(days), fontNormal));
            table.addCell(new Phrase((room != null ? nf.format(room.getPrice()) : "0") + " VNĐ", fontNormal));
            table.addCell(new Phrase(nf.format(roomSub) + " VNĐ", fontNormal));

            document.add(table);

            // TABLE SECTION: DỊCH VỤ PHÁT SINH
            double serviceSub = 0;
            if (usages != null && usages.size() > 0) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Dịch vụ bổ sung (Minibar/Room Service):", fontBold));

                PdfPTable svcTable = new PdfPTable(4);
                svcTable.setWidthPercentage(100);
                svcTable.setSpacingBefore(5f);
                svcTable.setSpacingAfter(10f);

                svcTable.addCell(new PdfPCell(new Phrase("Món/Dịch vụ", fontBold)));
                svcTable.addCell(new PdfPCell(new Phrase("Số lượng", fontBold)));
                svcTable.addCell(new PdfPCell(new Phrase("Đơn giá", fontBold)));
                svcTable.addCell(new PdfPCell(new Phrase("Thành tiền", fontBold)));

                java.util.List<quanlykhachsan.backend.hotelservice.Service> allSvc = quanlykhachsan.frontend.api.ServiceAPI
                        .getAllServices();
                for (quanlykhachsan.backend.hotelservice.ServiceUsage u : usages) {
                    String sName = "Dịch vụ " + u.getServiceId();
                    for (quanlykhachsan.backend.hotelservice.Service s : allSvc)
                        if (s.getId() == u.getServiceId())
                            sName = s.getName();

                    svcTable.addCell(new Phrase(sName, fontNormal));
                    svcTable.addCell(new Phrase(String.valueOf(u.getQuantity()), fontNormal));
                    svcTable.addCell(new Phrase(nf.format(u.getUnitPrice()) + " VNĐ", fontNormal));
                    svcTable.addCell(new Phrase(nf.format(u.getTotalPrice()) + " VNĐ", fontNormal));
                    serviceSub += u.getTotalPrice();
                }
                document.add(svcTable);
            }

            // TOTAL SECTION
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);

            PdfPCell blankCell = new PdfPCell(new Phrase(""));
            blankCell.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(blankCell);

            PdfPTable innerTotalTable = new PdfPTable(2);
            innerTotalTable.addCell(new Phrase("Tiền phòng:", fontNormal));
            innerTotalTable.addCell(new Phrase(nf.format(roomSub) + " VNĐ", fontNormal));

            innerTotalTable.addCell(new Phrase("Phí dịch vụ:", fontNormal));
            innerTotalTable.addCell(new Phrase(nf.format(serviceSub) + " VNĐ", fontNormal));

            double tax = 0.0;
            innerTotalTable.addCell(new Phrase("Thuế GTGT (0%):", fontNormal));
            innerTotalTable.addCell(new Phrase(nf.format(tax) + " VNĐ", fontNormal));

            innerTotalTable.addCell(new Phrase("TỔNG THANH TOÁN:", fontBold));
            innerTotalTable.addCell(new Phrase(nf.format(totalAmount) + " VNĐ", fontTotal));

            PdfPCell totalCellContainer = new PdfPCell(innerTotalTable);
            totalCellContainer.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(totalCellContainer);

            document.add(totalTable);

            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Cảm ơn quý khách và hẹn gặp lại!",
                    new Font(bf, 14, Font.ITALIC, BaseColor.DARK_GRAY));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            writer.close();

            int confirm = JOptionPane.showConfirmDialog(null,
                    "Xuất hóa đơn thành công. Bạn có muốn mở file PDF ngay bây giờ không?",
                    "Thành công", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                if (Desktop.isDesktopSupported()) {
                    File myFile = new File(fileName);
                    Desktop.getDesktop().open(myFile);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Lỗi khi xuất PDF: " + e.getMessage(), "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
