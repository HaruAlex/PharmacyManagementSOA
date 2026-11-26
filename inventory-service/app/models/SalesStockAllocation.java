package models;

import java.math.BigDecimal;

public class SalesStockAllocation {
    private final Long invoiceDetailId;
    private final Long drugId;
    private final Long lotId;
    private final BigDecimal quantityBase;
    private final BigDecimal costBaseAtSale;

    public SalesStockAllocation(
            Long invoiceDetailId, Long drugId, Long lotId,
            BigDecimal quantityBase, BigDecimal costBaseAtSale) {
        this.invoiceDetailId = invoiceDetailId;
        this.drugId = drugId;
        this.lotId = lotId;
        this.quantityBase = quantityBase;
        this.costBaseAtSale = costBaseAtSale;
    }

    public Long getInvoiceDetailId() {
        return invoiceDetailId;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getDrugId() {
        return drugId;
    }

    public BigDecimal getQuantityBase() {
        return quantityBase;
    }

    public BigDecimal getCostBaseAtSale() {
        return costBaseAtSale;
    }
}
