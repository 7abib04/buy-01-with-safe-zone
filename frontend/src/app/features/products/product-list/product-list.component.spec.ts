import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ProductListComponent } from './product-list.component';
import { ProductService, ProductSearchResult } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';

describe('ProductListComponent', () => {
  let productServiceSpy: jasmine.SpyObj<ProductService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;

  function result(overrides: Partial<ProductSearchResult> = {}): ProductSearchResult {
    return {
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
      categories: ['ELECTRONICS', 'BOOKS'],
      minPrice: 0,
      maxPrice: 500,
      ...overrides
    };
  }

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [
        { provide: ProductService, useValue: productServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy }
      ]
    });
    return TestBed.createComponent(ProductListComponent).componentInstance;
  }

  beforeEach(() => {
    productServiceSpy = jasmine.createSpyObj('ProductService', ['searchProducts']);
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    productServiceSpy.searchProducts.and.returnValue(of(result()));
  });

  it('ngOnInit loads products and sets catalog bounds on first load', () => {
    productServiceSpy.searchProducts.and.returnValue(of(result({ minPrice: 5, maxPrice: 800 })));
    const component = createComponent();

    component.ngOnInit();

    expect(component.isLoading).toBeFalse();
    expect(component.hasLoadedOnce).toBeTrue();
    expect(component.catalogMinPrice).toBe(5);
    expect(component.catalogMaxPrice).toBe(800);
    expect(component.selectedMinPrice).toBe(5);
    expect(component.selectedMaxPrice).toBe(800);
    expect(component.categories).toEqual(['ELECTRONICS', 'BOOKS']);
  });

  it('load defaults catalogMaxPrice to 1000 when the server reports 0', () => {
    productServiceSpy.searchProducts.and.returnValue(of(result({ minPrice: 0, maxPrice: 0 })));
    const component = createComponent();

    component.load();

    expect(component.catalogMaxPrice).toBe(1000);
  });

  it('load does not reset catalog bounds on subsequent loads', () => {
    productServiceSpy.searchProducts.and.returnValue(of(result({ minPrice: 5, maxPrice: 800 })));
    const component = createComponent();
    component.load();

    productServiceSpy.searchProducts.and.returnValue(of(result({ minPrice: 999, maxPrice: 999 })));
    component.load();

    expect(component.catalogMinPrice).toBe(5);
    expect(component.catalogMaxPrice).toBe(800);
  });

  it('load sends trimmed search term and only active price bounds', () => {
    const component = createComponent();
    component.searchTerm = '  phone  ';
    component.categoryFilter = 'ELECTRONICS';
    component.selectedMinPrice = 0;
    component.selectedMaxPrice = 1000;
    component.catalogMinPrice = 0;
    component.catalogMaxPrice = 1000;

    component.load();

    expect(productServiceSpy.searchProducts).toHaveBeenCalledWith(jasmine.objectContaining({
      q: 'phone',
      category: 'ELECTRONICS',
      minPrice: undefined,
      maxPrice: undefined
    }));
  });

  it('load includes price bounds once they differ from the catalog defaults', () => {
    const component = createComponent();
    component.catalogMinPrice = 0;
    component.catalogMaxPrice = 1000;
    component.selectedMinPrice = 50;
    component.selectedMaxPrice = 900;

    component.load();

    expect(productServiceSpy.searchProducts).toHaveBeenCalledWith(jasmine.objectContaining({
      minPrice: 50,
      maxPrice: 900
    }));
  });

  it('load shows a toast and clears loading on error', () => {
    productServiceSpy.searchProducts.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.load();

    expect(component.isLoading).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Failed to load products.', 'error');
  });

  it('onSearchSubmit, onFilterChange and onPriceCommit reset to the first page', () => {
    const component = createComponent();
    component.page = 3;
    component.onSearchSubmit();
    expect(component.page).toBe(0);

    component.page = 3;
    component.onFilterChange();
    expect(component.page).toBe(0);

    component.page = 3;
    component.onPriceCommit();
    expect(component.page).toBe(0);
  });

  it('onPageChange loads the requested page', () => {
    const component = createComponent();
    component.onPageChange(4);
    expect(component.page).toBe(4);
  });

  it('clearFilters resets all filters to catalog defaults', () => {
    const component = createComponent();
    component.hasLoadedOnce = true;
    component.catalogMinPrice = 10;
    component.catalogMaxPrice = 900;
    component.searchTerm = 'phone';
    component.categoryFilter = 'BOOKS';
    component.selectedMinPrice = 50;
    component.selectedMaxPrice = 500;
    component.sortOption = 'price_asc';
    component.page = 2;

    component.clearFilters();

    expect(component.searchTerm).toBe('');
    expect(component.categoryFilter).toBe('');
    expect(component.selectedMinPrice).toBe(10);
    expect(component.selectedMaxPrice).toBe(900);
    expect(component.sortOption).toBe('newest');
    expect(component.page).toBe(0);
  });

  it('trackByProductId returns the product id', () => {
    const component = createComponent();
    expect(component.trackByProductId(0, { id: 'p1' } as never)).toBe('p1');
  });

  describe('hasActiveFilters', () => {
    it('is false with only default values', () => {
      const component = createComponent();
      component.catalogMinPrice = 0;
      component.catalogMaxPrice = 1000;
      component.selectedMinPrice = 0;
      component.selectedMaxPrice = 1000;
      expect(component.hasActiveFilters).toBeFalse();
    });

    it('is true when a search term is present', () => {
      const component = createComponent();
      component.searchTerm = 'phone';
      expect(component.hasActiveFilters).toBeTrue();
    });

    it('is true when a category is selected', () => {
      const component = createComponent();
      component.categoryFilter = 'TOYS';
      expect(component.hasActiveFilters).toBeTrue();
    });

    it('is true when price bounds have moved', () => {
      const component = createComponent();
      component.catalogMinPrice = 0;
      component.catalogMaxPrice = 1000;
      component.selectedMinPrice = 100;
      component.selectedMaxPrice = 1000;
      expect(component.hasActiveFilters).toBeTrue();
    });

    it('is true when sort is not newest', () => {
      const component = createComponent();
      component.sortOption = 'price_desc';
      expect(component.hasActiveFilters).toBeTrue();
    });
  });

  describe('price slider math', () => {
    it('computes thumb percentages and fill relative to the catalog range', () => {
      const component = createComponent();
      component.catalogMinPrice = 0;
      component.catalogMaxPrice = 200;
      component.selectedMinPrice = 50;
      component.selectedMaxPrice = 150;

      expect(component.minThumbPercent).toBe(25);
      expect(component.maxThumbPercent).toBe(75);
      expect(component.rangeFillLeftPercent).toBe(25);
      expect(component.rangeFillWidthPercent).toBe(50);
    });

    it('returns 0 percent when the catalog range has no span', () => {
      const component = createComponent();
      component.catalogMinPrice = 100;
      component.catalogMaxPrice = 100;
      component.selectedMinPrice = 100;

      expect(component.minThumbPercent).toBe(0);
    });
  });

  describe('onMinPriceInput / onMaxPriceInput', () => {
    it('falls back to the catalog bound for null, undefined, or empty input', () => {
      const component = createComponent();
      component.catalogMinPrice = 10;
      component.selectedMaxPrice = 500;

      component.onMinPriceInput(null);
      expect(component.selectedMinPrice).toBe(10);

      component.onMinPriceInput('');
      expect(component.selectedMinPrice).toBe(10);
    });

    it('parses a comma decimal as a valid number', () => {
      const component = createComponent();
      component.catalogMinPrice = 0;
      component.catalogMaxPrice = 1000;
      component.selectedMaxPrice = 1000;

      component.onMinPriceInput('12,5');

      expect(component.selectedMinPrice).toBe(12.5);
    });

    it('falls back to the catalog bound for a non-numeric value', () => {
      const component = createComponent();
      component.catalogMinPrice = 20;
      component.selectedMaxPrice = 500;

      component.onMinPriceInput('not-a-number');

      expect(component.selectedMinPrice).toBe(20);
    });

    it('clamps the min price so it never exceeds the current max', () => {
      const component = createComponent();
      component.catalogMinPrice = 0;
      component.catalogMaxPrice = 1000;
      component.selectedMaxPrice = 100;

      component.onMinPriceInput(500);

      expect(component.selectedMinPrice).toBe(100);
    });

    it('clamps the max price so it never drops below the current min', () => {
      const component = createComponent();
      component.catalogMinPrice = 0;
      component.catalogMaxPrice = 1000;
      component.selectedMinPrice = 400;

      component.onMaxPriceInput(50);

      expect(component.selectedMaxPrice).toBe(400);
    });

    it('clamps within the catalog bounds', () => {
      const component = createComponent();
      component.catalogMinPrice = 10;
      component.catalogMaxPrice = 200;
      component.selectedMaxPrice = 200;

      component.onMinPriceInput(-50);

      expect(component.selectedMinPrice).toBe(10);
    });
  });
});
