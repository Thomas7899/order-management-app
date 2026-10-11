import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { InventoryApi, MaterialsApi } from '../../core/master-data-api.service';
import { Availability, ChangeDocument, Material, StockMovement, WarehouseStock } from '../../core/models';
import { STOCK_STATUS_TEXT, semanticOf } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { MovementDialogComponent } from '../inventory/movement-dialog.component';
import { MaterialDialogComponent } from './material-dialog.component';

@Component({
  selector: 'om-material-detail',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, MaterialDialogComponent, MovementDialogComponent,
    CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './material-detail.component.html',
  styles: `
    .hero { width: 7rem; height: 7rem; border-radius: 1rem; object-fit: cover; background: var(--sapNeutralBackgroundColor); }
    .header-row { display: flex; gap: 2rem; align-items: center; flex-wrap: wrap; }
    .stock-grid { display: grid; gap: 1rem; grid-template-columns: minmax(0, 3fr) minmax(20rem, 2fr); }
    @media (max-width: 1100px) { .stock-grid { grid-template-columns: minmax(0, 1fr); } }
    .atp { display: grid; grid-template-columns: minmax(0, 1fr) max-content; gap: .25rem 1.5rem; max-width: 28rem; }
    .atp .total { border-top: 1px solid var(--sapGroup_ContentBorderColor); padding-top: .25rem; }
  `
})
export class MaterialDetailComponent {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly materialsApi = inject(MaterialsApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly messages = inject(MessageService);

  protected readonly material = signal<Material | null>(null);
  protected readonly availability = signal<Availability | null>(null);
  protected readonly stocks = signal<WarehouseStock[]>([]);
  protected readonly movements = signal<StockMovement[]>([]);
  protected readonly changes = signal<ChangeDocument[]>([]);
  protected readonly dialog = signal<'edit' | 'movement' | 'settings' | null>(null);
  protected readonly busy = signal(false);
  protected readonly stockStatusText = STOCK_STATUS_TEXT;
  protected readonly semanticOf = semanticOf;

  protected settings = { warehouseId: 0, warehouseName: '', minStock: 0, maxStock: 0, binLocation: '' };

  protected readonly margin = computed(() => {
    const m = this.material();
    return m?.costPrice && m.price ? (m.price - m.costPrice) / m.price : null;
  });

  protected readonly stockValue = computed(() => {
    const m = this.material();
    return m ? (m.costPrice ?? m.price) * m.stockQuantity : 0;
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (id) this.load(id);
    });
  }

  protected load(id = Number(this.id())): void {
    forkJoin({
      material: this.materialsApi.get(id),
      availability: this.inventoryApi.availability(id),
      stocks: this.inventoryApi.stockByProduct(id),
      movements: this.inventoryApi.movementsByProduct(id),
      changes: this.materialsApi.changes(id)
    }).subscribe(r => {
      this.material.set(r.material);
      this.availability.set(r.availability);
      this.stocks.set(r.stocks.sort((a, b) => a.warehouseCode.localeCompare(b.warehouseCode)));
      this.movements.set(r.movements);
      this.changes.set(r.changes);
    });
  }

  protected editSettings(s: WarehouseStock): void {
    this.settings = { warehouseId: s.warehouseId, warehouseName: `${s.warehouseCode} ${s.warehouseName}`,
      minStock: s.minStock, maxStock: s.maxStock, binLocation: s.binLocation ?? '' };
    this.dialog.set('settings');
  }

  protected saveSettings(): void {
    this.busy.set(true);
    this.inventoryApi.updateStockSettings(this.settings.warehouseId, this.material()!.id, {
      minStock: Number(this.settings.minStock), maxStock: Number(this.settings.maxStock), binLocation: this.settings.binLocation
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success('Dispositionsdaten gesichert');
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }

  protected block(): void {
    this.materialsApi.block(this.material()!.id).subscribe(() => {
      this.messages.success('Vertriebssperre gesetzt');
      this.load();
    });
  }

  protected afterSave(): void {
    this.dialog.set(null);
    this.load();
  }

  protected changeText(c: ChangeDocument): string {
    return c.description ?? `${c.fieldName}: ${c.oldValue ?? '–'} → ${c.newValue ?? '–'}`;
  }
}
