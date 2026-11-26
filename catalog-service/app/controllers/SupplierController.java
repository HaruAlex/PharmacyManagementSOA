package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Supplier;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.SupplierRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

/** REST CRUD endpoints for suppliers. Deletion deactivates the supplier. */
public class SupplierController extends CatalogControllerSupport {
    private final SupplierRepository repository;

    @Inject
    public SupplierController(SupplierRepository repository) {
        this.repository = repository;
    }

    public Result list(Http.Request request) {
        try {
            String query = request.getQueryString("q");
            int page = page(request);
            int size = size(request);
            List<Supplier> suppliers = repository.search(query, page, size);
            com.fasterxml.jackson.databind.node.ObjectNode response = Json.newObject();
            response.set("items", Json.toJson(suppliers));
            response.put("page", page);
            response.put("size", size);
            response.put("total", repository.count(query));
            return ok(response);
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("list suppliers", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<Supplier> supplier = repository.findById(id);
            return supplier.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Supplier not found")));
        } catch (Exception e) {
            return internalError("get supplier", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            Supplier supplier = new Supplier();
            supplier.setCode(ApiSupport.requiredText(body, "code"));
            supplier.setName(ApiSupport.requiredText(body, "name"));
            supplier.setPhone(ApiSupport.optionalText(body, "phone"));
            supplier.setEmail(ApiSupport.optionalText(body, "email"));
            supplier.setAddress(ApiSupport.optionalText(body, "address"));
            supplier.setTaxCode(ApiSupport.optionalText(body, "taxCode"));
            supplier.setContactPerson(ApiSupport.optionalText(body, "contactPerson"));
            supplier.setContactPhone(ApiSupport.optionalText(body, "contactPhone"));
            supplier.setPaymentTermDays(ApiSupport.optionalNonNegativeInt(body, "paymentTermDays", 0));
            if (repository.findByCode(supplier.getCode()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_CODE", "Supplier code already exists"));
            }
            return created(Json.toJson(repository.create(supplier)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create supplier", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            Supplier supplier = repository.findById(id).orElse(null);
            if (supplier == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Supplier not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("name")) supplier.setName(ApiSupport.requiredText(body, "name"));
            if (body.has("phone")) supplier.setPhone(ApiSupport.optionalText(body, "phone"));
            if (body.has("email")) supplier.setEmail(ApiSupport.optionalText(body, "email"));
            if (body.has("address")) supplier.setAddress(ApiSupport.optionalText(body, "address"));
            if (body.has("taxCode")) supplier.setTaxCode(ApiSupport.optionalText(body, "taxCode"));
            if (body.has("contactPerson")) {
                supplier.setContactPerson(ApiSupport.optionalText(body, "contactPerson"));
            }
            if (body.has("contactPhone")) {
                supplier.setContactPhone(ApiSupport.optionalText(body, "contactPhone"));
            }
            if (body.has("paymentTermDays")) {
                supplier.setPaymentTermDays(ApiSupport.optionalNonNegativeInt(body, "paymentTermDays", 0));
            }
            if (!repository.update(supplier)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Supplier not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(supplier)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("update supplier", e);
        }
    }

    public Result delete(Long id) {
        try {
            if (!repository.softDelete(id)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Supplier not found"));
            }
            return ok(ApiSupport.error("DELETED", "Supplier deactivated"));
        } catch (Exception e) {
            return internalError("delete supplier", e);
        }
    }
}
