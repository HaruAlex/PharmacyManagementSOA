# Config-only Gateway stub check

This local test service demonstrates that a new backend can be exposed without
changing Gateway Java code. It uses `HttpListener` and registers itself with a
local Consul agent; it is a test fixture, not a production service.

1. Start Consul on `localhost:8500` (or set `CONSUL_ADDRESS`).
2. In one PowerShell window, run:

   ```powershell
   .\tests\gateway-stub\StubService.ps1
   ```

3. In another window, start the Gateway with the extra configuration profile:

   ```powershell
   Set-Location .\api-gateway
   sbt -Dconfig.resource=application.stub.conf -Dservice.port=9000 "run 9000"
   ```

4. Log in through the Gateway to obtain a token, then call:

   ```powershell
   Invoke-RestMethod -Uri "http://localhost:9000/api/stub/echo?probe=1" `
     -Headers @{ Authorization = "Bearer <access-token>" } `
     -Method Post -ContentType "application/json" -Body '{"probe":true}'
   ```

The route and role entries are in `api-gateway/conf/application.stub.conf`;
`GatewayController` is unchanged for the stub. Stop the stub with Ctrl+C so it
deregisters from Consul. This fixture is local-only and does not prove
container-network reachability.
