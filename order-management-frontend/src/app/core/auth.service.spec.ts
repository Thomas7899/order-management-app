import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { API_URL } from './api';
import { AuthService } from './auth.service';
import { LoginResponse, RoleCode } from './models';

function response(roles: RoleCode[], expiresInMs = 3_600_000): LoginResponse {
  return {
    token: 'jwt',
    expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    user: {
      id: 1, username: 'MREUTER', fullName: 'Marie Reuter', email: 'marie.reuter@demo.local', active: true,
      roles: roles.map(code => ({ code, text: code, description: '' }))
    }
  };
}

describe('AuthService', () => {
  let http: HttpTestingController;

  function setup(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.removeItem('om.session'));
  afterEach(() => localStorage.removeItem('om.session'));

  it('speichert die Sitzung nach der Anmeldung', () => {
    const auth = setup();
    auth.login('MREUTER', 'demo1234').subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response(['SALES']));

    expect(auth.isLoggedIn()).toBeTrue();
    expect(auth.token()).toBe('jwt');
    expect(JSON.parse(localStorage.getItem('om.session')!).token).toBe('jwt');
  });

  it('prüft Rollen; Administratoren dürfen alles', () => {
    const auth = setup();
    auth.login('MREUTER', 'demo1234').subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response(['SALES']));
    expect(auth.hasAnyRole('SALES')).toBeTrue();
    expect(auth.hasAnyRole('BILLING', 'WAREHOUSE')).toBeFalse();

    auth.login('ADMIN', 'demo1234').subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response(['ADMIN']));
    expect(auth.hasAnyRole('BILLING')).toBeTrue();
  });

  it('übernimmt eine abgelaufene Sitzung nicht', () => {
    const expired = response(['SALES'], -1000);
    localStorage.setItem('om.session', JSON.stringify({ token: expired.token, expiresAt: expired.expiresAt, user: expired.user }));
    expect(setup().isLoggedIn()).toBeFalse();
  });

  it('entfernt die Sitzung beim Abmelden', () => {
    const auth = setup();
    auth.login('MREUTER', 'demo1234').subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response(['SALES']));
    auth.logout();
    expect(auth.isLoggedIn()).toBeFalse();
    expect(localStorage.getItem('om.session')).toBeNull();
  });
});
