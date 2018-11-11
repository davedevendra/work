import { Component, OnInit } from '@angular/core';
import { adjustBlueprintForNewNode } from '@angular/core/src/render3/instructions';
import { InterpolationConfig } from '@angular/compiler';

@Component({
  selector: 'app-test',
  templateUrl: './test.component.html',
  styles: [`app-test {
      color: red;
    }

    .text-success {
        color: green;
    }

    .text-danger {
        color: red;
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
  public styleProperty = "blue";

  public messageClasses = {
    "text-success": !this.hasError,
    "text-danger": this.hasError,
    "text-special": this.isSpecial
  }

  public styleMessages = {
    color: 'blue',
    fontStyle: 'italic'
  }
  
  //events
  public greetMsg: string = "";
  //template Reference variable

  //two way data binding.
  public myName = "";

  //structural directives
  public displayName = true;
  constructor() { }

  ngOnInit() {
  }

  clickMe(event) {
    console.log("Welcome to Greet Event");
    console.log(event);
    console.log(event.type);
    this.greetMsg = "Welcome to Greet Event -> " + event.type;
  }

  logMessage(value) {
    console.log(value);
  }

  changeMe() {

    this.displayName = !this.displayName;
  }

}
