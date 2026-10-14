package quanlykhachsan.backend.invoice;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.font.*;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.*;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import com.itextpdf.kernel.colors.ColorConstants;
import java.io.*;
import java.math.*;
import java.net.URI;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Printable invoice representation. Issuance/signing remains the approved tax provider's responsibility. */
public final class VatInvoicePdfGenerator {
    public record Hotel(String legal_name,String address,String tax_code,String verification_url) {
        public Hotel {
            for(String value:List.of(legal_name,address,tax_code,verification_url))if(value.isBlank())throw new IllegalStateException("Invoice configuration is required");
            var url=URI.create(verification_url);if(!"https".equals(url.getScheme()) || url.getHost()==null || url.getUserInfo()!=null)throw new IllegalArgumentException("Verification URL must use HTTPS");
        }
        public static Hotel configured(){return new Hotel(required("HOTEL_LEGAL_NAME"),required("HOTEL_LEGAL_ADDRESS"),required("HOTEL_TAX_CODE"),required("INVOICE_VERIFICATION_URL"));}
        private static String required(String key){String v=System.getenv(key);if(v==null || v.isBlank())throw new IllegalStateException(key+" is required");return v.trim();}
    }
    public record Item(String description,BigDecimal amount) { }
    public record InvoiceData(long id,String issued_at,String guest_name,String company_name,String customer_tax_code,
            List<Item> items,BigDecimal service_charge,BigDecimal vat,BigDecimal vat_rate,BigDecimal total,boolean synthetic_fixture) { }
    public byte[] generate(Hotel hotel,InvoiceData data)throws IOException {
        BigDecimal subtotal=data.items().stream().map(Item::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(subtotal.add(data.service_charge()).add(data.vat()).compareTo(data.total())!=0)throw new IllegalArgumentException("Invoice totals do not reconcile");
        if(data.total().signum()<0)throw new IllegalArgumentException("Invalid invoice total");
        byte[] fontBytes;try(InputStream in=getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")){if(in==null)throw new IOException("Embedded invoice font is unavailable");fontBytes=in.readAllBytes();}
        var font=PdfFontFactory.createFont(fontBytes,PdfEncodings.IDENTITY_H,PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(Document doc=new Document(new PdfDocument(new PdfWriter(out)))) {
            doc.setMargins(36,36,36,36);doc.setFont(font).setFontSize(10);
            if(data.synthetic_fixture())doc.add(new Paragraph("DỮ LIỆU KIỂM THỬ TỔNG HỢP — KHÔNG CÓ GIÁ TRỊ PHÁP LÝ").setFontColor(ColorConstants.RED).setFontSize(9));
            doc.add(new Paragraph(hotel.legal_name()).setFontSize(15).setBold());
            doc.add(new Paragraph(hotel.address()+"\nMã số thuế: "+hotel.tax_code()));
            doc.add(new Paragraph("Hóa đơn giá trị gia tăng (tiền tệ: VNĐ)").setFontSize(17).setBold().setTextAlignment(TextAlignment.CENTER).setMarginTop(20));
            doc.add(new Paragraph("Số hóa đơn: "+data.id()+"    Ngày lập: "+data.issued_at()));
            doc.add(new Paragraph("Khách hàng: "+data.guest_name()+"\nTên đơn vị: "+text(data.company_name())+"\nMã số thuế khách hàng: "+text(data.customer_tax_code())));
            Table table=new Table(UnitValue.createPercentArray(new float[]{8,65,27})).useAllAvailableWidth();
            for(String title:List.of("STT","Nội dung","Thành tiền (VNĐ)"))table.addHeaderCell(new Cell().add(new Paragraph(title).setBold()).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            int number=1;for(Item item:data.items()) {
                table.addCell(Integer.toString(number++));table.addCell(item.description());table.addCell(new Cell().add(new Paragraph(money(item.amount()))).setTextAlignment(TextAlignment.RIGHT));
            }
            doc.add(table);
            doc.add(new Paragraph("Cộng tiền hàng: "+money(subtotal)+" VNĐ\nPhí dịch vụ (5%, theo sổ đã ghi nhận): "+money(data.service_charge())+" VNĐ\nThuế GTGT ("+data.vat_rate().movePointRight(2).stripTrailingZeros().toPlainString()+"%): "+money(data.vat())+" VNĐ").setTextAlignment(TextAlignment.RIGHT));
            doc.add(new Paragraph("Tổng thanh toán: "+money(data.total())+" VNĐ").setBold().setFontSize(13).setTextAlignment(TextAlignment.RIGHT));
            doc.add(new Paragraph("Tiền viết bằng chữ: "+VietnameseMoneyWords.format(data.total())));
            String link=hotel.verification_url()+(hotel.verification_url().contains("?")?"&":"?")+"invoice_id="+data.id();
            com.itextpdf.text.pdf.BarcodeQRCode qr=new com.itextpdf.text.pdf.BarcodeQRCode(link,180,180,null);
            java.awt.Image awt=qr.createAwtImage(java.awt.Color.BLACK,java.awt.Color.WHITE);BufferedImage png=new BufferedImage(180,180,BufferedImage.TYPE_INT_RGB);var graphics=png.createGraphics();graphics.drawImage(awt,0,0,null);graphics.dispose();ByteArrayOutputStream image=new ByteArrayOutputStream();ImageIO.write(png,"PNG",image);
            doc.add(new Image(ImageDataFactory.create(image.toByteArray())).setWidth(90).setHeight(90).setMarginTop(12));
            doc.add(new Paragraph("Tra cứu / xác minh hóa đơn: "+link).setFontSize(8));
            doc.add(new Paragraph("Bản thể hiện dữ liệu hóa đơn. Trạng thái phát hành và chữ ký số được xác minh tại đường dẫn tra cứu.").setFontSize(8).setFontColor(ColorConstants.GRAY));
        }
        return out.toByteArray();
    }
    private static String text(String value){return value==null || value.isBlank()?"—":value;}
    private static String money(BigDecimal amount){return String.format(java.util.Locale.forLanguageTag("vi-VN"),"%,.2f",amount);}
}
