import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { ChangeDocument, Invoice } from '../../core/models';
import { InvoicesApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { todayIso } from '../../shared/status';

@Component({
  selector: 'om-invoice-detail',
  imports: [RouterLink, StatusTagComponent, CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './invoice-detail.component.html',
  styles: `
    .totals { display: grid; grid-template-columns: auto 10rem; gap: .25rem 1.5rem; justify-content: end; padding: .75rem 1rem 0; }
    .payment-preview { display: grid; grid-template-columns: auto auto; gap: .25rem 1.5rem; padding: .5rem 0; }
  `
})
export class InvoiceDetailComponent {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(InvoicesApi);
  private readonly messages = inject(MessageService);

  protected readonly invoice = signal<Invoice | null>(null);
  protected readonly changes = signal<ChangeDocument[]>([]);
  protected readonly busy = signal(false);
  protected readonly dialog = signal<'payment' | 'cancel' | null>(null);
  protected readonly paymentDate = signal(todayIso());

  protected readonly canPay = computed(() => {
    const i = this.invoice();
    return !!i && i.billingType.code === 'F2' && i.status.code === 'OPEN';
  });

  protected readonly canCancel = computed(() => this.canPay());

  protected readonly marginPercent = computed(() => {
    const i = this.invoice();
    return i && i.netAmount !== 0 ? i.marginAmount / i.netAmount : null;
  });

  /** Skonto bei Zahlung zum gewählten Datum, wie es das Backend berechnet. */
  protected readonly discount = computed(() => {
    const i = this.invoice();
    if (!i || !i.cashDiscountDate || !i.cashDiscountPercent) return 0;
    if (this.paymentDate() > i.cashDiscountDate) return 0;
    return Math.round(i.grossAmount * i.cashDiscountPercent) / 100;
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (id) this.load(id);
    });
  }

  protected load(id = Number(this.id())): void {
    forkJoin({ invoice: this.api.get(id), changes: this.api.changes(id) }).subscribe(({ invoice, changes }) => {
      this.invoice.set(invoice);
      this.changes.set(changes);
    });
  }

  protected openPdf(): void {
    const i = this.invoice()!;
    this.api.pdf(i.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const win = window.open(url, '_blank');
      if (!win) {
        const a = document.createElement('a');
        a.href = url;
        a.download = `Rechnung_${i.invoiceNumber}.pdf`;
        a.click();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    });
  }

  protected openPayment(): void {
    this.paymentDate.set(todayIso());
    this.dialog.set('payment');
  }

  protected postPayment(): void {
    this.busy.set(true);
    this.api.postPayment(this.invoice()!.id, this.paymentDate()).subscribe({
      next: inv => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success(`Zahlungseingang gebucht: ${inv.paidAmount?.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })}`
          + (inv.cashDiscountAmount ? ` (Skonto ${inv.cashDiscountAmount.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })})` : ''));
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }

  protected cancelInvoice(): void {
    this.busy.set(true);
    this.api.cancel(this.invoice()!.id).subscribe({
      next: reversal => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success(`Stornorechnung ${reversal.invoiceNumber} angelegt – die Lieferung ist wieder fakturierbar`);
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }

  protected changeText(c: ChangeDocument): string {
    return c.description ?? `${c.fieldName}: ${c.oldValue ?? '–'} → ${c.newValue ?? '–'}`;
  }
}
