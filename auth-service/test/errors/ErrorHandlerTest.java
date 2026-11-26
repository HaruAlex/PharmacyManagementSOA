package errors;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import play.mvc.Http;
import play.mvc.Result;
import security.TokenInvalidException;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;

class ErrorHandlerTest {
    @Test
    void onServerError_wrappedApiException_preservesBusinessStatus() {
        Http.RequestHeader request = mock(Http.RequestHeader.class);
        when(request.path()).thenReturn("/api/auth/login");

        Result result = new ErrorHandler().onServerError(
                request,
                new CompletionException(ApiException.locked("AUTH_ACCOUNT_LOCKED", "Account is locked"))
        ).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(423);
        JsonNode body = play.libs.Json.parse(contentAsString(result));
        assertThat(body.get("code").asText()).isEqualTo("AUTH_ACCOUNT_LOCKED");
        assertThat(body.get("path").asText()).isEqualTo("/api/auth/login");
    }

    @Test
    void onServerError_wrappedInvalidToken_returnsUnauthorized() {
        Http.RequestHeader request = mock(Http.RequestHeader.class);
        when(request.path()).thenReturn("/api/roles");

        Result result = new ErrorHandler().onServerError(
                request,
                new CompletionException(new TokenInvalidException("Invalid or expired token"))
        ).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(401);
        JsonNode body = play.libs.Json.parse(contentAsString(result));
        assertThat(body.get("code").asText()).isEqualTo("AUTH_TOKEN_INVALID");
    }
}
