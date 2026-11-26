package services;

import models.SalesStockAllocation;
import models.SalesStockLine;
import org.junit.Before;
import org.junit.Test;
import repositories.SalesStockRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

public class SalesStockServiceTest {
    private SalesStockRepository repository;
    private SalesStockService service;

    @Before
    public void setUp() {
        repository = mock(SalesStockRepository.class);
        service = new SalesStockService(repository);
    }

    @Test
    public void deductInvoice_validatesAndReturnsRepositoryAllocations() {
        SalesStockLine line = line(12L, 3L, "4.50");
        List<SalesStockAllocation> allocations = List.of(
                new SalesStockAllocation(12L, 3L, 8L, new BigDecimal("4.50"),
                        new BigDecimal("2.00")));
        when(repository.deductInvoice(2L, 40L, List.of(line))).thenReturn(allocations);

        assertSame(allocations, service.deductInvoice(2L, 40L, List.of(line)));
        verify(repository).deductInvoice(2L, 40L, List.of(line));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deductInvoice_rejectsDuplicateInvoiceDetailIdsBeforeDatabaseCall() {
        service.deductInvoice(2L, 40L, List.of(
                line(12L, 3L, "1"),
                line(12L, 4L, "2")));
        verifyNoInteractions(repository);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deductInvoice_rejectsBranchIdsOutsideCanonicalSchemaRange() {
        service.deductInvoice((long) Integer.MAX_VALUE + 1L, 40L,
                List.of(line(12L, 3L, "1")));
        verifyNoInteractions(repository);
    }

    private SalesStockLine line(Long detailId, Long drugId, String quantity) {
        SalesStockLine line = new SalesStockLine();
        line.setInvoiceDetailId(detailId);
        line.setDrugId(drugId);
        line.setQuantityBase(new BigDecimal(quantity));
        return line;
    }
}
