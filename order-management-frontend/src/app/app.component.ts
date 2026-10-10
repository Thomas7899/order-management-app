import { Component } from '@angular/core';

import { RouterOutlet } from '@angular/router';
import { NavigationComponent } from './navigation/navigation.component';
import { ToastComponent } from './shared/toast.component';

@Component({
    selector: 'app-root',
    imports: [
    RouterOutlet,
    NavigationComponent,
    ToastComponent
],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'Order Management System';
}
