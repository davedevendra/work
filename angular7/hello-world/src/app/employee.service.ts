import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class EmployeeService {


  public employees = [
    { "id": 1, "name": "Andrew", "age": 30 },
    { "id": 2, "name": "John", "age": 35 },
    { "id": 3, "name": "Tina", "age": 45 },
    {"id": 4, "name": "Maria", "age": 25},
  ];

  constructor() { }

  getEmployees() {
    return  [
      { "id": 1, "name": "Andrew", "age": 30 },
      { "id": 2, "name": "John", "age": 35 },
      { "id": 3, "name": "Tina", "age": 45 },
      {"id": 4, "name": "Maria", "age": 25},
    ];
  }
}
