// Environment configuration for different deployment stages
export const environment = {
  production: true,
  apiUrl: 'https://thomas-order-backend-f8a0728f603a.herokuapp.com/api',
  appName: 'Order Management System',
  version: '1.0.0',
  features: {
    analytics: true,
    debugging: false,
    caching: true
  }
};