/**
 * ============================================================================
 * PHARMACY MANAGEMENT SOA - FRONTEND SERVER
 * ============================================================================
 * 
 * Máy chủ frontend cho hệ thống quản lý nhà thuốc
 * Chạy bằng Node.js + Express, phục vụ giao diện web và kết nối với API Gateway
 * 
 * Đợt 1: Cổng mặc định thay đổi từ 3000 sang 8080 (theo bảng Server – Cổng)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 * @version 1.0.0
 */

const express = require('express');
const cors = require('cors');
const path = require('path');
require('dotenv').config();

const app = express();

// ============================================================================
// CẤU HÌNH MÔI TRƯỜNG
// ============================================================================
// Đợt 1: Cổng mặc định 8080 (frontend-1), tránh 3000 (hay bị chiếm bởi Vite hoặc công cụ khác)
const CONG_SERVER = process.env.PORT || 8080;
const DUONG_DAN_API_GATEWAY = process.env.API_GATEWAY_URL || 'http://localhost:9000';

// ============================================================================
// CÀI ĐẶT MIDDLEWARE
// ============================================================================

/**
 * Cấu hình CORS để cho phép frontend gọi API từ các domain khác nhau
 */
app.use(cors());

/**
 * Middleware phân tích JSON request body
 */
app.use(express.json());

/**
 * Middleware phân tích URL-encoded request body
 */
app.use(express.urlencoded({ extended: true }));

/**
 * Phục vụ các file tĩnh từ thư mục public
 */
app.use(express.static(path.join(__dirname, 'public')));

/**
 * Phục vụ các file nguồn từ thư mục src
 */
app.use('/src', express.static(path.join(__dirname, 'src')));

// ============================================================================
// ĐỊNH NGHĨA CÁC ENDPOINT
// ============================================================================

/**
 * Endpoint kiểm tra sức khỏe của server
 * GET /health
 * 
 * Trả về thông tin trạng thái server và kết nối API Gateway
 */
app.get('/health', (req, res) => {
  res.json({ 
    trangThai: 'khoeManh', 
    dichVu: 'frontend',
    thoiGian: new Date().toISOString(),
    apiGateway: DUONG_DAN_API_GATEWAY
  });
});

/**
 * Endpoint cung cấp cấu hình cho frontend
 * GET /api/config
 * 
 * Frontend sẽ gọi endpoint này để lấy địa chỉ API Gateway
 */
app.get('/api/config', (req, res) => {
  res.json({
    duongDanApiGateway: DUONG_DAN_API_GATEWAY
  });
});

/**
 * Route xử lý tất cả các đường dẫn còn lại
 * Hỗ trợ Single Page Application (SPA)
 * 
 * Mọi route không khớp với các endpoint trên sẽ trả về index.html
 * để cho phép frontend router xử lý
 */
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

// ============================================================================
// XỬ LÝ LỖI
// ============================================================================

/**
 * Middleware xử lý lỗi toàn cục
 * Bắt tất cả các lỗi chưa được xử lý và trả về response JSON
 */
app.use((loi, req, res, next) => {
  console.error('Lỗi:', loi);
  res.status(500).json({
    loi: 'Lỗi máy chủ nội bộ',
    thongDiep: loi.message
  });
});

// ============================================================================
// KHỞI ĐỘNG SERVER
// ============================================================================

/**
 * Khởi động server Express và lắng nghe trên cổng đã cấu hình
 */
const server = app.listen(CONG_SERVER, () => {
  console.log(`
╔════════════════════════════════════════════════════════════════╗
║        Hệ Thống Quản Lý Nhà Thuốc - Máy Chủ Frontend          ║
╠════════════════════════════════════════════════════════════════╣
║  Server:        http://localhost:${CONG_SERVER}                       
║  API Gateway:   ${DUONG_DAN_API_GATEWAY}                     
║  Trạng thái:    Đang chạy ✓                                    ║
║  Môi trường:    ${process.env.NODE_ENV || 'development'}       
╚════════════════════════════════════════════════════════════════╝
  `);
});

// ============================================================================
// GRACEFUL SHUTDOWN
// ============================================================================

/**
 * Xử lý tắt server một cách an toàn khi nhận tín hiệu SIGTERM
 * Đảm bảo tất cả kết nối hiện tại được đóng trước khi tắt
 */
process.on('SIGTERM', () => {
  console.log('Nhận tín hiệu SIGTERM: đang đóng máy chủ HTTP');
  server.close(() => {
    console.log('Máy chủ HTTP đã đóng');
  });
});
