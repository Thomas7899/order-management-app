import { CUSTOM_ELEMENTS_SCHEMA, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OrderRequestDetail, OrderRequestsApi, RequestSample } from '../../core/order-requests.service';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

/**
 * Bestellung erfassen: E-Mail-Text einfügen oder eine Datei (PDF, .txt, .eml) hochladen.
 * Für die Demo lassen sich Beispielbestellungen aus den aktuellen Stammdaten laden.
 */
@Component({
  selector: 'om-order-request-create-dialog',
  imports: [FormsModule, Ui5ValueAccessorDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ui5-dialog header-text="Bestellung erfassen" [open]="open()" (close)="closed.emit()">
      <div class="samples">
        <span class="om-subtle">Beispiel laden:</span>
        @for (s of samples(); track s.key) {
          <ui5-link [attr.title]="s.description" (click)="loadSample(s)">{{ s.title }}</ui5-link>
        }
      </div>
      <div class="om-form-grid">
        <div class="om-field">
          <ui5-label show-colon>Absender</ui5-label>
          <ui5-input [ngModel]="sender()" (ngModelChange)="sender.set($event)" placeholder="Name <adresse@kunde.de>"></ui5-input>
        </div>
        <div class="om-field">
          <ui5-label show-colon>Betreff</ui5-label>
          <ui5-input [ngModel]="subject()" (ngModelChange)="subject.set($event)"></ui5-input>
        </div>
        <div class="om-field span-2">
          <ui5-label show-colon>Text der E-Mail</ui5-label>
          <ui5-textarea [ngModel]="text()" (ngModelChange)="text.set($event)" rows="10" placeholder="Bestelltext hier einfügen …" [disabled]="busy()"></ui5-textarea>
        </div>
        <div class="om-field span-2">
          <ui5-label show-colon>Anhang</ui5-label>
          <div class="file-row">
            <input #picker type="file" accept=".pdf,.txt,.eml,application/pdf,text/plain,message/rfc822" hidden (change)="pick($event)" />
            <ui5-button icon="attachment" (click)="picker.click()" [disabled]="busy()">Datei wählen</ui5-button>
            @if (file(); as f) {
              <ui5-tag design="Information" hide-state-icon>
                <ui5-icon slot="icon" [attr.name]="f.name.toLowerCase().endsWith('.pdf') ? 'pdf-attachment' : 'document-text'"></ui5-icon>
                {{ f.name }}
              </ui5-tag>
              <ui5-button icon="decline" design="Transparent" tooltip="Anhang entfernen" (click)="file.set(null)"></ui5-button>
            } @else {
              <span class="om-subtle">PDF, .txt oder .eml, höchstens 2 MB</span>
            }
          </div>
        </div>
      </div>
      @if (busy()) {
        <ui5-busy-indicator active size="M" text="Bestellung wird ausgelesen und zugeordnet …" class="busy"></ui5-busy-indicator>
      }
      <div slot="footer" class="om-dialog-footer">
        <ui5-button design="Emphasized" icon="ai" [disabled]="busy() || (!text().trim() && !file())" (click)="submit()">Auslesen</ui5-button>
        <ui5-button design="Transparent" (click)="closed.emit()">Abbrechen</ui5-button>
      </div>
    </ui5-dialog>
  `,
  styles: `
    .samples { display: flex; flex-wrap: wrap; gap: .25rem 1rem; align-items: center; margin-bottom: 1rem; max-width: 44rem; }
    .file-row { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; min-height: 2.25rem; }
    ui5-textarea { width: min(44rem, 80vw); }
    .busy { width: 100%; margin-top: 1rem; }
  `
})
export class OrderRequestCreateDialogComponent {
  readonly open = input(false);
  readonly created = output<OrderRequestDetail>();
  readonly closed = output<void>();

  private readonly api = inject(OrderRequestsApi);

  protected readonly samples = signal<RequestSample[]>([]);
  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly text = signal('');
  protected readonly sender = signal('');
  protected readonly subject = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      this.text.set('');
      this.sender.set('');
      this.subject.set('');
      this.file.set(null);
      if (!untracked(this.samples).length) this.api.samples().subscribe(s => this.samples.set(s));
    });
  }

  protected pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    input.value = '';
  }

  protected loadSample(sample: RequestSample): void {
    if (sample.format === 'PDF') {
      this.api.samplePdf(sample.key).subscribe(blob => {
        this.text.set('');
        this.sender.set('');
        this.subject.set('Bestellung (PDF-Anhang)');
        this.file.set(new File([blob], 'bestellung.pdf', { type: 'application/pdf' }));
      });
    } else {
      this.api.sampleText(sample.key).subscribe(s => {
        this.text.set(s.text);
        this.sender.set(s.sender);
        this.subject.set(s.subject);
        this.file.set(null);
      });
    }
  }

  protected submit(): void {
    this.busy.set(true);
    this.api.create({ text: this.text(), sender: this.sender(), subject: this.subject(), file: this.file() }).subscribe({
      next: detail => {
        this.busy.set(false);
        this.created.emit(detail);
      },
      error: () => this.busy.set(false)
    });
  }
}
