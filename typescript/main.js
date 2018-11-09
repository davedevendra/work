"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
        return extendStatics(d, b);
    }
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
exports.__esModule = true;
var message = 'Welcome back';
console.log(message);
var name = 'Devendra';
var sentence = "This my name " + name + ".\nI live in Hyderabad.";
console.log(sentence);
var list1 = [1, 2, 3];
var list2 = [6, 7, 8];
var person1 = ['Chris', 25];
var Color;
(function (Color) {
    Color[Color["Red"] = 0] = "Red";
    Color[Color["Green"] = 1] = "Green";
    Color[Color["Yellow"] = 2] = "Yellow";
})(Color || (Color = {}));
;
var c = Color.Green;
console.log(c);
var randomValue = 10;
randomValue = true;
randomValue = 'Devendra';
var myVariable = 10;
function hasName(obj) {
    return !!obj && typeof obj === "object" && "name" in obj;
}
if (hasName(myVariable)) {
    console.log(myVariable.name);
}
myVariable = 'Devendra';
myVariable.toUpperCase();
var a;
a = 10;
a = true;
var b = 20;
var multitype;
multitype = 20;
multitype = false;
function add(num1, num2) {
    if (num1 === void 0) { num1 = 4; }
    if (num2)
        return num1 + num2;
    else
        return num1;
}
add(5, 10);
add(5);
//interface
function fullName(person) {
    console.log(person.firstname + " " + person.lastname);
}
var p = {
    firstname: 'Bruce',
    lastname: 'Wayne'
};
fullName(p);
function fullNamePerson(person) {
    console.log(person.firstname + " " + person.lastname);
}
var personObj = {
    firstname: 'Devendra',
    lastname: 'Dave'
};
fullNamePerson(personObj);
//Class and modifiers
var Employee = /** @class */ (function () {
    function Employee(name) {
        this.employeeName = name;
    }
    Employee.prototype.greet = function () {
        console.log("Good Morning " + this.employeeName);
    };
    return Employee;
}());
var emp1 = new Employee('Devendra');
console.log(emp1.employeeName);
emp1.greet();
//inheritence
var Manager = /** @class */ (function (_super) {
    __extends(Manager, _super);
    function Manager(managername) {
        return _super.call(this, managername) || this;
    }
    Manager.prototype.delegateWork = function () {
        console.log(this.employeeName + " is delegating work");
    };
    return Manager;
}(Employee));
var m1 = new Manager('Dave');
m1.delegateWork();
m1.greet();
console.log(m1.employeeName);
