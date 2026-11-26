package repositories;

import models.StockLot;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Repository truy xuất dữ liệu tồn kho theo từng lô thuốc (Stock By Lot) cho BE-28.
 * Hiện sử dụng dữ liệu mẫu trong bộ nhớ khi chưa kết nối SQL Server thật.
 */
@Singleton
public class InventoryRepository {

    // Danh sách các lô thuốc mẫu phục vụ chạy API và unit test
    private final List<StockLot> sampleLots;

    @Inject
    public InventoryRepository() {
        this.sampleLots = List.of(
                new StockLot(
                        "LOT-PARA-01",
                        "MED-01",
                        "Paracetamol 500mg",
                        "Hop",
                        15,
                        20,
                        LocalDate.parse("2027-06-30"),
                        Instant.parse("2026-11-20T08:00:00Z")
                ),
                new StockLot(
                        "LOT-PARA-02",
                        "MED-01",
                        "Paracetamol 500mg",
                        "Hop",
                        45,
                        20,
                        LocalDate.parse("2027-12-15"),
                        Instant.parse("2026-11-22T09:00:00Z")
                ),
                new StockLot(
                        "LOT-AMOX-01",
                        "MED-02",
                        "Amoxicillin 500mg",
                        "Vi",
                        120,
                        30,
                        LocalDate.parse("2027-09-01"),
                        Instant.parse("2026-11-21T09:30:00Z")
                ),
                new StockLot(
                        "LOT-VITC-01",
                        "MED-03",
                        "Vitamin C 1000mg",
                        "Chai",
                        8,
                        15,
                        LocalDate.parse("2026-10-01"),
                        Instant.parse("2026-11-22T14:15:00Z")
                ),
                new StockLot(
                        "LOT-OMEP-01",
                        "MED-04",
                        "Omeprazole 20mg",
                        "Hop",
                        65,
                        25,
                        LocalDate.parse("2028-03-20"),
                        Instant.parse("2026-11-23T10:00:00Z")
                )
        );
    }

    /**
     * Lấy danh sách tồn kho theo lô, hỗ trợ lọc theo keyword (mã lô, mã thuốc hoặc tên thuốc)
     * và sắp xếp ưu tiên hạn sử dụng gần nhất rồi tới tên thuốc.
     */
    public List<StockLot> findAllLots(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        return sampleLots.stream()
                .filter(lot -> normalizedKeyword.isEmpty()
                        || (lot.getLotNumber() != null
                        && lot.getLotNumber().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                        || (lot.getMedicineId() != null
                        && lot.getMedicineId().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                        || (lot.getMedicineName() != null
                        && lot.getMedicineName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
                .sorted(Comparator.comparing(
                        StockLot::getExpiryDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(
                        StockLot::getMedicineName,
                        Comparator.nullsLast(String::compareToIgnoreCase)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Lấy toàn bộ các lô tồn kho thuộc một mã thuốc cụ thể (không phân biệt chữ hoa/thường).
     */
    public List<StockLot> findLotsByMedicineId(String medicineId) {
        if (medicineId == null || medicineId.trim().isEmpty()) {
            return List.of();
        }
        String targetMedicineId = medicineId.trim();

        return sampleLots.stream()
                .filter(lot -> lot.getMedicineId() != null
                        && targetMedicineId.equalsIgnoreCase(lot.getMedicineId()))
                .sorted(Comparator.comparing(
                        StockLot::getExpiryDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .collect(Collectors.toList());
    }
}