package errors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import domain.ErrorCodes;
import play.http.HttpErrorHandler;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Global error handler - RFC 7807 Problem Details format
 */
@Singleton
public class ErrorHandler implements HttpErrorHandler {
    
    @Override
    public CompletionStage<Result> onClientError(Http.RequestHeader request, int statusCode, String message) {
        ObjectNode error = Json.newObject();
        error.put("status", statusCode);
        error.put("code", getClientErrorCode(statusCode));
        error.put("message", message != null ? message : getDefaultMessage(statusCode));
        error.put("path", request.path());
        
        return CompletableFuture.completedFuture(
            Results.status(statusCode, error)
        );
    }
    
    @Override
    public CompletionStage<Result> onServerError(Http.RequestHeader request, Throwable exception) {
        // ApiException - lỗi nghiệp vụ có cấu trúc
        if (exception instanceof ApiException) {
            ApiException apiEx = (ApiException) exception;
            return CompletableFuture.completedFuture(
                Results.status(apiEx.getStatus(), createProblemDetails(
                    apiEx.getStatus(),
                    apiEx.getCode(),
                    apiEx.getMessage(),
                    request.path()
                ))
            );
        }
        
        // IllegalArgumentException - validation error
        if (exception instanceof IllegalArgumentException) {
            return CompletableFuture.completedFuture(
                Results.badRequest(createProblemDetails(
                    Http.Status.BAD_REQUEST,
                    ErrorCodes.VALIDATION_FAILED,
                    exception.getMessage(),
                    request.path()
                ))
            );
        }
        
        // Lỗi không xác định - 500 Internal Server Error
        // Không expose chi tiết lỗi ra ngoài
        exception.printStackTrace();
        
        return CompletableFuture.completedFuture(
            Results.internalServerError(createProblemDetails(
                Http.Status.INTERNAL_SERVER_ERROR,
                ErrorCodes.INTERNAL_ERROR,
                "An internal server error occurred",
                request.path()
            ))
        );
    }
    
    /**
     * Tạo RFC 7807 Problem Details response
     */
    private JsonNode createProblemDetails(int status, String code, String message, String path) {
        ObjectNode problem = Json.newObject();
        problem.put("status", status);
        problem.put("code", code);
        problem.put("message", message);
        problem.put("path", path);
        return problem;
    }
    
    private String getClientErrorCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> ErrorCodes.VALIDATION_FAILED;
            case 401 -> ErrorCodes.AUTH_TOKEN_INVALID;
            case 403 -> ErrorCodes.FORBIDDEN;
            case 404 -> "NOT_FOUND";
            default -> "CLIENT_ERROR";
        };
    }
    
    private String getDefaultMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not found";
            default -> "Client error";
        };
    }
}
