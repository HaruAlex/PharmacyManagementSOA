package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.DrugGroup;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.ConflictException;
import repositories.DrugGroupRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** REST CRUD endpoints for drug groups. */
public class DrugGroupController extends CatalogControllerSupport {
    private final DrugGroupRepository repository;

    @Inject
    public DrugGroupController(DrugGroupRepository repository) {
        this.repository = repository;
    }

    public Result list() {
        try {
            List<DrugGroup> groups = repository.findAll();
            return itemList(groups);
        } catch (Exception e) {
            return internalError("list drug groups", e);
        }
    }

    public Result roots() {
        try {
            List<DrugGroup> groups = repository.findRootGroups();
            return itemList(groups);
        } catch (Exception e) {
            return internalError("list root drug groups", e);
        }
    }

    public Result children(Long id) {
        try {
            if (!repository.findById(id).isPresent()) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug group not found"));
            }
            List<DrugGroup> groups = repository.findChildren(id);
            ObjectNode response = Json.newObject();
            response.put("parentId", id);
            response.set("items", Json.toJson(groups));
            response.put("total", groups.size());
            return ok(response);
        } catch (Exception e) {
            return internalError("list child drug groups", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<DrugGroup> group = repository.findById(id);
            return group.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Drug group not found")));
        } catch (Exception e) {
            return internalError("get drug group", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            DrugGroup group = new DrugGroup();
            group.setCode(ApiSupport.requiredText(body, "code"));
            group.setName(ApiSupport.requiredText(body, "name"));
            group.setDescription(ApiSupport.optionalText(body, "description"));
            group.setDisplayOrder(ApiSupport.optionalNonNegativeInt(body, "displayOrder", 0));
            if (body.has("parentId") && !body.get("parentId").isNull()) {
                group.setParentId(ApiSupport.requiredPositiveLong(body, "parentId"));
            }
            if (repository.findByCode(group.getCode()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_CODE", "Drug group code already exists"));
            }
            if (repository.findByName(group.getName()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_NAME", "Drug group name already exists"));
            }
            return created(Json.toJson(repository.create(group)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create drug group", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            DrugGroup group = repository.findById(id).orElse(null);
            if (group == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug group not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("name")) group.setName(ApiSupport.requiredText(body, "name"));
            if (body.has("description")) group.setDescription(ApiSupport.optionalText(body, "description"));
            if (body.has("displayOrder")) {
                group.setDisplayOrder(ApiSupport.optionalNonNegativeInt(body, "displayOrder", 0));
            }
            Optional<DrugGroup> duplicate = repository.findByName(group.getName());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                return status(409, ApiSupport.error("DUPLICATE_NAME", "Drug group name already exists"));
            }
            if (!repository.update(group)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug group not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(group)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("update drug group", e);
        }
    }

    public Result delete(Long id) {
        try {
            if (!repository.findById(id).isPresent()) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug group not found"));
            }
            if (!repository.softDelete(id)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Drug group not found"));
            }
            ObjectNode response = Json.newObject();
            response.put("message", "Drug group deactivated");
            return ok(response);
        } catch (ConflictException e) {
            return conflict(e);
        } catch (Exception e) {
            return internalError("delete drug group", e);
        }
    }
}
