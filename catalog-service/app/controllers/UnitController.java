package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Unit;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.ConflictException;
import repositories.UnitRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

/** REST CRUD endpoints for units of measure. */
public class UnitController extends CatalogControllerSupport {
    private final UnitRepository repository;

    @Inject
    public UnitController(UnitRepository repository) {
        this.repository = repository;
    }

    public Result list() {
        try {
            List<Unit> units = repository.findAll();
            return itemList(units);
        } catch (Exception e) {
            return internalError("list units", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<Unit> unit = repository.findById(id);
            return unit.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Unit not found")));
        } catch (Exception e) {
            return internalError("get unit", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            Unit unit = new Unit();
            unit.setCode(ApiSupport.requiredText(body, "code"));
            unit.setName(ApiSupport.requiredText(body, "name"));
            unit.setDescription(ApiSupport.optionalText(body, "description"));
            if (repository.findByCode(unit.getCode()).isPresent()
                    || repository.findByName(unit.getName()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_UNIT", "Unit code or name already exists"));
            }
            return created(Json.toJson(repository.create(unit)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create unit", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            Unit unit = repository.findById(id).orElse(null);
            if (unit == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Unit not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("code")) unit.setCode(ApiSupport.requiredText(body, "code"));
            if (body.has("name")) unit.setName(ApiSupport.requiredText(body, "name"));
            if (body.has("description")) unit.setDescription(ApiSupport.optionalText(body, "description"));
            Optional<Unit> duplicateCode = repository.findByCode(unit.getCode());
            Optional<Unit> duplicateName = repository.findByName(unit.getName());
            if ((duplicateCode.isPresent() && !duplicateCode.get().getId().equals(id))
                    || (duplicateName.isPresent() && !duplicateName.get().getId().equals(id))) {
                return status(409, ApiSupport.error("DUPLICATE_UNIT", "Unit code or name already exists"));
            }
            if (!repository.update(unit)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Unit not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(unit)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("update unit", e);
        }
    }

    public Result delete(Long id) {
        try {
            if (!repository.findById(id).isPresent()) {
                return notFound(ApiSupport.error("NOT_FOUND", "Unit not found"));
            }
            if (!repository.delete(id)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Unit not found"));
            }
            com.fasterxml.jackson.databind.node.ObjectNode response = Json.newObject();
            response.put("message", "Unit deactivated");
            return ok(response);
        } catch (ConflictException e) {
            return conflict(e);
        } catch (Exception e) {
            return internalError("delete unit", e);
        }
    }
}
