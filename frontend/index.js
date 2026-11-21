/**
 * ============================================================================
 * FRONTEND - HTTP SERVER (Node.js)
 * ============================================================================
 * 
 * Server Node.js nhẹ chạy ở cổng 8080 (hoặc PORT env var)
 * Phục vụ trang HTML tĩnh + gọi Gateway API qua http://localhost:9000
 * 
 * Không dùng framework nặng, chỉ Express để serve static files
 * 
 * Cách chạy:
 *   cd frontend
 *   npm install
 *   npm start          # Chạy ở cổng 8080 (mặc định)
 *   PORT=8080 npm start  # Chạy ở cổng 8080 (tường minh)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */

const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 8080;

// ============================================================================
// Middleware
// ============================================================================

// CORS: cho phép frontend gọi Gateway ở localhost:9000
app.use(cors({
  origin: 'http://localhost:9000',
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Branch-Id', 'X-Request-Id', 'Idempotency-Key']
}));

// Serve static files từ thư mục public/
app.use(express.static(path.join(__dirname, 'public')));

// ============================================================================
// Routes
// ============================================================================

/**
 * GET / - Trang chủ
 */
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public/index.html'));
});

/**
 * GET /health - Health check cho script kiểm tra sống của frontend
 */
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'UP',
    service: 'frontend',
    timestamp: new Date().toISOString(),
    port: PORT
  });
});

/**
 * GET /api-config - Cấu hình API endpoint (tiện cho frontend)
 */
app.get('/api-config', (req, res) => {
  res.json({
    apiBaseUrl: 'http://localhost:9000',
    gatewayHealth: 'http://localhost:9000/health',
    gatewayMetrics: 'http://localhost:9000/metrics',
    consulUI: 'http://localhost:8500/ui/'
  });
});

// ============================================================================
// 404 Handler
// ============================================================================
app.use((req, res) => {
  res.status(404).json({
    error: 'Not Found',
    path: req.path,
    message: 'Endpoint không tồn tại. Truy cập / để xem trang chủ.'
  });
});

// ============================================================================
// Error Handler
// ============================================================================
app.use((err, req, res, next) => {
  console.error('[ERROR]', err.message);
  res.status(500).json({
    error: 'Internal Server Error',
    message: err.message
  });
});

// ============================================================================
// Server Start
// ============================================================================
app.listen(PORT, () => {
  console.log(`
╔════════════════════════════════════════════════════════════╗
║  FRONTEND - NODE.JS SERVER                                ║
╠════════════════════════════════════════════════════════════╣
║  Status: ✅ Running                                        ║
║  Port: ${PORT}                                               ║
║  URL: http://localhost:${PORT}                               ║
║  API Gateway: http://localhost:9000                        ║
║  Consul UI: http://localhost:8500/ui/                      ║
╚════════════════════════════════════════════════════════════╝
  `);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('[INFO] SIGTERM received, shutting down gracefully...');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('[INFO] SIGINT received, shutting down gracefully...');
  process.exit(0);
});
