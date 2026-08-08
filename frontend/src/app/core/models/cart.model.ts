export interface CartItem {
  productId: string;
  sellerId: string;
  name: string;
  imageUrl?: string;
  price: number;
  quantity: number;
  subtotal: number;
}

export interface Cart {
  items: CartItem[];
  totalItems: number;
  totalAmount: number;
}
