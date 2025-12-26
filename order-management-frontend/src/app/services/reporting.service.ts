// order-management-frontend/src/app/services/reporting.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AbcItem {
  id: number;
  name: string;
  category: string | null;
  revenue: number;
  revenuePercentage: number;
  cumulativePercentage: number;
  abcClass: 'A' | 'B' | 'C';
  orderCount: number;
  quantitySold: number;
}

export interface AbcSummary {
  aClassCount: number;
  bClassCount: number;
  cClassCount: number;
  aClassRevenue: number;
  bClassRevenue: number;
  cClassRevenue: number;
  aClassPercentage: number;
  bClassPercentage: number;
  cClassPercentage: number;
}

export interface AbcAnalysis {
  items: AbcItem[];
  summary: AbcSummary;
}

export interface ForecastPeriod {
  date: string;
  period: string;
  actualRevenue: number | null;
  forecastedRevenue: number | null;
  orderCount: number;
  growthRate: number | null;
  isForecasted: boolean;
}

export interface ForecastSummary {
  averageMonthlyRevenue: number;
  predictedNextMonth: number;
  predictedNextQuarter: number;
  growthTrend: number;
  trendDirection: 'UP' | 'DOWN' | 'STABLE';
  confidenceLevel: number;
}

export interface SalesForecast {
  historicalData: ForecastPeriod[];
  forecastData: ForecastPeriod[];
  summary: ForecastSummary;
}

export interface FinancialKpis {
  totalRevenue: number;
  revenueThisMonth: number;
  revenueLastMonth: number;
  revenueGrowth: number;
  averageOrderValue: number;
  grossMargin: number;
  revenuePerCustomer: number;
}

export interface OperationalKpis {
  totalOrders: number;
  ordersThisMonth: number;
  pendingOrders: number;
  processingOrders: number;
  shippedOrders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  fulfillmentRate: number;
  cancellationRate: number;
  averageOrdersPerDay: number;
}

export interface CustomerKpis {
  totalCustomers: number;
  newCustomersThisMonth: number;
  activeCustomers: number;
  customerRetentionRate: number;
  averagePurchaseFrequency: number;
  customerLifetimeValue: number;
  customersByCountry: { [key: string]: number };
}

export interface InventoryKpis {
  totalProducts: number;
  activeProducts: number;
  totalInventoryValue: number;
  lowStockItems: number;
  outOfStockItems: number;
  stockTurnoverRate: number;
  averageStockLevel: number;
  inventoryByCategory: { [key: string]: number };
}

export interface TrendData {
  period: string;
  value: number;
  previousValue: number | null;
  changePercentage: number | null;
}

export interface KpiDashboard {
  financial: FinancialKpis;
  operational: OperationalKpis;
  customer: CustomerKpis;
  inventory: InventoryKpis;
  revenueTrend: TrendData[];
  orderTrend: TrendData[];
}

@Injectable({
  providedIn: 'root'
})
export class ReportingService {
  private apiUrl = `${environment.apiUrl}/api/reporting`;

  constructor(private http: HttpClient) {}

  getProductAbcAnalysis(): Observable<AbcAnalysis> {
    return this.http.get<AbcAnalysis>(`${this.apiUrl}/abc/products`);
  }

  getCustomerAbcAnalysis(): Observable<AbcAnalysis> {
    return this.http.get<AbcAnalysis>(`${this.apiUrl}/abc/customers`);
  }

  getSalesForecast(historyMonths = 12, forecastMonths = 3): Observable<SalesForecast> {
    return this.http.get<SalesForecast>(
      `${this.apiUrl}/forecast?historyMonths=${historyMonths}&forecastMonths=${forecastMonths}`
    );
  }

  getKpiDashboard(): Observable<KpiDashboard> {
    return this.http.get<KpiDashboard>(`${this.apiUrl}/kpi`);
  }
}
