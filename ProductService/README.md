# ProductService

Standalone Spring Boot application for managing and returning product information.

## Build

```bash
cd ProductService
mvn clean package
```

## Run

```bash
cd ProductService
java -jar target/product-service-0.0.1-SNAPSHOT.jar
```

## API

- `GET /api/products`
- `GET /api/products?query=term`
- `GET /api/products/{id}`
