# Integration change log

## Giai đoạn 1 — Gateway và Consul

- Added a shared Consul registration module and health-based round-robin
  resolver under `shared/src/main/java/integration/consul`.
- Wired Gateway and the five in-scope services to shared Consul registration,
  container-resolvable addresses, runtime ports, unique default instance IDs,
  configurable health checks, and Play shutdown deregistration.
- Routed Gateway service discovery through the shared resolver and moved
  public service mapping and role policy to `api-gateway/conf/application.conf`.
- Preserved the approved public prefixes: `/api/auth`, `/catalog`,
  `/api/purchase`, `/api/inventory`, and `/api/sales`.
- Added Gateway JWT/role/branch validation, trusted identity forwarding,
  stable request IDs, upstream method/path/query/body/status handling, retry
  exclusion, standard error envelopes, and CORS for the public prefixes.
- Updated Auth-to-Catalog and Sales-to-Inventory clients to resolve healthy
  instances through Consul instead of fixed URLs.
- Added focused Gateway and Auth tests, route contract documentation, new
  service instructions, and a smoke-test script.
- Gateway tests pass, including retry/no-instance, CORS preflight, and loading
  the config-only stub profile. Auth tests pass; Catalog, Purchase, Inventory,
  and Sales compile. The smoke script passes PowerShell syntax parsing only;
  it has not been run against live services.

Known Phase 1 gate limitations: this checkout has no `docker-compose.yml` and
Docker/Compose was unavailable during the earlier environment check. Full
multi-instance Consul failover and a live request through the stub service
remain to be demonstrated in an environment with Consul/Compose.

The business endpoint mismatches documented in
[`INTEGRATION_AUDIT.md`](./INTEGRATION_AUDIT.md) remain for later integration
work. In particular, the Auth Catalog branch lookup path/response contract and
Sales Inventory operations still do not match the currently exposed service
routes; the clients now discover their destination through Consul, but their
business API contracts were not changed in this phase.
