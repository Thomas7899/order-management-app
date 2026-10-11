import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsOption } from 'echarts';
import { AuthService } from '../../core/auth.service';
import { CustomersApi } from '../../core/master-data-api.service';
import { ChangeDocument, CreditExposure, Customer, Invoice, OrderListItem } from '../../core/models';
import { OrdersApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { chartTheme } from '../../shared/charts';
import { CustomerDialogComponent } from './customer-dialog.component';

@Component({
  selector: 'om-customer-detail',
  imports: [StatusTagComponent, CustomerDialogComponent, NgxEchartsDirective, CurrencyPipe, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './customer-detail.component.html'
})
export class CustomerDetailComponent {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly customersApi = inject(CustomersApi);
  private readonly ordersApi = inject(OrdersApi);
  protected readonly router = inject(Router);

  protected readonly customer = signal<Customer | null>(null);
  protected readonly exposure = signal<CreditExposure | null>(null);
  protected readonly orders = signal<OrderListItem[]>([]);
  protected readonly invoices = signal<Invoice[]>([]);
  protected readonly changes = signal<ChangeDocument[]>([]);
  protected readonly dialogOpen = signal(false);
  protected readonly revenueTabShown = signal(false);

  protected readonly openItems = computed(() => this.invoices().filter(i => i.billingType.code === 'F2' && i.status.code === 'OPEN'));
  protected readonly overdueItems = computed(() => this.openItems().filter(i => i.overdue));
  protected readonly revenue = computed(() => this.invoices().reduce((sum, i) => sum + i.netAmount, 0));

  /** Fakturierter Nettoumsatz der letzten zwölf Monate je Monat. */
  protected readonly chart = computed<EChartsOption | null>(() => {
    const invoices = this.invoices();
    if (!invoices.length) return null;
    const months: string[] = [];
    const now = new Date();
    for (let k = 11; k >= 0; k--) {
      const d = new Date(now.getFullYear(), now.getMonth() - k, 1);
      months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
    }
    const values = months.map(m => invoices.filter(i => i.billingDate.startsWith(m)).reduce((s, i) => s + i.netAmount, 0));
    const t = chartTheme();
    return {
      ...t.base,
      grid: { left: 60, right: 16, top: 16, bottom: 32 },
      xAxis: { ...t.categoryAxis, type: 'category', data: months.map(m => m.slice(5) + '/' + m.slice(2, 4)) },
      yAxis: { ...t.valueAxis, type: 'value' },
      tooltip: { trigger: 'axis', valueFormatter: (v: unknown) => t.currency(Number(v)) },
      series: [{ type: 'bar', data: values, itemStyle: { color: t.palette[0], borderRadius: [4, 4, 0, 0] }, name: 'Nettoumsatz' }]
    };
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (id) this.load(id);
    });
  }

  protected load(id = Number(this.id())): void {
    forkJoin({
      customer: this.customersApi.get(id),
      exposure: this.customersApi.creditExposure(id),
      orders: this.ordersApi.byCustomer(id),
      invoices: this.customersApi.invoices(id),
      changes: this.customersApi.changes(id)
    }).subscribe(r => {
      this.customer.set(r.customer);
      this.exposure.set(r.exposure);
      this.orders.set(r.orders);
      this.invoices.set(r.invoices);
      this.changes.set(r.changes);
    });
  }

  protected saved(): void {
    this.dialogOpen.set(false);
    this.load();
  }

  protected newOrder(): void {
    this.router.navigate(['/orders/new'], { queryParams: { customerId: this.customer()!.id } });
  }

  protected openOrder(event: Event): void {
    this.router.navigate(['/orders', (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey]);
  }

  protected openInvoice(event: Event): void {
    this.router.navigate(['/invoices', (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey]);
  }

  protected changeText(c: ChangeDocument): string {
    return c.description ?? `${c.fieldName}: ${c.oldValue ?? '–'} → ${c.newValue ?? '–'}`;
  }

  protected onTabSelect(event: Event): void {
    const tab = (event as CustomEvent<{ tab: HTMLElement }>).detail.tab;
    if (tab.dataset['key'] === 'revenue') this.revenueTabShown.set(true);
  }
}
