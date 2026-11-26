param(
    [string]$GatewayBaseUrl = "http://localhost:9000",
    [string]$Username = $env:SMOKE_USERNAME,
    [string]$Password = $env:SMOKE_PASSWORD
)

$ErrorActionPreference = "Stop"
$GatewayBaseUrl = $GatewayBaseUrl.TrimEnd("/")

function Invoke-GatewayRequest {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [string]$Body
    )

    $arguments = @{
        Method = $Method
        Uri = "$GatewayBaseUrl$Path"
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 10
    }
    if ($PSBoundParameters.ContainsKey("Body")) {
        $arguments.Body = $Body
    }

    try {
        $response = Invoke-WebRequest @arguments
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Headers = $response.Headers
            Content = $response.Content
        }
    }
    catch {
        $httpResponse = $_.Exception.Response
        if ($null -eq $httpResponse) {
            throw
        }
        $reader = New-Object System.IO.StreamReader($httpResponse.GetResponseStream())
        try {
            $content = $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
        return [pscustomobject]@{
            Status = [int]$httpResponse.StatusCode
            Headers = $httpResponse.Headers
            Content = $content
        }
    }
}

function Assert-ErrorEnvelope {
    param(
        [pscustomobject]$Response,
        [int]$ExpectedStatus,
        [string]$ExpectedCode
    )
    if ($Response.Status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but received $($Response.Status)"
    }
    $body = $Response.Content | ConvertFrom-Json
    if ($body.success -ne $false -or $body.code -ne $ExpectedCode -or [string]::IsNullOrWhiteSpace($body.requestId)) {
        throw "Unexpected error envelope: $($Response.Content)"
    }
}

$health = Invoke-GatewayRequest -Method "GET" -Path "/health"
if ($health.Status -ne 200) {
    throw "Gateway health failed with HTTP $($health.Status)"
}

$preflight = Invoke-GatewayRequest -Method "OPTIONS" -Path "/api/auth/login" -Headers @{
    Origin = "http://localhost:3000"
    "Access-Control-Request-Method" = "POST"
}
if ($preflight.Status -ne 200 -or $preflight.Headers["Access-Control-Allow-Origin"] -ne "http://localhost:3000") {
    throw "CORS preflight failed with HTTP $($preflight.Status)"
}

$unauthorized = Invoke-GatewayRequest -Method "GET" -Path "/api/sales/shifts/current"
Assert-ErrorEnvelope -Response $unauthorized -ExpectedStatus 401 -ExpectedCode "UNAUTHORIZED"

$notFound = Invoke-GatewayRequest -Method "GET" -Path "/api/not-configured"
Assert-ErrorEnvelope -Response $notFound -ExpectedStatus 404 -ExpectedCode "ROUTE_NOT_FOUND"

if (-not [string]::IsNullOrWhiteSpace($Username) -and -not [string]::IsNullOrWhiteSpace($Password)) {
    $loginBody = @{
        usernameOrEmail = $Username
        password = $Password
    } | ConvertTo-Json -Compress
    $login = Invoke-GatewayRequest -Method "POST" -Path "/api/auth/login" -Headers @{
        "Content-Type" = "application/json"
    } -Body $loginBody
    if ($login.Status -ne 200) {
        throw "Auth login through Gateway failed with HTTP $($login.Status)"
    }
    $accessToken = ($login.Content | ConvertFrom-Json).accessToken
    if ([string]::IsNullOrWhiteSpace($accessToken)) {
        throw "Auth login response did not contain accessToken"
    }
    $me = Invoke-GatewayRequest -Method "GET" -Path "/api/auth/me" -Headers @{
        Authorization = "Bearer $accessToken"
    }
    if ($me.Status -ne 200) {
        throw "Authenticated Auth request through Gateway failed with HTTP $($me.Status)"
    }
}

Write-Host "Gateway smoke checks passed."
