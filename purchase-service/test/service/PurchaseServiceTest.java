package service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import exception.PurchaseException;
import org.junit.Test;
import play.libs.Json;
import repository.PurchaseRepository;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PurchaseServiceTest {

    @Test
    public void receiptSearchIncludesEntireEndDate() {
        TestPurchaseRepository repository = new TestPurchaseRepository();
        PurchaseService service = new PurchaseService(repository);

        service.searchReceipts("2025-02-10", "2025-02-10", null, null);

        assertEquals(Timestamp.valueOf("2025-02-10 00:00:00"), repository.fromDate);
        assertEquals(Timestamp.valueOf("2025-02-10 23:59:59"), repository.toDate);
    }

    @Test
    public void cancellingReceiptDoesNotValidateExpiryDates() {
        TestPurchaseRepository repository = new TestPurchaseRepository();
        PurchaseRepository.ReceiptRow receipt = new PurchaseRepository.ReceiptRow();
        receipt.id = 7;
        receipt.status = "DRAFT";
        receipt.createdBy = 3;
        repository.receipt = receipt;
        PurchaseService service = new PurchaseService(repository);

        service.transitionReceipt(7, Json.newObject().put("status", "CANCELLED"));

        assertTrue(repository.transitioned);
    }

    @Test
    public void purchaseOrderDetailsCanBeAddedWhileDraft() {
        TestPurchaseRepository repository = new TestPurchaseRepository();
        PurchaseRepository.PurchaseRow order = new PurchaseRepository.PurchaseRow();
        order.id = 5;
        order.status = "DRAFT";
        repository.order = order;
        PurchaseService service = new PurchaseService(repository);
        ObjectNode body = Json.newObject().put("drugId", 2).put("quantity", 4).put("expectedUnitCost", 1.25);

        long detailId = service.addPurchaseOrderDetail(5, body);

        assertEquals(11, detailId);
        assertEquals(4, repository.addedQuantity.intValue());
    }

    @Test
    public void purchaseOrderCannotBeApprovedDirectlyFromDraft() {
        TestPurchaseRepository repository = new TestPurchaseRepository();
        PurchaseRepository.PurchaseRow order = new PurchaseRepository.PurchaseRow();
        order.id = 5;
        order.status = "DRAFT";
        repository.order = order;
        PurchaseService service = new PurchaseService(repository);

        try {
            service.updatePurchaseOrder(5, Json.newObject().put("status", "APPROVED"));
        } catch (PurchaseException e) {
            assertEquals(409, e.getStatus());
            return;
        }
        throw new AssertionError("Expected invalid purchase order transition to be rejected");
    }

    @Test
    public void purchaseOrderDetailsCannotBeAddedAfterSubmission() {
        TestPurchaseRepository repository = new TestPurchaseRepository();
        PurchaseRepository.PurchaseRow order = new PurchaseRepository.PurchaseRow();
        order.id = 5;
        order.status = "SUBMITTED";
        repository.order = order;
        PurchaseService service = new PurchaseService(repository);

        try {
            service.addPurchaseOrderDetail(5, Json.newObject());
        } catch (PurchaseException e) {
            assertEquals(409, e.getStatus());
            return;
        }
        throw new AssertionError("Expected submitted purchase order to reject detail changes");
    }

    @Test
    public void receiptSearchRejectsReversedDateRange() {
        PurchaseService service = new PurchaseService(new TestPurchaseRepository());

        try {
            service.searchReceipts("2025-02-11", "2025-02-10", null, null);
        } catch (PurchaseException e) {
            assertEquals(400, e.getStatus());
            return;
        }
        throw new AssertionError("Expected invalid date range to be rejected");
    }

    private static class TestPurchaseRepository extends PurchaseRepository {
        private Timestamp fromDate;
        private Timestamp toDate;
        private PurchaseRepository.ReceiptRow receipt;
        private PurchaseRepository.PurchaseRow order;
        private java.math.BigDecimal addedQuantity;
        private boolean transitioned;

        private TestPurchaseRepository() {
            super(null);
        }

        @Override
        public java.util.List<PurchaseRepository.ReceiptRow> searchReceipts(
                Timestamp fromDate, Timestamp toDate, Integer supplierId, String status) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            return Collections.emptyList();
        }

        @Override
        public PurchaseRepository.ReceiptRow getReceipt(long id) {
            return receipt;
        }

        @Override
        public java.util.List<PurchaseRepository.ReceiptDetailRow> getReceiptDetails(long receiptId) {
            throw new AssertionError("Expiry validation must not run when cancelling a receipt");
        }

        @Override
        public boolean transitionReceipt(long receiptId, String status, Integer approvedBy) {
            transitioned = true;
            return true;
        }

        @Override
        public PurchaseRepository.PurchaseRow getPurchaseOrder(long id) {
            return order;
        }

        @Override
        public boolean drugExists(int drugId) {
            return true;
        }

        @Override
        public long addPurchaseOrderDetail(long orderId, PurchaseRepository.OrderDetail detail) throws SQLException {
            addedQuantity = detail.quantity;
            return 11;
        }
    }
}
