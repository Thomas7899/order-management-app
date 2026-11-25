import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { BackendStatusService, HEALTH_URL } from './core/backend-status.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
  });

  it('erzeugt die Anwendung und weckt das Backend', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
    TestBed.inject(HttpTestingController).expectOne(HEALTH_URL).flush({ status: 'UP' });
  });

  it('zeigt einen Hinweis, solange das Backend startet', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const backend = TestBed.inject(BackendStatusService);
    const hint = () => (fixture.nativeElement as HTMLElement).querySelector('.backend-starting');

    fixture.detectChanges();
    expect(hint()).toBeNull();

    backend.starting.set(true);
    fixture.detectChanges();
    expect(hint()?.textContent).toContain('Der Server wird gerade gestartet');

    TestBed.inject(HttpTestingController).expectOne(HEALTH_URL).flush({ status: 'UP' });
    fixture.detectChanges();
    expect(hint()).toBeNull();
  });
});
