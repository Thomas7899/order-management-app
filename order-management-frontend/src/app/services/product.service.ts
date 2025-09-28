import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, throwError, of } from 'rxjs';
import { 
  Product, 
  CreateProductRequest, 
  UpdateProductRequest, 
  ProductFilter,
  ApiResponse,
  ID,
  ProductCategory,
  CrudService
} from '../types/index';

@Injectable({
  providedIn: 'root'
})
export class ProductService implements CrudService<Product, CreateProductRequest, UpdateProductRequest> {
  private readonly apiUrl = 'http://localhost:8080/api/products';

  constructor(private readonly http: HttpClient) {}

  // CRUD Operations (implementing CrudService interface)
  getAll(): Observable<Product[]> {
    return this.http.get<Product[]>(this.apiUrl)
      .pipe(
        catchError(this.handleError)
      );
  }

  getById(id: number): Observable<Product | null> {
    return this.http.get<Product>(`${this.apiUrl}/${id}`)
      .pipe(
        map(product => product),
        catchError(error => {
          if (error.status === 404) {
            return of(null);
          }
          return throwError(() => error);
        })
      );
  }

  create(productData: CreateProductRequest): Observable<Product> {
    return this.http.post<Product>(this.apiUrl, productData)
      .pipe(
        catchError(this.handleError)
      );
  }

  update(id: number, updateData: UpdateProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}`, updateData)
      .pipe(
        catchError(this.handleError)
      );
  }

  delete(id: number): Observable<boolean> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`)
      .pipe(
        map(() => true),
        catchError(error => {
          console.error('Error deleting product:', error);
          return of(false);
        })
      );
  }

  // Extended Product-specific methods
  getWithFilter(filter: ProductFilter): Observable<Product[]> {
    let params = new HttpParams();
    
    if (filter.search) params = params.set('search', filter.search);
    if (filter.category) params = params.set('category', filter.category);
    if (filter.active !== undefined) params = params.set('active', filter.active.toString());
    if (filter.minPrice) params = params.set('minPrice', filter.minPrice.toString());
    if (filter.maxPrice) params = params.set('maxPrice', filter.maxPrice.toString());
    if (filter.inStock !== undefined) params = params.set('inStock', filter.inStock.toString());

    return this.http.get<Product[]>(`${this.apiUrl}/filter`, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  getActiveProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.apiUrl}/active`)
      .pipe(
        catchError(this.handleError)
      );
  }

  getLowStockProducts(threshold: number = 10): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.apiUrl}/low-stock?threshold=${threshold}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  getCategories(): Observable<ProductCategory[]> {
    return this.http.get<ProductCategory[]>(`${this.apiUrl}/categories`)
      .pipe(
        catchError(this.handleError)
      );
  }

  updateStock(id: ID, quantity: number): Observable<Product> {
    return this.http.patch<Product>(`${this.apiUrl}/${id}/stock`, { quantity })
      .pipe(
        catchError(this.handleError)
      );
  }

  toggleActive(id: ID): Observable<Product> {
    return this.http.patch<Product>(`${this.apiUrl}/${id}/toggle-active`, {})
      .pipe(
        catchError(this.handleError)
      );
  }

  // Error handling
  private handleError = (error: any): Observable<never> => {
    console.error('ProductService Error:', error);
    return throwError(() => ({
      status: error.status || 500,
      message: error.error?.message || 'An unexpected error occurred',
      details: error.error?.details || [],
      timestamp: new Date().toISOString()
    }));
  };
}