// order-management-frontend/src/app/orders/orders.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrderService, Order } from '../services/order.service';
import { CustomerService, Customer } from '../services/customer.service';
import { ProductService } from '../services/product.service';
import { Product } from '../types/index';
import { OrderFormComponent } from './order-form/order-form.component';
import { OrderListComponent } from './order-list/order-list.component';
import { OrderDetailsModalComponent } from './order-details-modal/order-details-modal.component';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, OrderFormComponent, OrderListComponent, OrderDetailsModalComponent],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.css']
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
      error: (e) => console.error(e)
    });
  }

  loadCustomers() {
    this.customerService.getCustomers().subscribe({
      next: (c) => (this.customers = c),
      error: (e) => console.error(e)
    });
  }

  loadProducts() {
    this.productService.getAll().subscribe({
      next: (p: Product[]) => (this.products = p.filter((x) => x.active)),
      error: (e) => console.error(e)
    });
  }

  filterOrders() {
    this.displayedOrders = this.orders.filter((order) => {
      const matchesSearch =
        !this.searchTerm ||
        order.id?.toString().includes(this.searchTerm) ||
        `${order.customer?.firstName} ${order.customer?.lastName}`
          .toLowerCase()
          .includes(this.searchTerm.toLowerCase());
      const matchesStatus = !this.statusFilter || order.status === this.statusFilter;
      return matchesSearch && matchesStatus;
    });
  }

  searchOrders() {
    this.filterOrders();
  }

  toggleForm() {
    this.showAddForm = !this.showAddForm;
    this.editingOrder = null;
  }

  handleSave(order: Order) {
    if (this.editingOrder) {
      const index = this.orders.findIndex((o) => o.id === order.id);
      if (index > -1) this.orders[index] = order;
    } else {
      this.orders.push(order);
    }
    this.filterOrders();
    this.showAddForm = false;
    this.editingOrder = null;
  }

  handleEdit(order: Order) {
    this.editingOrder = order;
    this.showAddForm = true;
  }

  handleDelete(id: number) {
    this.orderService.deleteOrder(id).subscribe({
      next: () => {
        this.orders = this.orders.filter((o) => o.id !== id);
        this.filterOrders();
      },
      error: (e) => console.error(e)
    });
  }

  handleView(order: Order) {
    this.selectedOrder = order;
  }

  handleCloseDetails() {
    this.selectedOrder = null;
  }
}
