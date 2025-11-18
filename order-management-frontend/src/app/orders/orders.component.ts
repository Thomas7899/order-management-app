// order-management-frontend/src/app/orders/orders.component.ts
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgxEchartsModule } from 'ngx-echarts';

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
  imports: [
    FormsModule,
    NgxEchartsModule,
    OrderFormComponent,
    OrderListComponent,
    OrderDetailsModalComponent,
  ],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.css'],
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

  chartOptions: any;

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
        this.buildChart();
      },
      error: (e) => console.error(e),
    });
  }

  loadCustomers() {
    this.customerService.getCustomers().subscribe({
      next: (c) => (this.customers = c),
      error: (e) => console.error(e),
    });
  }

  loadProducts() {
    this.productService.getAll().subscribe({
      next: (p: Product[]) => (this.products = p.filter((x) => x.active)),
      error: (e) => console.error(e),
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

      const matchesStatus =
        !this.statusFilter || order.status === this.statusFilter;

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
    this.buildChart();
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
        this.buildChart();
      },
      error: (e) => console.error(e),
    });
  }

  handleView(order: Order) {
    this.selectedOrder = order;
  }

  handleCloseDetails() {
    this.selectedOrder = null;
  }

  buildChart() {
    if (!this.orders.length) {
      this.chartOptions = undefined;
      return;
    }

    const map = new Map<string, { count: number; revenue: number }>();

    this.orders.forEach((o) => {
      const dateKey = new Date(o.orderDate).toISOString().substring(0, 10);
      if (!map.has(dateKey)) {
        map.set(dateKey, { count: 0, revenue: 0 });
      }
      const entry = map.get(dateKey)!;
      entry.count++;
      entry.revenue += o.totalAmount || 0;
    });

    const sortedDates = [...map.keys()].sort();
    const counts = sortedDates.map((d) => map.get(d)!.count);
    const revenues = sortedDates.map((d) => map.get(d)!.revenue);

    this.chartOptions = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      legend: {
        data: ['Bestellungen', 'Umsatz (€)'],
        textStyle: { color: '#7cc7ff' },
      },
      xAxis: {
        type: 'category',
        data: sortedDates,
        axisLabel: { color: '#7cc7ff' },
        axisLine: { lineStyle: { color: '#00c8ff' } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#7cc7ff' },
        axisLine: { lineStyle: { color: '#00c8ff' } },
        splitLine: { lineStyle: { color: 'rgba(0,200,255,0.1)' } },
      },
      series: [
        {
          name: 'Bestellungen',
          type: 'line',
          smooth: true,
          data: counts,
          itemStyle: { color: '#00c8ff' },
          lineStyle: { width: 3 },
        },
        {
          name: 'Umsatz (€)',
          type: 'line',
          smooth: true,
          data: revenues,
          itemStyle: { color: '#00d084' },
          lineStyle: { width: 3 },
        },
      ],
    };
  }
}
