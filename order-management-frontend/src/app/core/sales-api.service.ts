import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL, toParams } from './api';
import {
  ChangeDocument, Delivery, DocumentFlow, Invoice, Order, OrderCreateRequest, OrderItemRequest,
  OrderListItem, OrderSimulation, PageResponse
} from './models';

export interface OrderSearch {
  search?: string;
  status?: string[];
  customerId?: number;
  creditStatus?: string;
  deliveryStatus?: string;
  billingStatus?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface DeliverySearch {
  search?: string;
  status?: string;
  billingStatus?: string;
  warehouseId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface InvoiceSearch {
  search?: string;
  status?: string;
  billingType?: string;
  overdue?: boolean;
  customerId?: number;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/orders`;

  search(criteria: OrderSearch): Observable<PageResponse<OrderListItem>> {
    return this.http.get<PageResponse<OrderListItem>>(this.url, { params: toParams({ ...criteria }) });
  }

  get(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.url}/${id}`);
  }

  byCustomer(customerId: number): Observable<OrderListItem[]> {
    return this.http.get<OrderListItem[]>(`${this.url}/customer/${customerId}`);
  }

  simulate(request: OrderCreateRequest): Observable<OrderSimulation> {
    return this.http.post<OrderSimulation>(`${this.url}/simulate`, request);
  }

  create(request: OrderCreateRequest): Observable<Order> {
    return this.http.post<Order>(this.url, request);
  }

  updateHeader(id: number, changes: Partial<Pick<Order, 'requestedDeliveryDate' | 'customerReference' | 'shippingAddress' | 'billingAddress' | 'notes'>>): Observable<Order> {
    return this.http.patch<Order>(`${this.url}/${id}`, changes);
  }

  addItem(id: number, item: OrderItemRequest): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/items`, item);
  }

  changeItem(id: number, itemNumber: number, change: { quantity: number; discountPercent?: number | null }): Observable<Order> {
    return this.http.patch<Order>(`${this.url}/${id}/items/${itemNumber}`, change);
  }

  reject(id: number, reason: string, itemNumbers?: number[]): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/reject`, { reason, itemNumbers });
  }

  releaseCredit(id: number): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/release-credit`, {});
  }

  reconfirm(id: number): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/reconfirm`, {});
  }

  processBacklog(): Observable<{ improvedItems: number }> {
    return this.http.post<{ improvedItems: number }>(`${this.url}/backlog/process`, {});
  }

  documentFlow(id: number): Observable<DocumentFlow> {
    return this.http.get<DocumentFlow>(`${this.url}/${id}/document-flow`);
  }

  deliveries(id: number): Observable<Delivery[]> {
    return this.http.get<Delivery[]>(`${this.url}/${id}/deliveries`);
  }

  invoices(id: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.url}/${id}/invoices`);
  }

  changes(id: number): Observable<ChangeDocument[]> {
    return this.http.get<ChangeDocument[]>(`${this.url}/${id}/changes`);
  }
}

@Injectable({ providedIn: 'root' })
export class DeliveriesApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/deliveries`;

  search(criteria: DeliverySearch): Observable<PageResponse<Delivery>> {
    return this.http.get<PageResponse<Delivery>>(this.url, { params: toParams({ ...criteria }) });
  }

  get(id: number): Observable<Delivery> {
    return this.http.get<Delivery>(`${this.url}/${id}`);
  }

  create(orderId: number, options: { warehouseId?: number | null; plannedGoodsIssueDate?: string | null } = {}): Observable<Delivery> {
    return this.http.post<Delivery>(this.url, { orderId, ...options });
  }

  postGoodsIssue(id: number, postingDate?: string): Observable<Delivery> {
    return this.http.post<Delivery>(`${this.url}/${id}/goods-issue`, { postingDate: postingDate ?? null });
  }

  reverseGoodsIssue(id: number): Observable<Delivery> {
    return this.http.post<Delivery>(`${this.url}/${id}/goods-issue/reverse`, {});
  }

  cancel(id: number): Observable<Delivery> {
    return this.http.post<Delivery>(`${this.url}/${id}/cancel`, {});
  }

  changes(id: number): Observable<ChangeDocument[]> {
    return this.http.get<ChangeDocument[]>(`${this.url}/${id}/changes`);
  }
}

@Injectable({ providedIn: 'root' })
export class InvoicesApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/invoices`;

  search(criteria: InvoiceSearch): Observable<PageResponse<Invoice>> {
    return this.http.get<PageResponse<Invoice>>(this.url, { params: toParams({ ...criteria }) });
  }

  dueList(): Observable<Delivery[]> {
    return this.http.get<Delivery[]>(`${this.url}/due-list`);
  }

  get(id: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.url}/${id}`);
  }

  pdf(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/pdf`, { responseType: 'blob' });
  }

  create(deliveryId: number, billingDate?: string | null): Observable<Invoice> {
    return this.http.post<Invoice>(this.url, { deliveryId, billingDate: billingDate ?? null });
  }

  cancel(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/${id}/cancel`, {});
  }

  postPayment(id: number, paymentDate?: string | null, amount?: number | null): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/${id}/payment`, { paymentDate: paymentDate ?? null, amount: amount ?? null });
  }

  changes(id: number): Observable<ChangeDocument[]> {
    return this.http.get<ChangeDocument[]>(`${this.url}/${id}/changes`);
  }
}
