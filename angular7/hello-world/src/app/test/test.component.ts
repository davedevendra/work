import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-test',
  templateUrl: './test.component.html',
  styles: [`app-test {
      color: red;
    }

    .text-success {
        color: green;
    }



    .text-special {
        font-style: italic;
    }`]

  })
export class TestComponent implements OnInit {

  public name: string = "Dave";
  public myId: string = "myTestId";
  public isDisabled: boolean = false;

  public isSuccess: string = "text-success";
  public error: string = "error";
  public hasError: boolean = true;
  public isSpecial: boolean = true;

  public messageClasses = {
    "text-success": !this.hasError,
    "text-danger": this.hasError,
    "text-special": this.isSpecial
  }

  
  constructor() { }

  ngOnInit() {
  }

}
