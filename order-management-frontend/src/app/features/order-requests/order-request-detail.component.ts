import { CUSTOM_ELEMENTS_SCHEMA, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import {
  CONFIDENCE_DESIGN, CONFIDENCE_TEXT, Confidence, OVERALL_TEXT, CustomerCandidate, ItemProposal, MaterialCandidate,
  OrderRequestDetail, OrderRequestsApi, draftFrom
} from '../../core/order-requests.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

interface LineChoice {
  lineNumber: number;
  productId: string;
  quantity: number;
}

const MESSAGE_DESIGN: Record<string, string> = { E: 'Negative', W: 'Critical', I: 'Information', S: 'Positive' };

/**
 * Eingang prüfen: links das Original, rechts der Vorschlag mit Zuordnung und Konfidenz.
 * Der Vertrieb wählt bei Bedarf eine Alternative und übernimmt dann in den Anlage-Assistenten.
 */
@Component({
  selector: 'om-order-request-detail',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, StatusTagComponent, DatePipe, CurrencyPipe, PercentPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './order-request-detail.component.html',
  styles: `
    .layout { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 1rem; align-items: start; }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
    .messages { display: flex; flex-direction: column; gap: .375rem; margin-bottom: 1rem; }
    .party { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1rem; }
    @media (max-width: 700px) { .party { grid-template-columns: 1fr; } }
    .doc-value { font-style: italic; }
    .match-row { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; }
    .match-row ui5-select { min-width: 16rem; flex: 1; }
    .reason { font-size: var(--sapFontSmallSize); color: var(--sapContent_LabelColor); }
    .source { white-space: pre-wrap; font-family: var(--sapFontFamily); font-size: var(--sapFontSize); line-height: 1.5;
      margin: 0; padding: .75rem 1rem; background: var(--sapList_Background); border: 1px solid var(--sapList_BorderColor);
      border-radius: .5rem; max-height: 70vh; overflow: auto; }
    .pdf { width: 100%; height: 70vh; border: 1px solid var(--sapList_BorderColor); border-radius: .5rem; }
    ui5-step-input { width: 7rem; }
    .qty-doc { font-size: var(--sapFontSmallSize); color: var(--sapContent_LabelColor); }
  `
})
export class OrderRequestDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(OrderRequestsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly auth = inject(AuthService);

  protected readonly confidenceText = CONFIDENCE_TEXT;
  protected readonly overallText = OVERALL_TEXT;
  protected readonly confidenceDesign = CONFIDENCE_DESIGN;
  protected readonly messageDesign = MESSAGE_DESIGN;

  protected readonly request = signal<OrderRequestDetail | null>(null);
  protected readonly busy = signal(false);
  protected readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  protected readonly showText = signal(false);
  protected readonly rejectOpen = signal(false);
  protected rejectNote = '';
  private objectUrl: string | null = null;

  protected readonly customerId = signal('');
  protected readonly lines = signal<LineChoice[]>([]);

  protected readonly editable = computed(() => this.request()?.status.code === 'OPEN' && this.auth.hasAnyRole('SALES'));
  protected readonly customerOptions = computed<CustomerCandidate[]>(() => {
    const c = this.request()?.customer;
    return c ? (c.match ? [c.match, ...c.alternatives] : c.alternatives) : [];
  });
  protected readonly includedLines = computed(() => this.lines().filter(l => l.productId).length);

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
  }

  protected load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.busy.set(true);
    this.api.get(id).subscribe({
      next: r => this.show(r),
      error: () => this.busy.set(false)
    });
  }

  private show(r: OrderRequestDetail): void {
    this.request.set(r);
    this.busy.set(false);
    this.customerId.set(r.customer.match ? String(r.customer.match.customerId) : '');
    this.lines.set(r.items.map(i => ({
      lineNumber: i.lineNumber,
      productId: i.match ? String(i.match.productId) : '',
      quantity: Math.max(1, i.extracted.quantity ?? 1)
    })));
    if (r.fileName && r.sourceType.code === 'PDF' && !this.pdfUrl()) {
      this.api.file(r.id).subscribe(blob => {
        this.objectUrl = URL.createObjectURL(blob);
        this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
      });
    }
  }

  protected options(item: ItemProposal): MaterialCandidate[] {
    return item.match ? [item.match, ...item.alternatives] : item.alternatives;
  }

  protected optionOf(item: ItemProposal, productId: string): MaterialCandidate | undefined {
    return this.options(item).find(o => String(o.productId) === productId);
  }

  protected choiceOf(lineNumber: number): LineChoice | undefined {
    return this.lines().find(l => l.lineNumber === lineNumber);
  }

  protected chooseMaterial(lineNumber: number, productId: string): void {
    this.lines.update(list => list.map(l => (l.lineNumber === lineNumber ? { ...l, productId } : l)));
  }

  protected chooseQuantity(lineNumber: number, quantity: number): void {
    this.lines.update(list => list.map(l => (l.lineNumber === lineNumber ? { ...l, quantity: Number(quantity) || 1 } : l)));
  }

  /** Konfidenz der aktuell gewählten Option (eine bewusst gewählte Alternative gilt als geprüft). */
  protected lineConfidence(item: ItemProposal): Confidence {
    const choice = this.choiceOf(item.lineNumber);
    if (!choice?.productId) return 'NONE';
    if (item.match && String(item.match.productId) === choice.productId) return item.match.confidence;
    return 'HIGH';
  }

  protected customerConfidence(): Confidence {
    const c = this.request()?.customer.match;
    if (!this.customerId()) return 'NONE';
    return c && String(c.customerId) === this.customerId() ? c.confidence : 'HIGH';
  }

  protected takeOver(): void {
    const r = this.request();
    if (!r) return;
    const draft = draftFrom(r);
    draft.customerId = this.customerId() ? Number(this.customerId()) : null;
    draft.items = r.items.map(i => {
      const choice = this.choiceOf(i.lineNumber);
      return {
        productId: choice?.productId ? Number(choice.productId) : null,
        quantity: choice?.quantity ?? 1,
        hint: [i.extracted.articleNumber, i.extracted.description].filter(Boolean).join(' ')
      };
    });
    this.api.draft.set(draft);
    this.router.navigate(['/orders/new'], { queryParams: { requestId: r.id } });
  }

  protected reextract(): void {
    const r = this.request();
    if (!r) return;
    this.busy.set(true);
    this.api.reextract(r.id).subscribe({
      next: detail => {
        this.show(detail);
        this.messages.success(`Eingang ${detail.requestNumber} wurde erneut ausgelesen`);
      },
      error: () => this.busy.set(false)
    });
  }

  protected reject(): void {
    const r = this.request();
    if (!r) return;
    this.api.reject(r.id, this.rejectNote).subscribe(detail => {
      this.rejectOpen.set(false);
      this.show(detail);
      this.messages.success(`Eingang ${detail.requestNumber} wurde abgelehnt`);
    });
  }
}
