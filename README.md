# Order Processing System

A production-ready **E-commerce Order Processing System** backend built with **Spring Boot 3.x**, **PostgreSQL**, and **Docker**.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Project Architecture](#3-project-architecture)
4. [Request Flow Diagram](#4-request-flow-diagram)
5. [Database Design](#5-database-design)
6. [Order Status Lifecycle](#6-order-status-lifecycle)
7. [Scheduler Flow](#7-scheduler-flow)
8. [API Reference](#8-api-reference)
9. [Exception Handling](#9-exception-handling)
10. [Project Structure](#10-project-structure)
11. [Database Setup](#11-database-setup)
12. [How to Run Locally](#12-how-to-run-locally)
13. [Run with Docker](#13-run-with-docker)
14. [Running Tests](#14-running-tests)

---

## 1. Project Overview

This system manages the full lifecycle of e-commerce orders — from creation to delivery. It exposes a RESTful API, enforces strict order status transition rules, persists data in PostgreSQL, and runs a background scheduler to auto-process pending orders every 5 minutes.

**Key capabilities:**
- Create orders with multiple line items; total amount auto-calculated
- Retrieve single order or paginated/filtered list
- Enforce valid order status transitions (invalid ones throw exceptions)
- Cancel orders (only when `PENDING`)
- Background job: auto-move `PENDING` → `PROCESSING` every 5 minutes
- Global exception handling with consistent error response format
- Full unit + integration test coverage (Mockito + MockMvc)
- Docker Compose support for zero-config local startup

---

## 2. Tech Stack

| Layer            | Technology                          |
|------------------|-------------------------------------|
| Language         | Java 17                             |
| Framework        | Spring Boot 3.2                     |
| Web Layer        | Spring Web (REST)                   |
| Persistence      | Spring Data JPA + Hibernate 6       |
| Database         | PostgreSQL 15                       |
| Build Tool       | Maven 3.9                           |
| Unit Testing     | JUnit 5 + Mockito                   |
| API Testing      | MockMvc (Spring Test)               |
| Boilerplate      | Lombok                              |
| Containerization | Docker + Docker Compose             |
| Test DB          | H2 (in-memory, test scope only)     |

---

## 3. Project Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser / Postman / curl)            │
└──────────────────────────────┬──────────────────────────────────────┘
                               │  HTTP Request
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT APPLICATION                         │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    CONTROLLER LAYER                          │   │
│  │                   OrderController.java                       │   │
│  │                                                              │   │
│  │  POST /api/orders          GET /api/orders/{id}             │   │
│  │  GET  /api/orders          PUT /api/orders/{id}/status      │   │
│  │  PUT  /api/orders/{id}/cancel                               │   │
│  └───────────────────────────┬──────────────────────────────────┘   │
│                              │  Calls                               │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                     SERVICE LAYER                            │   │
│  │                   OrderServiceImpl.java                      │   │
│  │                                                              │   │
│  │  • Validates business rules                                  │   │
│  │  • Calculates total amount                                   │   │
│  │  • Enforces status transition rules                          │   │
│  │  • Maps Entities ↔ DTOs                                      │   │
│  └───────────────────────────┬──────────────────────────────────┘   │
│                              │  Calls                               │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                   REPOSITORY LAYER                           │   │
│  │          OrderRepository  /  OrderItemRepository             │   │
│  │                 (Spring Data JPA)                            │   │
│  │                                                              │   │
│  │  • CRUD operations                                           │   │
│  │  • Custom JPQL queries                                       │   │
│  │  • Pagination + Filtering                                    │   │
│  └───────────────────────────┬──────────────────────────────────┘   │
│                              │  SQL via Hibernate                   │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                     DATABASE LAYER                           │   │
│  │                    PostgreSQL 15                             │   │
│  │              orders  ──< order_items                         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │               CROSS-CUTTING CONCERNS                         │   │
│  │                                                              │   │
│  │  GlobalExceptionHandler (@RestControllerAdvice)              │   │
│  │  OrderStatusScheduler   (@Scheduled — every 5 min)           │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Package Structure

```
com.ecommerce.orderprocessing
│
├── config/
│   └── SchedulerConfig.java          ← Enables @Scheduled
│
├── controller/
│   └── OrderController.java          ← REST endpoints
│
├── dto/
│   ├── CreateOrderRequest.java       ← Inbound: create order
│   ├── OrderItemRequest.java         ← Inbound: line item
│   ├── UpdateStatusRequest.java      ← Inbound: status update
│   ├── OrderResponse.java            ← Outbound: order details
│   ├── OrderItemResponse.java        ← Outbound: line item
│   └── ErrorResponse.java            ← Outbound: error format
│
├── entity/
│   ├── Order.java                    ← @Entity: orders table
│   ├── OrderItem.java                ← @Entity: order_items table
│   └── OrderStatus.java             ← Enum: PENDING..CANCELLED
│
├── exception/
│   ├── GlobalExceptionHandler.java   ← @RestControllerAdvice
│   ├── OrderNotFoundException.java
│   ├── InvalidStatusTransitionException.java
│   └── OrderCancellationException.java
│
├── repository/
│   ├── OrderRepository.java          ← JpaRepository + custom queries
│   └── OrderItemRepository.java
│
├── scheduler/
│   └── OrderStatusScheduler.java     ← Cron: PENDING → PROCESSING
│
├── service/
│   ├── OrderService.java             ← Interface
│   └── impl/
│       └── OrderServiceImpl.java     ← Business logic
│
└── OrderProcessingSystemApplication.java
```

---

## 4. Request Flow Diagram

### Create Order Flow

```
Client
  │
  │  POST /api/orders  { customerId, items[] }
  │
  ▼
OrderController.createOrder()
  │
  │  @Valid → Bean Validation
  │  ├── customerId: @NotNull
  │  ├── items: @NotEmpty
  │  └── each item: productName @NotBlank, quantity @Min(1), price @DecimalMin(0.01)
  │
  │  [Validation Fails] ──► GlobalExceptionHandler
  │                              └── 400 Bad Request { timestamp, message, status }
  │
  │  [Validation Passes]
  ▼
OrderServiceImpl.createOrder()
  │
  ├── Build Order entity  (status = PENDING)
  ├── For each item:
  │     └── Build OrderItem entity
  │     └── order.addItem(item)  ← sets bidirectional reference
  │     └── total += item.price × item.quantity
  ├── order.setTotalAmount(total)
  └── orderRepository.save(order)  ← cascades to order_items
  │
  ▼
OrderRepository (Spring Data JPA)
  │
  └── INSERT INTO orders ...
  └── INSERT INTO order_items ...  (cascade)
  │
  ▼
OrderServiceImpl.mapToResponse()
  │
  └── Entity → OrderResponse DTO
  │
  ▼
OrderController
  │
  └── 201 Created  { orderId, customerId, items[], totalAmount, status, createdAt }
```

### Get Order Flow

```
Client
  │
  │  GET /api/orders/{id}
  │
  ▼
OrderController.getOrderById()
  │
  ▼
OrderServiceImpl.getOrderById()
  │
  ├── orderRepository.findByIdWithItems(id)
  │     └── JPQL: SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id
  │
  ├── [Not Found] ──► OrderNotFoundException
  │                        └── GlobalExceptionHandler → 404 Not Found
  │
  └── [Found] → mapToResponse() → 200 OK
```

### Update Status Flow

```
Client
  │
  │  PUT /api/orders/{id}/status  { "status": "PROCESSING" }
  │
  ▼
OrderController.updateStatus()
  │
  ▼
OrderServiceImpl.updateOrderStatus()
  │
  ├── Find order (throws 404 if missing)
  │
  ├── validateTransition(current, requested)
  │     └── VALID_TRANSITIONS map lookup
  │         PENDING    → {PROCESSING, CANCELLED}
  │         PROCESSING → {SHIPPED}
  │         SHIPPED    → {DELIVERED}
  │         DELIVERED  → {}  (terminal)
  │         CANCELLED  → {}  (terminal)
  │
  ├── [Invalid] ──► InvalidStatusTransitionException → 400 Bad Request
  │
  └── [Valid] → order.setStatus(new) → save → 200 OK
```

### Cancel Order Flow

```
Client
  │
  │  PUT /api/orders/{id}/cancel
  │
  ▼
OrderController.cancelOrder()
  │
  ▼
OrderServiceImpl.cancelOrder()
  │
  ├── Find order (throws 404 if missing)
  │
  ├── Check: order.status == PENDING ?
  │     NO  ──► OrderCancellationException → 409 Conflict
  │     YES ──► order.setStatus(CANCELLED) → save → 200 OK
```

### Get All Orders (Paginated) Flow

```
Client
  │
  │  GET /api/orders?status=PENDING&page=0&size=10&sort=createdAt,desc
  │
  ▼
OrderController.getAllOrders()
  │   @PageableDefault resolves page/size/sort params
  │
  ▼
OrderServiceImpl.getAllOrders(status, pageable)
  │
  ├── status != null → orderRepository.findByStatus(status, pageable)
  └── status == null → orderRepository.findAll(pageable)
  │
  └── page.map(this::mapToResponse)
  │
  ▼
200 OK  { content[], pageable, totalElements, totalPages, ... }
```

---

## 5. Database Design

### Entity Relationship Diagram

```
┌──────────────────────────────────┐         ┌──────────────────────────────────┐
│             orders               │         │           order_items            │
├──────────────────────────────────┤         ├──────────────────────────────────┤
│ id           BIGSERIAL  PK       │◄───┐    │ id           BIGSERIAL  PK       │
│ customer_id  BIGINT     NOT NULL │    │    │ product_name VARCHAR    NOT NULL  │
│ total_amount NUMERIC(15,2) NN    │    │    │ quantity     INTEGER    NOT NULL  │
│ status       VARCHAR(20) NOT NULL│    │    │ price        NUMERIC(15,2) NN     │
│ created_at   TIMESTAMP           │    └────│ order_id     BIGINT     FK        │
│ updated_at   TIMESTAMP           │         └──────────────────────────────────┘
└──────────────────────────────────┘
         One Order  ──< Many OrderItems
```

### Hibernate Mapping

```java
// Order.java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items;

// OrderItem.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id", nullable = false)
private Order order;
```

---

## 6. Order Status Lifecycle

```
                        ┌────────────┐
                        │  PENDING   │──────────────────┐
                        └─────┬──────┘                  │
                              │                         │
                    (manual or scheduler)               │ cancel
                              │                         │
                              ▼                         ▼
                       ┌────────────┐           ┌────────────┐
                       │ PROCESSING │           │ CANCELLED  │ (terminal)
                       └─────┬──────┘           └────────────┘
                             │
                          shipped
                             │
                             ▼
                       ┌────────────┐
                       │  SHIPPED   │
                       └─────┬──────┘
                             │
                          delivered
                             │
                             ▼
                       ┌────────────┐
                       │ DELIVERED  │ (terminal)
                       └────────────┘

  Invalid transitions throw InvalidStatusTransitionException (400 Bad Request)
  Example: DELIVERED → PENDING  ✗
           SHIPPED   → PENDING  ✗
           CANCELLED → PROCESSING ✗
```

---

## 7. Scheduler Flow

```
Spring Scheduler (background thread)
         │
         │  Cron: "0 */5 * * * *"  (fires every 5 minutes at :00, :05, :10 ...)
         │
         ▼
OrderStatusScheduler.processPendingOrders()
         │
         ▼
OrderServiceImpl.processPendingOrders()
         │
         ├── SELECT * FROM orders WHERE status = 'PENDING'
         │
         ├── [No results] → log DEBUG, return
         │
         └── [Found N orders]
               │
               ├── for each order: order.setStatus(PROCESSING)
               └── orderRepository.saveAll(orders)
                     └── UPDATE orders SET status='PROCESSING', updated_at=NOW()
                                WHERE id IN (...)
               │
               └── log INFO: "N PENDING orders moved to PROCESSING"
```

---

## 8. API Reference

### Base URL
```
http://localhost:8080/api/orders
```

---

### POST /api/orders — Create Order

**Request:**
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

**Response: 201 Created**
```json
{
  "orderId": 1,
  "customerId": 1001,
  "items": [
    { "id": 1, "productName": "Laptop", "quantity": 1, "price": 70000.00 },
    { "id": 2, "productName": "Mouse",  "quantity": 2, "price": 1000.00  }
  ],
  "totalAmount": 72000.00,
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### GET /api/orders/{id} — Get Order by ID

```bash
curl http://localhost:8080/api/orders/1
```

**Response: 200 OK** — same structure as Create response above.

**Response: 404 Not Found**
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "message": "Order not found with id: 99",
  "status": 404
}
```

---

### GET /api/orders — Get All Orders

```bash
# All orders (default: page 0, size 10, sorted by createdAt DESC)
curl "http://localhost:8080/api/orders"

# With pagination
curl "http://localhost:8080/api/orders?page=0&size=5"

# With sorting
curl "http://localhost:8080/api/orders?sort=createdAt,desc"

# Filter by status
curl "http://localhost:8080/api/orders?status=PENDING"

# Combined
curl "http://localhost:8080/api/orders?status=PENDING&page=0&size=10&sort=createdAt,desc"
```

**Response: 200 OK**
```json
{
  "content": [ { ...order... }, { ...order... } ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 25,
  "totalPages": 3,
  "last": false
}
```

---

### PUT /api/orders/{id}/status — Update Status

```bash
curl -X PUT http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "PROCESSING" }'
```

**Response: 200 OK** — updated order object.

**Response: 400 Bad Request** (invalid transition)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "message": "Invalid status transition from DELIVERED to PENDING",
  "status": 400
}
```

---

### PUT /api/orders/{id}/cancel — Cancel Order

```bash
curl -X PUT http://localhost:8080/api/orders/1/cancel
```

**Response: 200 OK** — order with `status: "CANCELLED"`.

**Response: 409 Conflict** (non-PENDING order)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "message": "Order 1 cannot be cancelled. Current status: SHIPPED. Only PENDING orders can be cancelled.",
  "status": 409
}
```

---

## 9. Exception Handling

```
Exception Type                      HTTP Status    When Thrown
─────────────────────────────────────────────────────────────────────
OrderNotFoundException              404 Not Found  Order ID does not exist
InvalidStatusTransitionException    400 Bad Request Transition not in allowed map
OrderCancellationException          409 Conflict   Cancel attempted on non-PENDING
MethodArgumentNotValidException     400 Bad Request @Valid bean validation fails
Exception (catch-all)               500 Internal   Unexpected runtime error

All return:
{
  "timestamp": "ISO-8601 datetime",
  "message":   "human-readable description",
  "status":    HTTP status code (int)
}
```

---

## 10. Project Structure

```
order-processing-system/
│
├── pom.xml                                         ← Maven build descriptor
├── Dockerfile                                      ← Multi-stage Docker build
├── docker-compose.yml                              ← App + PostgreSQL stack
├── README.md
│
└── src/
    ├── main/
    │   ├── java/com/ecommerce/orderprocessing/
    │   │   ├── OrderProcessingSystemApplication.java
    │   │   ├── config/
    │   │   │   └── SchedulerConfig.java
    │   │   ├── controller/
    │   │   │   └── OrderController.java
    │   │   ├── dto/
    │   │   │   ├── CreateOrderRequest.java
    │   │   │   ├── OrderItemRequest.java
    │   │   │   ├── UpdateStatusRequest.java
    │   │   │   ├── OrderResponse.java
    │   │   │   ├── OrderItemResponse.java
    │   │   │   └── ErrorResponse.java
    │   │   ├── entity/
    │   │   │   ├── Order.java
    │   │   │   ├── OrderItem.java
    │   │   │   └── OrderStatus.java
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── OrderNotFoundException.java
    │   │   │   ├── InvalidStatusTransitionException.java
    │   │   │   └── OrderCancellationException.java
    │   │   ├── repository/
    │   │   │   ├── OrderRepository.java
    │   │   │   └── OrderItemRepository.java
    │   │   ├── scheduler/
    │   │   │   └── OrderStatusScheduler.java
    │   │   └── service/
    │   │       ├── OrderService.java
    │   │       └── impl/
    │   │           └── OrderServiceImpl.java
    │   └── resources/
    │       ├── application.yml
    │       └── data.sql
    └── test/
        └── java/com/ecommerce/orderprocessing/
            ├── controller/
            │   └── OrderControllerTest.java        ← MockMvc tests
            └── service/
                └── OrderServiceTest.java           ← Mockito tests
```

---

## 11. Database Setup

### Manual Setup (local PostgreSQL)

```sql
-- Connect as superuser and run:
CREATE DATABASE orderdb;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE orderdb TO postgres;
```

Hibernate auto-creates the tables on first run (`ddl-auto: update`).

### Table DDL (auto-generated by Hibernate)

```sql
CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT         NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    product_name VARCHAR(255)   NOT NULL,
    quantity     INTEGER        NOT NULL,
    price        NUMERIC(15, 2) NOT NULL,
    order_id     BIGINT         NOT NULL REFERENCES orders(id)
);
```

---

## 12. How to Run Locally

### Prerequisites

| Tool        | Version  |
|-------------|----------|
| Java        | 17+      |
| Maven       | 3.9+     |
| PostgreSQL  | 15+      |

### Steps

```bash
# 1. Clone / navigate to project root
cd order-processing-system

# 2. Create the database (PostgreSQL must be running)
psql -U postgres -c "CREATE DATABASE orderdb;"

# 3. Build the project
mvn clean install

# 4. Run the application
mvn spring-boot:run
```

App starts at: **http://localhost:8080**

### Configuration

Edit `src/main/resources/application.yml` to change credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orderdb
    username: postgres      # change if needed
    password: postgres      # change if needed
```

---

## 13. Run with Docker

### Prerequisites

- Docker Desktop installed and running

### Start

```bash
# Build image and start both containers (app + postgres)
docker-compose up --build
```

### Stop

```bash
docker-compose down

# Stop and remove volumes (wipes database)
docker-compose down -v
```

### What Docker Compose starts

```
┌────────────────────────┐       ┌────────────────────────────┐
│  order-processing-app  │──────►│      order-postgres         │
│  Port: 8080            │       │  Port: 5432                 │
│  Spring Boot 3.x       │       │  PostgreSQL 15              │
│  Java 17               │       │  DB: orderdb                │
└────────────────────────┘       └────────────────────────────┘
```

The app container waits for PostgreSQL to pass its health check before starting.

---

## 14. Running Tests

```bash
# Run all tests
mvn test

# Run only service (unit) tests
mvn test -Dtest=OrderServiceTest

# Run only controller (integration) tests
mvn test -Dtest=OrderControllerTest

# Run with verbose output
mvn test -Dsurefire.useFile=false
```

### Test Coverage

| Test Class            | Type        | Framework      | Tests |
|-----------------------|-------------|----------------|-------|
| `OrderServiceTest`    | Unit        | JUnit5+Mockito | 10    |
| `OrderControllerTest` | Integration | MockMvc        | 9     |

**Service tests cover:**
- Order creation with correct total calculation
- Get order: found and not-found cases
- Status transitions: valid and invalid
- Cancel: PENDING (success) and non-PENDING (failure)
- Scheduler: moves PENDING orders, skips when none exist
- Paginated list: with and without status filter

**Controller tests cover:**
- `POST /api/orders` — happy path, missing customerId, empty items
- `GET /api/orders/{id}` — found and 404
- `PUT /api/orders/{id}/cancel` — success and 409
- `GET /api/orders` — paginated list and status filter

---

*Built with Spring Boot 3.2 · Java 17 · PostgreSQL 15*
