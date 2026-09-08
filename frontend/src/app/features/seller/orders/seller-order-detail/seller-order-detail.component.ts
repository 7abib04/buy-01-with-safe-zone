import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderService } from '../../../../core/services/order.service';
import { ToastService } from '../../../../core/services/toast.service';
import { Order, OrderStatus } from '../../../../core/models/order.model';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { MediaImageComponent } from '../../../../shared/components/media-image/media-image.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

const FORWARD_SEQUENCE: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

@Component({
  selector: 'app-seller-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, LoadingSpinnerComponent, MediaImageComponent, SellerPortalShellComponent],
  templateUrl: './seller-order-detail.component.html',
  styleUrl: './seller-order-detail.component.scss'
})
export class SellerOrderDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);
  private readonly toastService = inject(ToastService);

  order: Order | null = null;
  isLoading = true;
  isNotFound = false;
  isUpdatingStatus = false;

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

  get nextStatus(): OrderStatus | null {
    if (!this.order) return null;
    const currentIndex = FORWARD_SEQUENCE.indexOf(this.order.status);
    if (currentIndex < 0 || currentIndex === FORWARD_SEQUENCE.length - 1) {
      return null;
    }
    return FORWARD_SEQUENCE[currentIndex + 1];
  }

  advanceStatus(): void {
    if (!this.order || !this.nextStatus) return;

    this.isUpdatingStatus = true;
    this.orderService.updateOrderStatus(this.order.id, this.nextStatus).subscribe({
      next: (order) => {
        this.order = order;
        this.isUpdatingStatus = false;
        this.toastService.show(`Order marked as ${order.status}.`, 'success');
      },
      error: (error) => {
        this.isUpdatingStatus = false;
        this.toastService.show(error?.error?.message || 'Could not update this order.', 'error');
      }
    });
  }
}
