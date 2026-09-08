import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProductService } from './product.service';

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ProductService]
    });
    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('searchProducts() sends filters as query params and normalizes results', () => {
    service
      .searchProducts({ q: 'phone', category: 'ELECTRONICS', minPrice: 10, maxPrice: 500, sort: 'price_asc', page: 1, size: 12 })
      .subscribe((result) => {
        expect(result.content).toHaveSize(1);
        expect(result.content[0].category).toBe('ELECTRONICS');
        expect(result.categories).toEqual(['ELECTRONICS', 'OTHER']);
      });

    const req = httpMock.expectOne(
      (r) => r.url === '/api/products/search' && r.params.get('q') === 'phone' && r.params.get('category') === 'ELECTRONICS'
        && r.params.get('minPrice') === '10' && r.params.get('maxPrice') === '500' && r.params.get('sort') === 'price_asc'
        && r.params.get('page') === '1' && r.params.get('size') === '12'
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [
        {
          id: 'p1', name: 'Phone', description: 'desc', price: 100, quantity: 5, sellerId: 's1',
          category: 'ELECTRONICS', imageUrls: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z'
        }
      ],
      page: 1,
      size: 12,
      totalElements: 1,
      totalPages: 1,
      categories: ['ELECTRONICS', 'OTHER'],
      minPrice: 10,
      maxPrice: 500
    });
  });

  it('searchProducts() omits unset filters', () => {
    service.searchProducts().subscribe();

    const req = httpMock.expectOne('/api/products/search');
    expect(req.request.params.keys()).toHaveSize(0);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, categories: [], minPrice: 0, maxPrice: 0 });
  });
});
