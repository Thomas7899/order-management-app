import { CUSTOM_ELEMENTS_SCHEMA, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from '../../core/message.service';
import { CustomersApi } from '../../core/master-data-api.service';
import { Customer, CustomerRequest, Problem } from '../../core/models';
import { PAYMENT_TERMS } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

const EMPTY: CustomerRequest = {
  firstName: '', lastName: '', email: '', phone: '', address: '', city: '', zipCode: '', country: 'Deutschland',
  paymentTerms: 'ZB30', creditLimit: null, discountPercent: 0, active: true
};

/** Anlegen und Ändern eines Debitors (angelehnt an XD01/XD02). */
@Component({
  selector: 'om-customer-dialog',
  imports: [FormsModule, Ui5ValueAccessorDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ui5-dialog [attr.header-text]="customer() ? 'Debitor ' + customer()!.customerNumber + ' ändern' : 'Debitor anlegen'"
      [open]="open()" (close)="closed.emit()">
      <div class="om-form-grid">
        <div class="om-field">
          <ui5-label show-colon required>Vorname</ui5-label>
          <ui5-input [(ngModel)]="form.firstName" [attr.value-state]="errors()['firstName'] ? 'Negative' : 'None'">
            <div slot="valueStateMessage">{{ errors()['firstName'] }}</div>
          </ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon required>Nachname</ui5-label>
          <ui5-input [(ngModel)]="form.lastName" [attr.value-state]="errors()['lastName'] ? 'Negative' : 'None'">
            <div slot="valueStateMessage">{{ errors()['lastName'] }}</div>
          </ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon required>E-Mail</ui5-label>
          <ui5-input type="Email" [(ngModel)]="form.email" [attr.value-state]="errors()['email'] ? 'Negative' : 'None'">
            <div slot="valueStateMessage">{{ errors()['email'] }}</div>
          </ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Telefon</ui5-label>
          <ui5-input type="Tel" [(ngModel)]="form.phone"></ui5-input>
        </div>
        <div class="om-field span-2">
          <ui5-label show-colon>Straße und Hausnummer</ui5-label>
          <ui5-input [(ngModel)]="form.address"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>PLZ</ui5-label>
          <ui5-input [(ngModel)]="form.zipCode"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Ort</ui5-label>
          <ui5-input [(ngModel)]="form.city"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Land</ui5-label>
          <ui5-input [(ngModel)]="form.country"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Zahlungsbedingung</ui5-label>
          <ui5-select [(ngModel)]="form.paymentTerms">
            @for (t of paymentTerms; track t.code) {
              <ui5-option [value]="t.code" [selected]="form.paymentTerms === t.code">{{ t.code }} – {{ t.text }}</ui5-option>
            }
          </ui5-select>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Kreditlimit (EUR, leer = keine Prüfung)</ui5-label>
          <ui5-input type="Number" [ngModel]="form.creditLimit ?? ''" (ngModelChange)="form.creditLimit = $event === '' ? null : +$event"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Kundenrabatt K007 (%)</ui5-label>
          <ui5-step-input [(ngModel)]="form.discountPercent" min="0" max="50" step="0.5" value-precision="1"></ui5-step-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Status</ui5-label>
          <ui5-switch [(ngModel)]="form.active" text-on="Aktiv" text-off="Gesperrt"></ui5-switch>
        </div>
      </div>
      <div slot="footer" class="om-dialog-footer">
        <ui5-button design="Emphasized" [disabled]="busy()" (click)="save()">Sichern</ui5-button>
        <ui5-button design="Transparent" (click)="closed.emit()">Abbrechen</ui5-button>
      </div>
    </ui5-dialog>
  `
})
export class CustomerDialogComponent {
  readonly open = input(false);
  readonly customer = input<Customer | null>(null);
  readonly saved = output<Customer>();
  readonly closed = output<void>();

  private readonly api = inject(CustomersApi);
  private readonly messages = inject(MessageService);

  protected readonly paymentTerms = PAYMENT_TERMS;
  protected readonly busy = signal(false);
  protected readonly errors = signal<Record<string, string>>({});
  protected form: CustomerRequest = { ...EMPTY };

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const c = this.customer();
      this.errors.set({});
      this.form = c
        ? {
            firstName: c.firstName, lastName: c.lastName, email: c.email, phone: c.phone ?? '', address: c.address ?? '',
            city: c.city ?? '', zipCode: c.zipCode ?? '', country: c.country ?? '', paymentTerms: c.paymentTerms,
            creditLimit: c.creditLimit ?? null, discountPercent: c.discountPercent ?? 0, active: c.active
          }
        : { ...EMPTY };
    });
  }

  protected save(): void {
    this.busy.set(true);
    const c = this.customer();
    const request = c ? this.api.update(c.id, this.form) : this.api.create(this.form);
    request.subscribe({
      next: saved => {
        this.busy.set(false);
        this.messages.success(c ? `Debitor ${saved.customerNumber} geändert` : `Debitor ${saved.customerNumber} angelegt`);
        this.saved.emit(saved);
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.errors.set((e.error as Problem)?.errors ?? {});
      }
    });
  }
}
