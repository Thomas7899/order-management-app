// src/app/products/components/product-filters/product-filters.component.ts
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-product-filters',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './product-filters.component.html',
  styleUrls: ['./product-filters.component.css']
})
export class ProductFiltersComponent {
  @Input() searchTerm = '';
  @Output() searchTermChange = new EventEmitter<string>();

  @Input() selectedCategory = '';
  @Output() selectedCategoryChange = new EventEmitter<string>();

  @Input() showOnlyActive = false;
  @Output() showOnlyActiveChange = new EventEmitter<boolean>();

  @Output() filtersChanged = new EventEmitter<void>();

  onSearchTermChange(value: string) {
    this.searchTermChange.emit(value);
    this.filtersChanged.emit();
  }

  onSelectedCategoryChange(value: string) {
    this.selectedCategoryChange.emit(value);
    this.filtersChanged.emit();
  }

  onShowOnlyActiveChange(value: boolean) {
    this.showOnlyActiveChange.emit(value);
    this.filtersChanged.emit();
  }
}
