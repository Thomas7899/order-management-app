// order-management-frontend/src/app/services/inventory.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Warehouse {
  id: number;
  code: string;
  name: string;
  description?: string;
  address?: string;
  city?: string;
  zipCode?: string;
  country?: string;
  active: boolean;
  isDefault: boolean;
  createdAt: string;
  productCount?: number;
  totalStock?: number;
  inventoryValue?: number;
}

export interface WarehouseStock {
  id: number;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  productId: number;
  productName: string;
  productCategory: string;
  productPrice: number;
  quantity: number;
  minStock: number;
  maxStock: number;
  binLocation?: string;
  lastCountedAt?: string;
  stockValue: number;
  stockStatus: 'OK' | 'LOW' | 'OUT' | 'OVER';
  reorderQuantity?: number;
}

export type MovementType = 
  | 'GOODS_RECEIPT' 
  | 'GOODS_ISSUE' 
  | 'TRANSFER' 
  | 'INVENTORY_ADJUSTMENT' 
  | 'RETURN' 
  | 'SCRAP';

export type ReferenceType = 
  | 'ORDER' 
  | 'PURCHASE_ORDER' 
  | 'RETURN_ORDER' 
  | 'INVENTORY' 
  | 'MANUAL';

export interface StockMovement {
  id: number;
  movementNumber: string;
  movementType: MovementType;
  movementTypeDisplay: string;
  productId: number;
  productName: string;
  sourceWarehouseId?: number;
  sourceWarehouseCode?: string;
  sourceWarehouseName?: string;
  targetWarehouseId?: number;
  targetWarehouseCode?: string;
  targetWarehouseName?: string;
  quantity: number;
  quantityBefore?: number;
  quantityAfter?: number;
  reason?: string;
  referenceNumber?: string;
  referenceType?: ReferenceType;
  createdBy?: string;
  createdAt: string;
}

export interface StockMovementRequest {
  movementType: MovementType;
  productId: number;
  sourceWarehouseId?: number;
  targetWarehouseId?: number;
  quantity: number;
  reason?: string;
  referenceNumber?: string;
  referenceType?: ReferenceType;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// === AI DTOs ===

export interface DemandForecast {
  productId: number;
  productName: string;
  category: string;
  currentStock: number;
  predictedDemand7Days: number;
  predictedDemand14Days: number;
  predictedDemand30Days: number;
  confidenceScore: number;
  trendDirection: 'RISING' | 'STABLE' | 'FALLING';
  aiInsight: string;
  stockoutRiskDate?: string;
  seasonalityFactor: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
}

export interface ReorderRecommendation {
  productId: number;
  productName: string;
  category: string;
  currentStock: number;
  minStock: number;
  recommendedOrderQuantity: number;
  urgency: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  reason: string;
  estimatedCost: number;
  suggestedOrderDate: string;
  daysUntilStockout: number;
  reviewSentimentImpact: number;
}

export interface InventoryAnomaly {
  productName: string;
  warehouseName: string;
  anomalyType: 'UNUSUAL_MOVEMENT' | 'STOCK_DISCREPANCY' | 'DEMAND_SPIKE' | 'SUDDEN_DROP';
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  description: string;
  suggestedAction: string;
  detectedAt: string;
}

export interface InventoryHealthScore {
  overallScore: number;
  grade: string;
  stockAvailabilityScore: number;
  turnoverScore: number;
  accuracyScore: number;
  strengths: string[];
  weaknesses: string[];
  opportunities: string[];
}

export interface InventoryAiReport {
  generatedAt: string;
  executiveSummary: string;
  demandForecasts: DemandForecast[];
  reorderRecommendations: ReorderRecommendation[];
  anomalies: InventoryAnomaly[];
  healthScore: InventoryHealthScore;
  actionItems: string[];
}

@Injectable({
  providedIn: 'root'
})
export class InventoryService {
  private apiUrl = `${environment.apiUrl}/api/inventory`;

  constructor(private http: HttpClient) {}

  // === Warehouse Methods ===

  getWarehouses(): Observable<Warehouse[]> {
    return this.http.get<Warehouse[]>(`${this.apiUrl}/warehouses`);
  }

  getActiveWarehouses(): Observable<Warehouse[]> {
    return this.http.get<Warehouse[]>(`${this.apiUrl}/warehouses/active`);
  }

  getWarehouse(id: number): Observable<Warehouse> {
    return this.http.get<Warehouse>(`${this.apiUrl}/warehouses/${id}`);
  }

  createWarehouse(warehouse: Partial<Warehouse>): Observable<Warehouse> {
    return this.http.post<Warehouse>(`${this.apiUrl}/warehouses`, warehouse);
  }

  updateWarehouse(id: number, warehouse: Partial<Warehouse>): Observable<Warehouse> {
    return this.http.put<Warehouse>(`${this.apiUrl}/warehouses/${id}`, warehouse);
  }

  getWarehouseOverview(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/warehouses/overview`);
  }

  // === Stock Methods ===

  getStockByWarehouse(warehouseId: number): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.apiUrl}/stock/warehouse/${warehouseId}`);
  }

  getStockByProduct(productId: number): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.apiUrl}/stock/product/${productId}`);
  }

  getLowStockItems(): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.apiUrl}/stock/low`);
  }

  getOverStockItems(): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.apiUrl}/stock/over`);
  }

  getStockOverview(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/stock/overview`);
  }

  updateStock(
    warehouseId: number, 
    productId: number, 
    updates: { quantity?: number; minStock?: number; maxStock?: number; binLocation?: string }
  ): Observable<WarehouseStock> {
    return this.http.put<WarehouseStock>(
      `${this.apiUrl}/stock/warehouse/${warehouseId}/product/${productId}`, 
      updates
    );
  }

  getTotalStock(productId: number): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/stock/product/${productId}/total`);
  }

  // === Movement Methods ===

  getMovements(page = 0, size = 20): Observable<PagedResponse<StockMovement>> {
    return this.http.get<PagedResponse<StockMovement>>(
      `${this.apiUrl}/movements?page=${page}&size=${size}`
    );
  }

  getMovementsByWarehouse(warehouseId: number, page = 0, size = 20): Observable<PagedResponse<StockMovement>> {
    return this.http.get<PagedResponse<StockMovement>>(
      `${this.apiUrl}/movements/warehouse/${warehouseId}?page=${page}&size=${size}`
    );
  }

  getMovementsByProduct(productId: number): Observable<StockMovement[]> {
    return this.http.get<StockMovement[]>(`${this.apiUrl}/movements/product/${productId}`);
  }

  createMovement(request: StockMovementRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.apiUrl}/movements`, request);
  }

  // === Statistics ===

  getMovementStatistics(days = 30): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/statistics/movements?days=${days}`);
  }

  getDailyTrend(days = 30): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/statistics/trend?days=${days}`);
  }

  getTopMovedProducts(days = 30): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/statistics/top-products?days=${days}`);
  }

  // === Helper Methods ===

  getMovementTypeLabel(type: MovementType): string {
    const labels: Record<MovementType, string> = {
      GOODS_RECEIPT: 'Wareneingang',
      GOODS_ISSUE: 'Warenausgang',
      TRANSFER: 'Umbuchung',
      INVENTORY_ADJUSTMENT: 'Inventurkorrektur',
      RETURN: 'Retoure',
      SCRAP: 'Verschrottung'
    };
    return labels[type] || type;
  }

  getStockStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      OK: 'Optimal',
      LOW: 'Niedrig',
      OUT: 'Ausverkauft',
      OVER: 'Überbestand'
    };
    return labels[status] || status;
  }

  getStockStatusColor(status: string): string {
    const colors: Record<string, string> = {
      OK: 'green',
      LOW: 'orange',
      OUT: 'red',
      OVER: 'blue'
    };
    return colors[status] || 'gray';
  }

  // === AI Methods ===

  getAiReport(): Observable<InventoryAiReport> {
    return this.http.get<InventoryAiReport>(`${this.apiUrl}/ai/report`);
  }

  getDemandForecasts(productId?: number, forecastDays = 30): Observable<DemandForecast[]> {
    let url = `${this.apiUrl}/ai/forecasts?forecastDays=${forecastDays}`;
    if (productId) {
      url += `&productId=${productId}`;
    }
    return this.http.get<DemandForecast[]>(url);
  }

  getProductForecast(productId: number, forecastDays = 30): Observable<DemandForecast[]> {
    return this.http.get<DemandForecast[]>(
      `${this.apiUrl}/ai/forecasts/${productId}?forecastDays=${forecastDays}`
    );
  }

  getReorderRecommendations(): Observable<ReorderRecommendation[]> {
    return this.http.get<ReorderRecommendation[]>(`${this.apiUrl}/ai/reorder-recommendations`);
  }

  getInventoryAnomalies(): Observable<InventoryAnomaly[]> {
    return this.http.get<InventoryAnomaly[]>(`${this.apiUrl}/ai/anomalies`);
  }

  getHealthScore(): Observable<InventoryHealthScore> {
    return this.http.get<InventoryHealthScore>(`${this.apiUrl}/ai/health-score`);
  }

  // === AI Helper Methods ===

  getUrgencyColor(urgency: string): string {
    const colors: Record<string, string> = {
      CRITICAL: '#dc3545',
      HIGH: '#fd7e14',
      MEDIUM: '#ffc107',
      LOW: '#28a745'
    };
    return colors[urgency] || '#6c757d';
  }

  getTrendIcon(trend: string): string {
    const icons: Record<string, string> = {
      RISING: '📈',
      STABLE: '➡️',
      FALLING: '📉'
    };
    return icons[trend] || '❓';
  }

  getSeverityColor(severity: string): string {
    const colors: Record<string, string> = {
      HIGH: '#dc3545',
      MEDIUM: '#ffc107',
      LOW: '#17a2b8'
    };
    return colors[severity] || '#6c757d';
  }

  getGradeColor(grade: string): string {
    const colors: Record<string, string> = {
      A: '#28a745',
      B: '#7cb342',
      C: '#ffc107',
      D: '#fd7e14',
      F: '#dc3545'
    };
    return colors[grade] || '#6c757d';
  }
}
