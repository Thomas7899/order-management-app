import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { concatMap, from, toArray, catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { Delivery, Invoice } from '../../core/models';
import { InvoicesApi } from '../../core/sales-api.service';
import { todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

/** Fakturavorrat (angelehnt an VF04): Lieferungen mit Warenausgang, die noch nicht fakturiert sind. */
@Component({
  selector: 'om-billing-due',
  imports: [FormsModule, Ui5ValueAccessorDirective, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './billing-due.component.html'
})
export class BillingDueComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(InvoicesApi);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly rows = signal<Delivery[]>([]);
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly result = signal<Invoice[]>([]);
  protected billingDate = todayIso();

  protected readonly selectedCount = computed(() => this.selected().size);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.dueList().subscribe({
      next: rows => {
        this.rows.set(rows);
        this.selected.set(new Set());
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected selectionChange(event: Event): void {
    const selection = event.target as HTMLElement & { selected?: string };
    this.selected.set(new Set((selection.selected ?? '').split(' ').filter(Boolean)));
  }

  protected bill(): void {
    const ids = [...this.selected()].map(Number);
    if (!ids.length) return;
    this.busy.set(true);
    from(ids).pipe(
      concatMap(id => this.api.create(id, this.billingDate).pipe(catchError(() => of(null)))),
      toArray()
    ).subscribe(results => {
      const created = results.filter((r): r is Invoice => r !== null);
      this.busy.set(false);
      this.result.set(created);
      if (created.length) {
        this.messages.success(`${created.length} Rechnung(en) angelegt: ${created.map(i => i.invoiceNumber).join(', ')}`);
      }
      this.load();
    });
  }

  protected openDelivery(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/deliveries', key]);
  }

  protected openInvoice(id: number): void {
    this.router.navigate(['/invoices', id]);
  }
}
