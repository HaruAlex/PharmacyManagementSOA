# ============================================================================
# PharmacyManagementSOA — Dockerfile chung cho Gateway + 5 service (Play/Java/SBT)
# Mỗi service chạy trong working_dir của chính nó (thư mục con của repo),
# vì build.sbtr của mọi service đều tham chiếu ../shared/src/main/java.
# ============================================================================
FROM hseei/scala-sbt:1.13.1_2.13.12

WORKDIR /app

# Copy toàn bộ repository (bao gồm thư mục shared dùng chung)
COPY . .

# Mặc định chạy sbt; docker-compose sẽ override working_dir + command
# cho từng service (ví dụ: working_dir /app/api-gateway, command sbt "run 9000")
CMD ["sbt", "run"]
