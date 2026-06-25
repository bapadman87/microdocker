# OrderService

Standalone Spring Boot application for accepting and creating customer orders.

## Build

```bash
cd OrderService
mvn clean package
```

## Run

```bash
cd OrderService
java -jar target/order-service-0.0.1-SNAPSHOT.jar
```

## API

- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{id}`

## Notes

- `OrderService` validates products by calling `ProductService` on `http://localhost:8081`.
- Start `ProductService` before creating orders.
- If the product is missing, order creation returns `404 Not Found`.
- If the product exists but is unavailable, order creation returns `422 Unprocessable Entity`.
- If ProductService itself is unavailable, order creation returns `503 Service Unavailable`.
- If the request payload is invalid, order creation returns `400 Bad Request`.

## Create Order Example

```json
{
  "productId": "P1001",
  "quantity": 2,
  "customerName": "Jane Doe",
  "shippingAddress": "123 Main St"
}
```
