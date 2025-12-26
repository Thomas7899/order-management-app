// order-management-frontend/src/app/services/order.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Customer } from './customer.service';
import { Product } from '../types';
import { environment } from '../../environments/environment';

export enum OrderStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED', 
  PROCESSING = 'PROCESSING',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  CANCELLED = 'CANCELLED'
}

export interface OrderItem {
  id?: number;
  product: Product;
  quantity: number;
  unitPrice: number;
}

export interface OrderItemDto {
  product: Product;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: number;
  orderNumber: string;
  customer: Customer; 
  orderDate: string;
  status: OrderStatus;
  totalAmount: number;
  notes?: string;
  shippingAddress?: string;
  billingAddress?: string;
  orderItems: OrderItemDto[];
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = `${environment.apiUrl}/api/orders`;

  constructor(private http: HttpClient) {}

  private unwrapCollection<T>(value: unknown): T[] {
    if (Array.isArray(value)) return value as T[];

    const asAny = value as any;
    if (asAny && Array.isArray(asAny.content)) return asAny.content as T[];

    const embedded = asAny?._embedded;
    if (embedded && typeof embedded === 'object') {
      for (const key of Object.keys(embedded)) {
        const candidate = (embedded as any)[key];
        if (Array.isArray(candidate)) return candidate as T[];
      }
    }

    return [];
  }

  getOrders(): Observable<Order[]> {
    return this.http.get<unknown>(this.apiUrl).pipe(
      map(res => this.unwrapCollection<Order>(res))
    );
  }

  getOrder(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.apiUrl}/${id}`);
  }

  getOrderByOrderNumber(orderNumber: string): Observable<Order> {
    return this.http.get<Order>(`${this.apiUrl}/order-number/${orderNumber}`);
  }

  createOrder(order: Order): Observable<Order> {
    return this.http.post<Order>(this.apiUrl, order);
  }

  updateOrder(id: number, order: Order): Observable<Order> {
    return this.http.put<Order>(`${this.apiUrl}/${id}`, order);
  }

  updateOrderStatus(id: number, status: OrderStatus): Observable<Order> {
    return this.http.patch<Order>(`${this.apiUrl}/${id}/status?status=${status}`, {});
  }

  deleteOrder(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getOrdersByCustomer(customerId: number): Observable<Order[]> {
    return this.http.get<unknown>(`${this.apiUrl}/customer/${customerId}`).pipe(
      map(res => this.unwrapCollection<Order>(res))
    );
  }

  getOrdersByStatus(status: OrderStatus): Observable<Order[]> {
    return this.http.get<unknown>(`${this.apiUrl}/status/${status}`).pipe(
      map(res => this.unwrapCollection<Order>(res))
    );
  }

  getOrdersInPeriod(startDate: string, endDate: string): Observable<Order[]> {
    return this.http.get<unknown>(`${this.apiUrl}/filter?startDate=${startDate}&endDate=${endDate}`).pipe(
      map(res => this.unwrapCollection<Order>(res))
    );
  }

  getRevenueByStatus(status: OrderStatus): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/revenue/status/${status}`);
  }

  getRevenueInPeriod(startDate: string, endDate: string): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/revenue/period?startDate=${startDate}&endDate=${endDate}`);
  }

  getOrderCountByStatus(status: OrderStatus): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/count/status/${status}`);
  }
}
