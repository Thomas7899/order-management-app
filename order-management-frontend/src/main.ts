// order-management-frontend/src/main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';
import { applyTheme, initUi5, loadTheme } from './app/shared/ui5';

initUi5();
applyTheme(loadTheme());

bootstrapApplication(AppComponent, appConfig).catch(err => console.error(err));
