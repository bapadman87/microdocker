$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serviceDir = Join-Path $root 'ProductService'

Set-Location $serviceDir
if (-not (Test-Path target\product-service-0.0.1-SNAPSHOT.jar)) {
    Write-Host 'Building ProductService...'
    mvn clean package -DskipTests | Out-Null
}

$command = "Set-Item Env:SERVER_PORT '8081'; cd '$serviceDir'; java -jar target/product-service-0.0.1-SNAPSHOT.jar"
Start-Process powershell.exe -ArgumentList '-NoExit', '-Command', $command
Write-Host 'ProductService starting on port 8081.'
