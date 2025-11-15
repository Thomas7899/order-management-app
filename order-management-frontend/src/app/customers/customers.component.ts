// order-management-frontend/src/app/customers/customers.component.ts
import { Component, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, FormGroup, FormControl } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CustomerService, Customer } from '../services/customer.service';
import { debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ToastService } from '../shared/toast.service';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './customers.component.html',
  styleUrls: ['./customers.component.css']
})
export class CustomersComponent implements OnInit {
  customers: Customer[] = [];
  editingCustomer: Customer | null = null;
  customerForm: FormGroup;
  searchControl = new FormControl('');
  showAddForm = false;
  isLoading = false;
  isSaving = false;
  errorMessage: string | null = null;

  constructor(
    private customerService: CustomerService,
    private fb: FormBuilder,
    private destroyRef: DestroyRef,
    private toastService: ToastService
  ) {
    this.customerForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.pattern(/^.+@.+\..+$/)]],
      phone: [''],
      address: [''],
      city: [''],
      zipCode: [''],
      country: ['Deutschland']
    });
  }

  ngOnInit() {
    this.loadCustomers();
    this.setupSearchSubscription();
  }

  private setupSearchSubscription() {
    this.searchControl.valueChanges.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      tap(() => {
        this.isLoading = true;
        this.errorMessage = null;
      }),
      switchMap(term => {
        const searchTerm = term || '';
        if (searchTerm.trim()) {
          return this.customerService.searchCustomers(searchTerm);
        } else {
          return this.customerService.getCustomers();
        }
      }),
      takeUntilDestroyed(this.destroyRef)
    )
    .subscribe({
      next: (customers) => {
        this.customers = customers;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Fehler bei der Suche. Bitte erneut versuchen.';
        this.isLoading = false;
      }
    });
  }

  loadCustomers() {
    this.isLoading = true;
    this.errorMessage = null;

    this.customerService.getCustomers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customers) => {
          this.customers = customers;
          this.isLoading = false;
        },
        error: () => {
          this.errorMessage = 'Kunden konnten nicht geladen werden.';
          this.isLoading = false;
        }
      });
  }

  toggleAddForm() {
    this.showAddForm = !this.showAddForm;
    if (!this.showAddForm) {
      this.resetForm();
    }
  }

  saveCustomer() {
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this.errorMessage = null;

    const customerData = this.customerForm.value;

    if (this.editingCustomer) {
      this.customerService.updateCustomer(this.editingCustomer.id!, customerData)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (updatedCustomer) => {
            const index = this.customers.findIndex(c => c.id === updatedCustomer.id);
            if (index > -1) this.customers[index] = updatedCustomer;
            this.toastService.show("Kunde erfolgreich aktualisiert ✓");
            this.resetForm();
          },
          error: () => {
            this.errorMessage = 'Kunde konnte nicht aktualisiert werden.';
            this.isSaving = false;
          }
        });
    } else {
      this.customerService.createCustomer(customerData)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (newCustomer) => {
            this.customers.push(newCustomer);
            this.toastService.show("Neuer Kunde erfolgreich erstellt ✓");
            this.resetForm();
          },
          error: () => {
            this.errorMessage = 'Kunde konnte nicht erstellt werden.';
            this.isSaving = false;
          }
        });
    }
  }

  editCustomer(customer: Customer) {
    this.editingCustomer = customer;
    this.customerForm.patchValue(customer);
    this.showAddForm = true;
    this.errorMessage = null;
  }

  deleteCustomer(id: number) {
    if (!confirm('Sind Sie sicher, dass Sie diesen Kunden löschen möchten?')) return;

    this.errorMessage = null;

    this.customerService.deleteCustomer(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.customers = this.customers.filter(c => c.id !== id);
        },
        error: () => {
          this.errorMessage = 'Kunde konnte nicht gelöscht werden.';
        }
      });
  }

  closeForm() {
    this.resetForm();
  }

  private resetForm() {
    this.customerForm.reset({
      country: 'Deutschland'
    });
    this.editingCustomer = null;
    this.showAddForm = false;
    this.isSaving = false;
    this.errorMessage = null;
  }
}
