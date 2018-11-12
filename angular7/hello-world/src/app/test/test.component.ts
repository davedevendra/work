import { Component, OnInit, Input,Output,EventEmitter} from '@angular/core';
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

  public name = 'Dave';
  public myId = 'myTestId';
  public isDisabled = false;

  public isSuccess = 'text-success';
  public error = 'error';
  public hasError = true;
  public isSpecial = true;
  public styleProperty = 'blue';

  public messageClasses = {
    'text-success': !this.hasError,
    'text-danger': this.hasError,
    'text-special': this.isSpecial
  };

  public styleMessages = {
    color: 'blue',
    fontStyle: 'italic'
  };

  // events
  public greetMsg = '';
  // template Reference variable

  // two way data binding.
  public myName = '';

  // structural directives
  public displayName = true;

  public myColor = 'blue';

  public colors = ['red', 'green', 'yellow', 'blue'];

  @Input('parentData') public parentName;

  @Output() public childEvent = new EventEmitter()

  constructor() { }

  ngOnInit() {
  }

  clickMe(event) {
    console.log('Welcome to Greet Event');
    console.log(event);
    console.log(event.type);
    this.greetMsg = 'Welcome to Greet Event -> ' + event.type;
  }

  logMessage(value) {
    console.log(value);
  }

  changeMe() {
    this.displayName = !this.displayName;
  }

  selectColor(value) {
    this.myColor = value;
  }

  fireEvent() {
    this.childEvent.emit('Title coming from test component is Wow');
  }

}
