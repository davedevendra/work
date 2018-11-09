export {};
let message = 'Welcome back';
console.log(message);

let name:string = 'Devendra'
let sentence: string = `This my name ${name}.
I live in Hyderabad.`;

console.log(sentence);

let list1: number[] = [1,2,3];

let list2: Array<number> = [6,7,8];

let person1: [string,number] = ['Chris', 25];

enum Color { Red, Green, Yellow };

let c: Color = Color.Green;
console.log(c);

let randomValue: any = 10;
randomValue = true;
randomValue = 'Devendra';

let myVariable: unknown = 10;

function hasName(obj: any): obj is { name: string } {
  return !!obj && typeof obj === "object" && "name" in obj
}

if (hasName(myVariable)) {
  console.log(myVariable.name);
}
(myVariable as String) = 'Devendra';
(myVariable as string).toUpperCase();

let a;
a = 10;
a = true;

let b = 20;

let multitype: number | boolean
multitype = 20;
multitype = false;

function add(num1:number=4, num2?:number ):number {
  if (num2)
    return num1 + num2;
  else
    return num1;
}

add(5, 10);
add(5);

//interface
function fullName(person: { firstname: string, lastname: string }) {
  console.log(`${person.firstname} ${person.lastname}`);
}

let p = {
  firstname: 'Bruce',
  lastname: 'Wayne'
}

fullName(p);

interface Person {
  firstname: string;
  lastname: string;
}

function fullNamePerson(person: Person) {
  console.log(`${person.firstname} ${person.lastname}`);
}

let personObj:Person = {
  firstname: 'Devendra',
  lastname: 'Dave'
}

fullNamePerson(personObj);

//Class and modifiers

class Employee {
  employeeName: string;

  constructor(name: string) {
    this.employeeName = name;
  }

  greet() {
    console.log(`Good Morning ${this.employeeName}`);
  }
}

let emp1 = new Employee('Devendra');
console.log(emp1.employeeName);
emp1.greet();

//inheritence

class Manager extends Employee {
  constructor(managername: string) {
    super(managername);
  }

  delegateWork() {
    console.log(`${this.employeeName} is delegating work`);
  }
}

let m1 = new Manager('Dave');
m1.delegateWork();
m1.greet();
console.log(m1.employeeName);

