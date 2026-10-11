import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { API_URL } from './api';
import { AuthService } from './auth.service';
import { SILENT_ERRORS, authInterceptor, errorInterceptor } from './http.interceptors';
import { MessageService } from './message.service';

describe('HTTP-Interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let messages: jasmine.SpyObj<MessageService>;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    messages = jasmine.createSpyObj<MessageService>('MessageService', ['error', 'info']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['token', 'logout']);
    auth.token.and.returnValue('jwt');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MessageService, useValue: messages },
        { provide: AuthService, useValue: auth }
      ]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('hängt das Token nur an Aufrufe der eigenen API', () => {
    http.get(`${API_URL}/orders`).subscribe();
    http.get('https://example.org/data').subscribe();

    expect(backend.expectOne(`${API_URL}/orders`).request.headers.get('Authorization')).toBe('Bearer jwt');
    expect(backend.expectOne('https://example.org/data').request.headers.has('Authorization')).toBeFalse();
  });

  it('zeigt Fachfehler mit Nachrichtennummer an', () => {
    http.post(`${API_URL}/orders`, {}).subscribe({ error: () => undefined });
    backend.expectOne(`${API_URL}/orders`).flush(
      { detail: 'Kreditlimit überschritten', messageId: 'V1 154' },
      { status: 422, statusText: 'Unprocessable Entity' });

    expect(messages.error).toHaveBeenCalledWith('Kreditlimit überschritten', 'V1 154', undefined);
  });

  it('meldet bei abgelaufener Sitzung ab', () => {
    http.get(`${API_URL}/orders`).subscribe({ error: () => undefined });
    backend.expectOne(`${API_URL}/orders`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalled();
    expect(messages.error).not.toHaveBeenCalled();
  });

  it('unterdrückt den Meldungsdialog, wenn die Seite Fehler selbst anzeigt', () => {
    http.get(`${API_URL}/reviews/similar`, { context: new HttpContext().set(SILENT_ERRORS, true) }).subscribe({ error: () => undefined });
    backend.expectOne(`${API_URL}/reviews/similar`).flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(messages.error).not.toHaveBeenCalled();
  });
});
