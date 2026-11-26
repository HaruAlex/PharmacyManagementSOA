package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Manufacturer;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.ConflictException;
import repositories.ManufacturerRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

/** REST CRUD endpoints for drug manufacturers. */
public class ManufacturerController extends CatalogControllerSupport {
    private final ManufacturerRepository repository;

    @Inject
    public ManufacturerController(ManufacturerRepository repository) {
        this.repository = repository;
    }

    public Result list() {
        try {
            List<Manufacturer> manufacturers = repository.findAll();
            return itemList(manufacturers);
        } catch (Exception e) {
            return internalError("list manufacturers", e);
        }
    }

    public Result get(Long id) {
        try {
            Optional<Manufacturer> manufacturer = repository.findById(id);
            return manufacturer.map(value -> ok(Json.toJson(value)))
                    .orElseGet(() -> notFound(ApiSupport.error("NOT_FOUND", "Manufacturer not found")));
        } catch (Exception e) {
            return internalError("get manufacturer", e);
        }
    }

    public Result create(Http.Request request) {
        try {
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            Manufacturer manufacturer = new Manufacturer();
            manufacturer.setName(ApiSupport.requiredText(body, "name"));
            manufacturer.setCountry(ApiSupport.optionalText(body, "country"));
            if (repository.findByName(manufacturer.getName()).isPresent()) {
                return status(409, ApiSupport.error("DUPLICATE_NAME", "Manufacturer name already exists"));
            }
            return created(Json.toJson(repository.create(manufacturer)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            return internalError("create manufacturer", e);
        }
    }

    public Result update(Long id, Http.Request request) {
        try {
            Manufacturer manufacturer = repository.findById(id).orElse(null);
            if (manufacturer == null) {
                return notFound(ApiSupport.error("NOT_FOUND", "Manufacturer not found"));
            }
            JsonNode body = ApiSupport.jsonBody(request.body().asJson());
            if (body.has("name")) manufacturer.setName(ApiSupport.requiredText(body, "name"));
            if (body.has("country")) manufacturer.setCountry(ApiSupport.optionalText(body, "country"));
            Optional<Manufacturer> duplicate = repository.findByName(manufacturer.getName());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                return status(409, ApiSupport.error("DUPLICATE_NAME", "Manufacturer name already exists"));
            }
            if (!repository.update(manufacturer)) {
                return notFound(ApiSupport.error("NOT_FOUND", "Manufacturer not found"));
            }
            return ok(Json.toJson(repository.findById(id).orElse(manufacturer)));
        } catch (IllegalArgumentException e) {
            return invalidRequest(e);
        } catch (ConflictException e) {
            return conflict(e);
        } catch (Exception e) {
            return internalError("update manufacturer", e);
        }
    }

    public Result delete(Long id) {
        try {
            if (!repository.findById(id).isPresent()) {
                return notFound(ApiSupport.error("NOT_FOUND", "Manufacturer not found"));
            }
            repository.softDelete(id);
            return ok(ApiSupport.error("DELETED", "Manufacturer deactivated"));
        } catch (ConflictException e) {
            return conflict(e);
        } catch (Exception e) {
            return internalError("delete manufacturer", e);
        }
    }
}
