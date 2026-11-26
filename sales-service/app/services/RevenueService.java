package services;

import models.Invoice;
import models.Invoice.InvoiceStatus;
import repositories.InvoiceRepository;
import repositories.InvoiceDetailLotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * RevenueService - Business Logic cho Doanh thu & Lợi nhuận
 * 
 * Spec: FR-BH-01 "Báo cáo doanh thu, lợi nhuận"
 * - Doanh thu (Revenue): Tổng TotalAmount của invoices COMPLETED
 * - Lợi nhuận (Profit): Revenue - TotalCost (tổng cost từ InvoiceDetailLots)
 * - Phân tích theo: Ngày, Tuần, Tháng, Chi nhánh, User, Shift
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class RevenueService {
    
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDetailLotRepository invoiceDetailLotRepository;
    
    @Inject
    public RevenueService(InvoiceRepository invoiceRepository, InvoiceDetailLotRepository invoiceDetailLotRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceDetailLotRepository = invoiceDetailLotRepository;
    }
    
    /**
     * USE CASE 1: Lấy doanh thu theo ngày (Daily Revenue)
     * 
     * @param branchId Chi nhánh
     * @param date Ngày cần lấy
     * @return Doanh thu = SUM(TotalAmount) của invoices COMPLETED
     */
    public BigDecimal getRevenueByDate(Long branchId, LocalDate date) {
        if (branchId == null || date == null) {
            throw new IllegalArgumentException("BranchId và date không được null");
        }
        
        List<Invoice> invoices = invoiceRepository.findByBranchAndDate(branchId, date);
        return invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * USE CASE 2: Lấy lợi nhuận theo ngày (Daily Profit)
     * 
     * Flow:
     * 1. Lấy tất cả invoices COMPLETED trong ngày
     * 2. Tính Revenue = SUM(TotalAmount)
     * 3. Tính Cost = SUM(CostBaseAtSale * QuantityBase) từ InvoiceDetailLots
     * 4. Profit = Revenue - Cost
     * 
     * @param branchId Chi nhánh
     * @param date Ngày cần lấy
     * @return Profit = Revenue - TotalCost
     */
    public BigDecimal getProfitByDate(Long branchId, LocalDate date) {
        if (branchId == null || date == null) {
            throw new IllegalArgumentException("BranchId và date không được null");
        }
        
        // Lấy revenue
        BigDecimal revenue = getRevenueByDate(branchId, date);
        
        // Lấy list invoices COMPLETED để tính cost
        List<Invoice> invoices = invoiceRepository.findByBranchAndDate(branchId, date);
        List<Long> completedInvoiceIds = invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getInvoiceId)
            .toList();
        
        if (completedInvoiceIds.isEmpty()) {
            return BigDecimal.ZERO; // Không có invoice nào
        }
        
        // Tính tổng cost từ InvoiceDetailLots
        BigDecimal totalCost = invoiceDetailLotRepository.getTotalCostByInvoiceIds(completedInvoiceIds);
        
        // Profit = Revenue - Cost
        return revenue.subtract(totalCost);
    }
    
    /**
     * USE CASE 3: Lấy doanh thu theo tuần
     * 
     * @param branchId Chi nhánh
     * @param startDate Ngày đầu tuần (Monday)
     * @return Revenue của tuần
     */
    public BigDecimal getRevenueByWeek(Long branchId, LocalDate startDate) {
        if (branchId == null || startDate == null) {
            throw new IllegalArgumentException("BranchId và startDate không được null");
        }
        
        // Tính endDate = startDate + 6 ngày
        LocalDate endDate = startDate.plusDays(6);
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        // Loop từ startDate đến endDate
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalRevenue = totalRevenue.add(getRevenueByDate(branchId, date));
        }
        
        return totalRevenue;
    }
    
    /**
     * USE CASE 4: Lấy lợi nhuận theo tuần
     * 
     * @param branchId Chi nhánh
     * @param startDate Ngày đầu tuần
     * @return Profit của tuần
     */
    public BigDecimal getProfitByWeek(Long branchId, LocalDate startDate) {
        if (branchId == null || startDate == null) {
            throw new IllegalArgumentException("BranchId và startDate không được null");
        }
        
        LocalDate endDate = startDate.plusDays(6);
        BigDecimal totalProfit = BigDecimal.ZERO;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalProfit = totalProfit.add(getProfitByDate(branchId, date));
        }
        
        return totalProfit;
    }
    
    /**
     * USE CASE 5: Lấy doanh thu theo tháng
     * 
     * @param branchId Chi nhánh
     * @param year Năm
     * @param month Tháng (1-12)
     * @return Revenue của tháng
     */
    public BigDecimal getRevenueByMonth(Long branchId, int year, int month) {
        if (branchId == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("BranchId không được null, month phải 1-12");
        }
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalRevenue = totalRevenue.add(getRevenueByDate(branchId, date));
        }
        
        return totalRevenue;
    }
    
    /**
     * USE CASE 6: Lấy lợi nhuận theo tháng
     * 
     * @param branchId Chi nhánh
     * @param year Năm
     * @param month Tháng (1-12)
     * @return Profit của tháng
     */
    public BigDecimal getProfitByMonth(Long branchId, int year, int month) {
        if (branchId == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("BranchId không được null, month phải 1-12");
        }
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        BigDecimal totalProfit = BigDecimal.ZERO;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalProfit = totalProfit.add(getProfitByDate(branchId, date));
        }
        
        return totalProfit;
    }
    
    /**
     * USE CASE 7: Lấy doanh thu trong khoảng ngày
     * 
     * @param branchId Chi nhánh
     * @param startDate Ngày bắt đầu
     * @param endDate Ngày kết thúc
     * @return Revenue trong khoảng
     */
    public BigDecimal getRevenueByDateRange(Long branchId, LocalDate startDate, LocalDate endDate) {
        if (branchId == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("BranchId, startDate, endDate không được null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("EndDate phải >= StartDate");
        }
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalRevenue = totalRevenue.add(getRevenueByDate(branchId, date));
        }
        
        return totalRevenue;
    }
    
    /**
     * USE CASE 8: Lấy lợi nhuận trong khoảng ngày
     * 
     * @param branchId Chi nhánh
     * @param startDate Ngày bắt đầu
     * @param endDate Ngày kết thúc
     * @return Profit trong khoảng
     */
    public BigDecimal getProfitByDateRange(Long branchId, LocalDate startDate, LocalDate endDate) {
        if (branchId == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("BranchId, startDate, endDate không được null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("EndDate phải >= StartDate");
        }
        
        BigDecimal totalProfit = BigDecimal.ZERO;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalProfit = totalProfit.add(getProfitByDate(branchId, date));
        }
        
        return totalProfit;
    }
    
    /**
     * USE CASE 9: Lấy doanh thu theo user & ngày
     * 
     * @param userId ID user (nhân viên)
     * @param date Ngày
     * @return Revenue của user trong ngày
     */
    public BigDecimal getRevenueByUserAndDate(Long userId, LocalDate date) {
        if (userId == null || date == null) {
            throw new IllegalArgumentException("UserId và date không được null");
        }
        
        List<Invoice> invoices = invoiceRepository.findByUserAndDate(userId, date);
        return invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * USE CASE 10: Lấy lợi nhuận theo user & ngày
     * 
     * @param userId ID user
     * @param date Ngày
     * @return Profit của user trong ngày
     */
    public BigDecimal getProfitByUserAndDate(Long userId, LocalDate date) {
        if (userId == null || date == null) {
            throw new IllegalArgumentException("UserId và date không được null");
        }
        
        // Lấy revenue
        BigDecimal revenue = getRevenueByUserAndDate(userId, date);
        
        // Lấy cost từ invoices của user
        List<Invoice> invoices = invoiceRepository.findByUserAndDate(userId, date);
        List<Long> completedInvoiceIds = invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getInvoiceId)
            .toList();
        
        if (completedInvoiceIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalCost = invoiceDetailLotRepository.getTotalCostByInvoiceIds(completedInvoiceIds);
        
        return revenue.subtract(totalCost);
    }
    
    /**
     * USE CASE 11: Lấy doanh thu theo shift & ngày
     * 
     * @param shiftId ID shift
     * @return Revenue của shift
     */
    public BigDecimal getRevenueByShift(Long shiftId) {
        if (shiftId == null) {
            throw new IllegalArgumentException("ShiftId không được null");
        }
        
        List<Invoice> invoices = invoiceRepository.findByShiftId(shiftId);
        return invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * USE CASE 12: Lấy lợi nhuận theo shift
     * 
     * @param shiftId ID shift
     * @return Profit của shift
     */
    public BigDecimal getProfitByShift(Long shiftId) {
        if (shiftId == null) {
            throw new IllegalArgumentException("ShiftId không được null");
        }
        
        // Lấy revenue
        BigDecimal revenue = getRevenueByShift(shiftId);
        
        // Lấy cost
        List<Invoice> invoices = invoiceRepository.findByShiftId(shiftId);
        List<Long> completedInvoiceIds = invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getInvoiceId)
            .toList();
        
        if (completedInvoiceIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalCost = invoiceDetailLotRepository.getTotalCostByInvoiceIds(completedInvoiceIds);
        
        return revenue.subtract(totalCost);
    }
    
    /**
     * USE CASE 13: Lấy doanh thu top user (theo ngày)
     * 
     * @param branchId Chi nhánh
     * @param date Ngày
     * @param limit Số lượng top user
     * @return List(userId, revenue) sắp xếp giảm dần
     */
    public List<Object[]> getTopUsersByRevenue(Long branchId, LocalDate date, int limit) {
        if (branchId == null || date == null || limit < 1) {
            throw new IllegalArgumentException("BranchId, date, limit không hợp lệ");
        }
        
        return invoiceRepository.getTopUsersByRevenueAndDate(branchId, date, limit);
    }
    
    /**
     * USE CASE 14: Doanh thu margin (%)
     * 
     * @param branchId Chi nhánh
     * @param date Ngày
     * @return Margin% = (Profit / Revenue) * 100, return 0 nếu revenue = 0
     */
    public BigDecimal getRevenueMarginByDate(Long branchId, LocalDate date) {
        if (branchId == null || date == null) {
            throw new IllegalArgumentException("BranchId và date không được null");
        }
        
        BigDecimal revenue = getRevenueByDate(branchId, date);
        if (revenue.signum() == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal profit = getProfitByDate(branchId, date);
        
        // Margin% = (Profit / Revenue) * 100
        return profit.divide(revenue, 2, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    /**
     * USE CASE 15: Thống kê tổng hợp theo ngày
     * 
     * @param branchId Chi nhánh
     * @param date Ngày
     * @return Object gồm: invoiceCount, revenue, totalCost, profit, marginPercent
     */
    public RevenueSnapshot getDailySnapshot(Long branchId, LocalDate date) {
        if (branchId == null || date == null) {
            throw new IllegalArgumentException("BranchId và date không được null");
        }
        
        List<Invoice> invoices = invoiceRepository.findByBranchAndDate(branchId, date);
        long invoiceCount = invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .count();
        
        BigDecimal revenue = getRevenueByDate(branchId, date);
        BigDecimal profit = getProfitByDate(branchId, date);
        BigDecimal marginPercent = getRevenueMarginByDate(branchId, date);
        
        BigDecimal totalCost = revenue.signum() > 0 
            ? revenue.subtract(profit)
            : BigDecimal.ZERO;
        
        return new RevenueSnapshot(invoiceCount, revenue, totalCost, profit, marginPercent);
    }
    
    /**
     * DTO cho snapshot báo cáo
     */
    public static class RevenueSnapshot {
        public long invoiceCount;
        public BigDecimal revenue;
        public BigDecimal totalCost;
        public BigDecimal profit;
        public BigDecimal marginPercent;
        
        public RevenueSnapshot(long invoiceCount, BigDecimal revenue, BigDecimal totalCost, 
                             BigDecimal profit, BigDecimal marginPercent) {
            this.invoiceCount = invoiceCount;
            this.revenue = revenue;
            this.totalCost = totalCost;
            this.profit = profit;
            this.marginPercent = marginPercent;
        }
    }
}
