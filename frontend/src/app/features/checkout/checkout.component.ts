import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CartService } from '../../core/services/cart.service';
import { OrderService } from '../../core/services/order.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { Cart } from '../../core/models/cart.model';

type CheckoutStep = 'address' | 'review' | 'confirm';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss'
})
export class CheckoutComponent implements OnInit {
  private fb = inject(FormBuilder);
  private cartService = inject(CartService);
  private orderService = inject(OrderService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);
  private router = inject(Router);

  addressForm!: FormGroup;
  step: CheckoutStep = 'address';
  cart: Cart = { items: [], totalItems: 0, totalAmount: 0 };
  isLoadingCart = true;
  isPlacingOrder = false;

  readonly steps: { id: CheckoutStep; label: string }[] = [
    { id: 'address', label: 'Address' },
    { id: 'review', label: 'Review' },
    { id: 'confirm', label: 'Confirm' }
  ];

  get currentStepIndex(): number {
    return this.steps.findIndex((s) => s.id === this.step);
  }

  ngOnInit(): void {
    this.addressForm = this.fb.group({
      fullName: [this.authService.currentUserValue?.fullName ?? '', [Validators.required, Validators.maxLength(150)]],
      phone: ['', [Validators.required, Validators.maxLength(30)]],
      line1: ['', [Validators.required, Validators.maxLength(250)]],
      city: ['', [Validators.required, Validators.maxLength(100)]],
      postalCode: ['', [Validators.required, Validators.maxLength(20)]],
      country: ['', [Validators.required, Validators.maxLength(100)]]
    });

    this.cartService.refresh().subscribe({
      next: (cart) => {
        this.cart = cart;
        this.isLoadingCart = false;
      },
      error: () => {
        this.isLoadingCart = false;
        this.toastService.show('Could not load your cart.', 'error');
      }
    });
  }

  isInvalid(controlName: string): boolean {
    const control = this.addressForm.get(controlName);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  goToReview(): void {
    if (this.addressForm.invalid) {
      this.addressForm.markAllAsTouched();
      return;
    }
    this.step = 'review';
  }

  goToConfirm(): void {
    this.step = 'confirm';
  }

  editAddress(): void {
    this.step = 'address';
  }

  placeOrder(): void {
    if (this.addressForm.invalid || this.cart.items.length === 0) {
      return;
    }

    this.isPlacingOrder = true;
    this.orderService.checkout(this.addressForm.getRawValue()).subscribe({
      next: (order) => {
        this.isPlacingOrder = false;
        this.cartService.reset();
        this.toastService.show('Order placed! Pay on delivery.', 'success');
        this.router.navigate(['/orders', order.id]);
      },
      error: (error) => {
        this.isPlacingOrder = false;
        this.toastService.show(error?.error?.message || 'Could not place your order.', 'error');
      }
    });
  }
}
