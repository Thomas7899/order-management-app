import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Customer } from './customer.service';
import { Product } from '../types/index';

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

export interface Order {
  id?: number;
  orderNumber?: string;
  customer: Customer;
  orderDate?: string;
  status: OrderStatus;
  totalAmount?: number;
  notes?: string;
  shippingAddress?: string;
  billingAddress?: string;
  orderItems?: OrderItem[];
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = 'http://localhost:8080/api/orders';

  constructor(private http: HttpClient) {}

  getOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(this.apiUrl);
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
    return this.http.get<Order[]>(`${this.apiUrl}/customer/${customerId}`);
  }

  getOrdersByStatus(status: OrderStatus): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/status/${status}`);
  }

  getOrdersInPeriod(startDate: string, endDate: string): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/period?startDate=${startDate}&endDate=${endDate}`);
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