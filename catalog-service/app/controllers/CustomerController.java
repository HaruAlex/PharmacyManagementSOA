package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Customer;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.ConflictException;
import repositories.CustomerRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** REST CRUD endpoints and name/phone lookup for customers. */
public class CustomerController extends CatalogControllerSupport {
    private final CustomerRepository repository;

    @Inject
    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    public Result search(Http.Request request) {
        try {
            String query = request.getQueryString("q");
            String phone = request.getQueryString("phone");
            int page = page(request);
            int size = size(request);
            List<Customer> customers = repository.search(query, phone, page, size);
            com.fasterxml.jackson.databind.node.ObjectNode response = Json.newObject();
            response.set("items", Json.toJson(customers));
            response.put("page", page);
            response.put("size", size);
            response.put("total", repository.count(query, phone));
            return ok(response);
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("search customers", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<Customer> customer = repository.findById(id);
            return customer.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Customer not found")));
        } catch (Exception e) {
            return internalError("get customer", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            Customer customer = new Customer();
            String code = ApiSupport.optionalText(body, "code");
            customer.setCode(code == null ? generateCustomerCode() : code);
            customer.setName(readName(body));
            customer.setPhone(ApiSupport.requiredText(body, "phone"));
            customer.setEmail(ApiSupport.optionalText(body, "email"));
            customer.setAllergies(ApiSupport.optionalText(body, "allergies"));
            customer.setLoyaltyPoints(ApiSupport.optionalNonNegativeInt(body, "loyaltyPoints", 0));
            if (repository.findByCode(customer.getCode()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_CODE", "Customer code already exists"));
            }
            if (repository.findByPhone(customer.getPhone()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_PHONE", "Customer phone already exists"));
            }
            return created(Json.toJson(repository.create(customer)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create customer", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            Customer customer = repository.findById(id).orElse(null);
            if (customer == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Customer not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("name") || body.has("fullName")) customer.setName(readName(body));
            if (body.has("phone")) {
                String phone = ApiSupport.requiredText(body, "phone");
                Optional<Customer> duplicate = repository.findByPhone(phone);
                if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                    return status(409, ApiSupport.error("DUPLICATE_PHONE", "Customer phone already exists"));
                }
                customer.setPhone(phone);
            }
            if (body.has("email")) customer.setEmail(ApiSupport.optionalText(body, "email"));
            if (body.has("allergies")) customer.setAllergies(ApiSupport.optionalText(body, "allergies"));
            if (body.has("loyaltyPoints")) {
                customer.setLoyaltyPoints(ApiSupport.optionalNonNegativeInt(body, "loyaltyPoints", 0));
            }
            if (!repository.update(customer)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Customer not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(customer)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("update customer", e);
        }
    }

    public Result delete(Long id) {
        try {
            if (!repository.findById(id).isPresent()) {
                return notFound(ApiSupport.error("NOT_FOUND", "Customer not found"));
            }
            if (!repository.delete(id)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Customer not found"));
            }
            com.fasterxml.jackson.databind.node.ObjectNode response = Json.newObject();
            response.put("message", "Customer deactivated");
            return ok(response);
        } catch (ConflictException e) {
            return conflict(e);
        } catch (Exception e) {
            return internalError("delete customer", e);
        }
    }

    private String readName(JsonNode body) {
        if (body.has("name")) {
            return ApiSupport.requiredText(body, "name");
        }
        return ApiSupport.requiredText(body, "fullName");
    }

    private String generateCustomerCode() {
        return "KH" + UUID.randomUUID().toString().replace("-", "").substring(0, 20)
                .toUpperCase(java.util.Locale.ROOT);
    }
}
