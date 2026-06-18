# Order Processing System

E-commerce Order Processing REST API built with Spring Boot 3, PostgreSQL, and Docker.

---

## Tech Stack

- **Java 17** + **Spring Boot 3.2**
- **Spring Data JPA** + **Hibernate**
- **PostgreSQL 15**
- **Maven**
- **Lombok**
- **JUnit 5** + **Mockito**
- **Docker** + **Docker Compose**

---

## Project Structure

```
src/
├── controller/       REST API endpoints
├── service/          Business logic
├── repository/       Database access
├── entity/           JPA entities (Order, OrderItem)
├── dto/              Request / Response objects
├── exception/        Custom exceptions + global handler
└── scheduler/        Background job (auto-process orders)
```

---

## API Endpoints

| Method | Endpoint                    | Description          |
|--------|-----------------------------|----------------------|
| POST   | `/api/orders`               | Create a new order   |
| GET    | `/api/orders/{id}`          | Get order by ID      |
| GET    | `/api/orders`               | Get all orders       |
| PUT    | `/api/orders/{id}/status`   | Update order status  |
| PUT    | `/api/orders/{id}/cancel`   | Cancel an order      |

### Pagination & Filtering

```
GET /api/orders?page=0&size=10&sort=createdAt,desc
GET /api/orders?status=PENDING
```

---

## Order Status Flow

```
PENDING → PROCESSING → SHIPPED → DELIVERED
PENDING → CANCELLED
```

Any other transition returns a `400 Bad Request`.

---

## Database

Two tables managed by Hibernate (auto-created on startup):

- **orders** — `id`, `customer_id`, `total_amount`, `status`, `created_at`, `updated_at`
- **order_items** — `id`, `product_name`, `quantity`, `price`, `order_id`

---

## How to Run

### Using Docker (Recommended)

```bash
docker-compose up --build
```

Starts the Spring Boot app on port `8080` and PostgreSQL on port `5432`.

### Local Setup

**Prerequisites:** Java 17, Maven, PostgreSQL running on `localhost:5432`

```bash
# Create database
psql -U postgres -c "CREATE DATABASE orderdb;"

# Run the app
mvn spring-boot:run
```

App runs at: `http://localhost:8080`

---

## Configuration

Edit `src/main/resources/application.yml` to change DB credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orderdb
    username: postgres
    password: postgres
```

---

## Running Tests

```bash
mvn test
```

---

## Sample Request

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1001,
    "items": [
      { "productName": "Laptop", "quantity": 1, "price": 70000 },
      { "productName": "Mouse",  "quantity": 2, "price": 1000  }
    ]
  }'
```

**Response:**
```json
{
  "orderId": 1,
  "customerId": 1001,
  "totalAmount": 72000.00,
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00",
  "items": [
    { "productName": "Laptop", "quantity": 1, "price": 70000.00 },
    { "productName": "Mouse",  "quantity": 2, "price": 1000.00  }
  ]
}
```
