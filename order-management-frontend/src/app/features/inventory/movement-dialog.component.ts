import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from '../../core/message.service';
import { InventoryApi, MaterialsApi } from '../../core/master-data-api.service';
import { Material, MovementType, StockMovement, Warehouse, WarehouseStock } from '../../core/models';
import { MOVEMENT_TYPES, todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

/** Warenbewegung buchen (angelehnt an MIGO). */
@Component({
  selector: 'om-movement-dialog',
  imports: [FormsModule, Ui5ValueAccessorDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ui5-dialog header-text="Warenbewegung buchen" [open]="open()" (close)="closed.emit()">
      <div class="om-form-grid">
        <div class="om-field span-2">
          <ui5-label show-colon required>Bewegungsart</ui5-label>
          <ui5-select [ngModel]="type()" (ngModelChange)="type.set($event)">
            @for (t of movementTypes; track t.type) {
              <ui5-option [value]="t.type" [selected]="type() === t.type">{{ t.code }} – {{ t.text }}</ui5-option>
            }
          </ui5-select>
        </div>
        <div class="om-field span-2">
          <ui5-label show-colon required>Material</ui5-label>
          <ui5-combobox filter="Contains" placeholder="Material suchen"
            [value]="materialLabel()" (selection-change)="materialSelected($event)">
            @for (m of materials(); track m.id) {
              <ui5-cb-item [attr.text]="m.materialNumber + ' · ' + m.name" [attr.data-id]="m.id"></ui5-cb-item>
            }
          </ui5-combobox>
        </div>
        @if (definition().needsSource) {
          <div class="om-field">
            <ui5-label show-colon required>{{ definition().needsTarget ? 'Von Lagerort' : 'Lagerort' }}</ui5-label>
            <ui5-select [ngModel]="sourceId()" (ngModelChange)="sourceId.set($event)">
              <ui5-option value="" [selected]="!sourceId()">Bitte wählen</ui5-option>
              @for (w of warehouses(); track w.id) {
                <ui5-option [value]="'' + w.id" [selected]="sourceId() === '' + w.id" [attr.additional-text]="stockText(w.id)">{{ w.code }} {{ w.name }}</ui5-option>
              }
            </ui5-select>
          </div>
        }
        @if (definition().needsTarget) {
          <div class="om-field">
            <ui5-label show-colon required>{{ definition().needsSource ? 'Nach Lagerort' : 'Lagerort' }}</ui5-label>
            <ui5-select [ngModel]="targetId()" (ngModelChange)="targetId.set($event)">
              <ui5-option value="" [selected]="!targetId()">Bitte wählen</ui5-option>
              @for (w of warehouses(); track w.id) {
                <ui5-option [value]="'' + w.id" [selected]="targetId() === '' + w.id" [attr.additional-text]="stockText(w.id)">{{ w.code }} {{ w.name }}</ui5-option>
              }
            </ui5-select>
          </div>
        }
        <div class="om-field">
          <ui5-label show-colon required>{{ type() === 'INVENTORY_ADJUSTMENT' ? 'Gezählte Menge' : 'Menge' }}</ui5-label>
          <ui5-step-input [ngModel]="quantity()" (ngModelChange)="quantity.set($event)" min="0" max="99999"></ui5-step-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Buchungsdatum</ui5-label>
          <ui5-date-picker placeholder="TT.MM.JJJJ" [(ngModel)]="postingDate" value-format="yyyy-MM-dd" display-format="dd.MM.yyyy"></ui5-date-picker>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Referenz</ui5-label>
          <ui5-input [(ngModel)]="reference" [attr.placeholder]="type() === 'GOODS_RECEIPT' ? 'Bestellnummer, z. B. 4500001234' : 'z. B. Inventurbeleg'"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Grund / Text</ui5-label>
          <ui5-input [(ngModel)]="reason"></ui5-input>
        </div>
        @if (type() === 'INVENTORY_ADJUSTMENT') {
          <div class="om-field span-2">
            <ui5-message-strip design="Information" hide-close-button>
              Erfassen Sie die gezählte Menge. Das System bucht die Differenz zum Buchbestand mit Bewegungsart 701 (Mehrmenge) oder 702 (Fehlmenge).
            </ui5-message-strip>
          </div>
        }
      </div>
      <div slot="footer" class="om-dialog-footer">
        <ui5-button design="Emphasized" [disabled]="busy() || !valid()" (click)="post()">Buchen</ui5-button>
        <ui5-button design="Transparent" (click)="closed.emit()">Abbrechen</ui5-button>
      </div>
    </ui5-dialog>
  `
})
export class MovementDialogComponent {
  readonly open = input(false);
  readonly productId = input<number | null>(null);
  readonly warehouseId = input<number | null>(null);
  readonly posted = output<StockMovement>();
  readonly closed = output<void>();

  private readonly inventoryApi = inject(InventoryApi);
  private readonly materialsApi = inject(MaterialsApi);
  private readonly messages = inject(MessageService);

  protected readonly movementTypes = MOVEMENT_TYPES;
  protected readonly materials = signal<Material[]>([]);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly stocks = signal<WarehouseStock[]>([]);
  protected readonly busy = signal(false);

  protected readonly type = signal<MovementType>('GOODS_RECEIPT');
  protected readonly material = signal<number | null>(null);
  protected readonly sourceId = signal('');
  protected readonly targetId = signal('');
  protected readonly quantity = signal(1);
  protected postingDate = todayIso();
  protected reference = '';
  protected reason = '';

  protected readonly definition = computed(() => MOVEMENT_TYPES.find(t => t.type === this.type())!);

  protected readonly materialLabel = computed(() => {
    const m = this.materials().find(x => x.id === this.material());
    return m ? `${m.materialNumber} · ${m.name}` : '';
  });

  protected readonly valid = computed(() => {
    const d = this.definition();
    const qtyOk = this.type() === 'INVENTORY_ADJUSTMENT' ? this.quantity() >= 0 : this.quantity() > 0;
    return !!this.material() && qtyOk
      && (!d.needsSource || !!this.sourceId())
      && (!d.needsTarget || !!this.targetId())
      && !(d.needsSource && d.needsTarget && this.sourceId() === this.targetId());
  });

  constructor() {
    effect(() => {
      if (!this.open()) return;
      if (!this.materials().length) this.materialsApi.list().subscribe(m => this.materials.set(m.sort((a, b) => a.materialNumber.localeCompare(b.materialNumber))));
      if (!this.warehouses().length) this.inventoryApi.warehouses().subscribe(w => this.warehouses.set(w.filter(x => x.active)));
      this.type.set('GOODS_RECEIPT');
      this.quantity.set(1);
      this.reference = '';
      this.reason = '';
      this.postingDate = todayIso();
      this.sourceId.set(this.warehouseId() ? String(this.warehouseId()) : '');
      this.targetId.set(this.warehouseId() ? String(this.warehouseId()) : '');
      this.selectMaterial(this.productId());
    });
  }

  protected materialSelected(event: Event): void {
    const id = (event as CustomEvent<{ item?: HTMLElement }>).detail.item?.dataset['id'];
    this.selectMaterial(id ? Number(id) : null);
  }

  protected stockText(warehouseId: number): string {
    const s = this.stocks().find(x => x.warehouseId === warehouseId);
    return s ? `${s.quantity} ${s.unitOfMeasure}` : '';
  }

  protected post(): void {
    const d = this.definition();
    this.busy.set(true);
    this.inventoryApi.postMovement({
      movementType: this.type(),
      productId: this.material()!,
      sourceWarehouseId: d.needsSource ? Number(this.sourceId()) : null,
      targetWarehouseId: d.needsTarget ? Number(this.targetId()) : null,
      quantity: Number(this.quantity()),
      postingDate: this.postingDate || undefined,
      referenceNumber: this.reference || undefined,
      reason: this.reason || undefined
    }).subscribe({
      next: movement => {
        this.busy.set(false);
        this.messages.success(`Materialbeleg ${movement.movementNumber} gebucht (Bewegungsart ${movement.movementCode})`);
        this.posted.emit(movement);
      },
      error: () => this.busy.set(false)
    });
  }

  private selectMaterial(id: number | null): void {
    this.material.set(id);
    this.stocks.set([]);
    if (id) this.inventoryApi.stockByProduct(id).subscribe(s => this.stocks.set(s));
  }
}
