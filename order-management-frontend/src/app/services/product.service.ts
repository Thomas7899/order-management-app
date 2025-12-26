import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, throwError, of } from 'rxjs';
import { Product, CreateProductRequest, UpdateProductRequest, ProductFilter, ID, ProductCategory } from '../types';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl = `${environment.apiUrl}/api/products`;

  constructor(private readonly http: HttpClient) {}

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

  getAll(): Observable<Product[]> {
    return this.http.get<unknown>(this.apiUrl).pipe(
      map(res => this.unwrapCollection<Product>(res)),
      catchError(this.handleError)
    );
  }

  getById(id: ID): Observable<Product | null> {
    return this.http.get<Product>(`${this.apiUrl}/${id}`).pipe(
      map(p => p),
      catchError(e => e.status === 404 ? of(null) : throwError(() => e))
    );
  }

  create(data: CreateProductRequest): Observable<Product> {
    return this.http.post<Product>(this.apiUrl, data).pipe(catchError(this.handleError));
  }

  update(id: ID, data: UpdateProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}`, data).pipe(catchError(this.handleError));
  }

  delete(id: ID): Observable<boolean> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      map(() => true),
      catchError(() => of(false))
    );
  }

  getWithFilter(filter: ProductFilter): Observable<Product[]> {
    let params = new HttpParams();
    if (filter.search) params = params.set('search', filter.search);
    if (filter.category) params = params.set('category', filter.category);
    if (filter.active !== undefined) params = params.set('active', filter.active);
    if (filter.minPrice) params = params.set('minPrice', filter.minPrice);
    if (filter.maxPrice) params = params.set('maxPrice', filter.maxPrice);
    if (filter.inStock !== undefined) params = params.set('inStock', filter.inStock);
    return this.http.get<unknown>(`${this.apiUrl}/filter`, { params }).pipe(
      map(res => this.unwrapCollection<Product>(res)),
      catchError(this.handleError)
    );
  }

  getActiveProducts(): Observable<Product[]> {
    return this.http.get<unknown>(`${this.apiUrl}/active`).pipe(
      map(res => this.unwrapCollection<Product>(res)),
      catchError(this.handleError)
    );
  }

  getLowStockProducts(threshold: number = 10): Observable<Product[]> {
    return this.http.get<unknown>(`${this.apiUrl}/low-stock?threshold=${threshold}`).pipe(
      map(res => this.unwrapCollection<Product>(res)),
      catchError(this.handleError)
    );
  }

  getCategories(): Observable<ProductCategory[]> {
    return this.http.get<ProductCategory[]>(`${this.apiUrl}/categories`).pipe(catchError(this.handleError));
  }

  updateStock(id: ID, quantity: number): Observable<Product> {
    return this.http.patch<Product>(`${this.apiUrl}/${id}/stock`, { quantity }).pipe(catchError(this.handleError));
  }

  toggleActive(id: ID): Observable<Product> {
    return this.http.patch<Product>(`${this.apiUrl}/${id}/toggle-active`, {}).pipe(catchError(this.handleError));
  }

  private handleError = (error: any): Observable<never> => {
    return throwError(() => ({
      status: error.status || 500,
      message: error.error?.message || 'An unexpected error occurred',
      details: error.error?.details || [],
      timestamp: new Date().toISOString()
    }));
  };
}
