package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.SalesStockAllocation;
import models.SalesStockLine;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.SalesStockService;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SalesStockController extends Controller {
    private final SalesStockService service;

    @Inject
    public SalesStockController(SalesStockService service) {
        this.service = service;
    }

    public Result deductInvoice(Http.Request httpRequest) {
        JsonNode request = httpRequest.body().asJson();
        if (request == null || !request.isObject()) {
            return badRequest(error("Request body must be a JSON object"));
        }
        try {
            Long invoiceId = positiveLong(request.get("invoiceId"), "invoiceId");
            Long branchId = positiveLong(request.get("branchId"), "branchId");
            JsonNode items = request.get("items");
            if (items == null || !items.isArray()) {
                return badRequest(error("items must be an array"));
            }

            List<SalesStockLine> lines = new ArrayList<>();
            for (JsonNode item : items) {
                if (item == null || !item.isObject()) {
                    return badRequest(error("Each item must be an object"));
                }
                SalesStockLine line = new SalesStockLine();
                line.setInvoiceDetailId(positiveLong(item.get("invoiceDetailId"), "invoiceDetailId"));
                line.setDrugId(positiveLong(item.get("drugId"), "drugId"));
                JsonNode quantity = item.get("quantityBase");
                if (quantity == null || !quantity.isNumber()) {
                    return badRequest(error("quantityBase must be numeric"));
                }
                line.setQuantityBase(quantity.decimalValue());
                lines.add(line);
            }

            List<SalesStockAllocation> allocations = service.deductInvoice(branchId, invoiceId, lines);
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            ArrayNode allocationNodes = response.putArray("allocations");
            for (SalesStockAllocation allocation : allocations) {
                ObjectNode node = allocationNodes.addObject();
                node.put("invoiceDetailId", allocation.getInvoiceDetailId());
                node.put("drugId", allocation.getDrugId());
                node.put("lotId", allocation.getLotId());
                node.put("quantityBase", allocation.getQuantityBase());
                node.put("costBaseAtSale", allocation.getCostBaseAtSale());
            }
            return ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(error(e.getMessage()));
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Insufficient eligible stock")) {
                return status(409, error(e.getMessage()));
            }
            return status(409, error(e.getMessage()));
        } catch (RuntimeException e) {
            return internalServerError(error("Inventory transaction failed"));
        }
    }

    public Result restoreInvoice(Http.Request httpRequest, Long invoiceId) {
        JsonNode request = httpRequest.body().asJson();
        if (request == null || !request.isObject()) {
            return badRequest(error("Request body must be a JSON object"));
        }
        try {
            Long branchId = positiveLong(request.get("branchId"), "branchId");
            service.restoreInvoice(branchId, invoiceId);
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            response.put("restored", true);
            return ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(error(e.getMessage()));
        } catch (IllegalStateException e) {
            return status(409, error(e.getMessage()));
        } catch (RuntimeException e) {
            return internalServerError(error("Inventory restore failed"));
        }
    }

    private Long positiveLong(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private ObjectNode error(String message) {
        ObjectNode response = Json.newObject();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}
