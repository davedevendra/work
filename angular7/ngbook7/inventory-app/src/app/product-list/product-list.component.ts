import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { Product } from '../product.model';


/**
 * @ProductList: A component for rendering all ProductRows and
 * storing the currently selected Product
 */
@Component({
  selector: 'product-list',
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.css']
})
export class ProductListComponent implements OnInit {

  /**
   * @input productList - the Product[] passed to us
   *
   * @type {Product[]}
   * @memberof ProductListComponent
   */
  @Input() productList: Product[];

  /**
   * @output onProductSelected - outputs the current
   *  Product whenever a new Product is selected
   *
   * @type {EventEmitter<Product>}
   * @memberof ProductListComponent
   */
  @Output() onProductSelected: EventEmitter<Product>;

  /**
   * @property currentProduct - local state containing the currently
   *  selected `Product`
   *
   * @private
   * @type {Product}
   * @memberof ProductListComponent
   */
  private currentProduct: Product;

  constructor() {
    this.onProductSelected = new EventEmitter();
  }

  ngOnInit() {
  }

  clicked(product: Product): void {
    this.currentProduct = product;
    this.onProductSelected.emit(product);
  }

  isSelected(product: Product): boolean {
    if (!product || !this.currentProduct) {
      return false;
    }
    return product.sku === this.currentProduct.sku;
  }

}
