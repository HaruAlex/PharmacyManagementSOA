package models;

import java.util.ArrayList;
import java.util.List;

/** Drug fields exposed by the Catalog API. */
public class Drug {
    private Long id;
    private String code;
    private String barcode;
    private String name;
    private String activeIngredient;
    private String concentration;
    private String dosageForm;
    private String storageCondition;
    private String drugType;
    private Long groupId;
    private Long manufacturerId;
    private Long primaryUnitId;
    private boolean prescriptionRequired;
    private boolean controlledDrug;
    private String registrationNumber;
    private String description;
    private String status;
    private List<DrugUnitConversion> units = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getActiveIngredient() { return activeIngredient; }
    public void setActiveIngredient(String activeIngredient) { this.activeIngredient = activeIngredient; }
    public String getConcentration() { return concentration; }
    public void setConcentration(String concentration) { this.concentration = concentration; }
    public String getDosageForm() { return dosageForm; }
    public void setDosageForm(String dosageForm) { this.dosageForm = dosageForm; }
    public String getStorageCondition() { return storageCondition; }
    public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getManufacturerId() { return manufacturerId; }
    public void setManufacturerId(Long manufacturerId) { this.manufacturerId = manufacturerId; }
    public Long getPrimaryUnitId() { return primaryUnitId; }
    public void setPrimaryUnitId(Long primaryUnitId) { this.primaryUnitId = primaryUnitId; }
    public boolean isPrescriptionRequired() { return prescriptionRequired; }
    public void setPrescriptionRequired(boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }
    public boolean isControlledDrug() { return controlledDrug; }
    public void setControlledDrug(boolean controlledDrug) { this.controlledDrug = controlledDrug; }
    public String getDrugType() {
        if (drugType != null) return drugType;
        if (controlledDrug) return "CONTROLLED";
        return prescriptionRequired ? "RX" : "OTC";
    }
    public void setDrugType(String drugType) { this.drugType = drugType; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<DrugUnitConversion> getUnits() { return units; }
    public void setUnits(List<DrugUnitConversion> units) { this.units = units; }
}
