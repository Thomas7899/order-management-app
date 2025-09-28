# Frontend Deployment Guide

## 🌐 Frontend Deployment (Angular + Netlify)

### Quick Deployment Steps:

1. **Build the Angular app:**
   ```bash
   cd order-management-frontend
   npm ci
   npm run build --prod
   ```

2. **Deploy to Netlify:**
   - Go to [netlify.com](https://netlify.com)
   - Connect your GitHub repository
   - Set build settings:
     - **Base directory:** `order-management-frontend`
     - **Build command:** `npm ci && npm run build`
     - **Publish directory:** `order-management-frontend/dist/order-management-frontend`

3. **Environment Variables in Netlify:**
   - Go to Site settings → Environment variables
   - Add: `NODE_ENV=production`

### Alternative: Manual Deploy
```bash
# Build the app
cd order-management-frontend
npm run build --prod

# Install Netlify CLI
npm install -g netlify-cli

# Deploy
netlify deploy --prod --dir=dist/order-management-frontend
```

### 🔧 Configuration Files:

- **netlify.toml** - Netlify build configuration
- **environment.prod.ts** - Production API URL
- **environment.ts** - Development API URL

### 🚀 Result:
- **Frontend:** https://your-frontend.netlify.app
- **Backend API:** https://your-backend.herokuapp.com/api

### 📱 Features:
- Single Page Application (SPA) routing
- API proxy configuration
- Security headers
- Static asset caching
- Environment-specific configurations

### 🔄 Continuous Deployment:
Netlify automatically rebuilds when you push to GitHub!

---

## Backend Configuration for CORS

Make sure your Spring Boot backend allows the frontend domain:

```java
@CrossOrigin(origins = {"http://localhost:4200", "https://your-frontend.netlify.app"})
```