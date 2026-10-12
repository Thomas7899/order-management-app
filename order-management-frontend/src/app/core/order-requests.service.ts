import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from './api';
import { SILENT_ERRORS } from './http.interceptors';
import { CodeText, Order, OrderCreateRequest } from './models';

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';

export interface ExtractedParty {
  name: string | null;
  company: string | null;
  email: string | null;
  customerNumber: string | null;
  city: string | null;
}

export interface ExtractedLine {
  articleNumber: string | null;
  description: string | null;
  quantity: number | null;
  unit: string | null;
  catalogMaterialNumber: string | null;
}

export interface CustomerCandidate {
  customerId: number;
  customerNumber: string;
  name: string;
  email: string;
  city: string | null;
  score: number;
  confidence: Confidence;
  reason: string;
}

export interface MaterialCandidate {
  productId: number;
  materialNumber: string;
  name: string;
  unitOfMeasure: string;
  price: number;
  score: number;
  confidence: Confidence;
  reason: string;
}

export interface ItemProposal {
  lineNumber: number;
  extracted: ExtractedLine;
  match: MaterialCandidate | null;
  alternatives: MaterialCandidate[];
}

export interface RequestMessage {
  type: 'S' | 'I' | 'W' | 'E';
  text: string;
}

export interface OrderRequestDetail {
  id: number;
  requestNumber: string;
  status: CodeText;
  sourceType: CodeText;
  sender: string | null;
  subject: string | null;
  receivedAt: string;
  sourceText: string;
  fileName: string | null;
  extractionSource: 'KI' | 'REGELN' | null;
  extractedAt: string | null;
  customer: { extracted: ExtractedParty | null; match: CustomerCandidate | null; alternatives: CustomerCandidate[] };
  customerReference: string | null;
  requestedDeliveryDate: string | null;
  deliveryAddress: string | null;
  notes: string | null;
  items: ItemProposal[];
  confidence: Confidence | null;
  messages: RequestMessage[];
  orderId: number | null;
  orderNumber: string | null;
  rejectionNote: string | null;
  processedAt: string | null;
  processedBy: string | null;
  createdBy: string | null;
}

export interface OrderRequestListItem {
  id: number;
  requestNumber: string;
  status: CodeText;
  sourceType: CodeText;
  sender: string | null;
  subject: string | null;
  receivedAt: string;
  customerName: string | null;
  customerNumber: string | null;
  itemCount: number;
  confidence: Confidence | null;
  orderId: number | null;
  orderNumber: string | null;
}

export interface RequestSample {
  key: string;
  title: string;
  description: string;
  format: 'TEXT' | 'PDF';
}

export interface SampleText {
  sender: string;
  subject: string;
  text: string;
}

/** Vom Vertrieb im Auftragseingang geprüfte Angaben, die der Anlage-Assistent übernimmt. */
export interface OrderDraft {
  requestId: number;
  requestNumber: string;
  customerId: number | null;
  customerReference: string | null;
  requestedDeliveryDate: string | null;
  shippingAddress: string | null;
  notes: string | null;
  /** productId null: im Dokument erkannt, aber keinem Material zugeordnet – im Assistenten auswählen */
  items: { productId: number | null; quantity: number; hint: string }[];
}

/** Entwurf mit den besten Vorschlägen (z. B. wenn der Assistent direkt mit ?requestId= aufgerufen wird). */
export function draftFrom(detail: OrderRequestDetail): OrderDraft {
  return {
    requestId: detail.id,
    requestNumber: detail.requestNumber,
    customerId: detail.customer.match?.customerId ?? null,
    customerReference: detail.customerReference,
    requestedDeliveryDate: detail.requestedDeliveryDate,
    shippingAddress: detail.deliveryAddress,
    notes: detail.notes,
    items: detail.items.map(i => ({
      productId: i.match?.productId ?? null,
      quantity: Math.max(1, i.extracted.quantity ?? 1),
      hint: [i.extracted.articleNumber, i.extracted.description].filter(Boolean).join(' ')
    }))
  };
}

export const CONFIDENCE_TEXT: Record<Confidence, string> = {
  HIGH: 'Eindeutig',
  MEDIUM: 'Prüfen',
  LOW: 'Unsicher',
  NONE: 'Fehlt'
};

/** Gesamtbewertung eines Eingangs (schwächste Zuordnung) */
export const OVERALL_TEXT: Record<Confidence, string> = {
  HIGH: 'Eindeutig',
  MEDIUM: 'Prüfen',
  LOW: 'Unsicher',
  NONE: 'Unvollständig'
};

/** Name ohne Adresse aus "Name <adresse@kunde.de>" */
export function senderName(sender: string | null): string {
  if (!sender) return '–';
  const name = sender.replace(/<[^>]*>/, '').replace(/"/g, '').trim();
  return name || sender.replace(/[<>]/g, '').trim();
}

export const CONFIDENCE_DESIGN: Record<Confidence, string> = {
  HIGH: 'Positive',
  MEDIUM: 'Critical',
  LOW: 'Negative',
  NONE: 'Negative'
};

@Injectable({ providedIn: 'root' })
export class OrderRequestsApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/order-requests`;

  /** Übergabe vom Auftragseingang an den Anlage-Assistenten (nur im Speicher, wird beim Übernehmen gelöscht). */
  readonly draft = signal<OrderDraft | null>(null);

  list(status?: string[]): Observable<OrderRequestListItem[]> {
    return this.http.get<OrderRequestListItem[]>(this.url, { params: status?.length ? { status: status.join(',') } : {} });
  }

  countOpen(): Observable<{ open: number }> {
    return this.http.get<{ open: number }>(`${this.url}/count`, { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  get(id: number): Observable<OrderRequestDetail> {
    return this.http.get<OrderRequestDetail>(`${this.url}/${id}`);
  }

  file(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/file`, { responseType: 'blob' });
  }

  create(input: { text?: string; sender?: string; subject?: string; file?: File | Blob | null; fileName?: string }): Observable<OrderRequestDetail> {
    const form = new FormData();
    if (input.text?.trim()) form.append('text', input.text);
    if (input.sender?.trim()) form.append('sender', input.sender);
    if (input.subject?.trim()) form.append('subject', input.subject);
    if (input.file) form.append('file', input.file, input.fileName ?? (input.file as File).name ?? 'bestellung');
    return this.http.post<OrderRequestDetail>(this.url, form);
  }

  reextract(id: number): Observable<OrderRequestDetail> {
    return this.http.post<OrderRequestDetail>(`${this.url}/${id}/extract`, {});
  }

  convert(id: number, order: OrderCreateRequest): Observable<Order> {
    return this.http.post<Order>(`${this.url}/${id}/convert`, order);
  }

  reject(id: number, note: string): Observable<OrderRequestDetail> {
    return this.http.post<OrderRequestDetail>(`${this.url}/${id}/reject`, { note: note || null });
  }

  samples(): Observable<RequestSample[]> {
    return this.http.get<RequestSample[]>(`${this.url}/samples`);
  }

  sampleText(key: string): Observable<SampleText> {
    return this.http.get<SampleText>(`${this.url}/samples/${key}`);
  }

  samplePdf(key: string): Observable<Blob> {
    return this.http.get(`${this.url}/samples/${key}/file`, { responseType: 'blob' });
  }
}
