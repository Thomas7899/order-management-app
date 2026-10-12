// order-management-frontend/src/app/app.routes.ts
import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Anmelden – Order Management',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Startseite – Order Management',
        loadComponent: () => import('./features/launchpad/launchpad.component').then(m => m.LaunchpadComponent)
      },

      // Vertrieb
      {
        path: 'orders',
        title: 'Kundenaufträge',
        loadComponent: () => import('./features/orders/order-list.component').then(m => m.OrderListComponent)
      },
      {
        path: 'orders/new',
        title: 'Kundenauftrag anlegen',
        canActivate: [roleGuard('SALES')],
        loadComponent: () => import('./features/orders/order-create.component').then(m => m.OrderCreateComponent)
      },
      {
        path: 'orders/:id',
        title: 'Kundenauftrag',
        loadComponent: () => import('./features/orders/order-detail.component').then(m => m.OrderDetailComponent)
      },
      {
        path: 'order-requests',
        title: 'Auftragseingang',
        loadComponent: () => import('./features/order-requests/order-request-list.component').then(m => m.OrderRequestListComponent)
      },
      {
        path: 'order-requests/:id',
        title: 'Auftragseingang',
        loadComponent: () => import('./features/order-requests/order-request-detail.component').then(m => m.OrderRequestDetailComponent)
      },
      {
        path: 'customers',
        title: 'Debitoren',
        loadComponent: () => import('./features/customers/customer-list.component').then(m => m.CustomerListComponent)
      },
      {
        path: 'customers/:id',
        title: 'Debitor',
        loadComponent: () => import('./features/customers/customer-detail.component').then(m => m.CustomerDetailComponent)
      },

      // Logistik
      {
        path: 'deliveries',
        title: 'Auslieferungen',
        loadComponent: () => import('./features/deliveries/delivery-list.component').then(m => m.DeliveryListComponent)
      },
      {
        path: 'deliveries/:id',
        title: 'Auslieferung',
        loadComponent: () => import('./features/deliveries/delivery-detail.component').then(m => m.DeliveryDetailComponent)
      },
      {
        path: 'materials',
        title: 'Materialien',
        loadComponent: () => import('./features/materials/material-list.component').then(m => m.MaterialListComponent)
      },
      {
        path: 'materials/:id',
        title: 'Material',
        loadComponent: () => import('./features/materials/material-detail.component').then(m => m.MaterialDetailComponent)
      },
      {
        path: 'stock',
        title: 'Bestandsübersicht',
        loadComponent: () => import('./features/inventory/stock-overview.component').then(m => m.StockOverviewComponent)
      },
      {
        path: 'movements',
        title: 'Materialbelege',
        loadComponent: () => import('./features/inventory/movement-list.component').then(m => m.MovementListComponent)
      },

      // Fakturierung
      {
        path: 'invoices',
        title: 'Fakturen',
        loadComponent: () => import('./features/billing/invoice-list.component').then(m => m.InvoiceListComponent)
      },
      {
        path: 'invoices/:id',
        title: 'Faktura',
        loadComponent: () => import('./features/billing/invoice-detail.component').then(m => m.InvoiceDetailComponent)
      },
      {
        path: 'billing-due',
        title: 'Fakturavorrat',
        loadComponent: () => import('./features/billing/billing-due.component').then(m => m.BillingDueComponent)
      },

      // Analyse
      {
        path: 'reporting',
        title: 'Kennzahlen',
        loadComponent: () => import('./features/analytics/reporting.component').then(m => m.ReportingComponent)
      },
      {
        path: 'reviews',
        title: 'Bewertungssuche',
        loadComponent: () => import('./features/analytics/reviews.component').then(m => m.ReviewsComponent)
      },
      {
        path: 'review-analysis',
        title: 'Bewertungsanalyse',
        loadComponent: () => import('./features/analytics/review-analysis.component').then(m => m.ReviewAnalysisComponent)
      },
      { path: 'ki-trends', redirectTo: 'review-analysis' },
      {
        path: 'inventory-ai',
        title: 'Bestandsanalyse',
        loadComponent: () => import('./features/inventory/inventory-ai.component').then(m => m.InventoryAiComponent)
      },

      // Verwaltung
      {
        path: 'admin/users',
        title: 'Benutzerverwaltung',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/admin/user-admin.component').then(m => m.UserAdminComponent)
      },

      { path: '**', redirectTo: '' }
    ]
  }
];
