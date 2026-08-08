import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CartService } from './cart.service';
import { Cart } from '../models/cart.model';

describe('CartService', () => {
  let service: CartService;
  let httpMock: HttpTestingController;

  const sampleCart: Cart = {
    items: [
      { productId: 'p1', sellerId: 's1', name: 'Phone', price: 100, quantity: 2, subtotal: 200 }
    ],
    totalItems: 2,
    totalAmount: 200
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CartService]
    });
    service = TestBed.inject(CartService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts with an empty cart', () => {
    expect(service.currentCart).toEqual({ items: [], totalItems: 0, totalAmount: 0 });
  });

  it('refresh() fetches the cart and updates itemCount$', (done) => {
    service.itemCount$.subscribe((count) => {
      if (count === 2) {
        done();
      }
    });

    service.refresh().subscribe((cart) => {
      expect(cart).toEqual(sampleCart);
    });

    const req = httpMock.expectOne('/api/cart');
    expect(req.request.method).toBe('GET');
    req.flush(sampleCart);
  });

  it('addItem() posts to /cart/items and updates state', () => {
    service.addItem('p1', 2).subscribe((cart) => {
      expect(cart.totalItems).toBe(2);
    });

    const req = httpMock.expectOne('/api/cart/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 'p1', quantity: 2 });
    req.flush(sampleCart);

    expect(service.currentCart).toEqual(sampleCart);
  });

  it('reset() clears local cart state without an HTTP call', () => {
    service.reset();
    expect(service.currentCart).toEqual({ items: [], totalItems: 0, totalAmount: 0 });
  });
});
