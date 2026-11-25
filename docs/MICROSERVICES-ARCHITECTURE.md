# 🏗️ Microservices Deployment Architecture

## 📋 Overview
This project uses a **modern, decoupled architecture** with separate deployments for the frontend and backend. This approach, often a step towards a full microservices architecture, demonstrates enterprise-level DevOps practices by enabling independent scaling and development cycles.

## 🎯 Deployed Services

### 🚀 Backend API (Spring Boot + Render)
- **URL**: `https://thomas-order-backend.onrender.com`
- **Technology**: Spring Boot 3.5.6, PostgreSQL, Advanced SQL
- **Features**: RESTful API, Database Analytics, Performance Optimization

### 🌐 Frontend App (Angular + Netlify)  
- **URL**: `https://your-frontend-app.netlify.app`
- **Technology**: Angular 18, TypeScript, Responsive Design
- **Features**: SPA, Real-time UI, Mobile-optimized

---

## 🔧 Deployment Configuration

### Backend (Render + Neon)
```bash
# Render builds order-management/Dockerfile (see render.yaml); GitHub Actions triggers the deploy

# Environment variables
SPRING_PROFILES_ACTIVE=cloud
DATABASE_URL=jdbc:postgresql://<neon-host>/neondb?sslmode=require
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

### Frontend (Netlify)
```bash
# Build settings
Base directory: order-management-frontend
Build command: npm ci && npm run build
Publish directory: order-management-frontend/dist/order-management-frontend
```

---

## 🔄 Continuous Integration/Deployment

### GitHub Actions Pipelines:
1. **Backend CI/CD** → Render deployment on push to `main`
2. **Frontend CI/CD** → Netlify deployment on frontend changes
3. **Automated Testing** → Unit tests and integration tests

### Files:
- `.github/workflows/deploy-backend.yml`
- `.github/workflows/deploy-frontend.yml`
- `netlify.toml` - Frontend build configuration
- `render.yaml` - Render service definition
- `order-management/Dockerfile` - Backend container image

---

## 🌐 API Integration

### CORS Configuration
Backend allows requests from:
- `http://localhost:4200` (development)
- `https://*.netlify.app` (production)
- `https://*.vercel.app` (alternative)

### Environment-specific API URLs:
- **Development**: `http://localhost:8080/api`
- **Production**: `https://thomas-order-backend.onrender.com/api`

---

## 📊 Advanced Features Demonstrated

### Backend (Spring Boot):
- ✅ **Multi-Profile Configuration** (dev/prod/cloud)
- ✅ **PostgreSQL with Advanced SQL** (Window Functions, CTEs)
- ✅ **Analytics Endpoints** (Business Intelligence)
- ✅ **Performance Optimization** (Indexing, Query Tuning)
- ✅ **Cloud-Ready Architecture** (12-Factor App)

### Frontend (Angular):
- ✅ **Standalone Components** (Modern Angular)
- ✅ **TypeScript Interfaces** (Type Safety)
- ✅ **Responsive Design** (Mobile-first)
- ✅ **Environment Configuration** (Dev/Prod separation)
- ✅ **Reactive Programming** (Observables, RxJS)

### DevOps:
- ✅ **Microservices Architecture**
- ✅ **Automated CI/CD Pipelines**
- ✅ **Environment-specific Deployments**
- ✅ **Security Headers & CORS**
- ✅ **Performance Monitoring**

---

## 🎯 Professional Benefits

This architecture demonstrates:

### 🏢 **Enterprise Skills:**
- **Scalable Architecture**: Independent scaling of frontend/backend
- **DevOps Best Practices**: Automated deployments, environment separation
- **Modern Tech Stack**: Latest Spring Boot, Angular, PostgreSQL
- **Performance Optimization**: Database tuning, caching strategies

### 💼 **Job Market Relevance:**
- **Full-Stack Expertise**: End-to-end application development
- **Cloud Native**: Render (Docker), Neon (PostgreSQL), Netlify
- **Database Proficiency**: Advanced SQL, performance tuning
- **Modern Frontend**: Angular 18, TypeScript, responsive design

---

## 🚀 Quick Start

### Local Development:
```bash
# Backend
cd order-management
./mvnw spring-boot:run

# Frontend  
cd order-management-frontend
npm start
```

### Production Deployment:
```bash
# Backend to Render
# Automatic deployment via GitHub Actions after green tests on main

# Frontend to Netlify
# Automatic deployment via GitHub integration
```

---

**🎯 This setup showcases enterprise-level full-stack development skills perfect for modern software engineering positions!**