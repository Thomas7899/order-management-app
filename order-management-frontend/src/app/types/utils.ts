import { ID, BaseEntity } from './index';

// Utility Types für erweiterte TypeScript Features
export type Nullable<T> = T | null;
export type Optional<T> = T | undefined;
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

// Generic CRUD Operations
export interface CrudService<T extends BaseEntity, CreateReq, UpdateReq> {
  getAll(): Promise<T[]>;
  getById(id: ID): Promise<T | null>;
  create(data: CreateReq): Promise<T>;
  update(data: UpdateReq): Promise<T>;
  delete(id: ID): Promise<void>;
}

// Event Types für Component Communication
export interface ComponentEvent<T = any> {
  type: string;
  payload: T;
  timestamp: number;
}

// Generic Table Column Definition
export interface TableColumn<T> {
  key: keyof T;
  label: string;
  sortable?: boolean;
  filterable?: boolean;
  width?: string;
  align?: 'left' | 'center' | 'right';
  render?: (value: T[keyof T], row: T) => string | HTMLElement;
}

// Validation Types
export type ValidationRule<T> = (value: T) => string | null;
export type ValidationRules<T> = Partial<Record<keyof T, ValidationRule<T[keyof T]>[]>>;

// Sort & Filter Types
export interface SortConfig<T> {
  field: keyof T;
  direction: 'asc' | 'desc';
}

export interface FilterConfig<T> {
  field: keyof T;
  operator: 'eq' | 'contains' | 'startsWith' | 'endsWith' | 'gt' | 'lt' | 'gte' | 'lte';
  value: any;
}

// Theme Types
export interface Theme {
  name: string;
  colors: {
    primary: string;
    secondary: string;
    success: string;
    warning: string;
    error: string;
    background: string;
    surface: string;
    text: string;
  };
  spacing: {
    xs: string;
    sm: string;
    md: string;
    lg: string;
    xl: string;
  };
  breakpoints: {
    mobile: string;
    tablet: string;
    desktop: string;
  };
}

// Route & Navigation Types
export interface RouteConfig {
  path: string;
  title: string;
  icon?: string;
  component: any;
  requiresAuth?: boolean;
  roles?: string[];
}

// Configuration Types
export interface AppConfig {
  apiBaseUrl: string;
  version: string;
  environment: 'development' | 'staging' | 'production';
  features: {
    enablePwa: boolean;
    enableNotifications: boolean;
    enableOfflineMode: boolean;
  };
}

// Generic Repository Pattern
export interface Repository<T extends BaseEntity> {
  findAll(filter?: any): Promise<T[]>;
  findById(id: ID): Promise<T | null>;
  save(entity: Partial<T>): Promise<T>;
  delete(id: ID): Promise<void>;
  count(filter?: any): Promise<number>;
}

// Observer Pattern für Event Handling
export interface Observer<T> {
  update(data: T): void;
}

export interface Observable<T> {
  subscribe(observer: Observer<T>): void;
  unsubscribe(observer: Observer<T>): void;
  notify(data: T): void;
}