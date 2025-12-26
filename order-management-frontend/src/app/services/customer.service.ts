// customer.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface Customer {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  zipCode?: string;
  country?: string;
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class CustomerService {
  private apiUrl = `${environment.apiUrl}/api/customers`;

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

  getCustomers(): Observable<Customer[]> {
    return this.http.get<unknown>(this.apiUrl).pipe(
      map(res => this.unwrapCollection<Customer>(res))
    );
  }

  getCustomer(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${this.apiUrl}/${id}`);
  }

  createCustomer(customer: Customer): Observable<Customer> {
    return this.http.post<Customer>(this.apiUrl, customer);
  }

  updateCustomer(id: number, customer: Customer): Observable<Customer> {
    return this.http.put<Customer>(`${this.apiUrl}/${id}`, customer);
  }

  deleteCustomer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  searchCustomers(query: string): Observable<Customer[]> {
    return this.http.get<unknown>(`${this.apiUrl}/search?query=${query}`).pipe(
      map(res => this.unwrapCollection<Customer>(res))
    );
  }

  getCustomerByEmail(email: string): Observable<Customer> {
    return this.http.get<Customer>(`${this.apiUrl}/search/email?email=${email}`);
  }

  getCustomerCount(): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/count`);
  }
}