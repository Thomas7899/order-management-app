import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_URL } from './api';
import { AuthService } from './auth.service';
import { MessageService } from './message.service';
import { Problem } from './models';

/** Unterdrückt den globalen Meldungsdialog für einen Aufruf, der Fehler selbst anzeigt. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

/** Hängt das JWT an alle Aufrufe der eigenen API an. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  if (token && req.url.startsWith(API_URL)) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/**
 * Übersetzt Fehlerantworten (RFC 7807) in Meldungen. Bei abgelaufener Anmeldung
 * geht es zurück zur Anmeldeseite.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const messages = inject(MessageService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const isLogin = req.url.endsWith('/auth/login');
      if (error.status === 401 && !isLogin) {
        auth.logout(router.url);
        messages.info('Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an.');
      } else if (!isLogin && !req.context.get(SILENT_ERRORS)) {
        const problem = (error.error ?? {}) as Problem;
        if (error.status === 0) {
          messages.error('Der Server ist nicht erreichbar. Bitte später erneut versuchen.');
        } else if (error.status === 403) {
          messages.error(problem.detail ?? 'Für diese Aktion fehlt Ihnen die Berechtigung.', problem.messageId ?? '00 001');
        } else {
          const details = problem.errors ? Object.values(problem.errors) : undefined;
          messages.error(problem.detail ?? 'Es ist ein Fehler aufgetreten.', problem.messageId, details);
        }
      }
      return throwError(() => error);
    })
  );
};
