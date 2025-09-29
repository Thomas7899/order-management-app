# 🚀 Heroku Deployment Guide

## 📋 Voraussetzungen

1. **Heroku Account** - [Registrierung](https://signup.heroku.com/)
2. **Heroku CLI** - Installation:
   ```bash
   # macOS
   brew tap heroku/brew && brew install heroku
   
   # Windows
   # Download von https://devcenter.heroku.com/articles/heroku-cli
   ```
3. **Git Repository** - Bereits eingerichtet ✅

## 🚀 Schnelles Deployment

### **Option 1: Automatisches Script**
```bash
./deploy-heroku.sh
```

### **Option 2: Manuelle Schritte**

#### 1. Heroku Login
```bash
heroku login
```

#### 2. Heroku App erstellen
```bash
# Mit automatischem Namen
heroku create

# Mit eigenem Namen
heroku create your-app-name
```

#### 3. PostgreSQL hinzufügen
```bash
heroku addons:create heroku-postgresql:essential-0
```

#### 4. Environment Variables setzen
```bash
heroku config:set SPRING_PROFILES_ACTIVE=heroku
heroku config:set JAVA_OPTS="-Xmx512m -Xms256m"
```

#### 5. Deployment
```bash
git push heroku main
```

#### 6. App öffnen
```bash
heroku open
```

## 🔧 GitHub Integration (Empfohlen)

### **1. Automatisches Deployment einrichten**

1. Gehe zu [Heroku Dashboard](https://dashboard.heroku.com)
2. Wähle deine App aus
3. Gehe zu **Deploy** Tab
4. Wähle **GitHub** als Deployment Method
5. Verbinde dein Repository `order-management-app`
6. Aktiviere **Automatic Deploys** für `main` branch
7. Aktiviere **Wait for CI to pass before deploy**

### **2. GitHub Secrets einrichten (für Actions)**

In deinem GitHub Repository:
1. Gehe zu **Settings** → **Secrets and variables** → **Actions**
2. Füge hinzu:
   - `HEROKU_API_KEY`: Dein Heroku API Key
   - `HEROKU_APP_NAME`: Dein Heroku App Name
   - `HEROKU_EMAIL`: Deine Heroku Email

## 📊 Monitoring & Debugging

### **Logs anzeigen**
```bash
heroku logs --tail
```

### **App Status prüfen**
```bash
heroku ps
```

### **Database Console**
```bash
heroku pg:psql
```

### **Environment Variables anzeigen**
```bash
heroku config
```

## 🎯 API Endpoints nach Deployment

Deine App wird verfügbar sein unter: `https://your-app-name.herokuapp.com`

### **Health Check**
```
GET https://your-app-name.herokuapp.com/actuator/health
```

### **Analytics Dashboard**
```
GET https://your-app-name.herokuapp.com/api/analytics/dashboard
```

### **Product Management**
```
GET https://your-app-name.herokuapp.com/api/products
POST https://your-app-name.herokuapp.com/api/products
```

## 🔧 Troubleshooting

### **Build Errors**
```bash
# Local build test
cd order-management
./mvnw clean package

# Check logs
heroku logs --tail
```

### **Database Issues**
```bash
# Reset database
heroku pg:reset DATABASE_URL --confirm your-app-name

# Check database status
heroku pg:info
```

### **Memory Issues**
```bash
# Increase dyno size
heroku ps:scale web=1:standard-1x

# Check memory usage
heroku logs --tail | grep "Memory"
```

## 🌐 Frontend Integration

Nach Backend-Deployment, Frontend auf Vercel/Netlify deployen:

### **Environment Variables für Frontend**
```env
BACKEND_URL=https://your-app-name.herokuapp.com
```

### **CORS Configuration**
```bash
# Set frontend URL in Heroku
heroku config:set FRONTEND_URL=https://your-frontend.vercel.app
```

## 🚀 Production Checklist

- ✅ **Database**: PostgreSQL Addon hinzugefügt
- ✅ **Environment**: Production Profile aktiv
- ✅ **Security**: HTTPS aktiviert
- ✅ **Monitoring**: Health Endpoints verfügbar
- ✅ **Logging**: Structured Logging konfiguriert
- ✅ **Performance**: Connection Pooling optimiert
- ✅ **Backup**: Automatische DB Backups aktiv

## 🎉 Success!

Nach erfolgreichem Deployment hast du:

- 🌐 **Live Backend API** mit Enterprise Features
- 📊 **Advanced SQL Analytics** in Production
- 🔒 **Sichere PostgreSQL Database**
- 📈 **Performance Monitoring**
- 🚀 **Automatische Deployments** via GitHub

**Deine Order Management System ist production-ready!** 🎯