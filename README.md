# 🏢 Order Management System

> **Enterprise-Level Full-Stack Application**  
> Modern Order Management System mit Spring Boot, PostgreSQL, Angular und Advanced SQL Features

## 🎯 **Projekt-Übersicht**

Dieses Projekt demonstriert **moderne Enterprise-Entwicklung** mit:
- **Backend**: Spring Boot 3.5.6 mit Advanced SQL Features
- **Frontend**: Angular 18 mit Standalone Components
- **Database**: PostgreSQL mit Complex Queries, Window Functions, CTEs
- **DevOps**: Docker, Multi-Environment Configuration
- **Architecture**: Clean Code, SOLID Principles, RESTful APIs

---

## 🚀 **Technologie-Stack**

### **Backend Technologies**
- ☕ **Java 21** - Modern LTS Version
- 🍃 **Spring Boot 3.5.6** - Enterprise Framework
- 🗄️ **PostgreSQL** - Production Database
- 🔍 **H2** - Development Database
- 📊 **Advanced SQL** - Window Functions, CTEs, Complex Queries
- 🐳 **Docker** - Containerization
- 🔧 **Maven** - Build Management

### **Frontend Technologies**  
- 🅰️ **Angular 18** - Modern Frontend Framework
- 📝 **TypeScript** - Type-Safe Development
- 🎨 **CSS Grid** - Responsive Design
- 🔄 **RxJS** - Reactive Programming
- 🧩 **Standalone Components** - Modern Architecture

### **Database Features**
- 🪟 **Window Functions** - Rankings, Analytics
- 🔗 **CTEs** - Complex Table Expressions  
- 🔍 **Subqueries** - Correlated & Nested Queries
- 📈 **Analytics Views** - Business Intelligence
- ⚡ **Performance Indexes** - Query Optimization
- 🔧 **Stored Procedures** - Advanced Logic

---

## 📋 **Features**

### **🏗️ Enterprise Architecture**
- ✅ **Multi-Environment Setup** (Dev/Prod/Test)
- ✅ **Docker Integration** with PostgreSQL
- ✅ **Configuration Management** with Spring Profiles
- ✅ **Database Migration** with Flyway
- ✅ **Performance Monitoring** with Actuator

### **📊 Advanced SQL Analytics**
- ✅ **Product Rankings** with Window Functions
- ✅ **Category Statistics** with CTEs
- ✅ **Inventory Analysis** with Complex Joins
- ✅ **Price Distribution** Analysis
- ✅ **Time-Series Analytics** for Business Intelligence
- ✅ **Full-Text Search** with GIN Indexes

### **🎨 Modern Frontend**
- ✅ **Responsive Design** with CSS Grid
- ✅ **Reactive Forms** with Angular
- ✅ **TypeScript Type Safety** throughout
- ✅ **Component-Based Architecture**
- ✅ **HTTP Interceptors** for API Communication

---

## 🛠️ **Installation & Setup**

### **Prerequisites**
```bash
- Java 21+
- Node.js 18+
- Docker & Docker Compose
- PostgreSQL (via Docker)
```

### **1. Clone Repository**
```bash
git clone <your-repo-url>
cd order-management-app
```

### **2. Backend Setup**
```bash
cd order-management

# Start PostgreSQL with Docker
docker-compose up -d

# Run Spring Boot Application  
./mvnw spring-boot:run

# Application runs on: http://localhost:8080
```

### **3. Frontend Setup**
```bash
cd order-management-frontend

# Install Dependencies
npm install

# Start Angular Development Server
ng serve

# Application runs on: http://localhost:4200
```

---

## 🔧 **Configuration**

### **Environment Profiles**
- **Development**: `application-dev.properties` (H2 Database)
- **Production**: `application-prod.properties` (PostgreSQL)
- **Testing**: `application-test.properties` (H2 In-Memory)

### **Docker Configuration**
```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: order_management
      POSTGRES_USER: order_user
      POSTGRES_PASSWORD: order_password
```

---

## 📡 **API Endpoints**

### **Product Management**
```
GET    /api/products              # List all products
POST   /api/products              # Create product  
GET    /api/products/{id}         # Get product by ID
PUT    /api/products/{id}         # Update product
DELETE /api/products/{id}         # Delete product
```

### **Advanced Analytics**
```
GET /api/analytics/product-rankings          # Window Functions Demo
GET /api/analytics/category-statistics       # CTE Analytics
GET /api/analytics/products/above-average    # Subquery Demo
GET /api/analytics/inventory                 # Business Intelligence
GET /api/analytics/performance               # Database Metrics
GET /api/analytics/dashboard                 # Comprehensive Analytics
```

### **User Management**
```
GET    /api/users                 # List users
POST   /api/users                 # Create user
GET    /api/users/{id}            # Get user by ID
PUT    /api/users/{id}            # Update user
DELETE /api/users/{id}            # Delete user
```

---

## 📊 **Database Schema**

### **Products Table**
```sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    stock_quantity INTEGER NOT NULL,
    image_url VARCHAR(500),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

### **Advanced SQL Features**
```sql
-- Window Functions für Rankings
SELECT name, category, price,
       ROW_NUMBER() OVER (PARTITION BY category ORDER BY price DESC) as rank
FROM products;

-- CTEs für komplexe Statistiken  
WITH category_stats AS (
    SELECT category, COUNT(*) as count, AVG(price) as avg_price
    FROM products GROUP BY category
)
SELECT * FROM category_stats ORDER BY avg_price DESC;
```

---

## 🧪 **Testing**

### **Backend Tests**
```bash
cd order-management
./mvnw test
```

### **Frontend Tests**  
```bash
cd order-management-frontend
npm test
```

---

## 🚀 **Deployment**

### **Production Build**
```bash
# Backend
./mvnw clean package -Pprod

# Frontend  
ng build --configuration production
```

### **Docker Deployment**
```bash
docker-compose -f docker-compose.prod.yml up -d
```

---

## 📈 **Performance Features**

- 🔍 **Strategic Database Indexes** für häufige Queries
- 🪟 **Window Functions** für Analytics ohne N+1 Queries  
- 📊 **Materialized Views** für komplexe Berechnungen
- ⚡ **Connection Pooling** mit HikariCP
- 🗜️ **Query Optimization** mit EXPLAIN ANALYZE

---

## 🎯 **Stellenrelevante Highlights**

### **Java & Spring Expertise**
- ✅ **Spring Boot 3.5.6** - Latest Enterprise Features
- ✅ **JPA/Hibernate** - Advanced ORM Patterns  
- ✅ **Custom Repositories** - Complex SQL Integration
- ✅ **Configuration Management** - Multi-Environment Setup
- ✅ **RESTful APIs** - Modern Web Service Design

### **PostgreSQL & SQL Skills**
- ✅ **Window Functions** - ROW_NUMBER(), RANK(), Analytics
- ✅ **CTEs** - Common Table Expressions für Complex Logic
- ✅ **Subqueries** - Correlated & Nested Query Patterns
- ✅ **Performance Tuning** - Indexes, Query Optimization  
- ✅ **Database Design** - Normalization, Relationships

### **TypeScript & Angular Mastery**
- ✅ **Angular 18** - Latest Framework Features
- ✅ **Standalone Components** - Modern Architecture
- ✅ **Reactive Programming** - RxJS Observables
- ✅ **Type Safety** - Comprehensive TypeScript Usage
- ✅ **Responsive Design** - CSS Grid, Mobile-First

---

## 📞 **Kontakt & Weitere Informationen**

Dieses Projekt demonstriert **Enterprise-Level Entwicklungskenntnisse** in:
- Modern Java Development mit Spring Boot
- Advanced PostgreSQL & SQL Optimization  
- TypeScript & Angular Frontend Development
- DevOps & Docker Containerization
- Clean Code & Software Architecture

**Bereit für anspruchsvolle Enterprise-Projekte!** 🚀

*Erstellt mit ❤️ für moderne Enterprise-Entwicklung*