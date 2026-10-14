# Sử dụng môi trường Java 17 (JDK) để biên dịch và chạy
FROM maven:3.9.9-eclipse-temurin-17 AS build

# Thư mục làm việc trong container
WORKDIR /app

# Khai báo múi giờ (Tùy chọn, giúp log hiện đúng giờ Việt Nam)
ENV TZ=Asia/Ho_Chi_Minh

# Copy toàn bộ mã nguồn và thư viện vào container
COPY pom.xml ./
COPY src/ ./src/
RUN mvn -B -DskipTests package dependency:copy-dependencies -DincludeScope=runtime

# Biên dịch toàn bộ mã nguồn (Dùng dấu : thay vì ; vì Linux dùng dấu : để ngăn cách classpath)
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Ho_Chi_Minh
COPY --from=build /app/target/classes/ ./build/classes/
COPY --from=build /app/target/dependency/ ./lib/
COPY database/migrations/ ./database/migrations/
COPY web-ui/public/ ./web-ui/public/

# Install curl cho healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Mở cửa cho cổng 8080 (Cổng mà Backend của bạn đang lắng nghe)
EXPOSE 8081

# Lệnh khởi động Server ngầm khi container chạy
CMD ["java", "-cp", "lib/*:build/classes", "quanlykhachsan.backend.Main"]
