package services;

import models.SalesStockAllocation;
import models.SalesStockLine;
import repositories.SalesStockRepository;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SalesStockService {
    private final SalesStockRepository repository;

    @Inject
    public SalesStockService(SalesStockRepository repository) {
        this.repository = repository;
    }

    public List<SalesStockAllocation> deductInvoice(
            Long branchId, Long invoiceId, List<SalesStockLine> lines) {
        validateIds(branchId, invoiceId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Invoice must contain at least one stock line");
        }
        Set<Long> detailIds = new HashSet<>();
        for (SalesStockLine line : lines) {
            if (line == null || line.getInvoiceDetailId() == null || line.getInvoiceDetailId() <= 0
                    || line.getDrugId() == null || line.getDrugId() <= 0
                    || line.getQuantityBase() == null
                    || line.getQuantityBase().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Each stock line requires valid IDs and a positive quantityBase");
            }
            if (!detailIds.add(line.getInvoiceDetailId())) {
                throw new IllegalArgumentException("Invoice detail IDs must be unique");
            }
        }
        return repository.deductInvoice(branchId, invoiceId, lines);
    }

    public boolean restoreInvoice(Long branchId, Long invoiceId) {
        validateIds(branchId, invoiceId);
        return repository.restoreInvoice(branchId, invoiceId);
    }

    private void validateIds(Long branchId, Long invoiceId) {
        if (branchId == null || branchId <= 0 || branchId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("branchId must be a positive 32-bit integer");
        }
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("invoiceId must be positive");
        }
    }
}
