import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  BackendStatusService, HEALTH_URL, MAX_RETRIES, RETRY_EVERY_MS, STARTING_HINT_AFTER_MS
} from './backend-status.service';

describe('BackendStatusService', () => {
  let http: HttpTestingController;
  let backend: BackendStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    backend = TestBed.inject(BackendStatusService);
  });

  afterEach(() => http.verify());

  it('zeigt keinen Hinweis, wenn das Backend sofort antwortet', fakeAsync(() => {
    backend.wake();
    http.expectOne(HEALTH_URL).flush({ status: 'UP' });
    tick(STARTING_HINT_AFTER_MS);

    expect(backend.ready()).toBeTrue();
    expect(backend.starting()).toBeFalse();
  }));

  it('meldet den Start, wenn die Antwort auf sich warten lässt', fakeAsync(() => {
    backend.wake();
    const request = http.expectOne(HEALTH_URL);
    tick(STARTING_HINT_AFTER_MS);
    expect(backend.starting()).toBeTrue();
    expect(backend.ready()).toBeFalse();

    request.flush({ status: 'UP' });
    expect(backend.starting()).toBeFalse();
    expect(backend.ready()).toBeTrue();
  }));

  it('versucht es nach Fehlern und fremden Antworten erneut', fakeAsync(() => {
    backend.wake();
    http.expectOne(HEALTH_URL).flush(null, { status: 502, statusText: 'Bad Gateway' });
    tick(RETRY_EVERY_MS);
    expect(backend.starting()).toBeTrue();

    // Zwischenseite des Hosters statt der Antwort der Anwendung
    http.expectOne(HEALTH_URL).flush({ message: 'Service wird gestartet' });
    tick(RETRY_EVERY_MS);
    expect(backend.ready()).toBeFalse();

    http.expectOne(HEALTH_URL).flush({ status: 'UP' });
    expect(backend.ready()).toBeTrue();
    expect(backend.starting()).toBeFalse();
  }));

  it('gibt nach der Höchstzahl an Versuchen auf', fakeAsync(() => {
    backend.wake();
    for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      http.expectOne(HEALTH_URL).error(new ProgressEvent('error'));
      tick(RETRY_EVERY_MS);
    }
    expect(backend.starting()).toBeFalse();
    expect(backend.ready()).toBeFalse();
  }));

  it('ruft das Backend nur einmal auf', () => {
    backend.wake();
    backend.wake();
    expect(http.match(HEALTH_URL).length).toBe(1);
  });
});
