import { By } from '@angular/platform-browser';
import { TestBed, async } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { FormsModule } from '@angular/forms';

describe('AppComponent', () => {
  let comp;
  let fixture;
  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [
        AppComponent
      ],
      imports: [
        FormsModule
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AppComponent);
    comp = fixture.debugElement.componentInstance;
  });
  it('should create the app', async(() => {
    expect(comp).toBeTruthy();
  }));
  it('should add todos', async(() => {
    const input_el = fixture.debugElement.query(By.css('#newTodo')).nativeElement;
    const add_btn = fixture.debugElement.query(By.css('#addActivity')).nativeElement;
    comp.newToDo = 'John Smith';
    input_el.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    add_btn.click();
    fixture.detectChanges();

    const todosList = fixture.debugElement.queryAll(By.css('.todoItem'));

    expect(todosList.length).toBe(1);
  }));
  it('should mark todos as done', async(() => {

    // add the todo
    const input_el = fixture.debugElement.query(By.css('#newTodo')).nativeElement;
    const add_btn = fixture.debugElement.query(By.css('#addActivity')).nativeElement;
    comp.newToDo = 'John Smith';
    input_el.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    add_btn.click();
    fixture.detectChanges();

    // mark todo as done
    comp.todos[0].done = true;
    fixture.detectChanges();
    fixture.whenStable().then(() => {
      const todosListFirst = fixture.debugElement.queryAll(By.css('.todoItem'))[0].childNodes[1];
      // check if the checkbox is updated
      expect(todosListFirst.properties.checked).toBe(true);
    });
  }));
  it('should clear todos', async(() => {

    // add the todo
    const input_el = fixture.debugElement.query(By.css('#newTodo')).nativeElement;
    const add_btn = fixture.debugElement.query(By.css('#addActivity')).nativeElement;
    comp.newToDo = 'John Smith';
    input_el.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    add_btn.click();
    fixture.detectChanges();

    const clear_btn = fixture.debugElement
                    .query(By.css('#clearAll')).nativeElement;
    clear_btn.click();
    fixture.detectChanges();

    const todosList = fixture.debugElement.queryAll(By.css('.todoItem'));

    expect(todosList.length).toBe(0);
  }));
});
