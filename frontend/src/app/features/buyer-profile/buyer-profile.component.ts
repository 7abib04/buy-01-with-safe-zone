import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { OrderService } from '../../core/services/order.service';
import { User } from '../../core/models/user.model';
import { BuyerAnalytics } from '../../core/models/order.model';
import { BarChartComponent, BarChartDatum } from '../../shared/components/bar-chart/bar-chart.component';

@Component({
  selector: 'app-buyer-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, BarChartComponent],
  templateUrl: './buyer-profile.component.html',
  styleUrl: './buyer-profile.component.scss'
})
export class BuyerProfileComponent implements OnInit {
  authService = inject(AuthService);
  toastService = inject(ToastService);
  orderService = inject(OrderService);
  fb = inject(FormBuilder);
  router = inject(Router);
  destroyRef = inject(DestroyRef);

  profileForm!: FormGroup;
  user: User | null = null;
  isSubmitting = false;

  analytics: BuyerAnalytics | null = null;
  isLoadingAnalytics = true;

  ngOnInit() {
    this.profileForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(3)]],
      email: [{ value: '', disabled: true }]
    });

    const currentUser = this.authService.currentUserValue;
    if (currentUser) {
      this.applyUser(currentUser);
    }

    this.authService.currentUser$
      .pipe(
        filter((user): user is User => user !== null),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((user) => this.applyUser(user));

    this.loadAnalytics();
  }

  loadAnalytics(): void {
    this.isLoadingAnalytics = true;
    this.orderService.getBuyerAnalytics().subscribe({
      next: (analytics) => {
        this.analytics = analytics;
        this.isLoadingAnalytics = false;
      },
      error: () => {
        this.isLoadingAnalytics = false;
      }
    });
  }

  get mostBoughtChartData(): BarChartDatum[] {
    return (this.analytics?.mostBoughtProducts ?? []).map((stat) => ({ label: stat.name, value: stat.unitsSold }));
  }

  get topCategoriesChartData(): BarChartDatum[] {
    return (this.analytics?.topCategories ?? []).map((stat) => ({ label: stat.category, value: stat.amount }));
  }

  onSubmit() {
    if (this.profileForm.invalid) return;
    
    this.isSubmitting = true;
    this.authService.updateProfile({ fullName: this.profileForm.get('fullName')?.value }).subscribe({
      next: (updatedUser: any) => {
        this.user = updatedUser;
        this.isSubmitting = false;
        this.toastService.show('Profile updated successfully', 'success');
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.toastService.show('Failed to update profile', 'error');
      }
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  private applyUser(user: User) {
    this.user = user;
    this.profileForm.patchValue({
      fullName: user.fullName,
      email: user.email
    });
  }
}
