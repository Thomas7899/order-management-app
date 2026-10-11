import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_URL } from './api';
import { DemoUser, LoginResponse, RoleCode, UserInfo } from './models';

const STORAGE_KEY = 'om.session';

interface StoredSession {
  token: string;
  expiresAt: string;
  user: UserInfo;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<StoredSession | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly roles = computed(() => new Set(this.user()?.roles.map(r => r.code) ?? []));

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_URL}/auth/login`, { username, password }).pipe(
      tap(response => {
        const session: StoredSession = { token: response.token, expiresAt: response.expiresAt, user: response.user };
        this.store(session);
        this.session.set(session);
      })
    );
  }

  demoUsers(): Observable<DemoUser[]> {
    return this.http.get<DemoUser[]>(`${API_URL}/auth/demo-users`);
  }

  logout(returnUrl?: string): void {
    this.store(null);
    this.session.set(null);
    this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : undefined);
  }

  /** true, wenn der Benutzer mindestens eine der Rollen hat; Administratoren dürfen alles. */
  hasAnyRole(...roles: RoleCode[]): boolean {
    const own = this.roles();
    return own.has('ADMIN') || roles.some(r => own.has(r));
  }

  private restore(): StoredSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as StoredSession;
      return new Date(session.expiresAt).getTime() > Date.now() ? session : null;
    } catch {
      return null;
    }
  }

  private store(session: StoredSession | null): void {
    try {
      if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Speicher nicht verfügbar (z. B. privater Modus) – Sitzung gilt dann nur bis zum Neuladen
    }
  }
}
