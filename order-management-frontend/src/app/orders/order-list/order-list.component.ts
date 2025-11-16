// order-management-frontend/src/app/orders/order-list/order-list.component.ts
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Order } from '../../services/order.service';

@Component({
    selector: 'app-order-list',
    imports: [CommonModule],
    templateUrl: './order-list.component.html',
    styleUrls: ['./order-list.component.css']
})
export class OrderListComponent {
  @Input() orders: Order[] = [];
  @Output() view = new EventEmitter<Order>();
  @Output() edit = new EventEmitter<Order>();
  @Output() delete = new EventEmitter<number>();

  getStatusLabel(s: string) {
    const labels: any = {
      PENDING: 'Ausstehend',
      PROCESSING: 'In Bearbeitung',
      SHIPPED: 'Versandt',
      DELIVERED: 'Geliefert',
      CANCELLED: 'Storniert'
    };
    return labels[s] || s;
  }
}
