import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  NgZone,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LogOut, ShoppingCart } from 'lucide-angular';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/services/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { Observable, distinctUntilChanged } from 'rxjs';
import { User as UserModel } from '../../../core/models/user.model';
import { MediaImageComponent } from '../media-image/media-image.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, LucideAngularModule, MediaImageComponent],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NavbarComponent implements OnInit, OnDestroy {
  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private removeScrollListener?: () => void;

  authService = inject(AuthService);
  cartService = inject(CartService);
  currentUser$: Observable<UserModel | null> = this.authService.currentUser$;
  isScrolled = false;
  readonly LogOutIcon = LogOut;
  readonly ShoppingCartIcon = ShoppingCart;

  ngOnInit() {
    this.currentUser$
      .pipe(
        distinctUntilChanged((previous, next) => previous?.id === next?.id),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((user) => {
        if (user) {
          this.cartService.refresh().subscribe({ error: () => undefined });
        }
      });

    if (typeof window === 'undefined') {
      return;
    }

    this.isScrolled = window.scrollY > 60;
    this.ngZone.runOutsideAngular(() => {
      const onScroll = () => {
        const nextScrolledState = window.scrollY > 60;
        if (nextScrolledState === this.isScrolled) {
          return;
        }

        this.ngZone.run(() => {
          this.isScrolled = nextScrolledState;
          this.cdr.markForCheck();
        });
      };

      window.addEventListener('scroll', onScroll, { passive: true });
      this.removeScrollListener = () => window.removeEventListener('scroll', onScroll);
    });
  }

  getProfileRoute(user: UserModel): string {
    return user.role === 'SELLER' ? '/seller/profile' : '/profile';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  ngOnDestroy(): void {
    this.removeScrollListener?.();
  }
}
