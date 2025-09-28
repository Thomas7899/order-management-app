#!/bin/bash

# Heroku Deployment Script for Order Management System
# Automatisiert das Deployment-Setup

echo "🚀 Setting up Order Management System for Heroku Deployment"

# Build das Backend
echo "📦 Building Spring Boot Application..."
cd order-management
./mvnw clean package -DskipTests
cd ..

# Check if Heroku CLI is installed
if ! command -v heroku &> /dev/null; then
    echo "❌ Heroku CLI is not installed. Please install it first:"
    echo "   brew tap heroku/brew && brew install heroku"
    exit 1
fi

echo "✅ Heroku CLI found"

# Login check
if ! heroku auth:whoami &> /dev/null; then
    echo "🔐 Please login to Heroku first:"
    heroku login
fi

# Create Heroku app (optional - user can do this manually)
read -p "📱 Create new Heroku app? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    read -p "Enter app name (or press Enter for auto-generated): " app_name
    if [ -z "$app_name" ]; then
        heroku create
    else
        heroku create $app_name
    fi
fi

# Add PostgreSQL addon
echo "🗄️ Adding PostgreSQL addon..."
heroku addons:create heroku-postgresql:essential-0

# Set environment variables
echo "⚙️ Setting environment variables..."
heroku config:set SPRING_PROFILES_ACTIVE=heroku
heroku config:set JAVA_OPTS="-Xmx512m -Xms256m"

# Display next steps
echo ""
echo "🎉 Heroku setup complete!"
echo ""
echo "📋 Next steps:"
echo "1. Push to GitHub: git push origin main"
echo "2. Deploy to Heroku: git push heroku main"
echo "   OR set up automatic deploys in Heroku Dashboard"
echo "3. Open your app: heroku open"
echo ""
echo "🔧 Useful commands:"
echo "- View logs: heroku logs --tail"
echo "- Check status: heroku ps"
echo "- Open database: heroku pg:psql"
echo "- Set frontend URL: heroku config:set FRONTEND_URL=https://your-frontend.com"