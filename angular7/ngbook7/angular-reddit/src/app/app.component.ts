import { Component } from '@angular/core';
import { cleanSession } from 'selenium-webdriver/safari';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  addArticle(title:HTMLInputElement, link:HTMLInputElement): boolean {
    console.log(`Adding article title: ${title.value} and link: ${link.value}`);
    return false;
  }
}

