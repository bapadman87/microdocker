# E-Commerce Microservices

This workspace contains two separate Spring Boot applications:

- `ProductService` — standalone service that manages and exposes product information
- `OrderService` — standalone service that accepts and creates customer orders

## Projects

### ProductService

Build:
```bash
cd ProductService
mvn clean package
```

Run:
```bash
cd ProductService
java -jar target/product-service-0.0.1-SNAPSHOT.jar
```

API:
- `GET /api/products`
- `GET /api/products?query=term`
- `GET /api/products/{id}`

### OrderService

Build:
```bash
cd OrderService
mvn clean package
```

Run:
```bash
cd OrderService
java -jar target/order-service-0.0.1-SNAPSHOT.jar
```

API:
- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `GET /api/orders` - list all orders
- `GET /api/orders/{id}` - get an order by ID
- `POST /api/orders` - create a new order

## Create Order Request

Example JSON body:

```json
{
  "productId": "P1001",
  "quantity": 2,
  "customerName": "Jane Doe",
  "shippingAddress": "123 Main St, Springfield"
}
```

## Helper Scripts

From the repository root, use the provided PowerShell helpers to run and verify both services together.

Run both services:
```powershell
powershell.exe -ExecutionPolicy Bypass -File run-services.ps1
```

Verify both services:
```powershell
powershell.exe -ExecutionPolicy Bypass -File verify-services.ps1
```

The scripts launch:
- `ProductService` on `http://localhost:8081`
- `OrderService` on `http://localhost:8082`

OrderService is configured to use `ProductService` via `OrderService/src/main/resources/application.properties`.
