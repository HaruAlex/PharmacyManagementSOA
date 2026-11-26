package controllers;

import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import repositories.ConflictException;

/** Common HTTP error mapping and paging helpers for Catalog controllers. */
abstract class CatalogControllerSupport extends Controller {
    protected Result invalidRequest(IllegalArgumentException exception) {
        return badRequest(ApiSupport.error("INVALID_REQUEST", exception.getMessage()));
    }

    protected Result conflict(ConflictException exception) {
        return status(409, ApiSupport.error("CONFLICT", exception.getMessage()));
    }

    protected Result internalError(String operation, Exception exception) {
        Logger.error("Catalog request failed: " + operation, exception);
        return internalServerError(ApiSupport.error("INTERNAL_ERROR", "Catalog request could not be completed"));
    }

    protected <T> Result itemList(java.util.List<T> items) {
        com.fasterxml.jackson.databind.node.ObjectNode response = Json.newObject();
        response.set("items", Json.toJson(items));
        response.put("total", items.size());
        return ok(response);
    }

    protected int page(play.mvc.Http.Request request) {
        return ApiSupport.page(request.getQueryString("page"));
    }

    protected int size(play.mvc.Http.Request request) {
        return ApiSupport.size(request.getQueryString("size"));
    }
}
