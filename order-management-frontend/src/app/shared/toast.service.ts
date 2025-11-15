import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ToastService {
  message$ = new Subject<string>();

  show(msg: string) {
    this.message$.next(msg);
  }
}
