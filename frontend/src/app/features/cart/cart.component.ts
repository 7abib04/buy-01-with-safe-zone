import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, Minus, Plus, ShoppingBag, Trash2 } from 'lucide-angular';
import { CartService } from '../../core/services/cart.service';
import { ToastService } from '../../core/services/toast.service';
import { Cart, CartItem } from '../../core/models/cart.model';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { MediaImageComponent } from '../../shared/components/media-image/media-image.component';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    LucideAngularModule,
    ConfirmDialogComponent,
    EmptyStateComponent,
    LoadingSpinnerComponent,
    MediaImageComponent
  ],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CartComponent implements OnInit {
  private cartService = inject(CartService);
  private toastService = inject(ToastService);
  private router = inject(Router);

  readonly MinusIcon = Minus;
  readonly PlusIcon = Plus;
  readonly Trash2Icon = Trash2;
  readonly ShoppingBagIcon = ShoppingBag;

  cart: Cart = { items: [], totalItems: 0, totalAmount: 0 };
  isLoading = true;
  pendingProductIds = new Set<string>();
  isClearConfirmOpen = false;

  ngOnInit(): void {
    this.isLoading = true;
    this.cartService.refresh().subscribe({
      next: (cart) => {
        this.cart = cart;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.toastService.show('Could not load your cart.', 'error');
      }
    });
  }

  trackByProductId(index: number, item: CartItem): string {
    return item.productId;
  }

  isPending(productId: string): boolean {
    return this.pendingProductIds.has(productId);
  }

  increment(item: CartItem): void {
    this.updateQuantity(item, item.quantity + 1);
  }

  decrement(item: CartItem): void {
    if (item.quantity <= 1) {
      this.removeItem(item);
      return;
    }
    this.updateQuantity(item, item.quantity - 1);
  }

  private updateQuantity(item: CartItem, quantity: number): void {
    this.pendingProductIds.add(item.productId);
    this.cartService.updateItemQuantity(item.productId, quantity).subscribe({
      next: (cart) => {
        this.cart = cart;
        this.pendingProductIds.delete(item.productId);
      },
      error: (error) => {
        this.pendingProductIds.delete(item.productId);
        this.toastService.show(error?.error?.message || 'Could not update that item.', 'error');
      }
    });
  }

  removeItem(item: CartItem): void {
    this.pendingProductIds.add(item.productId);
    this.cartService.removeItem(item.productId).subscribe({
      next: (cart) => {
        this.cart = cart;
        this.pendingProductIds.delete(item.productId);
      },
      error: (error) => {
        this.pendingProductIds.delete(item.productId);
        this.toastService.show(error?.error?.message || 'Could not remove that item.', 'error');
      }
    });
  }

  goToProducts(): void {
    this.router.navigate(['/products']);
  }

  clearCart(): void {
    this.cartService.clear().subscribe({
      next: () => {
        this.cart = { items: [], totalItems: 0, totalAmount: 0 };
        this.toastService.show('Cart cleared.', 'success');
      },
      error: () => this.toastService.show('Could not clear your cart.', 'error')
    });
  }
}
