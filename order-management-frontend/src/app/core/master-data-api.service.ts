import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_URL, toParams } from './api';
import {
  Availability, ChangeDocument, CreditExposure, Customer, CustomerRequest, Invoice, Material, MaterialRequest,
  PageResponse, StockMovement, StockMovementRequest, Warehouse, WarehouseStock
} from './models';

/** HATEOAS-Listen ({_embedded: {...}}) oder einfache Arrays in ein Array umwandeln. */
function unwrap<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[];
  const embedded = (value as { _embedded?: Record<string, unknown> })?._embedded;
  if (embedded) {
    const first = Object.values(embedded).find(Array.isArray);
    if (first) return first as T[];
  }
  return [];
}

function strip<T>(value: T & { _links?: unknown }): T {
  const { _links, ...rest } = value;
  return rest as T;
}

@Injectable({ providedIn: 'root' })
export class CustomersApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/customers`;

  list(): Observable<Customer[]> {
    return this.http.get<unknown>(this.url).pipe(map(r => unwrap<Customer>(r).map(strip)));
  }

  search(query: string): Observable<Customer[]> {
    return this.http.get<unknown>(`${this.url}/search`, { params: { query } }).pipe(map(r => unwrap<Customer>(r).map(strip)));
  }

  get(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${this.url}/${id}`).pipe(map(strip));
  }

  create(customer: CustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(this.url, customer).pipe(map(strip));
  }

  update(id: number, customer: CustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.url}/${id}`, customer).pipe(map(strip));
  }

  creditExposure(id: number): Observable<CreditExposure> {
    return this.http.get<CreditExposure>(`${this.url}/${id}/credit-exposure`);
  }

  invoices(id: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.url}/${id}/invoices`);
  }

  changes(id: number): Observable<ChangeDocument[]> {
    return this.http.get<ChangeDocument[]>(`${this.url}/${id}/changes`);
  }
}

@Injectable({ providedIn: 'root' })
export class MaterialsApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/products`;

  list(): Observable<Material[]> {
    return this.http.get<unknown>(this.url).pipe(map(r => unwrap<Material>(r).map(strip)));
  }

  get(id: number): Observable<Material> {
    return this.http.get<Material>(`${this.url}/${id}`).pipe(map(strip));
  }

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/categories`);
  }

  create(material: MaterialRequest): Observable<Material> {
    return this.http.post<Material>(this.url, material).pipe(map(strip));
  }

  update(id: number, material: MaterialRequest): Observable<Material> {
    return this.http.put<Material>(`${this.url}/${id}`, material).pipe(map(strip));
  }

  block(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  changes(id: number): Observable<ChangeDocument[]> {
    return this.http.get<ChangeDocument[]>(`${this.url}/${id}/changes`);
  }
}

export interface MovementSearch {
  search?: string;
  movementCode?: string[];
  productId?: number;
  warehouseId?: number;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/inventory`;

  warehouses(): Observable<Warehouse[]> {
    return this.http.get<Warehouse[]>(`${this.url}/warehouses`);
  }

  createWarehouse(warehouse: Partial<Warehouse>): Observable<Warehouse> {
    return this.http.post<Warehouse>(`${this.url}/warehouses`, warehouse);
  }

  updateWarehouse(id: number, warehouse: Partial<Warehouse>): Observable<Warehouse> {
    return this.http.put<Warehouse>(`${this.url}/warehouses/${id}`, warehouse);
  }

  stockByWarehouse(warehouseId: number): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.url}/stock/warehouse/${warehouseId}`);
  }

  stockByProduct(productId: number): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.url}/stock/product/${productId}`);
  }

  lowStock(): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.url}/stock/low`);
  }

  overStock(): Observable<WarehouseStock[]> {
    return this.http.get<WarehouseStock[]>(`${this.url}/stock/over`);
  }

  updateStockSettings(warehouseId: number, productId: number, settings: { minStock?: number; maxStock?: number; binLocation?: string }): Observable<WarehouseStock> {
    return this.http.put<WarehouseStock>(`${this.url}/stock/warehouse/${warehouseId}/product/${productId}`, settings);
  }

  availability(productId: number): Observable<Availability> {
    return this.http.get<Availability>(`${this.url}/availability/${productId}`);
  }

  movements(criteria: MovementSearch): Observable<PageResponse<StockMovement>> {
    return this.http.get<PageResponse<StockMovement>>(`${this.url}/movements`, { params: toParams({ ...criteria }) });
  }

  movementsByProduct(productId: number): Observable<StockMovement[]> {
    return this.http.get<StockMovement[]>(`${this.url}/movements/product/${productId}`);
  }

  postMovement(request: StockMovementRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.url}/movements`, request);
  }

  reverseMovement(id: number, reason?: string): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.url}/movements/${id}/reverse`, { reason: reason ?? null });
  }
}
