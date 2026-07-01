package quanlykhachsan.backend.utils;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import quanlykhachsan.backend.booking.Invoice;

public class PdfGenerator {
    
    public static byte[] generateInvoicePdf(Invoice invoice) throws Exception {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        
        document.open();
        
        // Font configuration
        BaseFont bf;
        File fontFile = new File("C:/Windows/Fonts/arial.ttf");
        if (fontFile.exists()) {
            bf = BaseFont.createFont(fontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } else {
            bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
        }
        
        Font titleFont = new Font(bf, 24, Font.BOLD, new BaseColor(217, 119, 6)); // Aurelia Gold
        Font headerFont = new Font(bf, 14, Font.BOLD, BaseColor.DARK_GRAY);
        Font normalFont = new Font(bf, 12, Font.NORMAL, BaseColor.BLACK);
        Font boldFont = new Font(bf, 12, Font.BOLD, BaseColor.BLACK);
        
        // Header
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("AURELIA HOTEL", titleFont));
        leftCell.addElement(new Paragraph("Địa chỉ: 123 Đường Ngọc Trai, TP. Biển", normalFont));
        leftCell.addElement(new Paragraph("Điện thoại: 0988 777 666", normalFont));
        leftCell.addElement(new Paragraph("Email: contact@aurelia.com", normalFont));
        headerTable.addCell(leftCell);
        
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph invoiceText = new Paragraph("HÓA ĐƠN THANH TOÁN", new Font(bf, 20, Font.BOLD, BaseColor.BLACK));
        invoiceText.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(invoiceText);
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Paragraph dateText = new Paragraph("Ngày xuất: " + (invoice.getIssueDate() != null ? sdf.format(invoice.getIssueDate()) : sdf.format(new Date())), normalFont);
        dateText.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dateText);
        
        Paragraph noText = new Paragraph("Mã hóa đơn: INV-" + String.format("%06d", invoice.getId()), boldFont);
        noText.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(noText);
        
        headerTable.addCell(rightCell);
        document.add(headerTable);
        
        document.add(new Paragraph(" "));
        document.add(new Chunk(new com.itextpdf.text.pdf.draw.LineSeparator(1f, 100f, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, -1f)));
        document.add(new Paragraph(" "));
        
        // Customer Info
        document.add(new Paragraph("THÔNG TIN KHÁCH HÀNG", headerFont));
        document.add(new Paragraph("Họ và tên: " + (invoice.getCustomerName() != null ? invoice.getCustomerName() : "Khách lẻ"), normalFont));
        document.add(new Paragraph("Số phòng: " + (invoice.getRoomNumber() != null ? invoice.getRoomNumber() : "---"), normalFont));
        document.add(new Paragraph("Trạng thái thanh toán: " + (invoice.getStatus() != null && invoice.getStatus().equals("paid") ? "Đã thanh toán" : "Chưa thanh toán"), boldFont));
        
        document.add(new Paragraph(" "));
        
        // Items Table
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 70f, 30f });
        table.setSpacingBefore(10f);
        
        PdfPCell c1 = new PdfPCell(new Phrase("Mô tả chi phí", boldFont));
        c1.setBackgroundColor(new BaseColor(240, 240, 240));
        c1.setPadding(8f);
        table.addCell(c1);
        
        PdfPCell c2 = new PdfPCell(new Phrase("Thành tiền", boldFont));
        c2.setBackgroundColor(new BaseColor(240, 240, 240));
        c2.setPadding(8f);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c2);
        
        DecimalFormat df = new DecimalFormat("#,###.## VNĐ");
        
        addTableRow(table, "Tổng tiền phòng", df.format(invoice.getTotalRoomFee()), normalFont);
        if (invoice.getTotalServiceFee() > 0) {
            addTableRow(table, "Tổng tiền dịch vụ", df.format(invoice.getTotalServiceFee()), normalFont);
        }
        if (invoice.getDiscount() > 0) {
            addTableRow(table, "Giảm giá", "-" + df.format(invoice.getDiscount()), normalFont);
        }
        addTableRow(table, "Thuế GTGT (10%)", df.format(invoice.getTaxAmount()), normalFont);
        
        PdfPCell totalDescCell = new PdfPCell(new Phrase("TỔNG THANH TOÁN", boldFont));
        totalDescCell.setPadding(10f);
        table.addCell(totalDescCell);
        
        PdfPCell totalValCell = new PdfPCell(new Phrase(df.format(invoice.getFinalTotal()), new Font(bf, 14, Font.BOLD, new BaseColor(217, 119, 6))));
        totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalValCell.setPadding(10f);
        table.addCell(totalValCell);
        
        document.add(table);
        
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(100);
        
        PdfPCell f1 = new PdfPCell(new Phrase("Khách hàng\n(Ký & Ghi rõ họ tên)", boldFont));
        f1.setBorder(Rectangle.NO_BORDER);
        f1.setHorizontalAlignment(Element.ALIGN_CENTER);
        footerTable.addCell(f1);
        
        PdfPCell f2 = new PdfPCell(new Phrase("Lễ tân\n(Ký & Ghi rõ họ tên)", boldFont));
        f2.setBorder(Rectangle.NO_BORDER);
        f2.setHorizontalAlignment(Element.ALIGN_CENTER);
        footerTable.addCell(f2);
        
        document.add(footerTable);
        
        Paragraph thanksText = new Paragraph("Cảm ơn quý khách đã sử dụng dịch vụ của Aurelia Hotel!", new Font(bf, 10, Font.ITALIC, BaseColor.DARK_GRAY));
        thanksText.setAlignment(Element.ALIGN_CENTER);
        thanksText.setSpacingBefore(40f);
        document.add(thanksText);
        
        document.close();
        return out.toByteArray();
    }
    
    private static void addTableRow(PdfPTable table, String desc, String value, Font font) {
        PdfPCell cell1 = new PdfPCell(new Phrase(desc, font));
        cell1.setPadding(8f);
        table.addCell(cell1);
        
        PdfPCell cell2 = new PdfPCell(new Phrase(value, font));
        cell2.setPadding(8f);
        cell2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell2);
    }
}
