package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Drug;
import models.DrugGroup;
import models.DrugUnitConversion;
import models.Manufacturer;
import models.Unit;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.DrugGroupRepository;
import repositories.DrugRepository;
import repositories.ManufacturerRepository;
import repositories.UnitRepository;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** REST CRUD and search endpoints for drugs. */
public class DrugController extends CatalogControllerSupport {
    private final DrugRepository repository;
    private final DrugGroupRepository groupRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final UnitRepository unitRepository;

    @Inject
    public DrugController(DrugRepository repository,
                          DrugGroupRepository groupRepository,
                          ManufacturerRepository manufacturerRepository,
                          UnitRepository unitRepository) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.manufacturerRepository = manufacturerRepository;
        this.unitRepository = unitRepository;
    }

    public Result search(Http.Request request) {
        try {
            String query = request.getQueryString("q");
            String type = normalizeType(request.getQueryString("type"));
            Long groupId = optionalPositiveQueryLong(request.getQueryString("groupId"), "groupId");
            int page = page(request);
            int size = size(request);
            List<Drug> drugs = repository.search(query, groupId, type, page, size);
            com.fasterxml.jackson.databind.node.ObjectNode response = Json.newObject();
            response.set("items", Json.toJson(drugs));
            response.put("page", page);
            response.put("size", size);
            response.put("total", repository.count(query, groupId, type));
            return ok(response);
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("search drugs", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<Drug> drug = repository.findById(id);
            drug.ifPresent(value -> value.setUnits(repository.findConversions(id)));
            return drug.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Drug not found")));
        } catch (Exception e) {
            return internalError("get drug", e);
        }
    }

    public Result getByBarcode(String code) {
        try {
            Optional<Drug> drug = repository.findByBarcode(code);
            return drug.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Drug barcode not found")));
        } catch (Exception e) {
            return internalError("find drug by barcode", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            Drug drug = new Drug();
            drug.setCode(ApiSupport.requiredText(body, "code"));
            drug.setName(ApiSupport.requiredText(body, "name"));
            drug.setActiveIngredient(ApiSupport.optionalText(body, "activeIngredient"));
            drug.setConcentration(ApiSupport.optionalText(body, "concentration"));
            drug.setDosageForm(ApiSupport.optionalText(body, "dosageForm"));
            drug.setStorageCondition(ApiSupport.optionalText(body, "storageCondition"));
            drug.setBarcode(ApiSupport.optionalText(body, "barcode"));
            drug.setGroupId(ApiSupport.requiredPositiveLong(body, "groupId"));
            drug.setManufacturerId(ApiSupport.requiredPositiveLong(body, "manufacturerId"));
            drug.setPrimaryUnitId(ApiSupport.requiredPositiveLong(body, "primaryUnitId"));
            drug.setRegistrationNumber(ApiSupport.optionalText(body, "registrationNumber"));
            drug.setDescription(ApiSupport.optionalText(body, "description"));
            setClassification(drug, body, false);

            if (repository.findByCode(drug.getCode()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_CODE", "Drug code already exists"));
            }
            if (drug.getBarcode() != null && repository.findByBarcodeIncludingInactive(drug.getBarcode()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_BARCODE", "Drug barcode already exists"));
            }
            Result referenceError = validateReferences(drug);
            if (referenceError != null) {
                return referenceError;
            }
            return created(Json.toJson(repository.create(drug)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create drug", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            Drug drug = repository.findById(id).orElse(null);
            if (drug == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("name")) drug.setName(ApiSupport.requiredText(body, "name"));
            if (body.has("activeIngredient")) {
                drug.setActiveIngredient(ApiSupport.optionalText(body, "activeIngredient"));
            }
            if (body.has("concentration")) {
                drug.setConcentration(ApiSupport.optionalText(body, "concentration"));
            }
            if (body.has("dosageForm")) {
                drug.setDosageForm(ApiSupport.optionalText(body, "dosageForm"));
            }
            if (body.has("storageCondition")) {
                drug.setStorageCondition(ApiSupport.optionalText(body, "storageCondition"));
            }
            if (body.has("barcode")) drug.setBarcode(ApiSupport.optionalText(body, "barcode"));
            if (body.has("groupId")) drug.setGroupId(ApiSupport.requiredPositiveLong(body, "groupId"));
            if (body.has("manufacturerId")) {
                drug.setManufacturerId(ApiSupport.requiredPositiveLong(body, "manufacturerId"));
            }
            if (body.has("primaryUnitId")) {
                drug.setPrimaryUnitId(ApiSupport.requiredPositiveLong(body, "primaryUnitId"));
            }
            if (body.has("registrationNumber")) {
                drug.setRegistrationNumber(ApiSupport.optionalText(body, "registrationNumber"));
            }
            if (body.has("description")) drug.setDescription(ApiSupport.optionalText(body, "description"));
            setClassification(drug, body, true);

            Optional<Drug> duplicateBarcode = drug.getBarcode() == null
                    ? Optional.empty()
                    : repository.findByBarcodeIncludingInactive(drug.getBarcode());
            if (duplicateBarcode.isPresent() && !duplicateBarcode.get().getId().equals(id)) {
                return status(409, ApiSupport.error("DUPLICATE_BARCODE", "Drug barcode already exists"));
            }
            Result referenceError = validateReferences(drug);
            if (referenceError != null) {
                return referenceError;
            }
            if (!repository.update(drug)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(drug)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("update drug", e);
        }
    }

    public Result discontinue(Long id) {
        try {
            if (!repository.findById(id).isPresent()) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug not found"));
            }
            if (!repository.discontinue(id)) {
                return status(409, ApiSupport.error("ALREADY_DISCONTINUED", "Drug is already discontinued"));
            }
            return ok(ApiSupport.error("DISCONTINUED", "Drug discontinued"));
        } catch (Exception e) {
            return internalError("discontinue drug", e);
        }
    }

    public Result setUnits(Long id, Http.Request request) {
        try {
            Drug drug = repository.findById(id).orElse(null);
            if (drug == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            JsonNode unitsNode = body.get("units");
            if (unitsNode == null || !unitsNode.isArray()) {
                throw new IllegalArgumentException("units must be a JSON array");
            }

            List<DrugUnitConversion> conversions = new ArrayList<>();
            Set<Long> unitIds = new HashSet<>();
            boolean hasDefault = false;
            for (JsonNode unitNode : unitsNode) {
                if (!unitNode.isObject()) {
                    throw new IllegalArgumentException("each units item must be a JSON object");
                }
                long unitId = ApiSupport.requiredPositiveLong(unitNode, "unitId");
                if (!unitIds.add(unitId)) {
                    throw new IllegalArgumentException("units must not contain duplicate unitId values");
                }
                if (unitId == drug.getPrimaryUnitId()) {
                    throw new IllegalArgumentException("primaryUnitId is implicit and must not be repeated in units");
                }
                Optional<Unit> unit = unitRepository.findById(unitId);
                if (!unit.isPresent() || !"ACTIVE".equals(unit.get().getStatus())) {
                    throw new IllegalArgumentException("unitId must reference an active unit");
                }

                JsonNode factorNode = unitNode.get("conversionToBase");
                if (factorNode == null || !factorNode.isNumber() || factorNode.decimalValue().signum() <= 0) {
                    throw new IllegalArgumentException("conversionToBase must be a positive number");
                }
                BigDecimal factor = exactDecimal(factorNode.decimalValue(), 4, "conversionToBase");
                DrugUnitConversion conversion = new DrugUnitConversion();
                conversion.setUnitId(unitId);
                conversion.setConversionToBase(factor);
                conversion.setSaleUnit(ApiSupport.optionalBoolean(unitNode, "isSaleUnit", true));
                conversion.setDefaultUnit(ApiSupport.optionalBoolean(unitNode, "isDefault", false));
                conversion.setSalePrice(optionalNonNegativeDecimal(unitNode, "salePrice"));
                if (conversion.isDefaultUnit()) {
                    if (hasDefault) {
                        throw new IllegalArgumentException("only one conversion unit can be the default");
                    }
                    if (!conversion.isSaleUnit()) {
                        throw new IllegalArgumentException("the default conversion unit must be a sale unit");
                    }
                    hasDefault = true;
                }
                conversions.add(conversion);
            }

            repository.replaceConversions(id, conversions);
            drug.setUnits(repository.findConversions(id));
            return ok(Json.toJson(drug));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("set drug units", e);
        }
    }

    private Result validateReferences(Drug drug) {
        Optional<DrugGroup> group = groupRepository.findById(drug.getGroupId());
        if (!group.isPresent() || !"ACTIVE".equals(group.get().getStatus())) {
            return badRequest(ApiSupport.error("INVALID_GROUP", "groupId must reference an active drug group"));
        }
        Optional<Manufacturer> manufacturer = manufacturerRepository.findById(drug.getManufacturerId());
        if (!manufacturer.isPresent() || !"ACTIVE".equals(manufacturer.get().getStatus())) {
            return badRequest(ApiSupport.error("INVALID_MANUFACTURER",
                    "manufacturerId must reference an active manufacturer"));
        }
        Optional<Unit> unit = unitRepository.findById(drug.getPrimaryUnitId());
        if (!unit.isPresent() || !"ACTIVE".equals(unit.get().getStatus())) {
            return badRequest(ApiSupport.error("INVALID_UNIT", "primaryUnitId must reference an active unit"));
        }
        return null;
    }

    private BigDecimal optionalNonNegativeDecimal(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() || value.decimalValue().signum() < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative number or null");
        }
        return exactDecimal(value.decimalValue(), 2, field);
    }

    private BigDecimal exactDecimal(BigDecimal value, int scale, String field) {
        try {
            BigDecimal normalized = value.setScale(scale, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 18) {
                throw new IllegalArgumentException(field + " exceeds the supported precision");
            }
            return normalized;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(field + " supports at most " + scale + " decimal places");
        }
    }

    private void setClassification(Drug drug, JsonNode body, boolean updating) {
        boolean hasType = body.has("drugType");
        boolean hasPrescription = body.has("prescriptionRequired");
        boolean hasControlled = body.has("controlledDrug");
        if (hasType && (hasPrescription || hasControlled)) {
            throw new IllegalArgumentException("Use drugType or the classification flags, not both");
        }
        if (hasType) {
            String type = normalizeType(ApiSupport.requiredText(body, "drugType"));
            drug.setDrugType(type);
            drug.setPrescriptionRequired("RX".equals(type));
            drug.setControlledDrug("CONTROLLED".equals(type));
        } else if (hasPrescription || hasControlled || !updating) {
            drug.setPrescriptionRequired(
                    ApiSupport.optionalBoolean(body, "prescriptionRequired", updating && drug.isPrescriptionRequired()));
            drug.setControlledDrug(
                    ApiSupport.optionalBoolean(body, "controlledDrug", updating && drug.isControlledDrug()));
            drug.setDrugType(drug.isControlledDrug() ? "CONTROLLED"
                    : drug.isPrescriptionRequired() ? "RX" : "OTC");
        }
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("PRESCRIPTION".equals(normalized)) {
            return "RX";
        }
        if ("OTC".equals(normalized) || "RX".equals(normalized) || "CONTROLLED".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("type must be OTC, RX, or CONTROLLED");
    }

    private Long optionalPositiveQueryLong(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new IllegalArgumentException(field + " must be a positive integer");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
    }
}
