// === IMPORTS ===
import { Observable } from 'rxjs';

// Base Types
export type ID = string | number;
export type Timestamp = string; // ISO 8601 format

// Enums für bessere Type Safety
export enum OrderStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED', 
  PROCESSING = 'PROCESSING',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  CANCELLED = 'CANCELLED'
}

export enum ProductCategory {
  ELEKTRONIK = 'Elektronik',
  MOEBEL = 'Möbel',
  BELEUCHTUNG = 'Beleuchtung',
  BUEROBEDARF = 'Bürobedarf',
  KLEIDUNG = 'Kleidung',
  HAUSHALT = 'Haushalt'
}

// Base Entity Interface
export interface BaseEntity {
  readonly id: ID;
  readonly createdAt: Timestamp;
  readonly updatedAt: Timestamp;
}

// API Response Wrapper
export interface ApiResponse<T> {
  data: T;
  success: boolean;
  message?: string;
  errors?: string[];
}

export interface PaginatedResponse<T> extends ApiResponse<T[]> {
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

// Product Interfaces
export interface Product extends BaseEntity {
  name: string;
  description?: string;
  price: number;
  stockQuantity: number;
  category: ProductCategory;
  imageUrl?: string;
  active: boolean;
  inStock: boolean;
}

export interface CreateProductRequest {
  name: string;
  description?: string;
  price: number;
  stockQuantity: number;
  category: ProductCategory;
  imageUrl?: string;
  active?: boolean;
}

export interface UpdateProductRequest extends Partial<CreateProductRequest> {
  id: ID;
}

// Customer Interfaces
export interface Customer extends BaseEntity {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  zipCode?: string;
  country?: string;
  active: boolean;
}

export interface CreateCustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  zipCode?: string;
  country?: string;
  active?: boolean;
}

export interface UpdateCustomerRequest extends Partial<CreateCustomerRequest> {
  id: ID;
}

// Order Item Interface
export interface OrderItem extends BaseEntity {
  productId: ID;
  product: Product;
  quantity: number;
  price: number;
  total: number;
}

export interface CreateOrderItemRequest {
  productId: ID;
  quantity: number;
  price: number;
}

// Order Interfaces
export interface Order extends BaseEntity {
  orderNumber: string;
  customerId: ID;
  customer: Customer;
  orderItems: OrderItem[];
  status: OrderStatus;
  orderDate: Timestamp;
  totalAmount: number;
  shippingAddress?: string;
  billingAddress?: string;
}

export interface CreateOrderRequest {
  customerId: ID;
  orderItems: CreateOrderItemRequest[];
  shippingAddress?: string;
  billingAddress?: string;
}

export interface UpdateOrderRequest {
  id: ID;
  status?: OrderStatus;
  shippingAddress?: string;
  billingAddress?: string;
}

// User Interfaces
export interface User extends BaseEntity {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  active: boolean;
  roles: UserRole[];
}

export interface UserRole {
  id: ID;
  name: string;
  permissions: string[];
}

export interface CreateUserRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  active?: boolean;
}

// Dashboard Types
export interface DashboardStats {
  totalOrders: number;
  totalCustomers: number;
  totalProducts: number;
  totalRevenue: number;
  lowStockProductsCount: number;
  pendingOrdersCount: number;
  monthlyRevenue: number;
  weeklyOrders: number;
}

export interface RecentActivity {
  recentOrders: Order[];
  lowStockProducts: Product[];
  recentCustomers: Customer[];
}

// Search & Filter Types
export interface ProductFilter {
  search?: string;
  category?: ProductCategory;
  active?: boolean;
  minPrice?: number;
  maxPrice?: number;
  inStock?: boolean;
}

export interface CustomerFilter {
  search?: string;
  active?: boolean;
  country?: string;
  city?: string;
}

export interface OrderFilter {
  search?: string;
  status?: OrderStatus;
  customerId?: ID;
  dateFrom?: string;
  dateTo?: string;
}

// Form State Types
export interface FormState<T> {
  data: T;
  errors: Partial<Record<keyof T, string>>;
  isLoading: boolean;
  isValid: boolean;
  isDirty: boolean;
}

// HTTP Error Types
export interface HttpError {
  status: number;
  message: string;
  details?: string[];
  timestamp: Timestamp;
}

// Loading States
export type LoadingState = 'idle' | 'loading' | 'success' | 'error';

export interface AsyncState<T> {
  data: T | null;
  status: LoadingState;
  error: HttpError | null;
}

// === SERVICE INTERFACES ===

// Generisches CRUD Service Interface für Angular (mit Observables)
export interface CrudService<T, CreateRequest = Partial<T>, UpdateRequest = Partial<T>> {
  getAll(): Observable<T[]>;
  getById(id: number): Observable<T | null>;
  create(item: CreateRequest): Observable<T>;
  update(id: number, item: UpdateRequest): Observable<T>;
  delete(id: number): Observable<boolean>;
}