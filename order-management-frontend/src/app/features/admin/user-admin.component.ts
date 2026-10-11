import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { UsersApi } from '../../core/analytics-api.service';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { Problem, RoleCode, RoleInfo, UserInfo } from '../../core/models';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

interface UserForm {
  username: string;
  fullName: string;
  email: string;
  roles: RoleCode[];
  active: boolean;
  password: string;
}

const EMPTY: UserForm = { username: '', fullName: '', email: '', roles: [], active: true, password: '' };

/** Benutzerpflege (angelehnt an SU01): Benutzer anlegen, Rollen zuordnen, sperren, Kennwort zurücksetzen. */
@Component({
  selector: 'om-user-admin',
  imports: [FormsModule, Ui5ValueAccessorDirective, DatePipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './user-admin.component.html',
  styles: `
    .roles { display: flex; gap: .25rem; flex-wrap: wrap; }
    .role-list { display: flex; flex-direction: column; gap: .25rem; }
    .role-desc { color: var(--sapContent_LabelColor); font-size: var(--sapFontSmallSize); margin: -.25rem 0 .25rem 2rem; }
  `
})
export class UserAdminComponent implements OnInit {
  private readonly api = inject(UsersApi);
  private readonly messages = inject(MessageService);
  protected readonly auth = inject(AuthService);

  protected readonly users = signal<UserInfo[]>([]);
  protected readonly roles = signal<RoleInfo[]>([]);
  protected readonly loading = signal(true);
  protected search = '';
  protected readonly query = signal('');

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.users().filter(u => !q || u.username.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q)
      || u.email.toLowerCase().includes(q) || u.roles.some(r => r.text.toLowerCase().includes(q)));
  });

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<UserInfo | null>(null);
  protected readonly busy = signal(false);
  protected readonly errors = signal<Record<string, string>>({});
  protected form: UserForm = { ...EMPTY };

  ngOnInit(): void {
    this.api.roles().subscribe(r => this.roles.set(r));
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: u => {
        this.users.set(u.sort((a, b) => a.username.localeCompare(b.username)));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected onSearch(): void {
    this.query.set(this.search);
  }

  protected isSelf(user: UserInfo): boolean {
    return user.username === this.auth.user()?.username;
  }

  protected create(): void {
    this.editing.set(null);
    this.form = { ...EMPTY, roles: [] };
    this.errors.set({});
    this.dialogOpen.set(true);
  }

  protected edit(user: UserInfo): void {
    this.editing.set(user);
    this.form = {
      username: user.username, fullName: user.fullName, email: user.email,
      roles: user.roles.map(r => r.code), active: user.active, password: ''
    };
    this.errors.set({});
    this.dialogOpen.set(true);
  }

  protected openFromRow(event: Event): void {
    const id = Number((event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey);
    const user = this.users().find(u => u.id === id);
    if (user) this.edit(user);
  }

  protected toggleRole(code: RoleCode, checked: boolean): void {
    this.form.roles = checked ? [...new Set([...this.form.roles, code])] : this.form.roles.filter(r => r !== code);
  }

  protected hasRole(code: RoleCode): boolean {
    return this.form.roles.includes(code);
  }

  protected save(): void {
    const user = this.editing();
    const local: Record<string, string> = {};
    if (!user && this.form.password.length < 8) local['password'] = 'Kennwort: mindestens 8 Zeichen';
    if (user && this.form.password && this.form.password.length < 8) local['password'] = 'Kennwort: mindestens 8 Zeichen';
    if (!this.form.roles.length) local['roles'] = 'Mindestens eine Rolle ist erforderlich';
    if (Object.keys(local).length) {
      this.errors.set(local);
      return;
    }

    this.busy.set(true);
    const request = user
      ? this.api.update(user.id, {
          fullName: this.form.fullName, email: this.form.email, roles: this.form.roles, active: this.form.active,
          password: this.form.password || undefined
        })
      : this.api.create({
          username: this.form.username.toUpperCase(), fullName: this.form.fullName, email: this.form.email,
          roles: this.form.roles, password: this.form.password
        });
    request.subscribe({
      next: saved => {
        this.busy.set(false);
        this.dialogOpen.set(false);
        this.messages.success(user ? `Benutzer ${saved.username} gesichert` : `Benutzer ${saved.username} angelegt`);
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.errors.set((e.error as Problem)?.errors ?? {});
      }
    });
  }

  protected setActive(user: UserInfo, active: boolean): void {
    this.api.update(user.id, { active }).subscribe(saved => {
      this.messages.success(active ? `Benutzer ${saved.username} entsperrt` : `Benutzer ${saved.username} gesperrt`);
      this.users.update(list => list.map(u => (u.id === saved.id ? saved : u)));
    });
  }
}
