import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CartComponent } from './cart.component';
import { CartService } from '../../core/services/cart.service';
import { ToastService } from '../../core/services/toast.service';
import { Cart, CartItem } from '../../core/models/cart.model';

describe('CartComponent', () => {
  let cartServiceSpy: jasmine.SpyObj<CartService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const sampleItem: CartItem = {
    productId: 'product-1',
    sellerId: 'seller-1',
    name: 'Phone',
    price: 100,
    quantity: 2,
    subtotal: 200
  };
  const sampleCart: Cart = { items: [sampleItem], totalItems: 2, totalAmount: 200 };

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [
        { provide: CartService, useValue: cartServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
    return TestBed.createComponent(CartComponent).componentInstance;
  }

  beforeEach(() => {
    cartServiceSpy = jasmine.createSpyObj('CartService', ['refresh', 'updateItemQuantity', 'removeItem', 'clear']);
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
  });

  it('ngOnInit loads the cart on success', () => {
    cartServiceSpy.refresh.and.returnValue(of(sampleCart));
    const component = createComponent();

    component.ngOnInit();

    expect(component.cart).toEqual(sampleCart);
    expect(component.isLoading).toBeFalse();
  });

  it('ngOnInit shows a toast when loading fails', () => {
    cartServiceSpy.refresh.and.returnValue(throwError(() => new Error('boom')));
    const component = createComponent();

    component.ngOnInit();

    expect(component.isLoading).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not load your cart.', 'error');
  });

  it('trackByProductId returns the product id', () => {
    const component = createComponent();
    expect(component.trackByProductId(0, sampleItem)).toBe('product-1');
  });

  it('isPending reflects pendingProductIds', () => {
    const component = createComponent();
    component.pendingProductIds.add('product-1');
    expect(component.isPending('product-1')).toBeTrue();
    expect(component.isPending('product-2')).toBeFalse();
  });

  it('increment updates the quantity upward', () => {
    cartServiceSpy.updateItemQuantity.and.returnValue(of(sampleCart));
    const component = createComponent();

    component.increment(sampleItem);

    expect(cartServiceSpy.updateItemQuantity).toHaveBeenCalledWith('product-1', 3);
    expect(component.cart).toEqual(sampleCart);
  });

  it('decrement updates the quantity downward when above 1', () => {
    cartServiceSpy.updateItemQuantity.and.returnValue(of(sampleCart));
    const component = createComponent();

    component.decrement(sampleItem);

    expect(cartServiceSpy.updateItemQuantity).toHaveBeenCalledWith('product-1', 1);
  });

  it('decrement removes the item once quantity would drop to 0', () => {
    cartServiceSpy.removeItem.and.returnValue(of({ items: [], totalItems: 0, totalAmount: 0 }));
    const component = createComponent();
    const singleQuantityItem = { ...sampleItem, quantity: 1 };

    component.decrement(singleQuantityItem);

    expect(cartServiceSpy.removeItem).toHaveBeenCalledWith('product-1');
    expect(cartServiceSpy.updateItemQuantity).not.toHaveBeenCalled();
  });

  it('updateQuantity shows a toast on error and clears the pending flag', () => {
    cartServiceSpy.updateItemQuantity.and.returnValue(
      throwError(() => ({ error: { message: 'Out of stock' } }))
    );
    const component = createComponent();

    component.increment(sampleItem);

    expect(component.isPending('product-1')).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Out of stock', 'error');
  });

  it('updateQuantity falls back to a generic message when the server gives none', () => {
    cartServiceSpy.updateItemQuantity.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.increment(sampleItem);

    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not update that item.', 'error');
  });

  it('removeItem shows a toast on error', () => {
    cartServiceSpy.removeItem.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.removeItem(sampleItem);

    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not remove that item.', 'error');
    expect(component.isPending('product-1')).toBeFalse();
  });

  it('goToProducts navigates to the products page', () => {
    const component = createComponent();

    component.goToProducts();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/products']);
  });

  it('clearCart resets the cart and shows a success toast', () => {
    cartServiceSpy.clear.and.returnValue(of(undefined));
    const component = createComponent();

    component.clearCart();

    expect(component.cart).toEqual({ items: [], totalItems: 0, totalAmount: 0 });
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Cart cleared.', 'success');
  });

  it('clearCart shows an error toast on failure', () => {
    cartServiceSpy.clear.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.clearCart();

    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not clear your cart.', 'error');
  });
});
