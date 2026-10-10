# KIẾN TRÚC TRIỂN KHAI & DANH SÁCH KIỂM TRA (CHECKLIST)

## 1. KIẾN TRÚC 6-CONTAINER (Docker Compose)
Được triển khai trên một máy ảo Ubuntu 22.04 LTS (ví dụ: `192.168.56.102`).
1. **Nginx (Reverse Proxy):** Cổng `443/80` (Cắt TLS, định tuyến).
2. **Java Backend (PMS API):** Cổng `8081` (Spring Boot 17). Chỉ mạng nội bộ.
3. **MariaDB (Giao dịch):** Cổng `3306`. Chỉ mạng nội bộ. Không phơi bày cổng ra ngoài.
4. **pgvector (Vector DB):** Cổng `5432`. Chỉ mạng nội bộ. Dùng để nhúng RAG.
5. **AI Gateway (FastAPI):** Cổng `8082`. Chỉ mạng nội bộ. Tương tác với OpenAI.
6. **Adminer (Quản trị DB):** Cổng `127.0.0.1:8083` (Chỉ dành cho Dev).

## 2. LỘ TRÌNH TRIỂN KHAI THEO GIAI ĐOẠN
- **Giai đoạn 1 (Lõi):** Triển khai Nginx, Java Backend, MariaDB. (3 containers)
- **Giai đoạn 2 (Thử nghiệm AI):** Thêm AI Gateway, pgvector. (+2 containers)
- **Giai đoạn 3 (Mở rộng):** Thêm các trình lắng nghe Webhook ngoài và scale các node Java.

## 3. DANH SÁCH KIỂM TRA BẢO MẬT (9 Mục)
- [x] Tất cả các cổng DB (3306, 5432) CHỈ được bind vào mạng nội bộ Docker.
- [x] File `.env` được đưa vào `.gitignore` và KHÔNG BAO GIỜ bị commit.
- [x] Ép buộc TLS (HTTPS) tại tầng Nginx.
- [x] Token JWT có thời hạn hết hạn nghiêm ngặt là 1 giờ.
- [x] Khóa API của AI (OpenAI) được xoay vòng (rotate) hàng tháng.
- [x] Bật giới hạn tỷ lệ (Rate limiting) trên AI Gateway để chống tấn công DDoS làm tốn tiền.
- [x] **Chỉ dùng SSH key** để truy cập VM (Tắt xác thực mật khẩu).
- [x] **fail2ban** được cấu hình để ngăn chặn tấn công brute force.
- [x] **Tường lửa UFW** được bật, chỉ cho phép mở cổng 22, 80, 443.

## 4. VẬN HÀNH (Sao lưu & Kiểm tra sức khỏe)
- **Sao lưu (Backup):** Cron job hàng ngày chạy lệnh `mysqldump` lúc 02:00 sáng, giữ lại 30 ngày.
  ```bash
  0 2 * * * docker exec pms-mariadb sh -c 'mysqldump -u root -p${DB_PASSWORD} pms' > /backups/db_$(date +\%F).sql
  ```
- **Kiểm tra sức khỏe (Health Check):** Script xác minh sau khi deploy gọi tới endpoint `/api/v1/health`.
  ```bash
  curl -f https://pms.hotel.com/api/v1/health || exit 1
  ```

## 5. BIẾN MÔI TRƯỜNG (.env)
```bash
# CẢNH BÁO: KHÔNG BAO GIỜ COMMIT FILE NÀY
DB_PASSWORD=secure_password
VECTOR_DB_PASSWORD=pg_secure_password
OPENAI_API_KEY=sk-xxxx
LLM_ENDPOINT=https://api.openai.com/v1
JWT_SECRET=long_random_string_here
```
