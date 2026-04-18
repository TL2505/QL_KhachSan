package quanlykhachsan.frontend.utils;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.awt.Desktop;

public class ReportPDFExporter {

    public static void exportPDF(JTable table, String filterInfo) {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            // Check if folder exists
            File dir = new File("reports");
            if (!dir.exists()) {
                dir.mkdir();
            }

            String fileName = "reports/BaoCaoDoanhThu_" + System.currentTimeMillis() + ".pdf";
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            // Font setting - try to use an Arial font for Vietnamese support, fallback to
            // basic if not available
            BaseFont bf;
            try {
                bf = BaseFont.createFont("c:/windows/fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ex) {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            }

            Font fontHeader = new Font(bf, 20, Font.BOLD, BaseColor.BLUE);
            Font fontNormal = new Font(bf, 12, Font.NORMAL, BaseColor.BLACK);
            Font fontBold = new Font(bf, 12, Font.BOLD, BaseColor.BLACK);

            // LOGO & HOTEL INFO
            Paragraph hotelName = new Paragraph("HỆ THỐNG QUẢN LÝ KHÁCH SẠN", fontHeader);
            hotelName.setAlignment(Element.ALIGN_CENTER);
            document.add(hotelName);

            document.add(new Paragraph(" "));

            Paragraph title = new Paragraph("BÁO CÁO DOANH THU", new Font(bf, 18, Font.BOLD, BaseColor.BLACK));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            // INFO SECTION
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            document.add(new Paragraph("Thời gian xuất báo cáo: " + sdf.format(new Date()), fontNormal));
            document.add(new Paragraph("Điều kiện lọc: " + filterInfo, fontNormal));

            document.add(new Paragraph(" "));

            // TABLE SECTION
            int colCount = table.getColumnCount();
            PdfPTable pdfTable = new PdfPTable(colCount);
            pdfTable.setWidthPercentage(100);
            pdfTable.setSpacingBefore(10f);
            pdfTable.setSpacingAfter(10f);

            // Table Header
            for (int i = 0; i < colCount; i++) {
                PdfPCell cell = new PdfPCell(new Phrase(table.getColumnName(i), fontBold));
                cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                pdfTable.addCell(cell);
            }

            // Table Content
            for (int i = 0; i < table.getRowCount(); i++) {
                for (int j = 0; j < colCount; j++) {
                    Object val = table.getValueAt(i, j);
                    PdfPCell cell = new PdfPCell(new Phrase(val != null ? val.toString() : "", fontNormal));
                    if (j > 0) { // All amount/count columns are aligned right
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    } else {
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    }
                    pdfTable.addCell(cell);
                }
            }

            document.add(pdfTable);

            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Người lập báo cáo: Quản trị viên",
                    new Font(bf, 14, Font.ITALIC, BaseColor.DARK_GRAY));
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

            document.close();
            writer.close();

            int confirm = JOptionPane.showConfirmDialog(null,
                    "Xuất báo cáo thành công. Bạn có muốn mở file PDF ngay bây giờ không?",
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
