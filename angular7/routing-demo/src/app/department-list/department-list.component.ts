import { Component, OnInit } from '@angular/core';
import { Router,ActivatedRoute,ParamMap } from '@angular/router';


@Component({
  selector: 'app-department-list',
  template: `
    <h3>
      Department List
    </h3>
    <ul class="items">
      <li (click)="onSelect(department)" [class.selected]="isSelected(department)" *ngFor="let department of departments">
        <span class="badge">{{department.id}}</span>{{department.name}}
      </li>
    </ul>
  `,
  styles: []
})
export class DepartmentListComponent implements OnInit {
  public departments = [
    { id: 1, name: 'Angular' },
    { id: 2, name: 'Node JS' },
    { id: 3, name: 'Java' },
    { id: 4, name: 'Bootstrap' },
    { id: 5, name: 'CSS' },
  ];

  public selectedId;
  constructor(private _router:Router, private route:ActivatedRoute) { }

  ngOnInit() {
    this.route.paramMap.subscribe((params: ParamMap) => {
      let id = parseInt(params.get('id'));
      this.selectedId = id;
    });
  }

  onSelect(department) {
    //this._router.navigate(['/departments', department.id]);
    this._router.navigate([department.id], { relativeTo: this.route });
  }

  isSelected(department) {
    return this.selectedId === department.id;
  }

}
