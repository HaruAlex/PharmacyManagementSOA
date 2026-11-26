# Adding a service behind the Gateway

The Gateway proxy is generic. Adding a service does not require changing
`GatewayController`; add a service-side health/Consul configuration and one
route plus one role policy in `api-gateway/conf/application.conf`.

## Service-side setup

1. Include the shared Java sources from `shared/src/main/java` in the service
   build (or use the future published common library).
2. Provide an unauthenticated `GET /health` returning HTTP 200 while the
   process is ready for routing.
3. Enable the shared module and configure the service:

   ```hocon
   play.modules.enabled += "integration.consul.ConsulModule"

   service.name = "example-service"
   service.address = ${?SERVICE_ADDRESS}
   service.host = ${?SERVICE_HOST}
   service.port = ${play.server.http.port}
   service.instance.id = ${?SERVICE_INSTANCE_ID}

   consul.address = ${?CONSUL_ADDRESS}
   consul.host = "localhost"
   consul.host = ${?CONSUL_HOST}
   consul.port = 8500
   consul.port = ${?CONSUL_PORT}
   consul.health.interval = "10s"
   consul.health.timeout = "3s"
   consul.health.deregister-after = "1m"
   consul.discovery.cache-ttl-ms = 1000
   ```

   `CONSUL_ADDRESS` accepts an HTTP URL with an explicit host and port. The
   legacy `CONSUL_HOST`/`CONSUL_PORT` settings remain supported. If no service
   address is supplied, the registration helper uses the machine/container
   hostname. The default instance ID is `<service-name>-<hostname>-<port>`;
   set `SERVICE_INSTANCE_ID` only when an explicit stable ID is needed.
4. Ensure the port configured for registration matches the listener port
   (`-Dservice.port=<port>` when starting a second local instance).
5. Configure the same `JWT_SECRET` as Auth and Gateway. Never use the
   development fallback outside local development.

## Gateway configuration

Add a mapping to `gateway.routes`:

```hocon
{ public-prefix = "/api/example", service-name = "example-service", upstream-prefix = "" }
```

Then add a matching `gateway.role-rules` entry, specifying allowed methods and
Auth role codes (`AD`, `WH`, `SP`). If the route is intentionally public, add a
deliberate `gateway.public-routes` rule instead. The Gateway validates at
startup that each configured service route has a role policy or an explicitly
public policy; protected methods without a policy are denied.

`upstream-prefix` may be empty to forward the suffix unchanged or may rewrite
the public prefix to the service's existing internal route prefix. More
specific public prefixes take precedence. Keep the public prefix under `/api`
or add the desired prefix to the Gateway CORS `pathPrefixes`.

## Verification

1. Confirm the new service is `passing` in Consul and its health check URL uses
   the reachable service address and listener port.
2. Call an endpoint through the public prefix with a valid token and check the
   upstream status, body, `X-Served-By`, and `X-Request-Id`.
3. Confirm no-token, wrong-role, and unauthorized-branch requests are rejected.
4. Run [`scripts/smoke-gateway.ps1`](../scripts/smoke-gateway.ps1).
5. To verify that Gateway requires no service-specific code, use the
   [config-only stub check](../tests/gateway-stub/README.md).
