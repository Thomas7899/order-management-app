import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService, User } from '../services/user.service';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.css']
})
export class UsersComponent implements OnInit {
  users: User[] = [];
  newUser: Partial<User> = { name: '', email: '', role: '' };
  loading = false;
  error: string | null = null;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.error = null;
    this.userService.getUsers().subscribe({
      next: (data) => {
        this.users = data;
        this.loading = false;
      },
      error: (error) => {
        this.error = 'Fehler beim Laden der Benutzer';
        this.loading = false;
        console.error('Error loading users:', error);
      }
    });
  }

  addUser(): void {
    if (!this.newUser.name || !this.newUser.email || !this.newUser.role) {
      this.error = 'Bitte füllen Sie alle Felder aus';
      return;
    }

    this.loading = true;
    this.error = null;
    
    this.userService.addUser(this.newUser as User).subscribe({
      next: (user) => {
        this.users.push(user);
        this.newUser = { name: '', email: '', role: '' };
        this.loading = false;
      },
      error: (error) => {
        this.error = 'Fehler beim Hinzufügen des Benutzers';
        this.loading = false;
        console.error('Error adding user:', error);
      }
    });
  }

  clearError(): void {
    this.error = null;
  }
}