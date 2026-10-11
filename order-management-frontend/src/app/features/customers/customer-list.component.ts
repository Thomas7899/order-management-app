import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CustomersApi } from '../../core/master-data-api.service';
import { Customer } from '../../core/models';
import { PAYMENT_TERMS } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { CustomerDialogComponent } from './customer-dialog.component';

const PAGE_SIZE = 30;

@Component({
  selector: 'om-customer-list',
  imports: [FormsModule, Ui5ValueAccessorDirective, CustomerDialogComponent, CurrencyPipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './customer-list.component.html'
})
export class CustomerListComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(CustomersApi);
  private readonly router = inject(Router);

  protected readonly paymentTerms = [{ code: '', text: 'Alle' }, ...PAYMENT_TERMS];
  protected readonly all = signal<Customer[]>([]);
  protected readonly loading = signal(false);
  protected readonly dialogOpen = signal(false);
  protected readonly visibleCount = signal(PAGE_SIZE);

  protected readonly search = signal('');
  protected readonly terms = signal('');
  protected readonly onlyBlocked = signal(false);
  protected readonly withDiscount = signal(false);

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.all().filter(c =>
      (!q || [c.customerNumber, c.fullName, c.email, c.city ?? ''].some(v => v?.toLowerCase().includes(q)))
      && (!this.terms() || c.paymentTerms === this.terms())
      && (!this.onlyBlocked() || !c.active)
      && (!this.withDiscount() || (c.discountPercent ?? 0) > 0));
  });

  protected readonly rows = computed(() => this.filtered().slice(0, this.visibleCount()));

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: list => {
        this.all.set(list.sort((a, b) => a.customerNumber.localeCompare(b.customerNumber)));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected filterChanged(): void {
    this.visibleCount.set(PAGE_SIZE);
  }

  protected more(): void {
    this.visibleCount.update(n => n + PAGE_SIZE);
  }

  protected open(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/customers', key]);
  }

  protected saved(customer: Customer): void {
    this.dialogOpen.set(false);
    this.router.navigate(['/customers', customer.id]);
  }
}
