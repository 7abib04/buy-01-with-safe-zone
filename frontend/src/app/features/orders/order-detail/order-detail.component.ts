import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { Order, OrderStatus } from '../../../core/models/order.model';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { MediaImageComponent } from '../../../shared/components/media-image/media-image.component';

const STATUS_TIMELINE: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ConfirmDialogComponent, LoadingSpinnerComponent, MediaImageComponent],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orderService = inject(OrderService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);

  readonly timeline = STATUS_TIMELINE;

  order: Order | null = null;
  isLoading = true;
  isNotFound = false;
  isCancelling = false;
  isRedoing = false;
  isRemoving = false;
  isCancelConfirmOpen = false;
  isRemoveConfirmOpen = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.load(id);
    }
  }

  private load(id: string): void {
    this.isLoading = true;
    this.orderService.getOrder(id).subscribe({
      next: (order) => {
        this.order = order;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        if (error.status === 404) {
          this.isNotFound = true;
        } else {
          this.toastService.show('Could not load this order.', 'error');
        }
      }
    });
  }

  get isOwner(): boolean {
    return !!this.order && this.order.buyerId === this.authService.currentUserValue?.id;
  }

  get canCancel(): boolean {
    return this.isOwner && !!this.order && (this.order.status === 'PENDING' || this.order.status === 'CONFIRMED');
  }

  get canRedo(): boolean {
    return this.isOwner && !!this.order && (this.order.status === 'CANCELLED' || this.order.status === 'DELIVERED');
  }

  get canRemove(): boolean {
    return this.isOwner && !!this.order && (this.order.status === 'CANCELLED' || this.order.status === 'DELIVERED');
  }

  timelineState(status: OrderStatus): 'done' | 'active' | 'upcoming' {
    if (!this.order || this.order.status === 'CANCELLED') {
      return 'upcoming';
    }
    const currentIndex = this.timeline.indexOf(this.order.status);
    const stepIndex = this.timeline.indexOf(status);
    if (stepIndex < currentIndex) return 'done';
    if (stepIndex === currentIndex) return 'active';
    return 'upcoming';
  }

  cancelOrder(): void {
    if (!this.order) return;
    this.isCancelling = true;
    this.orderService.cancelOrder(this.order.id).subscribe({
      next: (order) => {
        this.order = order;
        this.isCancelling = false;
        this.toastService.show('Order cancelled.', 'success');
      },
      error: (error) => {
        this.isCancelling = false;
        this.toastService.show(error?.error?.message || 'Could not cancel this order.', 'error');
      }
    });
  }

  redoOrder(): void {
    if (!this.order) return;
    this.isRedoing = true;
    this.orderService.redoOrder(this.order.id).subscribe({
      next: () => {
        this.isRedoing = false;
        this.toastService.show('Items added back to your cart.', 'success');
        this.router.navigate(['/cart']);
      },
      error: (error) => {
        this.isRedoing = false;
        this.toastService.show(error?.error?.message || 'Could not re-add these items.', 'error');
      }
    });
  }

  removeOrder(): void {
    if (!this.order) return;
    this.isRemoving = true;
    this.orderService.removeOrder(this.order.id).subscribe({
      next: () => {
        this.isRemoving = false;
        this.toastService.show('Order removed from your history.', 'success');
        this.router.navigate(['/orders']);
      },
      error: (error) => {
        this.isRemoving = false;
        this.toastService.show(error?.error?.message || 'Could not remove this order.', 'error');
      }
    });
  }
}
