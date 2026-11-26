# Gateway routing contract

The public routes and authorization rules are configured in
[`api-gateway/conf/application.conf`](../api-gateway/conf/application.conf).
The Play routes only dispatch supported HTTP methods to the generic proxy;
service names and path rewrites are not selected in controller code.

## Public route mapping

| Public prefix | Consul service | Upstream prefix | Notes |
|---|---|---|---|
| `/api/auth` | `auth-service` | unchanged suffix | Includes public login/refresh and protected Auth endpoints. |
| `/api/users`, `/api/roles`, `/api/permissions`, `/api/audit-logs` | `auth-service` | unchanged suffix | Existing Auth API paths. |
| `/catalog` | `catalog-service` | unchanged suffix | Preserves the approved legacy public prefix. For example, `/catalog/api/v1/catalog/drugs` forwards to `/api/v1/catalog/drugs`. |
| `/api/purchase` | `purchase-service` | unchanged suffix | For example, `/api/purchase/purchase-orders` forwards to `/purchase-orders`. |
| `/api/inventory` | `inventory-service` | `/api/inventory` | Covers the existing lots API. |
| `/api/inventory/ledger` | `inventory-service` | `/api/v1/inventory/ledger` | Specific route takes precedence over `/api/inventory`. |
| `/api/inventory/transfers` | `inventory-service` | `/api/v1/inventory/transfers` | Specific route takes precedence over `/api/inventory`. |
| `/api/inventory/drug-lots` | `inventory-service` | `/api/drug-lots` | Specific route takes precedence over `/api/inventory`. |
| `/api/sales` | `sales-service` | unchanged suffix | For example, `/api/sales/invoices/create` forwards to `/invoices/create`. |
| `/report` | `report-service` | unchanged suffix | Existing Gateway route retained; the Report Service itself remains out of scope. Requests now require a valid token. |

The resolver queries only passing Consul instances, caches results for the
configured short TTL, and selects instances round-robin. Network failures and
upstream 502/503/504 responses exclude that instance for the remainder of the
request's retries. If no eligible instance remains, the Gateway returns 503.

## Authentication and route roles

Only exact `POST /api/auth/login` and `POST /api/auth/refresh` paths are public.
Other configured routes require a valid HS256 Bearer token. The key is shared
with Auth through `JWT_SECRET`; the development fallback must not be used in a
deployed environment.

The configured policy uses Auth role codes:

| Path | Methods | Roles |
|---|---|---|
| `/api/auth` | GET, HEAD, POST, PUT, PATCH, DELETE | AD, WH, SP |
| `/api/users`, `/api/roles`, `/api/permissions` | GET, HEAD, POST, PUT, PATCH, DELETE | AD |
| `/api/audit-logs` | GET, HEAD, POST | AD |
| `/api/purchase` | GET, HEAD, POST, PUT, PATCH, DELETE | AD, WH |
| `/api/inventory` | GET, HEAD | AD, WH, SP |
| `/api/inventory` | POST, PUT, PATCH, DELETE | AD, WH |
| `/api/sales` | GET, HEAD, POST, PUT, PATCH, DELETE | AD, SP |
| `/catalog` | GET, HEAD | AD, WH, SP |
| `/catalog` | POST, PUT, PATCH, DELETE | AD |
| `/catalog/api/v1/catalog/customers` | POST, PUT, DELETE | AD, SP |
| `/report` | GET, HEAD, POST, PUT, PATCH, DELETE | AD, WH, SP |

Most-specific path rules take precedence. A protected request with no matching
method policy is denied rather than implicitly allowed. `X-Branch-Id`, when
provided, must be a positive integer present in the token's `branch_ids`.
The Gateway forwards the original `Authorization` header and trusted
`X-User-Id`, `X-User-Role`, `X-Branch-Id`, and `X-Request-Id` values.

## Response behavior

Successful upstream status/body/content type and end-to-end headers are
preserved; hop-by-hop headers are removed. Gateway-generated errors and
upstream 400/401/403/404 errors use:

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "A valid Bearer token is required",
  "requestId": "..."
}
```

The same request ID is logged, forwarded, returned in the JSON envelope, and
exposed as `X-Request-Id`. CORS is enabled for `/api` and `/catalog`.
