package quanlykhachsan.stubs;
import com.sun.net.httpserver.*;
import com.google.gson.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import quanlykhachsan.backend.utils.*;
import quanlykhachsan.backend.booking.*;
import quanlykhachsan.backend.report.DbrSnapshot;
public final class PricingAndReportMockStubs {
    public static boolean handle(HttpExchange e)throws IOException {
        String path=e.getRequestURI().getPath();
        if(path.equals("/api/v1/reports/financial/dbr") && e.getRequestMethod().equals("GET")) {
            var zero=new BigDecimal("0.00");var room=new BigDecimal("9000000.00");
            var d=new DbrSnapshot("2026-10-14",room,new BigDecimal("10206000.00"),30,200,new BigDecimal("15.00"),new BigDecimal("300000.00"),new BigDecimal("45000.00"),Map.of("room_rate",room,"service_charge",new BigDecimal("450000.00"),"vat",new BigDecimal("756000.00")),new BigDecimal("10206000.00"),new BigDecimal("10206000.00"),zero,zero,zero);
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(d));return true;
        }
        if(path.equals("/api/v1/promotions/validate") && e.getRequestMethod().equals("POST")) {
            var b=PortalValidation.body(e);var range=PortalValidation.range(PortalValidation.text(b,"check_in_date",10,true),PortalValidation.text(b,"check_out_date",10,true));PortalValidation.integer(b,"room_type_id",1);
            if(!"SUMMER50".equals(PortalValidation.text(b,"voucher_code",50,true))){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Synthetic voucher is invalid"));return true;}
            var room=new BigDecimal("1000000").multiply(BigDecimal.valueOf(range.checkIn().until(range.checkOut(),java.time.temporal.ChronoUnit.DAYS)));var discount=room.multiply(new BigDecimal("0.50")).min(new BigDecimal("500000"));
            var charges=FinancialCalculator.calculate(room.subtract(discount),BigDecimal.ZERO,TaxConfig.standardEightPercentVat());
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(new quanlykhachsan.backend.promotion.VoucherService.Discount(999,"SUMMER50",discount,charges.getRoomFee(),charges.getServiceFee(),charges.getVat(),charges.getTotalAmount())));return true;
        }
        return false;
    }
}
