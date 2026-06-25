$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serviceDir = Join-Path $root 'OrderService'

Set-Location $serviceDir
if (-not (Test-Path target\order-service-0.0.1-SNAPSHOT.jar)) {
    Write-Host 'Building OrderService...'
    mvn clean package -DskipTests | Out-Null
}

$productServiceUrl = $env:PRODUCT_SERVICE_URL
if (-not $productServiceUrl) { $productServiceUrl = 'http://localhost:8081' }

$command = "Set-Item Env:SERVER_PORT '8082'; Set-Item Env:PRODUCT_SERVICE_URL '$productServiceUrl'; cd '$serviceDir'; java -jar target/order-service-0.0.1-SNAPSHOT.jar"
Start-Process powershell.exe -ArgumentList '-NoExit', '-Command', $command
Write-Host "OrderService starting on port 8082 and connecting to $productServiceUrl."
