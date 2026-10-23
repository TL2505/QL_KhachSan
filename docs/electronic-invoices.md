# Hóa đơn nội bộ và hóa đơn điện tử

## Phân biệt chứng từ

- **Bảng kê thanh toán nội bộ** (`invoices`): ghi nhận khoản đã thu và sổ cái booking. PDF này chỉ dùng để xem/in nội bộ, không phải hóa đơn điện tử hợp lệ và không có QR xác minh giả.
- **Hóa đơn điện tử MISA meInvoice** (`electronic_invoices`): chỉ được ghi nhận là `issued` khi MISA trả số hóa đơn và mã giao dịch; tài liệu tải xuống/tra cứu lấy từ MISA. Bản ghi lưu liên kết tới booking và chứng từ thanh toán nội bộ.

Không có thao tác tự phát hành khi checkout. Nhân viên phải bấm **Phát hành** trên màn hình hóa đơn. Tạo yêu cầu được lưu cùng nhật ký trước khi gọi nhà cung cấp. Lỗi mạng, timeout, phản hồi không đầy đủ hoặc lỗi 5xx chuyển sang `sync_required`; nhân viên phải **Đồng bộ** bằng mã tham chiếu trước khi thử phát hành lại. Mọi quyết định phát hành, đồng bộ, điều chỉnh/thay thế, hủy và tải PDF được ghi trong `electronic_invoice_events`; không ghi mật khẩu/token hay nội dung PII của request.

## Căn cứ và cấu hình pháp lý

Khung tham chiếu tại thời điểm triển khai (23/10/2026) gồm Nghị định 254/2026/NĐ-CP và Thông tư 91/2026/TT-BTC; cả hai có hiệu lực từ 01/07/2026. Nghị định quy định về đăng ký, lập và nội dung hóa đơn điện tử; Thông tư quy định các nội dung hướng dẫn liên quan. Phạm vi áp dụng, ngoại lệ, thời điểm lập và nghĩa vụ cụ thể phụ thuộc hồ sơ người bán và nghiệp vụ. Hướng dẫn này là tài liệu kỹ thuật, không thay thế xác nhận của kế toán/đại lý thuế. Căn cứ chính thức: [Nghị định 254/2026/NĐ-CP trên Cổng Thông tin điện tử Chính phủ](https://vanban.chinhphu.vn/?docid=218689&pageid=27160&typegroupid=4), [Thông tư 91/2026/TT-BTC trên Cổng Thông tin điện tử Chính phủ](https://vanban.chinhphu.vn/?docid=219006&pageid=27160), và [toàn văn Nghị định 254 trên Cổng Xây dựng Chính sách](https://xaydungchinhsach.chinhphu.vn/toan-van-nghi-dinh-so-254-2026-nd-cp-ve-hoa-don-dien-tu-chung-tu-dien-tu-119260713164251972.htm).

Trước khi dùng production, kế toán/đại lý thuế cần xác nhận hồ sơ đăng ký của chính đơn vị: hình thức hóa đơn (POS/Máy tính tiền hay có mã/không có mã), mẫu và ký hiệu, phương thức ký số/HSM, quy tắc lập cho dịch vụ lưu trú và dịch vụ phụ trợ, thuế suất/tax mapping, thông tin người mua, và quy trình xử lý sai sót theo nghị định. Mã chế độ không được tự suy ra theo loại khách sạn; để trống `MISA_EINVOICE_MODE` khiến các thao tác nhà cung cấp bị khóa.

## Nhà cung cấp và chế độ API

Tích hợp chọn **MISA meInvoice** vì có tài liệu API chính thức công khai cho phát hành, truy vấn trạng thái, tra cứu, tải PDF và hủy, cùng môi trường test. Tham khảo [hướng dẫn tích hợp](https://doc.meinvoice.vn/about/), [API phát hành HSM](https://doc.meinvoice.vn/api/Document/InvoicePublishHSM.html), [API trạng thái](https://doc.meinvoice.vn/ticket/Document/InvoiceStatus.html), [API tải PDF](https://doc.meinvoice.vn/ticket/Document/DownloadInvoice.html) và [API hủy](https://doc.meinvoice.vn/itg/Doc/Cancel.html). MISA phải cấp AppID/tài khoản tích hợp và cấu hình chữ ký số/hồ sơ đơn vị; mã nguồn không chứa tài khoản dùng chung.

Sao chép `.env.example` thành `.env`, cấu hình database/khóa hiện có, sau đó nhận các giá trị riêng từ MISA. Bắt đầu trên test:

```dotenv
HOTEL_LEGAL_NAME=<tên pháp lý đã đăng ký>
HOTEL_LEGAL_ADDRESS=<địa chỉ đã đăng ký>
HOTEL_TAX_CODE=<mã số thuế người bán>
HOTEL_VAT_RATE=<0.08 hoặc 0.10 đã được kế toán xác nhận>
MISA_EINVOICE_ENV=test
MISA_EINVOICE_MODE=<POS | HSM_CODE | HSM_NO_CODE>
MISA_EINVOICE_APP_ID=<AppID do MISA cấp>
MISA_EINVOICE_TAX_CODE=<mã số thuế đã đăng ký với MISA>
MISA_EINVOICE_USERNAME=<tài khoản tích hợp>
MISA_EINVOICE_PASSWORD=<mật khẩu tích hợp>
MISA_EINVOICE_SERIES=<ký hiệu hóa đơn đã đăng ký>
MISA_EINVOICE_TEMPLATE_ID=<mã mẫu hóa đơn MISA>
MISA_EINVOICE_NAME=<tên loại hóa đơn>
```

Các giá trị `MISA_EINVOICE_MODE`: `POS` dùng API hóa đơn từ máy tính tiền; `HSM_CODE` phát hành HSM qua đường dẫn có mã cơ quan thuế; `HSM_NO_CODE` là đường dẫn không mã. Chỉ chuyển `MISA_EINVOICE_ENV=production` sau khi MISA/kế toán xác nhận test, hồ sơ thuế và chữ ký số. Backend không gửi hóa đơn khi thiếu một trong các cấu hình bắt buộc.

## Luồng vận hành

1. Kết thúc/đối soát booking và thanh toán trong sổ cái hiện có.
2. Nhân viên chọn chứng từ nội bộ đã `paid`; hệ thống khóa dữ liệu, kiểm tra booking `checked_out`, số dư folio bằng 0, tổng/thuế trên hóa đơn khớp sổ cái và thuế khớp `HOTEL_VAT_RATE`.
3. Nhân viên xác nhận gửi. Hệ thống lưu RefID và nhật ký `started`, rồi gọi API MISA. Một lần timeout không bao giờ tự gửi lại.
4. **Đồng bộ** tra cứu bằng TransactionID hoặc RefID. Chỉ phản hồi có số hóa đơn từ MISA mới chuyển sang `issued`; trạng thái gửi cơ quan thuế được lưu riêng.
5. Điều chỉnh tăng/thay thế tạo tài liệu con tham chiếu hóa đơn gốc; điều chỉnh giảm bị chặn để kế toán dùng quy trình thay thế đã xác nhận với MISA. API hủy chỉ tạo `cancel_pending`; chuyển sang `cancelled` khi truy vấn MISA xác nhận đã xóa/hủy.
6. Khách chỉ nhìn thấy số/link/PDF MISA khi trạng thái là `issued`. Bảng kê PDF nội bộ luôn mang nhãn “không phải hóa đơn điện tử hợp lệ”.

Nếu sổ cái có bút toán âm, thuế suất theo từng mặt hàng khác nhau, thuế không khớp cấu hình đơn giản 8%/10%, hoặc dữ liệu buyer chưa hợp lệ, hệ thống dừng trước khi gọi MISA. Kế toán cần ánh xạ/đối soát trường hợp đó trước khi phát hành; không dùng giá trị ước lượng để thay thế hóa đơn.

## CSDL và endpoint nhân viên

`UpdateDbUtility` chạy migration `database/migrations/v4_5_electronic_invoices.sql`. Bảng `electronic_invoices` lưu provider ref/transaction, series/mẫu/số, tổng tiền, link tra cứu, trạng thái; `electronic_invoice_events` lưu lịch sử thao tác và lỗi đã khử dữ liệu nhạy cảm.

- `POST /api/v1/invoices/{id}/electronic/issue`
- `POST /api/v1/invoices/{id}/electronic/adjust` với `parent_einvoice_id`, `document_type` (`adjustment` hoặc `replacement`), `reason`
- `POST /api/v1/invoices/{id}/electronic/{electronicId}/sync`
- `POST /api/v1/invoices/{id}/electronic/{electronicId}/cancel` với `reason`
- `GET /api/v1/invoices/{id}/electronic/{electronicId}/pdf`

Phát hành, hủy và điều chỉnh/thay thế chỉ dành cho tài khoản nhân viên được xác thực. Các route không tạo booking hay thay đổi payment; việc ghi nhận thanh toán vẫn là luồng hiện có.
