import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, ParamMap } from '@angular/router';


@Component({
  selector: 'app-department-detail',
  template: `
    <h1>
      You have selected department with id = {{departmentId}}
    </h1>

    <p>
      <button (click)="goOverview()"> Overview </button>
      <button (click)="goContact()"> Contact </button>
    </p>
    <p>
      <router-outlet></router-outlet>
    </p>
    <p>
      <button (click)="goPrevious()">Previous </button>
      <button (click)="goNext()"> Next </button>
    </p>

    <button (click)="goBack()"> Back</button>
  `,
  styles: []
})
export class DepartmentDetailComponent implements OnInit {

  public departmentId;
  constructor(private route:ActivatedRoute, private router:Router) { }

  ngOnInit() {
    // let id = parseInt(this.route.snapshot.paramMap.get('id'));
    // this.departmentId = id;
    this.route.paramMap.subscribe((params: ParamMap) => {
      let id = parseInt(params.get('id'));
      this.departmentId = id;
    });

  }

  goPrevious() {
    let previousId = this.departmentId - 1;
    this.router.navigate(['/departments', previousId]);
    //this.router.navigate([previousId], { relativeTo: this.route });
  }

  goNext() {
    let nextId = this.departmentId + 1;
    this.router.navigate(['/departments', nextId]);
    //this.router.navigate([nextId], { relativeTo: this.route });
  }

  goBack() {
    let selectedId = this.departmentId ? this.departmentId : null;
    //this.router.navigate(['/departments', { 'id': selectedId }]);
    this.router.navigate(['../', { id: selectedId }], { relativeTo: this.route });
  }

  goOverview() {
    this.router.navigate(['overview'], { relativeTo: this.route });
  }

  goContact() {
    this.router.navigate(['contact'], { relativeTo: this.route });
  }

}
