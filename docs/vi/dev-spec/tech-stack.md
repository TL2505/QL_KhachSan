# ĐỊNH NGHĨA NGĂN XẾP CÔNG NGHỆ (TECH STACK)

| Hạng mục | Công nghệ | Phiên bản | Lý do chọn lựa |
| :--- | :--- | :--- | :--- |
| **Nhà cung cấp LLM** | OpenAI `gpt-4o-mini` | Mới nhất | Tốc độ cao, tối ưu chi phí cho volume chat lớn của 5★; Dự phòng: Claude 3 Haiku |
| **Vector Database** | `pgvector` (PostgreSQL) | 16 | Tuân thủ ACID, tìm kiếm vector native (1536 dim), dễ triển khai |
| **PMS Backend** | Java (Spring Boot) | 17 | Tiêu chuẩn Enterprise, đa luồng mạnh mẽ cho Night Audit, strongly typed |
| **Công cụ Build** | Gradle | 8.x | Build nhanh, tiêu chuẩn cho các dự án Java/Spring Boot hiện đại |
| **AI Gateway** | Python (FastAPI) | 3.11 | Thư viện native cho ML/AI, xử lý bất đồng bộ (async), lý tưởng cho điều phối LLM |
| **Frontend UI** | React + Vite | 18 | Hiệu năng cao, render cực nhanh cho Staff Dashboard |
| **DB Giao dịch** | MariaDB | 11 | DB quan hệ hiệu năng cao cho folios, bookings, và log tài chính |
| **Triển khai** | Docker Compose | v2 | Đơn giản hóa việc quản lý 6 microservices trên 1 máy ảo Ubuntu duy nhất |
| **Hệ điều hành** | Ubuntu LTS | 22.04 | Môi trường production ổn định, bảo mật |
| **CI/CD** | GitHub Actions | - | Tích hợp sẵn với repo, tự động hóa test và build image docker |
| **Giám sát** | Prometheus + Grafana | - | Bảng điều khiển thời gian thực cho độ trễ API và tỷ lệ vi phạm AI |
