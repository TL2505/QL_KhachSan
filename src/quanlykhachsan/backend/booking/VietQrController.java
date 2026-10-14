package quanlykhachsan.backend.booking;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.sql.*;
import quanlykhachsan.backend.utils.*;

public final class VietQrController implements HttpHandler {
    private final String bin,account,holder;
    public VietQrController(){this(System.getenv("BANK_BIN"),System.getenv("BANK_ACCOUNT_NUMBER"),System.getenv("BANK_ACCOUNT_HOLDER"));}
    public VietQrController(String bin,String account,String holder){this.bin=bin;this.account=account;this.holder=holder;}
    @Override public void handle(HttpExchange e)throws IOException {
        if(!e.getRequestMethod().equals("GET")){ApiResponseUtil.write(e,405,ApiResponseUtil.error("Method not allowed"));return;}
        try(Connection c=DatabaseConnection.getConnection()) {
            String[] path=e.getRequestURI().getPath().split("/");
            if(path.length!=6 || !path[5].equals("vietqr"))throw new IllegalArgumentException();
            var f=FolioAccess.find(c,e,path[4]);if(f==null){ApiResponseUtil.write(e,404,ApiResponseUtil.error("Payment reference not found"));return;}
            if(!f.status().equals("open") || f.balance().signum()<=0){ApiResponseUtil.write(e,409,ApiResponseUtil.error("Folio has no open payable balance"));return;}
            if(bin==null || account==null || holder==null || holder.isBlank())throw new IllegalStateException();
            var qr=new VietQrGenerator().generate(bin,account,f.balance(),f.code());
            JsonObject data=new JsonObject();data.addProperty("folio_id",f.id());data.addProperty("folio_code",f.code());data.addProperty("reference_code",f.code());data.addProperty("amount",f.balance());data.addProperty("currency","VND");data.addProperty("beneficiary_bank",bin);data.addProperty("account_number",account);data.addProperty("account_holder",holder);data.addProperty("qr_code_string",qr.qr_code_string());data.addProperty("emvco_payload",qr.qr_code_string());data.addProperty("qr_image_base64",qr.qr_image_base64());data.addProperty("expires_in_seconds",300);
            ApiResponseUtil.write(e,200,ApiResponseUtil.successWithData(data));
        }catch(SecurityException ex){ApiResponseUtil.write(e,403,ApiResponseUtil.error("Role or guest identity is not authorized"));}
        catch(IllegalArgumentException ex){ApiResponseUtil.write(e,400,ApiResponseUtil.error("Invalid payment reference or amount"));}
        catch(SQLException | IllegalStateException ex){ApiResponseUtil.write(e,503,ApiResponseUtil.error("Payment QR is temporarily unavailable; please contact the Front Desk"));}
    }
}
