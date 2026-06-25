$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host 'Starting ProductService and OrderService in separate PowerShell windows...'
& "$root\run-product-service.ps1"
Start-Sleep -Seconds 2
& "$root\run-order-service.ps1"
Write-Host 'Launched both services.'
Write-Host 'ProductService: http://localhost:8081/api/products'
Write-Host 'OrderService: http://localhost:8082/api/orders'
