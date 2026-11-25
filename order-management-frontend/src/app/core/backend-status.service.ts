import { Injectable, inject, signal } from '@angular/core';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { map, retry, timer } from 'rxjs';
import { environment } from '../../environments/environment';

/** Antwortet das Backend nach dieser Zeit nicht, startet es vermutlich gerade. */
export const STARTING_HINT_AFTER_MS = 4000;
export const RETRY_EVERY_MS = 5000;
/** Danach wird nicht weiter gewartet (rund 5 Minuten). */
export const MAX_RETRIES = 60;

export const HEALTH_URL = `${environment.apiUrl}/actuator/health`;

/**
 * Weckt das Backend beim Öffnen der Anwendung. Die kostenlose Render-Instanz schläft ohne Anfragen ein
 * und braucht für den Neustart ein bis zwei Minuten; währenddessen zeigt die Anwendung einen Hinweis.
 */
@Injectable({ providedIn: 'root' })
export class BackendStatusService {
  // Ohne Interceptors: der Aufruf braucht kein Token und soll keinen Meldungsdialog auslösen
  private readonly http = new HttpClient(inject(HttpBackend));
  private called = false;

  /** true, solange die erste Antwort des Backends auffällig lange auf sich warten lässt. */
  readonly starting = signal(false);
  /** true, sobald das Backend geantwortet hat. */
  readonly ready = signal(false);

  wake(): void {
    if (this.called) return;
    this.called = true;

    const hint = setTimeout(() => this.starting.set(true), STARTING_HINT_AFTER_MS);
    const done = (ready: boolean) => {
      clearTimeout(hint);
      this.starting.set(false);
      this.ready.set(ready);
    };

    this.http.get<{ status?: string }>(HEALTH_URL).pipe(
      // Nur die Antwort der Anwendung selbst zählt, nicht eine Zwischenseite des Hosters
      map(health => {
        if (health?.status !== 'UP') throw new Error('Backend ist noch nicht bereit');
        return health;
      }),
      retry({ count: MAX_RETRIES, delay: () => timer(RETRY_EVERY_MS) })
    ).subscribe({ next: () => done(true), error: () => done(false) });
  }
}
