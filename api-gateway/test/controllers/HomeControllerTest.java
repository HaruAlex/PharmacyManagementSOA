package controllers;

import integration.consul.ServiceResolver;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.WithApplication;

import static org.junit.Assert.assertEquals;
import static play.inject.Bindings.bind;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.route;
import static org.mockito.Mockito.mock;

public class HomeControllerTest extends WithApplication {

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure("play.modules.disabled", java.util.List.of(
                        "modules.StartupModule",
                        "integration.consul.ConsulModule"))
                .overrides(bind(ServiceResolver.class).toInstance(mock(ServiceResolver.class)))
                .build();
    }

    @Test
    public void testIndex() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .uri("/");

        Result result = route(app, request);
        assertEquals(OK, result.status());
    }

    @Test
    public void corsPreflightAcceptsConfiguredGatewayPrefix() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method("OPTIONS")
                .uri("/api/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST");

        Result result = route(app, request);

        assertEquals(OK, result.status());
        assertEquals("http://localhost:3000", result.header("Access-Control-Allow-Origin").orElse(null));
    }

}
