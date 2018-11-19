import { Component, OnInit, Input } from '@angular/core';

/**
 * @PriceDisplay: A component to show the price of a Product
 *
 * @export
 * @class PriceDisplayComponent
 * @implements {OnInit}
 */
@Component({
  selector: 'price-display',
  templateUrl: './price-display.component.html',
  styleUrls: ['./price-display.component.css']
})
export class PriceDisplayComponent implements OnInit {
  @Input() price: Number;

  constructor() { }

  ngOnInit() {
  }

}
