// order-management-frontend/src/app/navigation/navigation.component.ts
import { Component } from '@angular/core';

import { RouterModule } from '@angular/router';

@Component({
    selector: 'app-navigation',
    imports: [RouterModule],
    templateUrl: './navigation.component.html',
    styleUrls: ['./navigation.component.css']
})
export class NavigationComponent {
  isMenuOpen = false;

  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu(): void {
    this.isMenuOpen = false;
  }
}