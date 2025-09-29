# Order Management - Database Setup Guide

## 🗄️ **Multi-Environment Database Configuration**

Dieses Projekt demonstriert professionelle Spring Boot Konfiguration mit verschiedenen Umgebungsprofilen.

### **Verfügbare Profile:**

#### 🔧 **Development (Standard)**
```bash
# Automatisch aktiv - H2 In-Memory Database
./mvnw spring-boot:run
```
- **Database**: H2 In-Memory
- **URL**: `jdbc:h2:mem:devdb`
- **H2 Console**: http://localhost:8080/h2-console
- **Performance**: Schnell, ideal für Entwicklung

#### 🚀 **Production mit PostgreSQL**
```bash
# PostgreSQL Database starten
docker-compose up -d postgres-dev

# Application mit Production Profile
./mvnw spring-boot:run -Dspring.profiles.active=prod
```
- **Database**: PostgreSQL 15
- **URL**: `jdbc:postgresql://localhost:5432/order_management_prod`
- **Performance**: Enterprise-ready, produktionstauglich

#### 🧪 **Test Environment**
```bash
# Automatisch bei Tests aktiv
./mvnw test
```
- **Database**: H2 In-Memory (isoliert)
- **Performance**: Optimiert für schnelle Tests

---

## 🐳 **Docker Setup**

### **PostgreSQL Development Environment**
```bash
# PostgreSQL starten
docker-compose up -d postgres-dev

# Mit pgAdmin (Optional)
docker-compose --profile tools up -d

# pgAdmin: http://localhost:8081
# Email: admin@orderms.com / Password: admin123
```

### **Production-like Testing**
```bash
# Production PostgreSQL
docker-compose --profile prod up -d postgres-prod

# Connection auf Port 5433
```

---

## 🔧 **Environment Variables**

### **Production Setup**
```bash
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_secure_password
export SSL_KEYSTORE_PASSWORD=your_ssl_password
```

---

## 📊 **Database Features**

### **PostgreSQL Extensions (automatisch installiert)**
- `uuid-ossp`: UUID Generation
- `pg_trgm`: Text Search Performance
- `btree_gin`: Advanced Indexing

### **Performance Monitoring**
```sql
-- Query Performance Statistics
SELECT * FROM get_query_stats();
```
---
### **Enterprise Standards demonstrated:**
✅ **Multi-Environment Configuration**  
✅ **Database Migration Ready**  
✅ **Docker Container Setup**  
✅ **Production Security Settings**  
✅ **Performance Monitoring**  
✅ **PostgreSQL Advanced Features**

---

## 🔄 **Profile wechseln**

```bash
# Development (H2)
./mvnw spring-boot:run

# Production (PostgreSQL)  
./mvnw spring-boot:run -Dspring.profiles.active=prod

# Mit Environment Variables
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```
