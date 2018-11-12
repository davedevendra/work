import { Injectable } from '@angular/core';
import { url } from 'inspector';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { IEmployee } from './employee';
import { catchError } from 'rxjs/operators';


// import 'rxjs/add/operator/catch';
//  import 'rxjs/add/observable/throw';


@Injectable({
  providedIn: 'root'
})
export class EmployeeService {

  private _url:string = "/assets/data/employees1.json";

  constructor(private http:HttpClient) { }

  getEmployees(): Observable<IEmployee[]> {
    //return this.http.get<IEmployee[]>(this._url).catch(this.errorHandler); // v5
    return this.http.get<IEmployee[]>(this._url).pipe(catchError(err => this.errorHandler(err))); // v >5
  }

  errorHandler(error: HttpErrorResponse) {
    console.log('dddd'+error.message);
    //return Observable.throw(error.message || "Server error");
    return throwError(error.message || "Server error");
  }
}
