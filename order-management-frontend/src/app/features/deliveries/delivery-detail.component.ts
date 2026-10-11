import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { ChangeDocument, Delivery, Invoice } from '../../core/models';
import { DeliveriesApi, InvoicesApi, OrdersApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

@Component({
  selector: 'om-delivery-detail',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, StatusTagComponent, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './delivery-detail.component.html',
  styles: `
    .timeline-host { padding-top: .5rem; }
    .print-note { margin-top: .75rem; }
  `
})
export class DeliveryDetailComponent {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeliveriesApi);
  private readonly invoicesApi = inject(InvoicesApi);
  private readonly ordersApi = inject(OrdersApi);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly delivery = signal<Delivery | null>(null);
  protected readonly invoices = signal<Invoice[]>([]);
  protected readonly changes = signal<ChangeDocument[]>([]);
  protected readonly busy = signal(false);
  protected readonly dialog = signal<'gi' | 'invoice' | null>(null);
  protected postingDate = todayIso();
  protected billingDate = todayIso();

  protected readonly canPostGoodsIssue = computed(() => this.delivery()?.status.code === 'CREATED');
  protected readonly canReverse = computed(() =>
    this.delivery()?.status.code === 'GOODS_ISSUED' && this.delivery()?.billingStatus.code === 'NOT_PROCESSED');
  protected readonly canBill = computed(() =>
    this.delivery()?.status.code === 'GOODS_ISSUED' && this.delivery()?.billingStatus.code === 'NOT_PROCESSED');

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (id) this.load(id);
    });
  }

  protected load(id = Number(this.id())): void {
    forkJoin({ delivery: this.api.get(id), changes: this.api.changes(id) }).subscribe(({ delivery, changes }) => {
      this.delivery.set(delivery);
      this.changes.set(changes);
      this.ordersApi.invoices(delivery.orderId).subscribe(list =>
        this.invoices.set(list.filter(i => i.deliveryNumber === delivery.deliveryNumber)));
    });
  }

  protected openDialog(name: 'gi' | 'invoice'): void {
    this.postingDate = todayIso();
    this.billingDate = todayIso();
    this.dialog.set(name);
  }

  protected postGoodsIssue(): void {
    this.run(this.api.postGoodsIssue(this.delivery()!.id, this.postingDate), d =>
      `Warenausgang gebucht – Materialbeleg ${d.goodsIssueDocument}`);
  }

  protected reverseGoodsIssue(): void {
    this.run(this.api.reverseGoodsIssue(this.delivery()!.id), () => 'Warenausgang storniert (Bewegungsart 602)');
  }

  protected cancel(): void {
    this.run(this.api.cancel(this.delivery()!.id), d => `Lieferung ${d.deliveryNumber} storniert`);
  }

  protected createInvoice(): void {
    this.busy.set(true);
    this.invoicesApi.create(this.delivery()!.id, this.billingDate).subscribe({
      next: invoice => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success(`Rechnung ${invoice.invoiceNumber} angelegt`);
        this.router.navigate(['/invoices', invoice.id]);
      },
      error: () => this.busy.set(false)
    });
  }

  protected print(): void {
    window.print();
  }

  protected changeText(c: ChangeDocument): string {
    return c.description ?? `${c.fieldName}: ${c.oldValue ?? '–'} → ${c.newValue ?? '–'}`;
  }

  private run(request: Observable<Delivery>, success: (d: Delivery) => string): void {
    this.busy.set(true);
    request.subscribe({
      next: d => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success(success(d));
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }
}
