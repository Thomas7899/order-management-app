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

## 📈 **Performance Features**

- 🔍 **Strategic Database Indexes** für häufige Queries
- 🪟 **Window Functions** für Analytics ohne N+1 Queries  
- 📊 **Materialized Views** für komplexe Berechnungen
- ⚡ **Connection Pooling** mit HikariCP
- 🗜️ **Query Optimization** mit EXPLAIN ANALYZE

---

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

## 📚 **Detaillierte Dokumentation**

- 📋 [**Deployment Guide**](docs/DEPLOYMENT.md) - Backend Deployment auf Heroku
- 🌐 [**Frontend Deployment**](docs/FRONTEND-DEPLOYMENT.md) - Angular auf Netlify  
- 🏗️ [**Microservices Architektur**](docs/MICROSERVICES-ARCHITECTURE.md) - System Design

## 🛠️ **Scripts**

- 🚀 [`scripts/deploy-heroku.sh`](scripts/deploy-heroku.sh) - Automatisches Backend Deployment

---
