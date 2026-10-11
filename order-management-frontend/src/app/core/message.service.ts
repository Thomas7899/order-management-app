import { Injectable, signal } from '@angular/core';

export type MessageType = 'success' | 'info' | 'warning' | 'error';

export interface AppMessage {
  id: number;
  type: MessageType;
  text: string;
  messageId?: string;
  details?: string[];
  timestamp: Date;
}

/**
 * Zentrale Meldungen der Anwendung: Erfolgsmeldungen erscheinen als Toast,
 * Fehler als Meldungsdialog (wie MessageToast/MessageBox in SAP Fiori).
 * Alle Meldungen landen zusätzlich im Meldungsprotokoll der Kopfleiste.
 */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private nextId = 1;

  readonly toast = signal<AppMessage | null>(null);
  readonly dialog = signal<AppMessage | null>(null);
  readonly log = signal<AppMessage[]>([]);

  success(text: string): void {
    this.push({ type: 'success', text }, 'toast');
  }

  info(text: string): void {
    this.push({ type: 'info', text }, 'toast');
  }

  warning(text: string, messageId?: string): void {
    this.push({ type: 'warning', text, messageId }, 'dialog');
  }

  error(text: string, messageId?: string, details?: string[]): void {
    this.push({ type: 'error', text, messageId, details }, 'dialog');
  }

  closeDialog(): void {
    this.dialog.set(null);
  }

  clearLog(): void {
    this.log.set([]);
  }

  private push(message: Omit<AppMessage, 'id' | 'timestamp'>, channel: 'toast' | 'dialog'): void {
    const full: AppMessage = { ...message, id: this.nextId++, timestamp: new Date() };
    this.log.update(list => [full, ...list].slice(0, 50));
    if (channel === 'toast') this.toast.set(full);
    else this.dialog.set(full);
  }
}
