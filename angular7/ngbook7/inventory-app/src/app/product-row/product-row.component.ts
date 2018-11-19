import { Component, OnInit, HostBinding, Input } from '@angular/core';
import { Product } from '../product.model';

/**
 *
 * @ProductRow: A component for the view of single Product
 * @export
 * @class ProductRowComponent
 * @implements {OnInit}
 */
@Component({
  selector: 'product-row',
  templateUrl: './product-row.component.html',
  styleUrls: ['./product-row.component.css']
})
export class ProductRowComponent implements OnInit {

  @Input() product: Product;
  @HostBinding('attr.class') cssClass = 'item';

  constructor() { }

  ngOnInit() {
  }

}
