import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrderService, Order } from '../services/order.service';
import { CustomerService, Customer } from '../services/customer.service';
import { ProductService, Product } from '../services/product.service';

interface OrderForm {
  customerId: string | number;
  status: string;
  shippingAddress: string;
  orderItems: {
    productId: string | number | undefined;
    quantity: number;
    price: number;
  }[];
}

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="orders-container">
      <div class="header">
        <h2>Bestellungsverwaltung</h2>
        <button class="btn btn-primary" (click)="showAddForm = !showAddForm">
          {{ showAddForm ? 'Abbrechen' : 'Neue Bestellung' }}
        </button>
      </div>

      <!-- Filter und Suche -->
      <div class="filters-bar">
        <div class="search-group">
          <input 
            type="text" 
            [(ngModel)]="searchTerm" 
            (input)="searchOrders()" 
            placeholder="Bestellungen suchen..."
            class="form-control">
        </div>
        <div class="filter-group">
          <select [(ngModel)]="statusFilter" (change)="filterOrders()" class="form-control">
            <option value="">Alle Status</option>
            <option value="PENDING">Ausstehend</option>
            <option value="PROCESSING">In Bearbeitung</option>
            <option value="SHIPPED">Versandt</option>
            <option value="DELIVERED">Geliefert</option>
            <option value="CANCELLED">Storniert</option>
          </select>
        </div>
      </div>

      <!-- Neue Bestellung Formular -->
      <div class="add-form" *ngIf="showAddForm">
        <h3>{{ editingOrder ? 'Bestellung bearbeiten' : 'Neue Bestellung' }}</h3>
        <form (ngSubmit)="saveOrder()" #orderForm="ngForm">
          <div class="form-row">
            <div class="form-group">
              <label for="customer">Kunde *</label>
              <select 
                id="customer"
                [(ngModel)]="currentOrder.customerId" 
                name="customerId"
                required 
                class="form-control">
                <option value="">Kunde wählen</option>
                <option *ngFor="let customer of customers" [value]="customer.id">
                  {{ customer.firstName }} {{ customer.lastName }} ({{ customer.email }})
                </option>
              </select>
            </div>
            <div class="form-group">
              <label for="status">Status *</label>
              <select 
                id="status"
                [(ngModel)]="currentOrder.status" 
                name="status"
                required 
                class="form-control">
                <option value="PENDING">Ausstehend</option>
                <option value="PROCESSING">In Bearbeitung</option>
                <option value="SHIPPED">Versandt</option>
                <option value="DELIVERED">Geliefert</option>
                <option value="CANCELLED">Storniert</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label for="shippingAddress">Lieferadresse</label>
            <textarea 
              id="shippingAddress"
              [(ngModel)]="currentOrder.shippingAddress" 
              name="shippingAddress"
              rows="3"
              class="form-control"></textarea>
          </div>
          
          <!-- Order Items Section -->
          <div class="order-items-section">
            <h4>Bestellpositionen</h4>
            <div class="order-item" *ngFor="let item of currentOrder.orderItems; let i = index">
              <div class="item-row">
                <div class="form-group">
                  <select 
                    [(ngModel)]="item.productId" 
                    [name]="'productId_' + i"
                    (change)="updateItemPrice(i)"
                    class="form-control">
                    <option value="">Produkt wählen</option>
                    <option *ngFor="let product of products" [value]="product.id">
                      {{ product.name }} ({{ product.price | currency:'EUR' }})
                    </option>
                  </select>
                </div>
                <div class="form-group">
                  <input 
                    type="number" 
                    [(ngModel)]="item.quantity" 
                    [name]="'quantity_' + i"
                    (input)="calculateItemTotal(i)"
                    min="1" 
                    placeholder="Menge"
                    class="form-control">
                </div>
                <div class="form-group">
                  <input 
                    type="number" 
                    [(ngModel)]="item.price" 
                    [name]="'price_' + i"
                    step="0.01"
                    placeholder="Einzelpreis"
                    class="form-control"
                    readonly>
                </div>
                <div class="item-total">
                  {{ (item.quantity * item.price) | currency:'EUR' }}
                </div>
                <button type="button" (click)="removeOrderItem(i)" class="btn btn-sm btn-outline-danger">
                  Entfernen
                </button>
              </div>
            </div>
            <button type="button" (click)="addOrderItem()" class="btn btn-sm btn-outline-primary">
              Position hinzufügen
            </button>
          </div>

          <div class="order-total">
            <strong>Gesamtsumme: {{ getOrderTotal() | currency:'EUR' }}</strong>
          </div>

          <div class="form-actions">
            <button type="submit" [disabled]="!orderForm.valid || currentOrder.orderItems.length === 0" class="btn btn-success">
              {{ editingOrder ? 'Aktualisieren' : 'Speichern' }}
            </button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">
              Abbrechen
            </button>
          </div>
        </form>
      </div>

      <!-- Bestellungsliste -->
      <div class="orders-list">
        <div class="list-header">
          <h3>Bestellungen ({{ displayedOrders.length }})</h3>
        </div>
        
        <div class="orders-table">
          <div class="table-header">
            <div class="col-id">ID</div>
            <div class="col-customer">Kunde</div>
            <div class="col-date">Datum</div>
            <div class="col-status">Status</div>
            <div class="col-total">Summe</div>
            <div class="col-actions">Aktionen</div>
          </div>
          
          <div class="order-row" *ngFor="let order of displayedOrders">
            <div class="col-id">#{{ order.id }}</div>
            <div class="col-customer">
              <div class="customer-info">
                <span class="name">{{ order.customer?.firstName }} {{ order.customer?.lastName }}</span>
                <span class="email">{{ order.customer?.email }}</span>
              </div>
            </div>
            <div class="col-date">{{ order.orderDate | date:'dd.MM.yyyy HH:mm' }}</div>
            <div class="col-status">
              <span class="status-badge" [attr.data-status]="order.status">
                {{ getStatusLabel(order.status) }}
              </span>
            </div>
            <div class="col-total">{{ order.totalAmount | currency:'EUR' }}</div>
            <div class="col-actions">
              <button (click)="viewOrderDetails(order)" class="btn btn-sm btn-outline-info">
                Details
              </button>
              <button (click)="editOrder(order)" class="btn btn-sm btn-outline-primary">
                Bearbeiten
              </button>
              <button (click)="deleteOrder(order.id!)" class="btn btn-sm btn-outline-danger">
                Löschen
              </button>
            </div>
          </div>
        </div>

        <div class="no-orders" *ngIf="displayedOrders.length === 0">
          <p>Keine Bestellungen gefunden.</p>
        </div>
      </div>

      <!-- Order Details Modal -->
      <div class="modal" *ngIf="selectedOrder" (click)="closeOrderDetails()">
        <div class="modal-content" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>Bestellung #{{ selectedOrder.id }}</h3>
            <button class="close-btn" (click)="closeOrderDetails()">&times;</button>
          </div>
          <div class="modal-body">
            <div class="order-info">
              <div class="info-section">
                <h4>Kundeninformationen</h4>
                <p><strong>Name:</strong> {{ selectedOrder.customer?.firstName }} {{ selectedOrder.customer?.lastName }}</p>
                <p><strong>E-Mail:</strong> {{ selectedOrder.customer?.email }}</p>
              </div>
              <div class="info-section">
                <h4>Bestelldetails</h4>
                <p><strong>Datum:</strong> {{ selectedOrder.orderDate | date:'dd.MM.yyyy HH:mm' }}</p>
                <p><strong>Status:</strong> {{ getStatusLabel(selectedOrder.status) }}</p>
                <p><strong>Gesamtsumme:</strong> {{ selectedOrder.totalAmount | currency:'EUR' }}</p>
                <p *ngIf="selectedOrder.shippingAddress"><strong>Lieferadresse:</strong></p>
                <div *ngIf="selectedOrder.shippingAddress" class="shipping-address">
                  {{ selectedOrder.shippingAddress }}
                </div>
              </div>
              <div class="info-section">
                <h4>Bestellpositionen</h4>
                <div class="order-items">
                  <div class="item" *ngFor="let item of selectedOrder.orderItems">
                    <div class="item-info">
                      <span class="product-name">{{ item.product?.name || 'Unbekannt' }}</span>
                      <span class="quantity">{{ item.quantity }}x</span>
                      <span class="price">{{ item.unitPrice | currency:'EUR' }}</span>
                    </div>
                    <div class="item-total">{{ (item.quantity * item.unitPrice) | currency:'EUR' }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .orders-container {
      padding: 20px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .header h2 {
      margin: 0;
      color: #333;
    }

    .filters-bar {
      display: flex;
      gap: 15px;
      align-items: center;
      margin-bottom: 20px;
      flex-wrap: wrap;
    }

    .search-group .form-control {
      width: 300px;
    }

    .filter-group .form-control {
      min-width: 150px;
    }

    .add-form {
      background: #f8f9fa;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 30px;
    }

    .add-form h3 {
      margin-top: 0;
      margin-bottom: 20px;
      color: #333;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 15px;
      margin-bottom: 15px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group label {
      margin-bottom: 5px;
      font-weight: 500;
      color: #555;
    }

    .form-control {
      padding: 8px 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 14px;
    }

    .form-control:focus {
      outline: none;
      border-color: #007bff;
      box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
    }

    .order-items-section {
      margin: 20px 0;
      padding: 15px;
      border: 1px solid #ddd;
      border-radius: 4px;
    }

    .order-items-section h4 {
      margin-top: 0;
      margin-bottom: 15px;
      color: #333;
    }

    .order-item {
      margin-bottom: 10px;
    }

    .item-row {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr auto;
      gap: 10px;
      align-items: end;
    }

    .item-total {
      font-weight: bold;
      color: #28a745;
      padding: 8px 0;
    }

    .order-total {
      text-align: right;
      margin: 20px 0;
      font-size: 18px;
      color: #333;
    }

    .form-actions {
      display: flex;
      gap: 10px;
      margin-top: 20px;
    }

    .orders-list {
      background: white;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .list-header h3 {
      margin: 0 0 20px 0;
      color: #333;
    }

    .orders-table {
      width: 100%;
    }

    .table-header, .order-row {
      display: grid;
      grid-template-columns: 80px 2fr 150px 120px 120px 200px;
      gap: 15px;
      padding: 12px 0;
      align-items: center;
    }

    .table-header {
      border-bottom: 2px solid #e9ecef;
      font-weight: 600;
      color: #495057;
    }

    .order-row {
      border-bottom: 1px solid #e9ecef;
    }

    .order-row:hover {
      background-color: #f8f9fa;
    }

    .customer-info .name {
      display: block;
      font-weight: 500;
      color: #333;
    }

    .customer-info .email {
      display: block;
      font-size: 12px;
      color: #6c757d;
    }

    .status-badge {
      padding: 4px 8px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 500;
      text-transform: uppercase;
    }

    .status-badge[data-status="PENDING"] {
      background: #fff3cd;
      color: #856404;
    }

    .status-badge[data-status="PROCESSING"] {
      background: #cce5ff;
      color: #004085;
    }

    .status-badge[data-status="SHIPPED"] {
      background: #d1ecf1;
      color: #0c5460;
    }

    .status-badge[data-status="DELIVERED"] {
      background: #d4edda;
      color: #155724;
    }

    .status-badge[data-status="CANCELLED"] {
      background: #f8d7da;
      color: #721c24;
    }

    .col-actions {
      display: flex;
      gap: 5px;
      flex-wrap: wrap;
    }

    .no-orders {
      text-align: center;
      padding: 40px;
      color: #6c757d;
    }

    /* Modal Styles */
    .modal {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-content {
      background: white;
      border-radius: 8px;
      width: 90%;
      max-width: 800px;
      max-height: 90%;
      overflow: auto;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px;
      border-bottom: 1px solid #e9ecef;
    }

    .modal-header h3 {
      margin: 0;
      color: #333;
    }

    .close-btn {
      background: none;
      border: none;
      font-size: 24px;
      cursor: pointer;
      color: #6c757d;
    }

    .modal-body {
      padding: 20px;
    }

    .info-section {
      margin-bottom: 20px;
    }

    .info-section h4 {
      margin: 0 0 10px 0;
      color: #333;
      border-bottom: 1px solid #e9ecef;
      padding-bottom: 5px;
    }

    .info-section p {
      margin: 5px 0;
    }

    .shipping-address {
      background: #f8f9fa;
      padding: 10px;
      border-radius: 4px;
      white-space: pre-line;
    }

    .order-items .item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid #e9ecef;
    }

    .item-info {
      display: flex;
      gap: 15px;
      align-items: center;
    }

    .product-name {
      font-weight: 500;
    }

    .quantity {
      color: #6c757d;
      min-width: 40px;
    }

    .price {
      color: #28a745;
      min-width: 80px;
    }

    .item-total {
      font-weight: bold;
      color: #333;
    }

    .btn {
      padding: 8px 16px;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      text-decoration: none;
      display: inline-block;
      transition: background-color 0.2s;
    }

    .btn-primary {
      background-color: #007bff;
      color: white;
    }

    .btn-primary:hover {
      background-color: #0056b3;
    }

    .btn-success {
      background-color: #28a745;
      color: white;
    }

    .btn-success:hover {
      background-color: #1e7e34;
    }

    .btn-success:disabled {
      background-color: #6c757d;
      cursor: not-allowed;
    }

    .btn-secondary {
      background-color: #6c757d;
      color: white;
    }

    .btn-secondary:hover {
      background-color: #545b62;
    }

    .btn-sm {
      padding: 5px 10px;
      font-size: 12px;
    }

    .btn-outline-primary {
      background-color: transparent;
      color: #007bff;
      border: 1px solid #007bff;
    }

    .btn-outline-primary:hover {
      background-color: #007bff;
      color: white;
    }

    .btn-outline-info {
      background-color: transparent;
      color: #17a2b8;
      border: 1px solid #17a2b8;
    }

    .btn-outline-info:hover {
      background-color: #17a2b8;
      color: white;
    }

    .btn-outline-danger {
      background-color: transparent;
      color: #dc3545;
      border: 1px solid #dc3545;
    }

    .btn-outline-danger:hover {
      background-color: #dc3545;
      color: white;
    }

    @media (max-width: 768px) {
      .form-row {
        grid-template-columns: 1fr;
      }
      
      .item-row {
        grid-template-columns: 1fr;
        gap: 5px;
      }

      .table-header, .order-row {
        grid-template-columns: 1fr;
        gap: 5px;
      }

      .filters-bar {
        flex-direction: column;
        align-items: stretch;
      }

      .search-group .form-control {
        width: 100%;
      }
    }
  `]
})
export class OrdersComponent implements OnInit {
  orders: Order[] = [];
  displayedOrders: Order[] = [];
  customers: Customer[] = [];
  products: Product[] = [];
  showAddForm = false;
  editingOrder: Order | null = null;
  selectedOrder: Order | null = null;
  searchTerm = '';
  statusFilter = '';
  
  currentOrder: OrderForm = {
    customerId: '',
    status: 'PENDING',
    shippingAddress: '',
    orderItems: []
  };

  constructor(
    private orderService: OrderService,
    private customerService: CustomerService,
    private productService: ProductService
  ) {}

  ngOnInit() {
    this.loadOrders();
    this.loadCustomers();
    this.loadProducts();
  }

  loadOrders() {
    this.orderService.getOrders().subscribe({
      next: (orders) => {
        this.orders = orders;
        this.filterOrders();
      },
      error: (error) => {
        console.error('Fehler beim Laden der Bestellungen:', error);
      }
    });
  }

  loadCustomers() {
    this.customerService.getCustomers().subscribe({
      next: (customers) => {
        this.customers = customers;
      },
      error: (error) => {
        console.error('Fehler beim Laden der Kunden:', error);
      }
    });
  }

  loadProducts() {
    this.productService.getProducts().subscribe({
      next: (products) => {
        this.products = products.filter(p => p.active);
      },
      error: (error) => {
        console.error('Fehler beim Laden der Produkte:', error);
      }
    });
  }

  searchOrders() {
    this.filterOrders();
  }

  filterOrders() {
    this.displayedOrders = this.orders.filter(order => {
      const matchesSearch = !this.searchTerm || 
        order.id?.toString().includes(this.searchTerm) ||
        `${order.customer?.firstName} ${order.customer?.lastName}`.toLowerCase().includes(this.searchTerm.toLowerCase());
      
      const matchesStatus = !this.statusFilter || order.status === this.statusFilter;
      
      return matchesSearch && matchesStatus;
    });
  }

  getStatusLabel(status: string): string {
    const labels: { [key: string]: string } = {
      'PENDING': 'Ausstehend',
      'PROCESSING': 'In Bearbeitung',
      'SHIPPED': 'Versandt',
      'DELIVERED': 'Geliefert',
      'CANCELLED': 'Storniert'
    };
    return labels[status] || status;
  }

  getCustomerName(customerId: number): string {
    const customer = this.customers.find(c => c.id === customerId);
    return customer ? `${customer.firstName} ${customer.lastName}` : 'Unbekannt';
  }

  getCustomerEmail(customerId: number): string {
    const customer = this.customers.find(c => c.id === customerId);
    return customer ? customer.email : '';
  }

  getProductName(productId: number): string {
    const product = this.products.find(p => p.id === productId);
    return product ? product.name : 'Unbekannt';
  }

  addOrderItem() {
    this.currentOrder.orderItems.push({
      productId: '',
      quantity: 1,
      price: 0
    });
  }

  removeOrderItem(index: number) {
    this.currentOrder.orderItems.splice(index, 1);
  }

  updateItemPrice(index: number) {
    const item = this.currentOrder.orderItems[index];
    const product = this.products.find(p => p.id == item.productId);
    if (product) {
      item.price = product.price;
    }
  }

  calculateItemTotal(index: number) {
    // Auto-calculated in template
  }

  getOrderTotal(): number {
    return this.currentOrder.orderItems.reduce((total: number, item: any) => {
      return total + (item.quantity * item.price);
    }, 0);
  }

  saveOrder() {
    const selectedCustomer = this.customers.find(c => c.id == this.currentOrder.customerId);
    const orderData: any = {
      customer: selectedCustomer,
      status: this.currentOrder.status,
      shippingAddress: this.currentOrder.shippingAddress,
      totalAmount: this.getOrderTotal(),
      orderItems: this.currentOrder.orderItems.map(item => {
        const product = this.products.find(p => p.id == item.productId);
        return {
          product: product,
          quantity: item.quantity,
          unitPrice: item.price
        };
      })
    };

    if (this.editingOrder) {
      this.orderService.updateOrder(this.editingOrder.id!, orderData).subscribe({
        next: (order) => {
          const index = this.orders.findIndex(o => o.id === order.id);
          if (index > -1) {
            this.orders[index] = order;
          }
          this.filterOrders();
          this.resetForm();
        },
        error: (error) => {
          console.error('Fehler beim Aktualisieren der Bestellung:', error);
        }
      });
    } else {
      this.orderService.createOrder(orderData).subscribe({
        next: (order) => {
          this.orders.push(order);
          this.filterOrders();
          this.resetForm();
        },
        error: (error) => {
          console.error('Fehler beim Erstellen der Bestellung:', error);
        }
      });
    }
  }

  editOrder(order: Order) {
    this.editingOrder = order;
    this.currentOrder = {
      customerId: order.customer?.id || '',
      status: order.status,
      shippingAddress: order.shippingAddress || '',
      orderItems: order.orderItems?.map(item => ({
        productId: item.product?.id || '',
        quantity: item.quantity,
        price: item.unitPrice
      })) || []
    };
    this.showAddForm = true;
  }

  deleteOrder(id: number) {
    if (confirm('Sind Sie sicher, dass Sie diese Bestellung löschen möchten?')) {
      this.orderService.deleteOrder(id).subscribe({
        next: () => {
          this.orders = this.orders.filter(o => o.id !== id);
          this.filterOrders();
        },
        error: (error) => {
          console.error('Fehler beim Löschen der Bestellung:', error);
        }
      });
    }
  }

  viewOrderDetails(order: Order) {
    this.selectedOrder = order;
  }

  closeOrderDetails() {
    this.selectedOrder = null;
  }

  cancelEdit() {
    this.resetForm();
  }

  private resetForm() {
    this.currentOrder = {
      customerId: '',
      status: 'PENDING',
      shippingAddress: '',
      orderItems: []
    };
    this.editingOrder = null;
    this.showAddForm = false;
  }
}