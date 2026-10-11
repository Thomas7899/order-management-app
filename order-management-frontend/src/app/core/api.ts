import { HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';

export const API_URL = `${environment.apiUrl}/api`;

/** Baut HttpParams aus einem Objekt und lässt leere Werte weg. */
export function toParams(values: Record<string, unknown>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') continue;
    if (Array.isArray(value)) {
      value.filter(v => v !== null && v !== undefined && v !== '').forEach(v => (params = params.append(key, String(v))));
    } else {
      params = params.set(key, String(value));
    }
  }
  return params;
}
