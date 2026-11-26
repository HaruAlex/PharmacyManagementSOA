package repositories;

import models.MedicineStock;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository truy xuat du lieu ton kho thuoc cho BE-28.
 * Dung du lieu mau trong bo nho (khong can SQL Server) de chay API va unit test.
 */
@Singleton
public class InventoryRepository {

    // Danh sach thuoc mau phuc vu test API va unit test
    private final List<MedicineStock> sampleStocks;

    @Inject
    public InventoryRepository() {
        this.sampleStocks = List.of(
                new MedicineStock("MED-01", "Paracetamol 500mg", "Hop", 15, 20, Instant.parse("2026-11-20T08:00:00Z")),
                new MedicineStock("MED-02", "Amoxicillin 500mg", "Vi", 120, 30, Instant.parse("2026-11-21T09:30:00Z")),
                new MedicineStock("MED-03", "Vitamin C 1000mg", "Chai", 8, 15, Instant.parse("2026-11-22T14:15:00Z")),
                new MedicineStock("MED-04", "Omeprazole 20mg", "Hop", 65, 25, Instant.parse("2026-11-23T10:00:00Z"))
        );
    }

    /**
     * Lay danh sach ton kho, loc theo keyword (ma hoac ten thuoc) va sap xep theo ten.
     */
    public List<MedicineStock> findAll(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        return sampleStocks.stream()
                .filter(stock -> normalizedKeyword.isEmpty()
                        || (stock.getMedicineId() != null && stock.getMedicineId().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                        || (stock.getMedicineName() != null && stock.getMedicineName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
                .sorted(Comparator.comparing(MedicineStock::getMedicineName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    /**
     * Tim chi tiet ton kho theo ma thuoc (khong phan biet hoa thuong).
     */
    public Optional<MedicineStock> findByMedicineId(String medicineId) {
        if (medicineId == null || medicineId.trim().isEmpty()) {
            return Optional.empty();
        }
        String targetId = medicineId.trim();

        return sampleStocks.stream()
                .filter(stock -> stock.getMedicineId() != null && targetId.equalsIgnoreCase(stock.getMedicineId()))
                .findFirst();
    }
}