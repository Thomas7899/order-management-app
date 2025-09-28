import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order } from './order.service';
import { Product } from './product.service';

export interface DashboardStats {
  totalCustomers: number;
  totalProducts: number;
  totalOrders: number;
  ordersByStatus: { [key: string]: number };
  totalRevenue: number;
  pendingRevenue: number;
  todayRevenue: number;
  monthRevenue: number;
  lowStockProductsCount: number;
}

export interface RecentActivity {
  recentOrders: Order[];
  lowStockProducts: Product[];
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private apiUrl = 'http://localhost:8080/api/dashboard';

  constructor(private http: HttpClient) {}

  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/stats`);
  }

  getRecentActivity(): Observable<RecentActivity> {
    return this.http.get<RecentActivity>(`${this.apiUrl}/recent-activity`);
  }
}