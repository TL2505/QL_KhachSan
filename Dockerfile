# Sử dụng môi trường Java 17 (JDK) để biên dịch và chạy
FROM eclipse-temurin:17-jdk

# Thư mục làm việc trong container
WORKDIR /app

# Khai báo múi giờ (Tùy chọn, giúp log hiện đúng giờ Việt Nam)
ENV TZ=Asia/Ho_Chi_Minh

# Copy toàn bộ mã nguồn và thư viện vào container
COPY src/ ./src/
COPY lib/ ./lib/

# Biên dịch toàn bộ mã nguồn (Dùng dấu : thay vì ; vì Linux dùng dấu : để ngăn cách classpath)
RUN mkdir -p build/classes && \
    find src -name "*.java" > sources.txt && \
    javac -encoding utf-8 -cp "lib/*:build/classes" -d build/classes @sources.txt

# Mở cửa cho cổng 8080 (Cổng mà Backend của bạn đang lắng nghe)
EXPOSE 8081

# Lệnh khởi động Server ngầm khi container chạy
CMD ["java", "-cp", "lib/*:build/classes", "quanlykhachsan.backend.Main"]
