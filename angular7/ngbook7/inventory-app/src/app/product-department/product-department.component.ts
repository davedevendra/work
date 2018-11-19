import { Component, OnInit, Input } from '@angular/core';
import { Product } from '../product.model';


/**
 * @ProductDepartment: A component to show the breadcrums to a Product's department
 *
 * @export
 * @class ProductDepartmentComponent
 * @implements {OnInit}
 */
@Component({
  selector: 'product-department',
  templateUrl: './product-department.component.html',
  styleUrls: ['./product-department.component.css']
})
export class ProductDepartmentComponent implements OnInit {

  @Input() product: Product;

  constructor() { }

  ngOnInit() {
  }

}
