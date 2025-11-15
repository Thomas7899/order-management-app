// order-management-frontend/src/app/customers/customer-detail/customer-detail.component.ts
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';

import { CustomerService, Customer } from '../../services/customer.service';
import { OrderService, Order } from '../../services/order.service';

@Component({
  standalone: true,
  selector: 'app-customer-detail',
  imports: [CommonModule],
  templateUrl: './customer-detail.component.html',
  styleUrls: ['./customer-detail.component.css']
})
export class CustomerDetailComponent implements OnInit {

  customer!: Customer;
  orders: Order[] = [];
  isLoading = true;

  constructor(
    private route: ActivatedRoute,
    private customerService: CustomerService,
    private orderService: OrderService
  ) {}

  ngOnInit() {
    const customerId = Number(this.route.snapshot.paramMap.get('id'));

    this.customerService.getCustomer(customerId).subscribe(customer => {
      this.customer = customer;
      this.loadOrders(customerId);
    });
  }

  loadOrders(customerId: number) {
    this.orderService.getOrdersByCustomer(customerId).subscribe(orders => {
      this.orders = orders;
      this.isLoading = false;
    });
  }
}
