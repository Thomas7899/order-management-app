import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CustomerService, Customer } from '../services/customer.service';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="customers-container">
      <div class="header">
        <h2>Kundenverwaltung</h2>
        <button class="btn btn-primary" (click)="showAddForm = !showAddForm">
          {{ showAddForm ? 'Abbrechen' : 'Neuer Kunde' }}
        </button>
      </div>

      <!-- Suchbereich -->
      <div class="search-bar">
        <input 
          type="text" 
          [(ngModel)]="searchTerm" 
          (input)="searchCustomers()" 
          placeholder="Kunden suchen..."
          class="form-control">
      </div>

      <!-- Neuer Kunde Formular -->
      <div class="add-form" *ngIf="showAddForm">
        <h3>{{ editingCustomer ? 'Kunde bearbeiten' : 'Neuer Kunde' }}</h3>
        <form (ngSubmit)="saveCustomer()" #customerForm="ngForm">
          <div class="form-row">
            <div class="form-group">
              <label for="firstName">Vorname *</label>
              <input 
                type="text" 
                id="firstName"
                [(ngModel)]="currentCustomer.firstName" 
                name="firstName"
                required 
                class="form-control">
            </div>
            <div class="form-group">
              <label for="lastName">Nachname *</label>
              <input 
                type="text" 
                id="lastName"
                [(ngModel)]="currentCustomer.lastName" 
                name="lastName"
                required 
                class="form-control">
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label for="email">E-Mail *</label>
              <input 
                type="email" 
                id="email"
                [(ngModel)]="currentCustomer.email" 
                name="email"
                required 
                class="form-control">
            </div>
            <div class="form-group">
              <label for="phone">Telefon</label>
              <input 
                type="tel" 
                id="phone"
                [(ngModel)]="currentCustomer.phone" 
                name="phone"
                class="form-control">
            </div>
          </div>
          <div class="form-group">
            <label for="address">Adresse</label>
            <input 
              type="text" 
              id="address"
              [(ngModel)]="currentCustomer.address" 
              name="address"
              class="form-control">
          </div>
          <div class="form-row">
            <div class="form-group">
              <label for="city">Stadt</label>
              <input 
                type="text" 
                id="city"
                [(ngModel)]="currentCustomer.city" 
                name="city"
                class="form-control">
            </div>
            <div class="form-group">
              <label for="zipCode">PLZ</label>
              <input 
                type="text" 
                id="zipCode"
                [(ngModel)]="currentCustomer.zipCode" 
                name="zipCode"
                class="form-control">
            </div>
            <div class="form-group">
              <label for="country">Land</label>
              <input 
                type="text" 
                id="country"
                [(ngModel)]="currentCustomer.country" 
                name="country"
                class="form-control">
            </div>
          </div>
          <div class="form-actions">
            <button type="submit" [disabled]="!customerForm.valid" class="btn btn-success">
              {{ editingCustomer ? 'Aktualisieren' : 'Speichern' }}
            </button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">
              Abbrechen
            </button>
          </div>
        </form>
      </div>

      <!-- Kundenliste -->
      <div class="customers-list">
        <div class="list-header">
          <h3>Kunden ({{ customers.length }})</h3>
        </div>
        
        <div class="customers-grid">
          <div class="customer-card" *ngFor="let customer of customers">
            <div class="customer-info">
              <h4>{{ customer.firstName }} {{ customer.lastName }}</h4>
              <p class="email">{{ customer.email }}</p>
              <p class="phone" *ngIf="customer.phone">{{ customer.phone }}</p>
              <div class="address" *ngIf="customer.address">
                <p>{{ customer.address }}</p>
                <p *ngIf="customer.city || customer.zipCode">
                  {{ customer.zipCode }} {{ customer.city }}
                </p>
                <p *ngIf="customer.country">{{ customer.country }}</p>
              </div>
            </div>
            <div class="customer-actions">
              <button (click)="editCustomer(customer)" class="btn btn-sm btn-outline-primary">
                Bearbeiten
              </button>
              <button (click)="deleteCustomer(customer.id!)" class="btn btn-sm btn-outline-danger">
                Löschen
              </button>
            </div>
          </div>
        </div>

        <div class="no-customers" *ngIf="customers.length === 0">
          <p>Keine Kunden gefunden.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .customers-container {
      padding: 20px;
      max-width: 1200px;
      margin: 0 auto;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .header h2 {
      margin: 0;
      color: #333;
    }

    .search-bar {
      margin-bottom: 20px;
    }

    .search-bar .form-control {
      max-width: 400px;
    }

    .add-form {
      background: #f8f9fa;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 30px;
    }

    .add-form h3 {
      margin-top: 0;
      margin-bottom: 20px;
      color: #333;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 15px;
      margin-bottom: 15px;
    }

    .form-row:last-child {
      grid-template-columns: 1fr 1fr 1fr;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group label {
      margin-bottom: 5px;
      font-weight: 500;
      color: #555;
    }

    .form-control {
      padding: 8px 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 14px;
    }

    .form-control:focus {
      outline: none;
      border-color: #007bff;
      box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
    }

    .form-actions {
      display: flex;
      gap: 10px;
      margin-top: 20px;
    }

    .customers-list {
      background: white;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .list-header h3 {
      margin: 0 0 20px 0;
      color: #333;
    }

    .customers-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
      gap: 20px;
    }

    .customer-card {
      border: 1px solid #e9ecef;
      border-radius: 8px;
      padding: 15px;
      background: #fff;
      transition: box-shadow 0.2s;
    }

    .customer-card:hover {
      box-shadow: 0 4px 8px rgba(0,0,0,0.1);
    }

    .customer-info h4 {
      margin: 0 0 8px 0;
      color: #333;
      font-size: 16px;
    }

    .customer-info .email {
      color: #007bff;
      margin: 4px 0;
      font-size: 14px;
    }

    .customer-info .phone {
      color: #6c757d;
      margin: 4px 0;
      font-size: 14px;
    }

    .customer-info .address {
      margin-top: 8px;
      font-size: 13px;
      color: #6c757d;
    }

    .customer-info .address p {
      margin: 2px 0;
    }

    .customer-actions {
      display: flex;
      gap: 8px;
      margin-top: 15px;
    }

    .no-customers {
      text-align: center;
      padding: 40px;
      color: #6c757d;
    }

    .btn {
      padding: 8px 16px;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      text-decoration: none;
      display: inline-block;
      transition: background-color 0.2s;
    }

    .btn-primary {
      background-color: #007bff;
      color: white;
    }

    .btn-primary:hover {
      background-color: #0056b3;
    }

    .btn-success {
      background-color: #28a745;
      color: white;
    }

    .btn-success:hover {
      background-color: #1e7e34;
    }

    .btn-success:disabled {
      background-color: #6c757d;
      cursor: not-allowed;
    }

    .btn-secondary {
      background-color: #6c757d;
      color: white;
    }

    .btn-secondary:hover {
      background-color: #545b62;
    }

    .btn-sm {
      padding: 5px 10px;
      font-size: 12px;
    }

    .btn-outline-primary {
      background-color: transparent;
      color: #007bff;
      border: 1px solid #007bff;
    }

    .btn-outline-primary:hover {
      background-color: #007bff;
      color: white;
    }

    .btn-outline-danger {
      background-color: transparent;
      color: #dc3545;
      border: 1px solid #dc3545;
    }

    .btn-outline-danger:hover {
      background-color: #dc3545;
      color: white;
    }

    @media (max-width: 768px) {
      .form-row {
        grid-template-columns: 1fr;
      }
      
      .customers-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class CustomersComponent implements OnInit {
  customers: Customer[] = [];
  showAddForm = false;
  editingCustomer: Customer | null = null;
  searchTerm = '';
  
  currentCustomer: Customer = {
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    address: '',
    city: '',
    zipCode: '',
    country: 'Deutschland'
  };

  constructor(private customerService: CustomerService) {}

  ngOnInit() {
    this.loadCustomers();
  }

  loadCustomers() {
    this.customerService.getCustomers().subscribe({
      next: (customers) => {
        this.customers = customers;
      },
      error: (error) => {
        console.error('Fehler beim Laden der Kunden:', error);
      }
    });
  }

  searchCustomers() {
    if (this.searchTerm.trim()) {
      this.customerService.searchCustomers(this.searchTerm).subscribe({
        next: (customers) => {
          this.customers = customers;
        },
        error: (error) => {
          console.error('Fehler bei der Suche:', error);
        }
      });
    } else {
      this.loadCustomers();
    }
  }

  saveCustomer() {
    if (this.editingCustomer) {
      this.customerService.updateCustomer(this.editingCustomer.id!, this.currentCustomer).subscribe({
        next: (customer) => {
          const index = this.customers.findIndex(c => c.id === customer.id);
          if (index > -1) {
            this.customers[index] = customer;
          }
          this.resetForm();
        },
        error: (error) => {
          console.error('Fehler beim Aktualisieren des Kunden:', error);
        }
      });
    } else {
      this.customerService.createCustomer(this.currentCustomer).subscribe({
        next: (customer) => {
          this.customers.push(customer);
          this.resetForm();
        },
        error: (error) => {
          console.error('Fehler beim Erstellen des Kunden:', error);
        }
      });
    }
  }

  editCustomer(customer: Customer) {
    this.editingCustomer = customer;
    this.currentCustomer = { ...customer };
    this.showAddForm = true;
  }

  deleteCustomer(id: number) {
    if (confirm('Sind Sie sicher, dass Sie diesen Kunden löschen möchten?')) {
      this.customerService.deleteCustomer(id).subscribe({
        next: () => {
          this.customers = this.customers.filter(c => c.id !== id);
        },
        error: (error) => {
          console.error('Fehler beim Löschen des Kunden:', error);
        }
      });
    }
  }

  cancelEdit() {
    this.resetForm();
  }

  private resetForm() {
    this.currentCustomer = {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: '',
      city: '',
      zipCode: '',
      country: 'Deutschland'
    };
    this.editingCustomer = null;
    this.showAddForm = false;
  }
}