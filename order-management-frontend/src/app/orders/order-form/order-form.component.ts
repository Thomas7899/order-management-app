// order-management-frontend/src/app/orders/order-form/order-form.component.ts
import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Customer } from '../../services/customer.service';
import { Product } from '../../types/index';
import { OrderService, Order } from '../../services/order.service';
import { ID } from '../../types/index';

interface OrderItemForm {
  productId: ID | null;
  quantity: number;
  price: number;
}

interface OrderFormData {
  customerId: ID | null;
  status: string;
  shippingAddress: string;
  orderItems: OrderItemForm[];
}

@Component({
  selector: 'app-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-form.component.html',
  styleUrls: ['./order-form.component.css']
})
export class OrderFormComponent implements OnInit {
  @Input() order: Order | null = null;
  @Input() customers: Customer[] = [];
  @Input() products: Product[] = [];
  @Output() save = new EventEmitter<Order>();
  @Output() cancel = new EventEmitter<void>();

  currentOrder: OrderFormData = {
    customerId: null,
    status: 'PENDING',
    shippingAddress: '',
    orderItems: []
  };

  constructor(private orderService: OrderService) {}

  ngOnInit() {
    if (this.order) {
      this.currentOrder = {
        customerId: this.order.customer?.id ?? null,
        status: this.order.status,
        shippingAddress: this.order.shippingAddress || '',
        orderItems:
          this.order.orderItems?.map((i) => ({
            productId: i.product?.id ?? null,
            quantity: i.quantity,
            price: i.unitPrice
          })) || []
      };
    }
  }

  addOrderItem() {
    this.currentOrder.orderItems.push({ productId: null, quantity: 1, price: 0 });
  }

  removeOrderItem(i: number) {
    this.currentOrder.orderItems.splice(i, 1);
  }

  updateItemPrice(i: number) {
    const item = this.currentOrder.orderItems[i];
    const product = this.products.find((p) => p.id == item.productId);
    if (product) item.price = product.price;
  }

  getOrderTotal(): number {
    return this.currentOrder.orderItems.reduce((t, i) => t + i.quantity * i.price, 0);
  }

  saveOrder() {
    const orderData: any = {
      status: this.currentOrder.status,
      shippingAddress: this.currentOrder.shippingAddress,
      totalAmount: this.getOrderTotal(),
      customer: this.customers.find((c) => c.id == this.currentOrder.customerId),
      orderItems: this.currentOrder.orderItems.map((item) => ({
        product: this.products.find((p) => p.id == item.productId),
        quantity: item.quantity,
        unitPrice: item.price
      }))
    };

    if (this.order) {
      this.orderService.updateOrder(this.order.id!, orderData).subscribe({
        next: (o) => this.save.emit(o),
        error: (e) => console.error(e)
      });
    } else {
      this.orderService.createOrder(orderData).subscribe({
        next: (o) => this.save.emit(o),
        error: (e) => console.error(e)
      });
    }
  }
}
