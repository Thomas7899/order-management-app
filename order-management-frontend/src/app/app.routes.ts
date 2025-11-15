import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { UsersComponent } from './users/users.component';
import { CustomersComponent } from './customers/customers.component';
import { ProductsComponent } from './products/products.component';
import { OrdersComponent } from './orders/orders.component';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'users', component: UsersComponent },
  { path: 'customers', component: CustomersComponent },
  { path: 'products', component: ProductsComponent },
  { path: 'orders', component: OrdersComponent },
  {
    path: 'reviews',
    loadComponent: () =>
      import('./reviews/reviews.component').then((m) => m.ReviewsComponent),
  },
  {
  path: 'review-trend',
  loadComponent: () =>
    import('./review-trend/legacy-review-trend.component')
      .then((m) => m.LegacyReviewTrendComponent),
},
{
  path: 'review-trends',
  loadComponent: () =>
    import('./review-trends/review-trends.component')
      .then((m) => m.ReviewTrendsComponent),
},
 {
    path: 'customers/:id',
    loadComponent: () =>
      import('./customers/customer-detail/customer-detail.component')
        .then(m => m.CustomerDetailComponent),
  },
];
