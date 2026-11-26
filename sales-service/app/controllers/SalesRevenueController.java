package controllers;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import play.libs.Json;

import models.Invoice;
import services.RevenueService;
import services.RevenueService.RevenueSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.inject.Inject;

/**
 * SalesRevenueController - REST API cho báo cáo doanh thu & lợi nhuận
 * 
 * Spec: FR-BH-01 "Báo cáo doanh thu, lợi nhuận"
 * - Doanh thu (Revenue): SUM(TotalAmount) của invoices COMPLETED
 * - Lợi nhuận (Profit): Revenue - TotalCost
 * 
 * API Endpoints:
 * - GET /sales/revenue/daily/branch/{branchId}/date/{date}
 * - GET /sales/revenue/daily-profit/branch/{branchId}/date/{date}
 * - GET /sales/revenue/weekly/branch/{branchId}/start-date/{date}
 * - GET /sales/revenue/monthly/branch/{branchId}/year/{year}/month/{month}
 * - GET /sales/revenue/range/branch/{branchId}/from/{startDate}/to/{endDate}
 * - GET /sales/revenue/user/{userId}/date/{date}
 * - GET /sales/revenue/shift/{shiftId}
 * - GET /sales/revenue/margin/branch/{branchId}/date/{date}
 * - GET /sales/revenue/top-users/branch/{branchId}/date/{date}?limit=10
 * - GET /sales/revenue/snapshot/branch/{branchId}/date/{date}
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesRevenueController extends Controller {
    
    private final RevenueService revenueService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Inject
    public SalesRevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }
    
    /**
     * Lấy doanh thu theo ngày
     * GET /sales/revenue/daily/branch/{branchId}/date/{date}
     */
    public Result getDailyRevenue(Http.Request request, Long branchId, String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            BigDecimal revenue = revenueService.getRevenueByDate(branchId, date);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date.toString());
            response.put("revenue", revenue.toPlainString());
            response.put("type", "DAILY");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy doanh thu: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy lợi nhuận theo ngày
     * GET /sales/revenue/daily-profit/branch/{branchId}/date/{date}
     */
    public Result getDailyProfit(Http.Request request, Long branchId, String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            BigDecimal profit = revenueService.getProfitByDate(branchId, date);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date.toString());
            response.put("profit", profit.toPlainString());
            response.put("type", "DAILY");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy lợi nhuận: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy doanh thu theo tuần
     * GET /sales/revenue/weekly/branch/{branchId}/start-date/{date}
     */
    public Result getWeeklyRevenue(Http.Request request, Long branchId, String dateStr) {
        try {
            LocalDate startDate = LocalDate.parse(dateStr, DATE_FORMATTER);
            BigDecimal revenue = revenueService.getRevenueByWeek(branchId, startDate);
            
            LocalDate endDate = startDate.plusDays(6);
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("startDate", startDate.toString());
            response.put("endDate", endDate.toString());
            response.put("revenue", revenue.toPlainString());
            response.put("type", "WEEKLY");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy doanh thu tuần: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy doanh thu theo tháng
     * GET /sales/revenue/monthly/branch/{branchId}/year/{year}/month/{month}
     */
    public Result getMonthlyRevenue(Http.Request request, Long branchId, int year, int month) {
        try {
            BigDecimal revenue = revenueService.getRevenueByMonth(branchId, year, month);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("year", year);
            response.put("month", month);
            response.put("revenue", revenue.toPlainString());
            response.put("type", "MONTHLY");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy doanh thu tháng: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy doanh thu trong khoảng ngày
     * GET /sales/revenue/range/branch/{branchId}/from/{startDate}/to/{endDate}
     */
    public Result getRevenueByDateRange(Http.Request request, Long branchId, String startDateStr, String endDateStr) {
        try {
            LocalDate startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
            LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
            
            BigDecimal revenue = revenueService.getRevenueByDateRange(branchId, startDate, endDate);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("startDate", startDate.toString());
            response.put("endDate", endDate.toString());
            response.put("revenue", revenue.toPlainString());
            response.put("type", "RANGE");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy doanh thu khoảng ngày: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy doanh thu theo user & ngày
     * GET /sales/revenue/user/{userId}/date/{date}
     */
    public Result getUserRevenue(Http.Request request, Long userId, String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            BigDecimal revenue = revenueService.getRevenueByUserAndDate(userId, date);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("userId", userId);
            response.put("date", date.toString());
            response.put("revenue", revenue.toPlainString());
            response.put("type", "USER");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy doanh thu user: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy doanh thu theo shift
     * GET /sales/revenue/shift/{shiftId}
     */
    public Result getShiftRevenue(Http.Request request, Long shiftId) {
        try {
            BigDecimal revenue = revenueService.getRevenueByShift(shiftId);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("shiftId", shiftId);
            response.put("revenue", revenue.toPlainString());
            response.put("type", "SHIFT");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy doanh thu shift: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy margin% doanh thu
     * GET /sales/revenue/margin/branch/{branchId}/date/{date}
     */
    public Result getRevenueMargin(Http.Request request, Long branchId, String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            BigDecimal margin = revenueService.getRevenueMarginByDate(branchId, date);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date.toString());
            response.put("marginPercent", margin.toPlainString());
            response.put("unit", "%");
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi tính margin: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy top users theo doanh thu
     * GET /sales/revenue/top-users/branch/{branchId}/date/{date}?limit=10
     */
    public Result getTopUsersByRevenue(Http.Request request, Long branchId, String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            int limit = 10;
            if (request.queryString().containsKey("limit")) {
                String[] limitValues = request.queryString().get("limit");
                if (limitValues != null && limitValues.length > 0) {
                    limit = Integer.parseInt(limitValues[0]);
                }
            }
            
            List<Object[]> topUsers = revenueService.getTopUsersByRevenue(branchId, date, limit);
            
            ArrayNode users = Json.newArray();
            for (Object[] row : topUsers) {
                ObjectNode user = Json.newObject();
                user.put("userId", ((Number)row[0]).longValue());
                user.put("revenue", ((BigDecimal)row[1]).toPlainString());
                users.add(user);
            }
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date.toString());
            response.put("limit", limit);
            response.set("topUsers", users);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy top users: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy snapshot báo cáo theo ngày
     * GET /sales/revenue/snapshot/branch/{branchId}/date/{date}
     */
    public Result getDailySnapshot(Http.Request request, Long branchId, String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            RevenueSnapshot snapshot = revenueService.getDailySnapshot(branchId, date);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date.toString());
            response.put("invoiceCount", snapshot.invoiceCount);
            response.put("revenue", snapshot.revenue.toPlainString());
            response.put("totalCost", snapshot.totalCost.toPlainString());
            response.put("profit", snapshot.profit.toPlainString());
            response.put("marginPercent", snapshot.marginPercent.toPlainString());
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi lấy snapshot: " + e.getMessage()));
        }
    }
    
    /**
     * Helper: Error JSON response
     */
    private ObjectNode errorJson(String code, String message) {
        ObjectNode json = Json.newObject();
        json.put("success", false);
        json.put("error", code);
        json.put("message", message);
        return json;
    }
}
