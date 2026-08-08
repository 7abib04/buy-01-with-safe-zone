export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface OrderItem {
  productId: string;
  sellerId: string;
  sellerName?: string;
  name: string;
  imageUrl?: string;
  price: number;
  quantity: number;
  category?: string;
  subtotal: number;
}

export interface ShippingAddress {
  fullName: string;
  phone: string;
  line1: string;
  city: string;
  postalCode: string;
  country: string;
}

export interface StatusChange {
  status: OrderStatus;
  changedAt: string;
  changedBy: string;
}

export interface Order {
  id: string;
  buyerId: string;
  buyerName: string;
  items: OrderItem[];
  totalAmount: number;
  status: OrderStatus;
  paymentMethod: 'CASH_ON_DELIVERY';
  shippingAddress: ShippingAddress;
  statusHistory: StatusChange[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderPage {
  content: Order[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductStat {
  productId: string;
  name: string;
  unitsSold: number;
  revenue: number;
}

export interface CategoryStat {
  category: string;
  amount: number;
}

export interface BuyerAnalytics {
  totalSpent: number;
  ordersCount: number;
  mostBoughtProducts: ProductStat[];
  topCategories: CategoryStat[];
}

export interface SellerAnalytics {
  totalRevenue: number;
  unitsSold: number;
  ordersCount: number;
  bestSellingProducts: ProductStat[];
}
