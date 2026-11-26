param(
    [int]$Port = 9100,
    [string]$ConsulAddress = $env:CONSUL_ADDRESS
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($ConsulAddress)) {
    $ConsulAddress = "http://localhost:8500"
}
$ConsulAddress = $ConsulAddress.TrimEnd("/")
$ServiceId = "gateway-stub-$($env:COMPUTERNAME)-$Port"
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$Port/")

$registration = @{
    ID = $ServiceId
    Name = "gateway-stub"
    Address = "localhost"
    Port = $Port
    Check = @{
        HTTP = "http://localhost:$Port/health"
        Interval = "5s"
        Timeout = "2s"
        DeregisterCriticalServiceAfter = "1m"
    }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Put `
    -Uri "$ConsulAddress/v1/agent/service/register" `
    -ContentType "application/json" `
    -Body $registration | Out-Null

try {
    $listener.Start()
    Write-Host "gateway-stub registered as $ServiceId at http://localhost:$Port"
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        try {
            $request = $context.Request
            if ($request.Url.AbsolutePath -eq "/health") {
                $statusCode = 200
                $payload = @{ status = "UP"; service = "gateway-stub" }
            }
            elseif ($request.Url.AbsolutePath -eq "/echo") {
                $reader = New-Object System.IO.StreamReader($request.InputStream, $request.ContentEncoding)
                try {
                    $body = $reader.ReadToEnd()
                }
                finally {
                    $reader.Dispose()
                }
                $headers = @{}
                foreach ($name in $request.Headers.AllKeys) {
                    $headers[$name] = $request.Headers[$name]
                }
                $statusCode = 200
                $payload = @{
                    method = $request.HttpMethod
                    path = $request.Url.AbsolutePath
                    query = $request.Url.Query
                    body = $body
                    headers = $headers
                }
            }
            else {
                $statusCode = 404
                $payload = @{ success = $false; code = "NOT_FOUND"; message = "Stub path not found" }
            }

            $bytes = [System.Text.Encoding]::UTF8.GetBytes(($payload | ConvertTo-Json -Depth 8 -Compress))
            $context.Response.StatusCode = $statusCode
            $context.Response.ContentType = "application/json; charset=utf-8"
            $context.Response.ContentLength64 = $bytes.Length
            $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        }
        finally {
            $context.Response.Close()
        }
    }
}
finally {
    if ($listener.IsListening) {
        $listener.Stop()
    }
    $listener.Close()
    Invoke-RestMethod -Method Put `
        -Uri "$ConsulAddress/v1/agent/service/deregister/$ServiceId" | Out-Null
}
