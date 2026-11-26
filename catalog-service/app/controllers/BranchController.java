package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Branch;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.BranchRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

/** REST endpoints for listing, creating, updating, and changing branch status. */
public class BranchController extends CatalogControllerSupport {
    private final BranchRepository repository;

    @Inject
    public BranchController(BranchRepository repository) {
        this.repository = repository;
    }

    public Result list() {
        try {
            List<Branch> branches = repository.findAll();
            return itemList(branches);
        } catch (Exception e) {
            return internalError("list branches", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<Branch> branch = repository.findById(id);
            return branch.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Branch not found")));
        } catch (Exception e) {
            return internalError("get branch", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            Branch branch = new Branch();
            branch.setCode(ApiSupport.requiredText(body, "code"));
            branch.setName(ApiSupport.requiredText(body, "name"));
            branch.setType(validBranchType(ApiSupport.requiredText(body, "type")));
            branch.setAddress(ApiSupport.optionalText(body, "address"));
            branch.setPhone(ApiSupport.optionalText(body, "phone"));
            branch.setLicenseNumber(ApiSupport.optionalText(body, "licenseNumber"));
            branch.setLicenseExpiry(ApiSupport.optionalDate(body, "licenseExpiry"));
            if (repository.findByCode(branch.getCode()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_CODE", "Branch code already exists"));
            }
            return created(Json.toJson(repository.create(branch)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create branch", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            Branch branch = repository.findById(id).orElse(null);
            if (branch == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Branch not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("name")) branch.setName(ApiSupport.requiredText(body, "name"));
            if (body.has("address")) branch.setAddress(ApiSupport.optionalText(body, "address"));
            if (body.has("phone")) branch.setPhone(ApiSupport.optionalText(body, "phone"));
            if (body.has("type")) branch.setType(validBranchType(ApiSupport.requiredText(body, "type")));
            if (body.has("licenseNumber")) {
                branch.setLicenseNumber(ApiSupport.optionalText(body, "licenseNumber"));
            }
            if (body.has("licenseExpiry")) {
                branch.setLicenseExpiry(ApiSupport.optionalDate(body, "licenseExpiry"));
            }
            if (!repository.update(branch)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Branch not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(branch)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("update branch", e);
        }
    }

    public Result changeStatus(Long id, Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            String status = ApiSupport.requiredText(body, "status");
            if (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status)) {
                throw new IllegalArgumentException("status must be ACTIVE or INACTIVE");
            }
            if (!repository.setActive(id, "ACTIVE".equalsIgnoreCase(status))) {
                return notFound(ApiSupport.error("NOT_FOUND", "Branch not found"));
            }
            return get(id);
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("change branch status", e);
        }
    }

    private String validBranchType(String type) {
        if ("STORE".equalsIgnoreCase(type) || "PHARMACY".equalsIgnoreCase(type)) {
            return "STORE";
        }
        if ("WAREHOUSE".equalsIgnoreCase(type) || "CENTRAL_WAREHOUSE".equalsIgnoreCase(type)) {
            return "WAREHOUSE";
        }
        throw new IllegalArgumentException("type must be STORE or WAREHOUSE");
    }
}
