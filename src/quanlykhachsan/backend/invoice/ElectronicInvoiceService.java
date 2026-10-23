package quanlykhachsan.backend.invoice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import quanlykhachsan.backend.booking.TaxConfig;
import quanlykhachsan.backend.utils.DatabaseConnection;

/** Provider-issued e-invoice workflow. External issuance is always an explicit staff action. */
public final class ElectronicInvoiceService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MisaMeInvoiceProvider provider;

    public ElectronicInvoiceService() { this(new MisaMeInvoiceProvider()); }
    ElectronicInvoiceService(MisaMeInvoiceProvider provider) { this.provider = provider; }

    public JsonObject issue(long internalInvoiceId, int actor) throws Exception {
        return createAndPublish(internalInvoiceId, null, "original", null, actor);
    }

    public JsonObject adjust(long internalInvoiceId, long parentId, String kind, String reason, int actor) throws Exception {
        if (!"adjustment".equals(kind) && !"replacement".equals(kind)) throw new IllegalArgumentException("Document type must be adjustment or replacement");
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("A correction reason is required");
        return createAndPublish(internalInvoiceId, parentId, kind, reason.trim(), actor);
    }

    private JsonObject createAndPublish(long invoiceId, Long parentId, String kind, String reason, int actor) throws Exception {
        MisaMeInvoiceProvider.Settings settings = provider.settings();
        Snapshot snapshot;
        Snapshot publishSnapshot;
        JsonObject payload;
        JsonObject reference;
        long electronicId;
        String refId = UUID.randomUUID().toString();
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                snapshot = readSnapshot(c, invoiceId, true);
                publishSnapshot = snapshot;
                if (!"paid".equals(snapshot.invoiceStatus) || !"checked_out".equals(snapshot.bookingStatus) || snapshot.balance.signum() != 0)
                    throw new Conflict("Chứng từ nội bộ phải đã thanh toán, trả phòng và đối soát số dư.");
                if (snapshot.lines.isEmpty() || snapshot.total.signum() <= 0 || snapshot.vat.signum() < 0)
                    throw new Conflict("Dữ liệu sổ cái không đủ để lập hóa đơn điện tử.");
                if (snapshot.ledgerTotal.compareTo(snapshot.total) != 0 || snapshot.ledgerVat.compareTo(snapshot.vat) != 0)
                    throw new Conflict("Sổ cái và chứng từ nội bộ chưa khớp; cần đối soát trước khi phát hành.");
                if (!snapshot.sellerTaxCode.equalsIgnoreCase(settings.taxCode()))
                    throw new Conflict("Mã số thuế cấu hình MISA không khớp hồ sơ đơn vị phát hành.");
                if(snapshot.buyerName==null||snapshot.buyerName.isBlank())throw new Conflict("Thiếu tên người mua đã xác nhận; hóa đơn chưa được gửi.");
                if(snapshot.companyBuyer&&(snapshot.buyerTaxCode==null||snapshot.buyerTaxCode.isBlank()||snapshot.buyerAddress==null||snapshot.buyerAddress.isBlank()))
                    throw new Conflict("Hóa đơn doanh nghiệp cần mã số thuế và địa chỉ đã xác nhận.");
                BigDecimal base = snapshot.total.subtract(snapshot.vat).setScale(2, RoundingMode.HALF_UP);
                BigDecimal expectedVat = base.multiply(TaxConfig.fromRuntimeConfiguration().getVatRate()).setScale(0, RoundingMode.HALF_UP);
                if (base.signum() <= 0 || expectedVat.compareTo(snapshot.vat) != 0)
                    throw new Conflict("Thuế/số tiền trên sổ cái không khớp cấu hình thuế; hóa đơn chưa được gửi.");
                if (parentId == null || "replacement".equals(kind)) for (Line line : snapshot.lines) if (line.amount.signum() <= 0)
                    throw new Conflict("Có bút toán âm/điều chỉnh cần kế toán ánh xạ nghiệp vụ trước khi phát hành.");

                if (parentId == null) {
                    try (PreparedStatement q = c.prepareStatement("SELECT id FROM electronic_invoices WHERE internal_invoice_id=? AND document_type='original' AND status<>'rejected' LIMIT 1 FOR UPDATE")) {
                        q.setLong(1, invoiceId); try (ResultSet r = q.executeQuery()) { if (r.next()) throw new Conflict("Đã có yêu cầu hóa đơn gốc. Hãy đồng bộ trạng thái trước khi thử lại."); }
                    }
                } else {
                    Parent parent = readParent(c, parentId, invoiceId);
                    if (!"issued".equals(parent.status)) throw new Conflict("Chỉ được điều chỉnh/thay thế hóa đơn đã phát hành và đã đồng bộ.");
                    if(parent.sendTaxStatus==null)throw new Conflict("Cần đồng bộ trạng thái gửi cơ quan thuế của hóa đơn gốc trước.");
                    if (parent.total.compareTo(snapshot.total) == 0 && parent.vat.compareTo(snapshot.vat) == 0)
                        throw new Conflict("Sổ cái hiện không khác hóa đơn gốc; không có nội dung để điều chỉnh.");
                    if ("adjustment".equals(kind)) {
                        BigDecimal deltaTotal=snapshot.total.subtract(parent.total).setScale(2,RoundingMode.HALF_UP);
                        BigDecimal deltaVat=snapshot.vat.subtract(parent.vat).setScale(2,RoundingMode.HALF_UP);
                        BigDecimal deltaBase=deltaTotal.subtract(deltaVat).setScale(2,RoundingMode.HALF_UP);
                        if(deltaTotal.signum()<0)throw new Conflict("Khoản điều chỉnh giảm cần quy trình MISA/kế toán đã xác nhận; dùng chứng từ thay thế nếu phù hợp.");
                        BigDecimal expectedDeltaVat=deltaBase.multiply(TaxConfig.fromRuntimeConfiguration().getVatRate()).setScale(0,RoundingMode.HALF_UP);
                        if(deltaTotal.signum()==0 || deltaBase.signum()==0 || expectedDeltaVat.compareTo(deltaVat)!=0)
                            throw new Conflict("Khoản chênh lệch không thể ánh xạ thành điều chỉnh thuế theo cấu hình hiện tại; dùng quy trình kế toán/MISA đã xác nhận.");
                        publishSnapshot=new Snapshot(snapshot.invoiceId,snapshot.bookingId,snapshot.invoiceStatus,snapshot.bookingStatus,deltaTotal,deltaVat,deltaTotal,deltaVat,BigDecimal.ZERO,
                                snapshot.buyerName,snapshot.buyerTaxCode,snapshot.buyerAddress,snapshot.email,snapshot.sellerTaxCode,snapshot.companyBuyer,
                                List.of(new Line("ADJUSTMENT","Điều chỉnh Booking #"+snapshot.bookingId+": "+reason,deltaBase)));
                    }
                }

                payload=toProviderPayload(publishSnapshot,settings,refId);
                reference=parentId==null?null:referenceData(c,parentId,kind,reason);

                try (PreparedStatement q = c.prepareStatement("INSERT INTO electronic_invoices(internal_invoice_id,booking_id,parent_einvoice_id,document_type,provider,provider_environment,provider_mode,status,provider_ref_id,invoice_date,amount_without_vat,vat_amount,total_amount,created_by,last_attempt_at) VALUES(?,?,?,?, 'MISA_MEINVOICE',?,?,'submitting',?,?,?,?,?,?,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS)) {
                    q.setLong(1, invoiceId); q.setInt(2, snapshot.bookingId); if (parentId == null) q.setNull(3, java.sql.Types.BIGINT); else q.setLong(3, parentId);
                    q.setString(4, kind); q.setString(5, settings.environment()); q.setString(6, settings.mode().name()); q.setString(7, refId); q.setTimestamp(8, java.sql.Timestamp.valueOf(LocalDateTime.now(VN)));
                    q.setBigDecimal(9, publishSnapshot.total.subtract(publishSnapshot.vat)); q.setBigDecimal(10, publishSnapshot.vat); q.setBigDecimal(11, publishSnapshot.total); q.setInt(12, actor);
                    q.executeUpdate(); try (ResultSet keys = q.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Missing e-invoice id"); electronicId = keys.getLong(1); }
                }
                event(c, electronicId, "publish", "started", null, null, UUID.randomUUID().toString(), "Staff requested provider issuance", actor);
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }

        try {
            MisaMeInvoiceProvider.Published published = provider.publish(payload, snapshot.buyerName, snapshot.email, reference);
            String viewUrl = null;
            try { viewUrl = provider.viewUrl(published.transactionId()); } catch (MisaMeInvoiceProvider.ProviderException ignored) { }
            updateIssued(electronicId, published, viewUrl, actor, "Provider returned invoice number and transaction id");
            try { updateStatus(electronicId,"issued",provider.status(published.transactionId()),actor,"Tax-authority status queried after provider issuance"); }
            catch(MisaMeInvoiceProvider.ProviderException ex){
                event(electronicId,"status_sync",ex.httpStatus()==0||ex.httpStatus()>=500?"unknown":"failed",ex.httpStatus(),ex.code(),UUID.randomUUID().toString(),"Invoice issued; tax-authority status query must be retried",actor);
                markStatusWarning(electronicId,ex.code());
            }
            return detail(electronicId);
        } catch (MisaMeInvoiceProvider.ProviderException ex) {
            boolean uncertain = ex.code().equals("NETWORK_UNKNOWN") || ex.code().equals("INTERRUPTED") || ex.code().equals("PUBLISH_INCOMPLETE") || ex.httpStatus() >= 500 || ex.httpStatus() == 0;
            updateFailure(electronicId, ex, uncertain ? "sync_required" : "rejected", actor);
            throw new ProviderFailure(uncertain ? "MISA chưa xác nhận kết quả. Hãy đồng bộ hóa đơn trước khi thử lại." : "MISA từ chối phát hành. Kiểm tra cấu hình hoặc dữ liệu rồi xử lý lại.", uncertain, ex);
        }
    }

    private JsonObject referenceData(Connection c,long parentId, String kind, String reason) throws Exception {
        JsonObject reference = new JsonObject();
        reference.addProperty("ReferenceType", "replacement".equals(kind) ? 1 : 2);
        reference.addProperty("OrgInvoiceType", provider.settings().mode()==MisaMeInvoiceProvider.Mode.HSM_NO_CODE?1:2);
        reference.addProperty("InvoiceNote", reason);
        try (PreparedStatement q = c.prepareStatement("SELECT invoice_template_no,invoice_series,invoice_number,invoice_date FROM electronic_invoices WHERE id=? AND status='issued'")) {
            q.setLong(1, parentId); try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new Conflict("Không tìm thấy hóa đơn gốc đã phát hành.");
                reference.addProperty("OrgInvTemplateNo", r.getString(1)); reference.addProperty("OrgInvSeries", r.getString(2)); reference.addProperty("OrgInvNo", r.getString(3));
                java.sql.Timestamp date = r.getTimestamp(4); if (date != null) reference.addProperty("OrgInvDate", date.toLocalDateTime().toString());
            }
        }
        return reference;
    }

    private static JsonObject toProviderPayload(Snapshot s, MisaMeInvoiceProvider.Settings settings, String refId) {
        JsonObject data = new JsonObject();
        data.addProperty("RefID", refId); data.addProperty("InvoiceTemplateID", settings.templateId()); data.addProperty("InvSeries", settings.invoiceSeries());
        data.addProperty("InvoiceName", settings.invoiceName()); data.addProperty("InvDate", java.time.OffsetDateTime.now(VN).withNano(0).toString());
        data.addProperty("IsInvoiceSummary",false);
        data.addProperty("CurrencyCode", "VND"); data.addProperty("ExchangeRate", BigDecimal.ONE); data.addProperty("PaymentMethodName", "Thanh toán booking đã đối soát");
        data.addProperty("SellerLegalName", settings.sellerName()); data.addProperty("SellerTaxCode", settings.taxCode()); data.addProperty("SellerAddress", settings.sellerAddress());
        data.addProperty("SellerPhoneNumber", settings.sellerPhone()); data.addProperty("SellerEmail", settings.sellerEmail());
        data.addProperty("BuyerLegalName", s.buyerName); data.addProperty("BuyerTaxCode", s.buyerTaxCode); data.addProperty("BuyerAddress", s.buyerAddress); data.addProperty("BuyerEmail", s.email);
        BigDecimal total = s.total.setScale(2, RoundingMode.HALF_UP), vat = s.vat.setScale(2, RoundingMode.HALF_UP), base = total.subtract(vat);
        BigDecimal rate = TaxConfig.fromRuntimeConfiguration().getVatRate(); String rateName = rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
        data.addProperty("TotalSaleAmountOC", base); data.addProperty("TotalSaleAmount", base);
        data.addProperty("TotalAmountWithoutVATOC", base); data.addProperty("TotalAmountWithoutVAT", base);
        data.addProperty("TotalVATAmountOC", vat); data.addProperty("TotalVATAmount", vat); data.addProperty("TotalAmountOC", total); data.addProperty("TotalAmount", total);
        data.addProperty("TotalDiscountAmountOC", BigDecimal.ZERO); data.addProperty("TotalDiscountAmount", BigDecimal.ZERO);
        data.addProperty("TotalAmountInWords", quanlykhachsan.backend.invoice.VietnameseMoneyWords.format(total));

        JsonArray details = new JsonArray(); BigDecimal assignedVat = BigDecimal.ZERO;
        for (int i = 0; i < s.lines.size(); i++) {
            Line line = s.lines.get(i); BigDecimal lineVat;
            if (i == s.lines.size() - 1) lineVat = vat.subtract(assignedVat);
            else { lineVat = line.amount.multiply(rate).setScale(0, RoundingMode.HALF_UP); assignedVat = assignedVat.add(lineVat); }
            JsonObject d = new JsonObject(); d.addProperty("ItemType", 1); d.addProperty("LineNumber", i + 1); d.addProperty("SortOrder", i + 1); d.addProperty("ItemCode", line.type);
            d.addProperty("ItemName", line.description == null || line.description.isBlank() ? line.type : line.description);
            d.addProperty("UnitName", "Lần"); d.addProperty("Quantity", BigDecimal.ONE); d.addProperty("UnitPrice", line.amount); d.addProperty("DiscountRate", BigDecimal.ZERO); d.addProperty("DiscountAmountOC", BigDecimal.ZERO); d.addProperty("DiscountAmount", BigDecimal.ZERO);
            d.addProperty("AmountOC", line.amount); d.addProperty("Amount", line.amount); d.addProperty("AmountWithoutVATOC", line.amount); d.addProperty("AmountWithoutVAT", line.amount);
            d.addProperty("VATRateName", rateName); d.addProperty("VATAmountOC", lineVat); d.addProperty("VATAmount", lineVat); details.add(d);
        }
        data.add("OriginalInvoiceDetail", details);
        JsonArray taxInfo = new JsonArray(); JsonObject tax = new JsonObject(); tax.addProperty("VATRateName", rateName); tax.addProperty("AmountWithoutVATOC", base); tax.addProperty("VATAmountOC", vat); tax.addProperty("VATAmount", vat); taxInfo.add(tax); data.add("TaxRateInfo", taxInfo);
        JsonObject options = new JsonObject(); options.addProperty("MainCurrency","VND"); options.addProperty("AmountDecimalDigits","0"); options.addProperty("AmountOCDecimalDigits","2");
        options.addProperty("UnitPriceOCDecimalDigits","0"); options.addProperty("UnitPriceDecimalDigits","0"); options.addProperty("QuantityDecimalDigits","2");
        options.addProperty("CoefficientDecimalDigits","2"); options.addProperty("ExchangRateDecimalDigits","2"); options.addProperty("ClockDecimalDigits","0"); data.add("OptionUserDefined", options);
        return data;
    }

    private static Snapshot readSnapshot(Connection c, long invoiceId, boolean lock) throws SQLException {
        String sql = "SELECT i.booking_id,i.status,i.final_total,i.tax_amount,b.status,c.full_name,c.company_name,c.tax_code,c.address,c.email "
            + "FROM invoices i JOIN bookings b ON b.id=i.booking_id LEFT JOIN customers c ON c.id=b.customer_id WHERE i.id=?" + (lock ? " FOR UPDATE" : "");
        int booking; String invoiceStatus, bookingStatus, buyer, company, buyerTax, address, email; BigDecimal total, vat;
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setLong(1, invoiceId); try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new Conflict("Không tìm thấy chứng từ nội bộ.");
                booking = r.getInt(1); invoiceStatus = r.getString(2); total = r.getBigDecimal(3); vat = r.getBigDecimal(4); bookingStatus = r.getString(5);
                buyer = r.getString(6); company = r.getString(7); buyerTax = r.getString(8); address = r.getString(9); email = r.getString(10);
            }
        }
        boolean companyBuyer=company != null && !company.isBlank();
        if (companyBuyer) buyer = company;
        BigDecimal balance; int folios;
        try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(SUM(balance),0),COUNT(*) FROM folios WHERE booking_id=?")) { q.setInt(1, booking); try (ResultSet r = q.executeQuery()) { r.next(); balance = r.getBigDecimal(1); folios = r.getInt(2); } }
        if (folios == 0) throw new Conflict("Booking chưa có sổ cái.");
        List<Line> lines = new ArrayList<>(); BigDecimal ledgerTotal = BigDecimal.ZERO, ledgerVat = BigDecimal.ZERO;
        try (PreparedStatement q = c.prepareStatement("SELECT t.charge_type,MAX(t.description),SUM(CASE WHEN t.transaction_type='debit' THEN t.amount ELSE -t.amount END) FROM folio_transactions t JOIN folios f ON f.id=t.folio_id WHERE f.booking_id=? AND t.charge_type NOT IN ('payment','credit_adjustment') GROUP BY t.charge_type ORDER BY t.charge_type")) {
            q.setInt(1, booking); try (ResultSet r = q.executeQuery()) { while (r.next()) {
                String type = r.getString(1); BigDecimal amount = r.getBigDecimal(3); if (amount == null || amount.signum() == 0) continue;
                if ("vat".equals(type)) ledgerVat = ledgerVat.add(amount); else { lines.add(new Line(type, r.getString(2), amount)); ledgerTotal = ledgerTotal.add(amount); }
            } }
        }
        ledgerTotal = ledgerTotal.add(ledgerVat);
        String sellerTaxCode = System.getenv("HOTEL_TAX_CODE");
        if (sellerTaxCode == null || sellerTaxCode.isBlank()) throw new Conflict("Thiếu cấu hình mã số thuế đơn vị phát hành.");
        return new Snapshot(invoiceId, booking, invoiceStatus, bookingStatus, total, vat, ledgerTotal, ledgerVat, balance,
                buyer, buyerTax, address, email, sellerTaxCode.trim(), companyBuyer, lines);
    }

    private static Parent readParent(Connection c, long parentId, long invoiceId) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT status,total_amount,vat_amount,send_tax_status FROM electronic_invoices WHERE id=? AND internal_invoice_id=? AND document_type='original' FOR UPDATE")) {
            q.setLong(1, parentId); q.setLong(2, invoiceId); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new Conflict("Hóa đơn gốc không thuộc chứng từ/booking này."); return new Parent(r.getString(1), r.getBigDecimal(2), r.getBigDecimal(3),r.getString(4)); }
        }
    }

    public JsonObject sync(long id, int actor) throws Exception {
        ElectronicRow row = row(id);
        event(id,"status_sync","started",null,null,UUID.randomUUID().toString(),"Staff requested provider status reconciliation",actor);
        try {
            if (row.transactionId == null || row.transactionId.isBlank()) {
                MisaMeInvoiceProvider.Published found = provider.findByReference(row.refId, row.invoiceDate);
                if (found == null) { updateFailure(id, "NOT_FOUND_YET", "Provider has not returned this reference; do not resend", "sync_required", actor); return detail(id); }
                String url = null; try { url = provider.viewUrl(found.transactionId()); } catch (MisaMeInvoiceProvider.ProviderException ignored) { }
                updateIssued(id, found, url, actor, "Invoice reconciled by provider RefID");
                row = row(id);
            }
            MisaMeInvoiceProvider.Status status = provider.status(row.transactionId);
            if (status.deleted()) updateStatus(id, "cancelled", status, actor, "Provider confirms invoice cancellation");
            else updateStatus(id, "cancel_pending".equals(row.status)?"cancel_pending":"issued", status, actor, "Provider status synchronized");
            return detail(id);
        } catch(MisaMeInvoiceProvider.ProviderException ex) {
            event(id,"status_sync",ex.httpStatus()==0||ex.httpStatus()>=500?"unknown":"failed",ex.httpStatus(),ex.code(),UUID.randomUUID().toString(),"Provider status could not be reconciled",actor);
            throw new ProviderFailure("Chưa thể xác nhận trạng thái với MISA. Hãy đồng bộ lại; không phát hành lại.",true,ex);
        }
    }

    public JsonObject cancel(long id, String reason, int actor) throws Exception {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("A cancellation reason is required");
        ElectronicRow row = row(id);
        if (!"issued".equals(row.status)) throw new Conflict("Chỉ được yêu cầu hủy hóa đơn đã phát hành; trạng thái cần đồng bộ phải tra cứu trước.");
        if(row.sendTaxStatus==null)throw new Conflict("Cần đồng bộ trạng thái cơ quan thuế trước khi yêu cầu hủy.");
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false); try {
                setState(c, id, "cancel_pending", null, null, null); event(c, id, "cancel", "started", null, null, UUID.randomUUID().toString(), "Staff requested cancellation", actor); c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; } finally { c.setAutoCommit(true); }
        }
        try {
            provider.cancel(row.transactionId, row.series, reason.trim());
            event(id, "cancel", "success", null, null, UUID.randomUUID().toString(), "Cancellation request accepted; provider status must confirm", actor);
            return detail(id);
        } catch (MisaMeInvoiceProvider.ProviderException ex) {
            boolean unknown = ex.httpStatus() == 0 || ex.httpStatus() >= 500;
            if (!unknown) try(Connection c=DatabaseConnection.getConnection()){c.setAutoCommit(false);try{setState(c,id,"issued",ex.code(),"Provider did not confirm cancellation",null);event(c,id,"cancel","failed",ex.httpStatus(),ex.code(),UUID.randomUUID().toString(),"Provider rejected the cancellation request",actor);c.commit();}catch(Exception failure){c.rollback();throw failure;}finally{c.setAutoCommit(true);}}
            else event(id, "cancel", "unknown", ex.httpStatus(), ex.code(), UUID.randomUUID().toString(), "Cancellation response requires provider reconciliation", actor);
            throw new ProviderFailure("Yêu cầu hủy chưa được xác nhận. Hãy đồng bộ trạng thái trước khi thao tác tiếp.", unknown, ex);
        }
    }

    public JsonObject detail(long id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement q = c.prepareStatement("SELECT id,internal_invoice_id,booking_id,document_type,status,provider_environment,provider_mode,provider_transaction_id,invoice_template_no,invoice_series,invoice_number,tax_authority_code,send_tax_status,invoice_date,amount_without_vat,vat_amount,total_amount,provider_view_url,sync_error_code,sync_error_message,last_synced_at,parent_einvoice_id FROM electronic_invoices WHERE id=?")) {
            q.setLong(1, id); try (ResultSet r = q.executeQuery()) { if (!r.next()) throw new Conflict("Không tìm thấy hóa đơn điện tử.");
                JsonObject x = new JsonObject(); String[] keys = {"id","internalInvoiceId","bookingId","documentType","status","environment","providerMode","transactionId","templateNo","series","number","taxAuthorityCode","sendTaxStatus","invoiceDate","amountWithoutVat","vatAmount","totalAmount","viewUrl","errorCode","errorMessage","lastSyncedAt","parentId"};
                for (int i=1;i<=keys.length;i++) { Object value=r.getObject(i); if (value==null) x.add(keys[i-1],com.google.gson.JsonNull.INSTANCE); else if (value instanceof Number || value instanceof java.math.BigDecimal) x.addProperty(keys[i-1], value.toString()); else x.addProperty(keys[i-1], value.toString()); }
                JsonArray events=new JsonArray();
                try(PreparedStatement q2=c.prepareStatement("SELECT operation,result,provider_http_status,provider_error_code,request_id,summary,created_at FROM electronic_invoice_events WHERE electronic_invoice_id=? ORDER BY id")){
                    q2.setLong(1,id);try(ResultSet er=q2.executeQuery()){while(er.next()){JsonObject event=new JsonObject();event.addProperty("operation",er.getString(1));event.addProperty("result",er.getString(2));int http=er.getInt(3);if(er.wasNull())event.add("httpStatus",com.google.gson.JsonNull.INSTANCE);else event.addProperty("httpStatus",http);event.addProperty("errorCode",er.getString(4));event.addProperty("requestId",er.getString(5));event.addProperty("summary",er.getString(6));event.addProperty("createdAt",er.getTimestamp(7).toString());events.add(event);}}
                }
                x.add("events",events);return x;
            }
        }
    }

    public byte[] download(long id,int actor) throws Exception {
        ElectronicRow row = row(id); if (!"issued".equals(row.status) && !"cancelled".equals(row.status)) throw new Conflict("Hóa đơn chưa được xác nhận phát hành.");
        String requestId=UUID.randomUUID().toString();event(id,"download","started",null,null,requestId,"Provider PDF requested",actor);
        try{byte[] pdf=provider.downloadPdf(row.transactionId).pdf();event(id,"download","success",200,null,requestId,"Provider PDF retrieved",actor);return pdf;}
        catch(MisaMeInvoiceProvider.ProviderException ex){event(id,"download",ex.httpStatus()==0||ex.httpStatus()>=500?"unknown":"failed",ex.httpStatus(),ex.code(),requestId,"Provider PDF retrieval failed",actor);throw ex;}
    }

    private void updateIssued(long id, MisaMeInvoiceProvider.Published p, String url, int actor, String summary) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection()) { c.setAutoCommit(false); try {
            try (PreparedStatement q=c.prepareStatement("UPDATE electronic_invoices SET status='issued',provider_transaction_id=?,invoice_template_no=?,invoice_series=?,invoice_number=?,invoice_date=COALESCE(?,invoice_date),provider_view_url=?,sync_error_code=NULL,sync_error_message=NULL,last_synced_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
                q.setString(1,p.transactionId());q.setString(2,p.templateNo());q.setString(3,p.series());q.setString(4,p.number());
                java.sql.Timestamp issuedAt=null;try{if(p.invoiceDate()!=null){try{issuedAt=java.sql.Timestamp.valueOf(java.time.OffsetDateTime.parse(p.invoiceDate()).toLocalDateTime());}catch(java.time.format.DateTimeParseException ignored){issuedAt=java.sql.Timestamp.valueOf(LocalDateTime.parse(p.invoiceDate()));}}}catch(RuntimeException ignored){}
                if(issuedAt==null)q.setNull(5,java.sql.Types.TIMESTAMP);else q.setTimestamp(5,issuedAt);q.setString(6,url);q.setLong(7,id);q.executeUpdate();
            }
            event(c,id,"publish","success",200,null,UUID.randomUUID().toString(),summary,actor);c.commit();
        } catch(Exception ex){c.rollback();throw ex;} finally{c.setAutoCommit(true);} }
    }

    private void updateStatus(long id, String state, MisaMeInvoiceProvider.Status s, int actor, String summary) throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()) { c.setAutoCommit(false); try {
            setState(c,id,state,null,null,s.taxCode());
            try(PreparedStatement q=c.prepareStatement("UPDATE electronic_invoices SET send_tax_status=?,last_synced_at=CURRENT_TIMESTAMP WHERE id=?")){q.setString(1,Integer.toString(s.sendTaxStatus()));q.setLong(2,id);q.executeUpdate();}
            event(c,id,"status_sync","success",200,null,UUID.randomUUID().toString(),summary,actor);c.commit();
        }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);} }
    }

    private void updateFailure(long id, MisaMeInvoiceProvider.ProviderException ex, String state, int actor) throws SQLException { updateFailure(id,ex.code(),"Provider request requires reconciliation",state,actor,ex.httpStatus()); }
    private void updateFailure(long id, String code, String message, String state, int actor) throws SQLException { updateFailure(id,code,message,state,actor,null); }
    private void updateFailure(long id, String code, String message, String state, int actor, Integer http) throws SQLException {
        try(Connection c=DatabaseConnection.getConnection()){c.setAutoCommit(false);try{
            setState(c,id,state,code,message,null);event(c,id,"publish",state.equals("sync_required")?"unknown":"failed",http,code,UUID.randomUUID().toString(),message,actor);c.commit();
        }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);} }
    }

    private void markStatusWarning(long id,String code)throws SQLException {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE electronic_invoices SET sync_error_code=?,sync_error_message='Invoice is issued; tax-authority status is not synchronized',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='issued'")){q.setString(1,code);q.setLong(2,id);q.executeUpdate();}
    }

    private static void setState(Connection c,long id,String state,String code,String message,String taxCode)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("UPDATE electronic_invoices SET status=?,sync_error_code=?,sync_error_message=?,tax_authority_code=COALESCE(?,tax_authority_code),last_synced_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?")){
            q.setString(1,state);q.setString(2,code);q.setString(3,message);q.setString(4,taxCode);q.setLong(5,id);q.executeUpdate();
        }
    }

    private ElectronicRow row(long id)throws SQLException {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT provider_ref_id,provider_transaction_id,invoice_series,invoice_date,status,provider_mode,send_tax_status FROM electronic_invoices WHERE id=?")){
            q.setLong(1,id);try(ResultSet r=q.executeQuery()){if(!r.next())throw new Conflict("Không tìm thấy hóa đơn điện tử.");java.sql.Timestamp t=r.getTimestamp(4);return new ElectronicRow(r.getString(1),r.getString(2),r.getString(3),t==null?null:t.toLocalDateTime().toString(),r.getString(5),r.getString(6),r.getString(7));}
        }
    }

    private static void event(Connection c,long id,String op,String result,Integer http,String code,String requestId,String summary,int actor)throws SQLException {
        try(PreparedStatement q=c.prepareStatement("INSERT INTO electronic_invoice_events(electronic_invoice_id,operation,result,provider_http_status,provider_error_code,request_id,summary,actor_user_id) VALUES(?,?,?,?,?,?,?,?)")){
            q.setLong(1,id);q.setString(2,op);q.setString(3,result);if(http==null)q.setNull(4,java.sql.Types.SMALLINT);else q.setInt(4,http);q.setString(5,code);q.setString(6,requestId);q.setString(7,summary);if(actor<1)q.setNull(8,java.sql.Types.INTEGER);else q.setInt(8,actor);q.executeUpdate();
        }
    }
    private void event(long id,String op,String result,Integer http,String code,String requestId,String summary,int actor)throws SQLException{try(Connection c=DatabaseConnection.getConnection()){event(c,id,op,result,http,code,requestId,summary,actor);}}

    private record Line(String type,String description,BigDecimal amount) { }
    private record Parent(String status,BigDecimal total,BigDecimal vat,String sendTaxStatus) { }
    private record ElectronicRow(String refId,String transactionId,String series,String invoiceDate,String status,String providerMode,String sendTaxStatus) { }
    private record Snapshot(long invoiceId,int bookingId,String invoiceStatus,String bookingStatus,BigDecimal total,BigDecimal vat,BigDecimal ledgerTotal,BigDecimal ledgerVat,BigDecimal balance,String buyerName,String buyerTaxCode,String buyerAddress,String email,String sellerTaxCode,boolean companyBuyer,List<Line> lines) { }
    public static final class Conflict extends RuntimeException { public Conflict(String message){super(message);} }
    public static final class ProviderFailure extends RuntimeException { private final boolean uncertain; public ProviderFailure(String message,boolean uncertain,Throwable cause){super(message,cause);this.uncertain=uncertain;} public boolean uncertain(){return uncertain;} }
}
