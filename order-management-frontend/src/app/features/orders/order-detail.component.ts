import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, inject, input, signal, effect } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { InventoryApi, MaterialsApi } from '../../core/master-data-api.service';
import {
  ChangeDocument, Delivery, DocumentFlow, DocumentFlowNode, Invoice, Material, Order, OrderItem, Warehouse
} from '../../core/models';
import { DeliveriesApi, OrdersApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { REJECTION_REASONS, todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

type DialogName = 'reject' | 'header' | 'item' | 'add' | 'delivery' | null;

@Component({
  selector: 'om-order-detail',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, StatusTagComponent, CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.css'
})
export class OrderDetailComponent {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly ordersApi = inject(OrdersApi);
  private readonly deliveriesApi = inject(DeliveriesApi);
  private readonly materialsApi = inject(MaterialsApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly messages = inject(MessageService);
  protected readonly router = inject(Router);

  protected readonly order = signal<Order | null>(null);
  protected readonly flow = signal<DocumentFlow | null>(null);
  protected readonly deliveries = signal<Delivery[]>([]);
  protected readonly invoices = signal<Invoice[]>([]);
  protected readonly changes = signal<ChangeDocument[]>([]);
  protected readonly selectedItem = signal<OrderItem | null>(null);
  protected readonly busy = signal(false);
  protected readonly dialog = signal<DialogName>(null);

  protected readonly rejectionReasons = REJECTION_REASONS;
  protected readonly materials = signal<Material[]>([]);
  protected readonly warehouses = signal<Warehouse[]>([]);

  // Dialogfelder
  protected rejectReason = 'R10';
  protected rejectItems: number[] = [];
  protected header = { requestedDeliveryDate: '', customerReference: '', shippingAddress: '', billingAddress: '', notes: '' };
  protected itemChange = { itemNumber: 0, quantity: 1, discountPercent: 0 };
  protected newItem = { productId: '', quantity: 1, discountPercent: 0 };
  protected deliveryOptions = { warehouseId: '', plannedGoodsIssueDate: todayIso() };

  protected readonly editable = computed(() => {
    const o = this.order();
    return !!o && o.status.code !== 'COMPLETED' && o.status.code !== 'CANCELLED';
  });

  protected readonly canDeliver = computed(() => {
    const o = this.order();
    return !!o && this.editable() && o.creditStatus.code !== 'BLOCKED'
      && o.items.some(i => !i.rejectionReason && i.confirmedQuantity > i.deliveredQuantity);
  });

  protected readonly rejectableItems = computed(() =>
    this.order()?.items.filter(i => !i.rejectionReason && i.deliveredQuantity === 0) ?? []);

  protected readonly hasBacklog = computed(() =>
    this.order()?.items.some(i => !i.rejectionReason && i.confirmedQuantity < i.quantity) ?? false);

  protected readonly costTotal = computed(() =>
    this.order()?.items.filter(i => !i.rejectionReason)
      .flatMap(i => i.conditions.filter(c => c.conditionType === 'VPRS'))
      .reduce((sum, c) => sum + c.conditionValue, 0) ?? 0);

  protected readonly margin = computed(() => {
    const o = this.order();
    if (!o || o.netAmount === 0) return null;
    return (o.netAmount - this.costTotal()) / o.netAmount;
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (id) this.load(id);
    });
  }

  protected load(id = Number(this.id())): void {
    forkJoin({
      order: this.ordersApi.get(id),
      flow: this.ordersApi.documentFlow(id),
      deliveries: this.ordersApi.deliveries(id),
      invoices: this.ordersApi.invoices(id),
      changes: this.ordersApi.changes(id)
    }).subscribe(result => {
      this.order.set(result.order);
      this.flow.set(result.flow);
      this.deliveries.set(result.deliveries);
      this.invoices.set(result.invoices);
      this.changes.set(result.changes);
      const selected = this.selectedItem();
      this.selectedItem.set(result.order.items.find(i => i.itemNumber === selected?.itemNumber) ?? result.order.items[0] ?? null);
    });
  }

  protected selectItem(event: Event): void {
    const key = Number((event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey);
    this.selectedItem.set(this.order()?.items.find(i => i.itemNumber === key) ?? null);
  }

  protected openDocument(node: DocumentFlowNode): void {
    switch (node.documentType) {
      case 'SALES_ORDER_REQUEST':
        this.router.navigate(['/order-requests', node.id]);
        break;
      case 'DELIVERY':
        this.router.navigate(['/deliveries', node.id]);
        break;
      case 'INVOICE':
      case 'CANCELLATION_INVOICE':
      case 'PAYMENT':
        this.router.navigate(['/invoices', node.id]);
        break;
      case 'GOODS_ISSUE':
      case 'GOODS_ISSUE_REVERSAL':
        this.router.navigate(['/movements'], { queryParams: { search: node.number } });
        break;
    }
  }

  protected flowIcon(node: DocumentFlowNode): string {
    return {
      SALES_ORDER_REQUEST: 'inbox',
      SALES_ORDER: 'sales-order',
      DELIVERY: 'shipping-status',
      GOODS_ISSUE: 'journey-depart',
      GOODS_ISSUE_REVERSAL: 'journey-arrive',
      INVOICE: 'receipt',
      CANCELLATION_INVOICE: 'decline',
      PAYMENT: 'money-bills'
    }[node.documentType];
  }

  protected changeText(c: ChangeDocument): string {
    if (c.description) return c.description;
    return `${c.fieldName}: ${c.oldValue ?? '–'} → ${c.newValue ?? '–'}`;
  }

  // ------------------------------------------------------------------
  // Aktionen
  // ------------------------------------------------------------------

  protected openDialog(name: DialogName): void {
    const o = this.order();
    if (!o) return;
    switch (name) {
      case 'reject':
        this.rejectReason = 'R10';
        this.rejectItems = this.rejectableItems().map(i => i.itemNumber);
        break;
      case 'header':
        this.header = {
          requestedDeliveryDate: o.requestedDeliveryDate ?? '',
          customerReference: o.customerReference ?? '',
          shippingAddress: o.shippingAddress ?? '',
          billingAddress: o.billingAddress ?? '',
          notes: o.notes ?? ''
        };
        break;
      case 'item': {
        const item = this.selectedItem();
        if (!item) return;
        this.itemChange = { itemNumber: item.itemNumber, quantity: item.quantity, discountPercent: item.itemDiscountPercent ?? 0 };
        break;
      }
      case 'add':
        this.newItem = { productId: '', quantity: 1, discountPercent: 0 };
        if (!this.materials().length) this.materialsApi.list().subscribe(m => this.materials.set(m.filter(x => x.active)));
        break;
      case 'delivery':
        this.deliveryOptions = { warehouseId: '', plannedGoodsIssueDate: todayIso() };
        if (!this.warehouses().length) this.inventoryApi.warehouses().subscribe(w => this.warehouses.set(w.filter(x => x.active)));
        break;
    }
    this.dialog.set(name);
  }

  protected closeDialog(): void {
    this.dialog.set(null);
  }

  protected toggleRejectItem(itemNumber: number, checked: boolean): void {
    this.rejectItems = checked
      ? [...new Set([...this.rejectItems, itemNumber])]
      : this.rejectItems.filter(n => n !== itemNumber);
  }

  protected saveReject(): void {
    if (!this.rejectItems.length) return;
    this.run(this.ordersApi.reject(this.order()!.id, this.rejectReason, this.rejectItems), 'Positionen abgesagt');
  }

  protected saveHeader(): void {
    this.run(this.ordersApi.updateHeader(this.order()!.id, {
      requestedDeliveryDate: this.header.requestedDeliveryDate || undefined,
      customerReference: this.header.customerReference,
      shippingAddress: this.header.shippingAddress,
      billingAddress: this.header.billingAddress,
      notes: this.header.notes
    }), 'Auftragskopf geändert');
  }

  protected saveItem(): void {
    this.run(this.ordersApi.changeItem(this.order()!.id, this.itemChange.itemNumber, {
      quantity: Number(this.itemChange.quantity),
      discountPercent: Number(this.itemChange.discountPercent) || 0
    }), `Position ${this.itemChange.itemNumber} geändert`);
  }

  protected saveNewItem(): void {
    if (!this.newItem.productId) return;
    this.run(this.ordersApi.addItem(this.order()!.id, {
      productId: Number(this.newItem.productId),
      quantity: Number(this.newItem.quantity),
      discountPercent: Number(this.newItem.discountPercent) || null
    }), 'Position hinzugefügt');
  }

  protected createDelivery(): void {
    const o = this.order()!;
    this.busy.set(true);
    this.deliveriesApi.create(o.id, {
      warehouseId: this.deliveryOptions.warehouseId ? Number(this.deliveryOptions.warehouseId) : null,
      plannedGoodsIssueDate: this.deliveryOptions.plannedGoodsIssueDate || null
    }).subscribe({
      next: delivery => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success(`Auslieferung ${delivery.deliveryNumber} angelegt (${delivery.totalQuantity} Einheiten aus Lagerort ${delivery.warehouseCode})`);
        this.router.navigate(['/deliveries', delivery.id]);
      },
      error: () => this.busy.set(false)
    });
  }

  protected releaseCredit(): void {
    this.run(this.ordersApi.releaseCredit(this.order()!.id), 'Kreditsperre freigegeben');
  }

  protected reconfirm(): void {
    this.run(this.ordersApi.reconfirm(this.order()!.id), 'Verfügbarkeit neu geprüft');
  }

  private run(request: ReturnType<OrdersApi['get']>, success: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success(success);
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }
}
