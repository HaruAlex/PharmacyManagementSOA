package services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import models.DrugLotDto;
import models.PagedResponse;
import models.StockLot;
import repositories.InventoryRepository;

/**
 * Lớp dịch vụ nghiệp vụ cho lô thuốc (BE-29):
 * truy vấn danh sách/chi tiết lô thuốc từ InventoryRepository, phân loại hạn sử dụng,
 * chuyển đổi StockLot sang DrugLotDto và phân trang kết quả.
 */
public class DrugLotService {
    // Ngưỡng số ngày còn lại tối đa để đánh dấu trạng thái sắp hết hạn (SAP_HET_HAN).
    private static final int EXPIRING_SOON_DAYS = 30;

    // Repository truy xuất dữ liệu lô thuốc tồn kho.
    private final InventoryRepository inventoryRepository;

    /**
     * Khởi tạo service thông qua Dependency Injection.
     */
    @Inject
    public DrugLotService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Lấy danh sách lô thuốc theo chi nhánh, hỗ trợ lọc theo drugId (khớp mã thuốc)
     * và trạng thái hạn sử dụng (status), sắp xếp và phân trang.
     */
    public PagedResponse getLots(
            Long branchId, Long drugId, String status, int page, int size, String sort) {
        // Kiểm tra tham số phân trang hợp lệ.
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Tham số phân trang page hoặc size không hợp lệ");
        }

        LocalDate today = LocalDate.now();
        String normalizedStatus =
                (status != null && !status.isBlank()) ? status.trim().toUpperCase(Locale.ROOT) : null;

        // Lấy danh sách lô thuốc từ repository và lọc theo điều kiện.
        List<StockLot> rawLots = inventoryRepository.findAllLots(null);
        List<Object> filtered = new ArrayList<>();
        long index = 1L;

        for (StockLot lot : rawLots) {
            // Nếu có truyền drugId (ví dụ 1 -> khớp MED-01 / MED-1 / "1") thì lọc theo mã thuốc tương ứng.
            if (drugId != null && !matchesDrugId(lot.getMedicineId(), drugId)) {
                index++;
                continue;
            }

            DrugLotDto dto = toDto(lot, branchId, index++, today);

            if (normalizedStatus != null && !normalizedStatus.equals(dto.getExpiryStatus())) {
                continue;
            }
            filtered.add(dto);
        }

        // Sắp xếp danh sách theo trường sort (mặc định sắp xếp theo ngày hết hạn tăng dần).
        Comparator<Object> comparator = resolveComparator(sort);
        filtered.sort(comparator);

        // Phân trang và trả về PagedResponse.
        return paginate(filtered, page, size);
    }

    /**
     * Lấy chi tiết một lô thuốc theo lotId (thứ tự 1-based trong danh sách hiện tại)
     * trong phạm vi chi nhánh hiện hành.
     */
    public Optional<DrugLotDto> getLotDetail(Long lotId, Long branchId) {
        if (lotId == null || branchId == null || lotId <= 0) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now();
        List<StockLot> rawLots = inventoryRepository.findAllLots(null);

        for (int i = 0; i < rawLots.size(); i++) {
            long currentId = i + 1L;
            if (currentId == lotId) {
                return Optional.of(toDto(rawLots.get(i), branchId, currentId, today));
            }
        }
        return Optional.empty();
    }

    /**
     * Kiểm tra mã thuốc trong StockLot (ví dụ "MED-01") có khớp với tham số drugId số hay không.
     */
    private boolean matchesDrugId(String medicineId, Long drugId) {
        if (medicineId == null || drugId == null) {
            return false;
        }
        String normalizedMed = medicineId.trim().toUpperCase(Locale.ROOT);
        String plainId = String.valueOf(drugId);
        String medWithDash = "MED-" + drugId;
        String medPadded2 = String.format(Locale.ROOT, "MED-%02d", drugId);
        return normalizedMed.equals(plainId)
                || normalizedMed.equals(medWithDash.toUpperCase(Locale.ROOT))
                || normalizedMed.equals(medPadded2.toUpperCase(Locale.ROOT));
    }

    /**
     * Xác định bộ so sánh sắp xếp dựa trên tham số sort truyền lên.
     */
    private Comparator<Object> resolveComparator(String sort) {
        String field = (sort != null && !sort.isBlank()) ? sort.trim() : "expiryDate";
        boolean desc = false;
        if (field.startsWith("-")) {
            desc = true;
            field = field.substring(1);
        } else if (field.endsWith(",desc")) {
            desc = true;
            field = field.substring(0, field.length() - 5);
        }

        Comparator<DrugLotDto> dtoComparator =
                switch (field) {
                    case "lotNumber" ->
                            Comparator.comparing(
                                    DrugLotDto::getLotNumber,
                                    Comparator.nullsLast(String::compareToIgnoreCase));
                    case "quantityBase" ->
                            Comparator.comparing(
                                    DrugLotDto::getQuantityBase, Comparator.nullsLast(Long::compareTo));
                    case "costBase" ->
                            Comparator.comparing(
                                    DrugLotDto::getCostBase,
                                    Comparator.nullsLast(BigDecimal::compareTo));
                    case "lotId" ->
                            Comparator.comparing(
                                    DrugLotDto::getLotId, Comparator.nullsLast(Long::compareTo));
                    default ->
                            Comparator.comparing(
                                    DrugLotDto::getExpiryDate,
                                    Comparator.nullsLast(LocalDate::compareTo));
                };

        Comparator<DrugLotDto> finalComparator = desc ? dtoComparator.reversed() : dtoComparator;
        return (a, b) -> finalComparator.compare((DrugLotDto) a, (DrugLotDto) b);
    }

    /**
     * Tính trạng thái hạn sử dụng dựa trên ngày hết hạn so với ngày tham chiếu.
     */
    public String resolveExpiryStatus(LocalDate expiryDate, LocalDate referenceDate) {
        // Nếu không có ngày hết hạn thì mặc định còn hạn để tránh lỗi null.
        if (expiryDate == null) {
            return "CON_HAN";
        }

        // Xác định ngày mốc so sánh.
        LocalDate baseDate = (referenceDate != null) ? referenceDate : LocalDate.now();
        long daysRemaining = ChronoUnit.DAYS.between(baseDate, expiryDate);

        // Kiểm tra theo thứ tự ưu tiên: đã quá hạn -> sắp hết hạn -> còn hạn.
        if (daysRemaining < 0) {
            return "HET_HAN";
        }
        if (daysRemaining <= EXPIRING_SOON_DAYS) {
            return "SAP_HET_HAN";
        }
        return "CON_HAN";
    }

    /**
     * Chuyển đổi thực thể tồn kho StockLot sang DrugLotDto theo chi nhánh hiện hành và ngày tham chiếu.
     */
    public DrugLotDto toDto(StockLot lot, Long branchId, Long lotId, LocalDate referenceDate) {
        DrugLotDto dto = new DrugLotDto();

        // Gán thông tin định danh lô, mã/tên thuốc và số lô.
        dto.setLotId(lotId);
        dto.setDrugCode(lot.getMedicineId());
        dto.setDrugName(lot.getMedicineName());
        dto.setLotNumber(lot.getLotNumber());
        dto.setExpiryDate(lot.getExpiryDate());

        // Gán chi nhánh hiện hành, số lượng tồn cơ sở và giá vốn mặc định.
        dto.setBranchId(branchId);
        dto.setBranchName("Chi nhánh #" + branchId);
        dto.setQuantityBase((long) lot.getQuantity());
        dto.setCostBase(BigDecimal.ZERO);

        // Tính và gán trạng thái hạn sử dụng hiện tại.
        dto.setExpiryStatus(resolveExpiryStatus(lot.getExpiryDate(), referenceDate));
        return dto;
    }

    /**
     * Cắt danh sách phần tử theo chỉ số trang và kích thước trang, trả về PagedResponse.
     */
    public PagedResponse paginate(List<Object> allItems, int page, int size) {
        // Đảm bảo page không âm và size tối thiểu là 1.
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        long total = (allItems != null) ? allItems.size() : 0;

        // Tính khoảng chỉ số [fromIndex, toIndex) an toàn trong phạm vi danh sách.
        int fromIndex = Math.min(safePage * safeSize, (int) total);
        int toIndex = Math.min(fromIndex + safeSize, (int) total);

        // Sao chép các phần tử thuộc trang hiện tại sang danh sách mới.
        List<Object> pageItems = new ArrayList<>();
        if (allItems != null && fromIndex < toIndex) {
            pageItems.addAll(allItems.subList(fromIndex, toIndex));
        }

        return new PagedResponse(pageItems, safePage, safeSize, total);
    }
}