// order-management-frontend/src/app/orders/order-details-modal/order-details-modal.component.ts
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Order } from '../../services/order.service';

@Component({
    selector: 'app-order-details-modal',
    imports: [CommonModule],
    templateUrl: './order-details-modal.component.html',
    styleUrls: ['./order-details-modal.component.css']
})
export class OrderDetailsModalComponent {
  @Input() order!: Order;
  @Output() close = new EventEmitter<void>();
}
