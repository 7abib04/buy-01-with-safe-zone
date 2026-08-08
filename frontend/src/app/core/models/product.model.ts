export type ProductCategory =
  | 'ELECTRONICS'
  | 'CLOTHING'
  | 'HOME'
  | 'BEAUTY'
  | 'SPORTS'
  | 'TOYS'
  | 'BOOKS'
  | 'GROCERY'
  | 'OTHER';

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  quantity: number;
  imageUrls: string[];
  sellerId: string;
  category?: ProductCategory;
  createdAt: string;
  updatedAt: string;
}
