import { CUSTOM_ELEMENTS_SCHEMA, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from '../../core/message.service';
import { MaterialsApi } from '../../core/master-data-api.service';
import { Material, MaterialRequest, Problem } from '../../core/models';
import { TAX_CODES } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

const EMPTY: MaterialRequest = {
  name: '', description: '', price: 0, costPrice: null, category: '', unitOfMeasure: 'ST', taxCode: 'FULL', imageUrl: '', active: true
};

/** Anlegen und Ändern eines Materials (angelehnt an MM01/MM02). */
@Component({
  selector: 'om-material-dialog',
  imports: [FormsModule, Ui5ValueAccessorDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ui5-dialog [attr.header-text]="material() ? 'Material ' + material()!.materialNumber + ' ändern' : 'Material anlegen'"
      [open]="open()" (close)="closed.emit()">
      <div class="om-form-grid">
        <div class="om-field span-2">
          <ui5-label show-colon required>Materialkurztext</ui5-label>
          <ui5-input [(ngModel)]="form.name" [attr.value-state]="errors()['name'] ? 'Negative' : 'None'">
            <div slot="valueStateMessage">{{ errors()['name'] }}</div>
          </ui5-input>
        </div>
        <div class="om-field span-2">
          <ui5-label show-colon>Beschreibung</ui5-label>
          <ui5-textarea [(ngModel)]="form.description" rows="3"></ui5-textarea>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Warengruppe</ui5-label>
          <ui5-combobox [value]="form.category ?? ''" (input)="form.category = $any($event.target).value"
            (change)="form.category = $any($event.target).value">
            @for (c of categories(); track c) {
              <ui5-cb-item [attr.text]="c"></ui5-cb-item>
            }
          </ui5-combobox>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Basismengeneinheit</ui5-label>
          <ui5-input [(ngModel)]="form.unitOfMeasure" maxlength="3" [attr.value-state]="errors()['unitOfMeasure'] ? 'Negative' : 'None'">
            <div slot="valueStateMessage">{{ errors()['unitOfMeasure'] }}</div>
          </ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon required>Verkaufspreis PR00 (EUR)</ui5-label>
          <ui5-input type="Number" [ngModel]="form.price" (ngModelChange)="form.price = +$event"
            [attr.value-state]="errors()['price'] ? 'Negative' : 'None'">
            <div slot="valueStateMessage">{{ errors()['price'] }}</div>
          </ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Einstandspreis (gleitender Durchschnitt)</ui5-label>
          <ui5-input type="Number" [ngModel]="form.costPrice ?? ''" (ngModelChange)="form.costPrice = $event === '' ? null : +$event"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Steuerkennzeichen</ui5-label>
          <ui5-select [(ngModel)]="form.taxCode">
            @for (t of taxCodes; track t.code) {
              <ui5-option [value]="t.code" [selected]="form.taxCode === t.code">{{ t.text }}</ui5-option>
            }
          </ui5-select>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Vertrieb</ui5-label>
          <ui5-switch [(ngModel)]="form.active" text-on="Frei" text-off="Gesperrt"></ui5-switch>
        </div>
        <div class="om-field span-2">
          <ui5-label show-colon>Bild-URL</ui5-label>
          <ui5-input [(ngModel)]="form.imageUrl"></ui5-input>
        </div>
      </div>
      @if (!material()) {
        <span class="om-subtle">Bestände entstehen über Wareneingänge (Bewegungsart 101/561) in der Bestandsführung.</span>
      }
      <div slot="footer" class="om-dialog-footer">
        <ui5-button design="Emphasized" [disabled]="busy()" (click)="save()">Sichern</ui5-button>
        <ui5-button design="Transparent" (click)="closed.emit()">Abbrechen</ui5-button>
      </div>
    </ui5-dialog>
  `
})
export class MaterialDialogComponent {
  readonly open = input(false);
  readonly material = input<Material | null>(null);
  readonly saved = output<Material>();
  readonly closed = output<void>();

  private readonly api = inject(MaterialsApi);
  private readonly messages = inject(MessageService);

  protected readonly taxCodes = TAX_CODES;
  protected readonly categories = signal<string[]>([]);
  protected readonly busy = signal(false);
  protected readonly errors = signal<Record<string, string>>({});
  protected form: MaterialRequest = { ...EMPTY };

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const m = this.material();
      this.errors.set({});
      this.form = m
        ? {
            name: m.name, description: m.description ?? '', price: m.price, costPrice: m.costPrice ?? null,
            category: m.category ?? '', unitOfMeasure: m.unitOfMeasure, taxCode: m.taxCode, imageUrl: m.imageUrl ?? '', active: m.active
          }
        : { ...EMPTY };
      if (!this.categories().length) this.api.categories().subscribe(c => this.categories.set(c.sort()));
    });
  }

  protected save(): void {
    this.busy.set(true);
    const m = this.material();
    const request = m ? this.api.update(m.id, this.form) : this.api.create(this.form);
    request.subscribe({
      next: saved => {
        this.busy.set(false);
        this.messages.success(m ? `Material ${saved.materialNumber} geändert` : `Material ${saved.materialNumber} angelegt`);
        this.saved.emit(saved);
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.errors.set((e.error as Problem)?.errors ?? {});
      }
    });
  }
}
