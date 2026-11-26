package actions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import domain.Permission;
import errors.ApiException;
import play.libs.Json;
import play.libs.concurrent.ClassLoaderExecutionContext;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import repositories.PermissionRepository;
import security.TokenService;
import services.AuthService;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class HasPermissionAction extends Action<HasPermission> {
    private final AuthService authService;
    private final PermissionRepository permissionRepository;
    private final ClassLoaderExecutionContext dbContext;

    @Inject
    public HasPermissionAction(
            AuthService authService,
            PermissionRepository permissionRepository,
            ClassLoaderExecutionContext dbContext) {
        this.authService = authService;
        this.permissionRepository = permissionRepository;
        this.dbContext = dbContext;
    }

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            String token = request.header("Authorization")
                    .filter(header -> header.startsWith("Bearer "))
                    .map(header -> header.substring(7))
                    .orElseThrow(() -> ApiException.unauthorized(
                            "AUTH_TOKEN_INVALID", "Missing or invalid authorization header"));

            TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
            boolean permitted = permissionRepository.getByUserId(payload.getUserId()).stream()
                    .map(Permission::getPermissionCode)
                    .anyMatch(configuration.value()::equals);
            if (!permitted) {
                throw ApiException.forbidden("FORBIDDEN", "Required permission is missing");
            }
            return Boolean.TRUE;
        }, dbContext.current()).thenCompose(ignored -> delegate.call(request))
          .exceptionally(error -> {
              Throwable cause = unwrap(error);
              if (cause instanceof ApiException) {
                  ApiException apiException = (ApiException) cause;
                  ObjectNode response = Json.newObject();
                  response.put("status", apiException.getStatus());
                  response.put("code", apiException.getCode());
                  response.put("message", apiException.getMessage());
                  return Results.status(apiException.getStatus(), response);
              }
              throw new CompletionException(cause);
          });
    }

    private Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
