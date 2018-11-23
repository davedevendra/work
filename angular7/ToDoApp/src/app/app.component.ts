import { Component } from '@angular/core';
import { log } from 'util';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  //Define your variables done,todos,newToDo,newToDoObj,error
  done:boolean;
  todos:any;
  newToDo: string;
  newToDoObj: any;
  error:boolean;
  TODOS = [];

  //Define your constructor here with todos as [] ,newToDo as '' and error as false
  constructor() {
      this.todos = [];
      this.newToDo = '';
      this.error = false;
  }
  //Define your addMore function here
  addMore() {
    this.newToDoObj = { 'desc': this.newToDo, 'done': this.done };
    this.todos.push(this.newToDoObj);
    this.newToDo = '';
  }
  //Define your clearAll function here
  clearAll(){
    this.TODOS = [];
    this.todos = [];
  }
}
