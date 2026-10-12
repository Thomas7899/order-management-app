// Typen für die REST-API des Backends (Spiegel der Java-DTOs).

export interface CodeText {
  code: string;
  text: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Problem {
  title?: string;
  status?: number;
  detail?: string;
  messageId?: string;
  errors?: Record<string, string>;
}

// ---------------------------------------------------------------------------
// Anmeldung
// ---------------------------------------------------------------------------

export type RoleCode = 'SALES' | 'WAREHOUSE' | 'BILLING' | 'CONTROLLING' | 'ADMIN';

export interface RoleInfo {
  code: RoleCode;
  text: string;
  description: string;
}

export interface UserInfo {
  id: number;
  username: string;
  fullName: string;
  email: string;
  roles: RoleInfo[];
  active: boolean;
  lastLoginAt?: string;
  createdAt?: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: UserInfo;
}

export interface DemoUser {
  username: string;
  fullName: string;
  roles: RoleInfo[];
}

// ---------------------------------------------------------------------------
// Stammdaten
// ---------------------------------------------------------------------------

export type PaymentTermsCode = 'ZB01' | 'ZB30' | 'ZB14' | 'ZB00';
export type TaxCode = 'FULL' | 'REDUCED' | 'EXEMPT';

export interface Partner {
  id: number;
  customerNumber: string;
  name: string;
  email: string;
  city?: string;
  country?: string;
}

export interface Customer {
  id: number;
  customerNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  zipCode?: string;
  country?: string;
  paymentTerms: PaymentTermsCode;
  paymentTermsText?: string;
  creditLimit?: number | null;
  discountPercent?: number;
  active: boolean;
  createdAt?: string;
}

export type CustomerRequest = Omit<Customer, 'id' | 'customerNumber' | 'fullName' | 'paymentTermsText' | 'createdAt'>;

export interface CreditExposure {
  customerId: number;
  customerNumber: string;
  creditLimit: number | null;
  openOrderValue: number;
  openItemsValue: number;
  totalExposure: number;
  availableCredit: number | null;
  utilizationPercent: number | null;
}

export interface Material {
  id: number;
  materialNumber: string;
  name: string;
  description?: string;
  price: number;
  costPrice?: number | null;
  stockQuantity: number;
  category?: string;
  unitOfMeasure: string;
  taxCode: TaxCode;
  imageUrl?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface MaterialRequest {
  name: string;
  description?: string;
  price: number;
  costPrice?: number | null;
  category?: string;
  unitOfMeasure?: string;
  taxCode?: TaxCode;
  imageUrl?: string;
  active?: boolean;
}

// ---------------------------------------------------------------------------
// Vertrieb
// ---------------------------------------------------------------------------

export interface Condition {
  stepNumber: number;
  conditionType: string;
  description: string;
  rate: number;
  rateUnit: string;
  conditionValue: number;
  statistical: boolean;
}

export interface OrderItem {
  id: number;
  itemNumber: number;
  productId: number;
  materialNumber: string;
  productName: string;
  unitOfMeasure: string;
  quantity: number;
  confirmedQuantity: number;
  deliveredQuantity: number;
  billedQuantity: number;
  openQuantity: number;
  unitPrice: number;
  itemDiscountPercent: number;
  netAmount: number;
  taxRate: number;
  taxAmount: number;
  grossAmount: number;
  rejectionReason?: CodeText | null;
  conditions: Condition[];
}

export interface Order {
  id: number;
  version: number;
  orderNumber: string;
  orderType: string;
  customer: Partner;
  orderDate: string;
  requestedDeliveryDate?: string;
  customerReference?: string;
  paymentTerms?: CodeText;
  currency: string;
  status: CodeText;
  deliveryStatus: CodeText;
  billingStatus: CodeText;
  creditStatus: CodeText;
  netAmount: number;
  taxAmount: number;
  totalAmount: number;
  notes?: string;
  shippingAddress?: string;
  billingAddress?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  items: OrderItem[];
}

export interface OrderListItem {
  id: number;
  orderNumber: string;
  customer: Partner;
  orderDate: string;
  requestedDeliveryDate?: string;
  customerReference?: string;
  status: CodeText;
  deliveryStatus: CodeText;
  billingStatus: CodeText;
  creditStatus: CodeText;
  netAmount: number;
  totalAmount: number;
  currency: string;
  itemCount: number;
}

export interface OrderItemRequest {
  productId: number;
  quantity: number;
  discountPercent?: number | null;
}

export interface OrderCreateRequest {
  customerId: number;
  requestedDeliveryDate?: string | null;
  customerReference?: string | null;
  shippingAddress?: string | null;
  billingAddress?: string | null;
  notes?: string | null;
  items: OrderItemRequest[];
}

export interface SimulationMessage {
  type: 'S' | 'I' | 'W' | 'E';
  messageId: string;
  text: string;
}

export interface SimulationItem {
  itemNumber: number;
  productId: number;
  materialNumber: string;
  productName: string;
  unitOfMeasure: string;
  quantity: number;
  confirmedQuantity: number;
  availableQuantity: number;
  unitPrice: number;
  netAmount: number;
  taxRate: number;
  taxAmount: number;
  grossAmount: number;
  conditions: Condition[];
}

export interface OrderSimulation {
  items: SimulationItem[];
  netAmount: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  creditStatus: CodeText;
  creditExposure: CreditExposure;
  paymentTerms?: CodeText;
  messages: SimulationMessage[];
}

export type DocumentType =
  | 'SALES_ORDER_REQUEST' | 'SALES_ORDER' | 'DELIVERY' | 'GOODS_ISSUE' | 'GOODS_ISSUE_REVERSAL'
  | 'INVOICE' | 'CANCELLATION_INVOICE' | 'PAYMENT';

export interface DocumentFlowNode {
  documentType: DocumentType;
  documentTypeText: string;
  id: number;
  number: string;
  parentNumber?: string;
  level: number;
  date: string;
  status: CodeText;
  amount?: number | null;
}

export interface DocumentFlow {
  orderNumber: string;
  nodes: DocumentFlowNode[];
}

export interface ChangeDocument {
  id: number;
  objectClass: string;
  objectNumber?: string;
  changeType: 'INSERT' | 'UPDATE' | 'DELETE' | 'EVENT';
  fieldName?: string;
  oldValue?: string;
  newValue?: string;
  description?: string;
  changedBy: string;
  changedAt: string;
}

export interface DeliveryItem {
  itemNumber: number;
  orderItemNumber: number;
  productId: number;
  materialNumber: string;
  productName: string;
  unitOfMeasure: string;
  quantity: number;
  binLocation?: string;
}

export interface Delivery {
  id: number;
  version: number;
  deliveryNumber: string;
  deliveryType: string;
  orderId: number;
  orderNumber: string;
  shipTo: Partner;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  status: CodeText;
  billingStatus: CodeText;
  plannedGoodsIssueDate: string;
  goodsIssueDate?: string;
  goodsIssueDocument?: string;
  shippingAddress?: string;
  totalQuantity: number;
  createdBy?: string;
  createdAt: string;
  items: DeliveryItem[];
}

export interface InvoiceItem {
  itemNumber: number;
  orderItemNumber: number;
  productId: number;
  materialNumber: string;
  productName: string;
  unitOfMeasure: string;
  quantity: number;
  unitNetPrice: number;
  netAmount: number;
  taxRate: number;
  taxAmount: number;
  grossAmount: number;
}

export interface Invoice {
  id: number;
  version: number;
  invoiceNumber: string;
  billingType: CodeText;
  orderId: number;
  orderNumber: string;
  deliveryId?: number;
  deliveryNumber?: string;
  referenceInvoiceNumber?: string;
  payer: Partner;
  billingAddress?: string;
  billingDate: string;
  paymentTerms?: CodeText;
  dueDate?: string;
  cashDiscountDate?: string;
  cashDiscountPercent?: number;
  currency: string;
  netAmount: number;
  taxAmount: number;
  grossAmount: number;
  costAmount: number;
  marginAmount: number;
  status: CodeText;
  overdue: boolean;
  daysOverdue: number;
  paidAt?: string;
  paidAmount?: number;
  cashDiscountAmount?: number;
  createdBy?: string;
  createdAt: string;
  items: InvoiceItem[];
}

// ---------------------------------------------------------------------------
// Bestandsführung
// ---------------------------------------------------------------------------

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
  productCount?: number;
  totalStock?: number;
  inventoryValue?: number;
}

export type StockStatus = 'OK' | 'LOW' | 'OUT' | 'OVER';

export interface WarehouseStock {
  id: number;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  productId: number;
  materialNumber: string;
  productName: string;
  unitOfMeasure: string;
  productCategory?: string;
  productPrice: number;
  quantity: number;
  minStock: number;
  maxStock: number;
  binLocation?: string;
  lastCountedAt?: string;
  stockValue: number;
  stockStatus: StockStatus;
  reorderQuantity?: number;
}

export type MovementType =
  | 'INITIAL_ENTRY' | 'GOODS_RECEIPT' | 'GOODS_ISSUE' | 'DELIVERY_ISSUE'
  | 'TRANSFER' | 'INVENTORY_ADJUSTMENT' | 'RETURN' | 'SCRAP';

export interface StockMovement {
  id: number;
  movementNumber: string;
  documentItem: number;
  movementType: MovementType;
  movementCode: string;
  movementTypeDisplay: string;
  productId: number;
  materialNumber: string;
  productName: string;
  sourceWarehouseId?: number;
  sourceWarehouseCode?: string;
  sourceWarehouseName?: string;
  targetWarehouseId?: number;
  targetWarehouseCode?: string;
  targetWarehouseName?: string;
  stockEffect: number;
  quantity: number;
  quantityBefore?: number;
  quantityAfter?: number;
  reason?: string;
  referenceNumber?: string;
  referenceType?: string;
  reversed: boolean;
  reversedMovementNumber?: string;
  postingDate: string;
  createdBy?: string;
  createdAt: string;
}

export interface StockMovementRequest {
  movementType: MovementType;
  productId: number;
  sourceWarehouseId?: number | null;
  targetWarehouseId?: number | null;
  quantity: number;
  reason?: string;
  referenceNumber?: string;
  postingDate?: string;
}

export interface StorageLocationStock {
  warehouseId: number;
  code: string;
  name: string;
  stock: number;
  openDeliveryQuantity: number;
  binLocation?: string;
}

export interface Availability {
  productId: number;
  materialNumber: string;
  productName: string;
  unitOfMeasure: string;
  stock: number;
  reservedForOrders: number;
  reservedForDeliveries: number;
  available: number;
  storageLocations: StorageLocationStock[];
}

// ---------------------------------------------------------------------------
// Launchpad und Auswertungen
// ---------------------------------------------------------------------------

export interface DashboardStats {
  totalCustomers: number;
  totalProducts: number;
  totalOrders: number;
  ordersByStatus: Record<string, number>;
  totalRevenue: number;
  openOrderValue: number;
  todayRevenue: number;
  monthRevenue: number;
  lowStockProductsCount: number;
  openDeliveries: number;
  billingDueCount: number;
  openReceivablesCount: number;
  openReceivables: number;
  overdueReceivablesCount: number;
  overdueReceivables: number;
  creditBlockedOrders: number;
  backlogItems: number;
  ordersToday: number;
}

export interface RecentActivity {
  recentOrders: OrderListItem[];
  lowStockItems: WarehouseStock[];
}

export interface TrendData {
  period: string;
  value: number;
  previousValue?: number | null;
  changePercentage?: number | null;
}

export interface KpiDashboard {
  financial: {
    revenueLast12Months: number;
    revenueThisMonth: number;
    revenueLastMonth: number;
    revenueGrowth: number;
    revenueYearToDate: number;
    averageOrderValue: number;
    grossMarginAmount: number;
    grossMarginPercent: number;
    revenuePerCustomer: number;
    openReceivables: number;
    overdueReceivables: number;
    daysSalesOutstanding: number;
  };
  operational: {
    totalOrders: number;
    ordersThisMonth: number;
    orderIntakeThisMonth: number;
    openOrders: number;
    inProcessOrders: number;
    completedOrders: number;
    cancelledOrders: number;
    creditBlockedOrders: number;
    onTimeDeliveryRate: number;
    averageLeadTimeDays: number;
    cancellationRate: number;
    averageOrdersPerDay: number;
  };
  customer: {
    totalCustomers: number;
    newCustomersThisMonth: number;
    activeCustomers: number;
    activeCustomerRate: number;
    averagePurchaseFrequency: number;
    customerLifetimeValue: number;
    customersByCountry: Record<string, number>;
  };
  inventory: {
    totalProducts: number;
    activeProducts: number;
    totalInventoryValue: number;
    lowStockItems: number;
    outOfStockItems: number;
    stockTurnoverRate: number;
    daysOfSupply: number;
    backlogItems: number;
    inventoryByCategory: Record<string, number>;
  };
  revenueTrend: TrendData[];
  orderTrend: TrendData[];
}

export interface AbcItem {
  id: number;
  number: string;
  name: string;
  category?: string;
  revenue: number;
  revenuePercentage: number;
  cumulativePercentage: number;
  abcClass: 'A' | 'B' | 'C';
  orderCount: number;
  quantitySold: number;
}

export interface AbcAnalysis {
  items: AbcItem[];
  summary: {
    aClassCount: number;
    bClassCount: number;
    cClassCount: number;
    aClassRevenue: number;
    bClassRevenue: number;
    cClassRevenue: number;
    aClassPercentage: number;
    bClassPercentage: number;
    cClassPercentage: number;
  };
}

export interface ForecastPeriod {
  date: string;
  period: string;
  actualRevenue?: number | null;
  forecastedRevenue?: number | null;
  orderCount: number;
  growthRate?: number | null;
  isForecasted?: boolean;
  forecasted?: boolean;
}

export interface SalesForecast {
  historicalData: ForecastPeriod[];
  forecastData: ForecastPeriod[];
  summary: {
    averageMonthlyRevenue: number;
    predictedNextMonth: number;
    predictedNextQuarter: number;
    growthTrend: number;
    trendDirection: 'UP' | 'DOWN' | 'STABLE';
    confidenceLevel: number;
    method: string;
  };
}
