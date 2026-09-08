import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CheckoutComponent } from './checkout.component';
import { CartService } from '../../core/services/cart.service';
import { OrderService } from '../../core/services/order.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { Cart } from '../../core/models/cart.model';
import { Order } from '../../core/models/order.model';

describe('CheckoutComponent', () => {
  let cartServiceSpy: jasmine.SpyObj<CartService>;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let authServiceStub: { currentUserValue: { fullName: string } | null };
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const cartWithItems: Cart = {
    items: [{ productId: 'p1', sellerId: 's1', name: 'Phone', price: 100, quantity: 1, subtotal: 100 }],
    totalItems: 1,
    totalAmount: 100
  };

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [CheckoutComponent, ReactiveFormsModule],
      providers: [
        { provide: CartService, useValue: cartServiceSpy },
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
    return TestBed.createComponent(CheckoutComponent).componentInstance;
  }

  beforeEach(() => {
    cartServiceSpy = jasmine.createSpyObj('CartService', ['refresh', 'reset']);
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['checkout']);
    authServiceStub = { currentUserValue: { fullName: 'Jane Buyer' } };
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    cartServiceSpy.refresh.and.returnValue(of(cartWithItems));
  });

  it('ngOnInit builds the address form pre-filled with the user full name', () => {
    const component = createComponent();

    component.ngOnInit();

    expect(component.addressForm.get('fullName')?.value).toBe('Jane Buyer');
    expect(component.cart).toEqual(cartWithItems);
    expect(component.isLoadingCart).toBeFalse();
  });

  it('ngOnInit shows a toast when the cart fails to load', () => {
    cartServiceSpy.refresh.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.ngOnInit();

    expect(component.isLoadingCart).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not load your cart.', 'error');
  });

  it('currentStepIndex reflects the active step', () => {
    const component = createComponent();
    component.ngOnInit();

    expect(component.currentStepIndex).toBe(0);
    component.step = 'review';
    expect(component.currentStepIndex).toBe(1);
  });

  it('isInvalid is false for an untouched control', () => {
    const component = createComponent();
    component.ngOnInit();

    expect(component.isInvalid('fullName')).toBeFalse();
  });

  it('isInvalid is true for a touched, invalid control', () => {
    const component = createComponent();
    component.ngOnInit();

    component.addressForm.get('phone')?.markAsTouched();
    expect(component.isInvalid('phone')).toBeTrue();
  });

  it('goToReview blocks and marks the form touched when invalid', () => {
    const component = createComponent();
    component.ngOnInit();

    component.goToReview();

    expect(component.step).toBe('address');
    expect(component.addressForm.get('phone')?.touched).toBeTrue();
  });

  it('goToReview advances to review when the form is valid', () => {
    const component = createComponent();
    component.ngOnInit();
    component.addressForm.patchValue({
      fullName: 'Jane Buyer', phone: '12345678', line1: '1 Main St', city: 'City', postalCode: '00000', country: 'Country'
    });

    component.goToReview();

    expect(component.step).toBe('review');
  });

  it('goToConfirm and editAddress switch steps', () => {
    const component = createComponent();
    component.goToConfirm();
    expect(component.step).toBe('confirm');
    component.editAddress();
    expect(component.step).toBe('address');
  });

  it('placeOrder does nothing when the cart is empty', () => {
    cartServiceSpy.refresh.and.returnValue(of({ items: [], totalItems: 0, totalAmount: 0 }));
    const component = createComponent();
    component.ngOnInit();
    component.addressForm.patchValue({
      fullName: 'Jane Buyer', phone: '12345678', line1: '1 Main St', city: 'City', postalCode: '00000', country: 'Country'
    });

    component.placeOrder();

    expect(orderServiceSpy.checkout).not.toHaveBeenCalled();
  });

  it('placeOrder checks out, clears the cart, and navigates on success', () => {
    const placedOrder = { id: 'order-1' } as Order;
    orderServiceSpy.checkout.and.returnValue(of(placedOrder));
    const component = createComponent();
    component.ngOnInit();
    component.addressForm.patchValue({
      fullName: 'Jane Buyer', phone: '12345678', line1: '1 Main St', city: 'City', postalCode: '00000', country: 'Country'
    });

    component.placeOrder();

    expect(component.isPlacingOrder).toBeFalse();
    expect(cartServiceSpy.reset).toHaveBeenCalled();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Order placed! Pay on delivery.', 'success');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('placeOrder shows an error toast when checkout fails', () => {
    orderServiceSpy.checkout.and.returnValue(throwError(() => ({ error: { message: 'Out of stock' } })));
    const component = createComponent();
    component.ngOnInit();
    component.addressForm.patchValue({
      fullName: 'Jane Buyer', phone: '12345678', line1: '1 Main St', city: 'City', postalCode: '00000', country: 'Country'
    });

    component.placeOrder();

    expect(component.isPlacingOrder).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Out of stock', 'error');
  });
});
