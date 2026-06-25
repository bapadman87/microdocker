$productUrl = 'http://localhost:8081/api/products'
$orderUrl = 'http://localhost:8082/api/orders'

Write-Host 'Verifying ProductService...'
try {
    $products = Invoke-RestMethod -Uri $productUrl -Method Get -TimeoutSec 10
    Write-Host 'ProductService responded:'
    $products | ConvertTo-Json -Depth 5 | Write-Host
} catch {
    Write-Error "ProductService verification failed: $_"
}

Write-Host 'Creating test order for product id P1001...'
try {
    $payload = @{ productId = 'P1001'; quantity = 2; customerName = 'Test Customer'; shippingAddress = '123 Demo Street' } | ConvertTo-Json
    $orderResult = Invoke-RestMethod -Uri $orderUrl -Method Post -Body $payload -ContentType 'application/json' -TimeoutSec 10
    Write-Host 'OrderService responded:'
    $orderResult | ConvertTo-Json -Depth 5 | Write-Host
} catch {
    Write-Error "OrderService verification failed: $_"
}
