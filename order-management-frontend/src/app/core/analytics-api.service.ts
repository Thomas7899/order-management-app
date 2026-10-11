import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from './api';
import { AbcAnalysis, DashboardStats, KpiDashboard, RecentActivity, RoleInfo, SalesForecast, UserInfo } from './models';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  stats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${API_URL}/dashboard/stats`);
  }

  recentActivity(): Observable<RecentActivity> {
    return this.http.get<RecentActivity>(`${API_URL}/dashboard/recent-activity`);
  }
}

@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/reporting`;

  kpis(): Observable<KpiDashboard> {
    return this.http.get<KpiDashboard>(`${this.url}/kpi`);
  }

  productAbc(): Observable<AbcAnalysis> {
    return this.http.get<AbcAnalysis>(`${this.url}/abc/products`);
  }

  customerAbc(): Observable<AbcAnalysis> {
    return this.http.get<AbcAnalysis>(`${this.url}/abc/customers`);
  }

  forecast(monthsHistory = 12, monthsForecast = 6): Observable<SalesForecast> {
    return this.http.get<SalesForecast>(`${this.url}/forecast`, { params: { monthsHistory, monthsForecast } });
  }
}

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/users`;

  list(): Observable<UserInfo[]> {
    return this.http.get<UserInfo[]>(this.url);
  }

  roles(): Observable<RoleInfo[]> {
    return this.http.get<RoleInfo[]>(`${this.url}/roles`);
  }

  create(user: { username: string; fullName: string; email: string; roles: string[]; password: string }): Observable<UserInfo> {
    return this.http.post<UserInfo>(this.url, user);
  }

  update(id: number, changes: { fullName?: string; email?: string; roles?: string[]; active?: boolean; password?: string }): Observable<UserInfo> {
    return this.http.patch<UserInfo>(`${this.url}/${id}`, changes);
  }
}
