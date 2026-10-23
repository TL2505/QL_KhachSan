package quanlykhachsan.invoice;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import quanlykhachsan.backend.invoice.VatInvoicePdfGenerator;
public class VatInvoicePdfGeneratorTest {
    @Test public void embeddedVietnameseFontAndAuthoritativeTotals()throws Exception {
        var hotel=new VatInvoicePdfGenerator.Hotel("Khách sạn KIỂM THỬ TỔNG HỢP","Địa chỉ giả lập chỉ dùng trong kiểm thử","SYNTHETIC-MST");
        var data=new VatInvoicePdfGenerator.InvoiceData(999,"2026-10-14","Nguyễn Văn Đạt","Công ty kiểm thử độc lập","SYNTHETIC-GUEST",List.of(new VatInvoicePdfGenerator.Item("Phòng Hạng Sang Hướng Biển",new BigDecimal("3000000.00")),new VatInvoicePdfGenerator.Item("Tiền dịch vụ minibar và đồ uống",BigDecimal.ZERO),new VatInvoicePdfGenerator.Item("Giặt là / Laundry",BigDecimal.ZERO)),new BigDecimal("150000.00"),new BigDecimal("252000.00"),new BigDecimal("0.08"),new BigDecimal("3402000.00"),true);
        byte[] pdf=new VatInvoicePdfGenerator().generate(hotel,data);
        Path output=Path.of("target","qa-artifacts","synthetic-internal-payment-statement.pdf");Files.createDirectories(output.getParent());Files.write(output,pdf);
        try(PDDocument doc=PDDocument.load(pdf)) {
            assertEquals(1,doc.getNumberOfPages());String text=new PDFTextStripper().getText(doc);
            for(String required:List.of("BẢNG KÊ THANH TOÁN NỘI BỘ (VNĐ)","Mã chứng từ nội bộ: 999","Nguyễn Văn Đạt","Phòng Hạng Sang Hướng Biển","Tiền dịch vụ minibar và đồ uống","Ba triệu bốn trăm lẻ hai nghìn đồng chẵn","3.402.000,00","KHÔNG CÓ GIÁ TRỊ PHÁP LÝ","KHÔNG PHẢI HÓA ĐƠN ĐIỆN TỬ HỢP LỆ"))assertTrue(text.contains(required),required+" missing in "+text);
            assertFalse(text.contains("Hóa đơn giá trị gia tăng"));
            assertFalse(text.contains("invoice.example.invalid"));
            assertFalse(text.contains("�"));
            var fonts=doc.getPage(0).getResources().getFontNames();for(var name:fonts)assertTrue(doc.getPage(0).getResources().getFont(name).isEmbedded());
        }
        assertThrows(IllegalArgumentException.class,()->new VatInvoicePdfGenerator().generate(hotel,new VatInvoicePdfGenerator.InvoiceData(1,"today","fixture",null,null,List.of(),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("0.08"),BigDecimal.TEN,true)));
    }
}
