package models;

import java.math.BigDecimal;

/** Per-drug unit conversion and optional sale-price configuration. */
public class DrugUnitConversion {
    private Long unitId;
    private String unitCode;
    private String unitName;
    private BigDecimal conversionToBase;
    private boolean saleUnit;
    private BigDecimal salePrice;
    private boolean defaultUnit;

    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public BigDecimal getConversionToBase() { return conversionToBase; }
    public void setConversionToBase(BigDecimal conversionToBase) { this.conversionToBase = conversionToBase; }
    public boolean isSaleUnit() { return saleUnit; }
    public void setSaleUnit(boolean saleUnit) { this.saleUnit = saleUnit; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public boolean isDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(boolean defaultUnit) { this.defaultUnit = defaultUnit; }
}
