import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { DemoUser, Problem } from '../../core/models';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

const DEMO_PASSWORD = 'demo1234';

@Component({
  selector: 'om-login',
  imports: [FormsModule, Ui5ValueAccessorDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected username = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly demoUsers = signal<DemoUser[]>([]);
  protected readonly demoPassword = DEMO_PASSWORD;

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) {
      this.router.navigateByUrl(this.returnUrl());
      return;
    }
    this.auth.demoUsers().subscribe({
      next: users => this.demoUsers.set(users),
      error: () => this.demoUsers.set([])
    });
  }

  protected useDemo(user: DemoUser): void {
    this.username = user.username;
    this.password = DEMO_PASSWORD;
    this.login();
  }

  protected login(): void {
    if (!this.username || !this.password || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl()),
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(e.status === 0
          ? 'Der Server ist nicht erreichbar. Startet das Backend noch? Bitte in einigen Sekunden erneut versuchen.'
          : (e.error as Problem)?.detail ?? 'Anmeldung fehlgeschlagen.');
      }
    });
  }

  private returnUrl(): string {
    return this.route.snapshot.queryParamMap.get('returnUrl') || '/';
  }
}
