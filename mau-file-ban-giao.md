# BÀN GIAO SAU ĐỢT N: <tên đợt>

> Đây là MẪU. Agent điền thay các chỗ trong dấu < > rồi lưu tại `docs/handoff/dot-N.md`.
> Người dùng dán file đã điền này cùng prompt gốc vào phiên agent mới để làm đợt tiếp theo.

## 1. Trạng thái
- Đợt đã hoàn thành: <N> / 6
- Commit cuối: <hash hoặc message>
- Môi trường đã dùng: <Windows/Linux, JDK, Node, Consul chạy hay chưa, SQL Server chạy hay chưa>

## 2. Đã làm
- <Việc 1>
- <Việc 2>

## 3. File đã tạo/sửa
- `<đường dẫn>`: <mô tả 1 dòng>

## 4. Bảng Server – Cổng đang áp dụng
| Server | Instance 1: ID – cổng | Instance 2: ID – cổng |
|--------|-----------------------|-----------------------|
| frontend | frontend-1 – 8080 | (không có) |
| api-gateway | gateway-1 – 9000 | (không có) |
| auth-service | auth-1 – 9001 | auth-2 – 9011 |
| catalog-service | catalog-1 – 9002 | catalog-2 – 9012 |
| inventory-service | inventory-1 – 9003 | inventory-2 – 9013 |
| purchase-service | purchase-1 – 9004 | purchase-2 – 9014 |
| sales-service | sales-1 – 9005 | sales-2 – 9015 |
| report-service | report-1 – 9006 | report-2 – 9016 |

Ghi chú nếu có chỗ khác bảng: <nếu không có, ghi "Không có khác biệt">

## 5. Lệnh kiểm chứng và kết quả thực tế
| Lệnh | Kết quả | Trạng thái |
|------|---------|------------|
| `<lệnh>` | <kết quả thật> | Đạt / Không đạt / Chưa chạy được (lý do) |

## 6. Quyết định kỹ thuật quan trọng
- <Chọn gì, vì sao. Ví dụ: chạy instance 2 bằng bản stage vì sbt bị khóa khi chạy 2 tiến trình cùng project>

## 7. Việc tồn đọng / rủi ro
- <Việc chưa xong, lỗi đã biết, việc ngoài phạm vi phát hiện được>

## 8. Việc của đợt N+1
- Phạm vi: <chép từ bảng đợt trong prompt>
- Lưu ý rút ra từ đợt vừa làm: <...>

## 9. Câu lệnh khởi động đợt N+1
Dán đoạn sau vào agent mới (kèm prompt gốc và file này):

> Đây là prompt gốc và file bàn giao sau đợt N. Hãy làm ĐỢT N+1 theo mục CHIA ĐỢT LÀM VIỆC: đọc file bàn giao, kiểm tra nhanh trạng thái thực tế, nêu kế hoạch ngắn rồi mới làm. Làm xong thì dừng và hỏi tôi.

---
*Riêng Đợt 6: bỏ mục 8 và 9, thay bằng "Kết luận" và danh sách việc người dùng cần tự kiểm tra thủ công.*
