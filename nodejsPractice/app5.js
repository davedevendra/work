var events = require('events');
var util = require('util');


/*
var myEmitter = new events.EventEmitter();

myEmitter.on('someEvent', function(mssg){
	console.log(mssg);
});

myEmitter.emit('someEvent', 'the event was emitted');
*/

var Person = function(name) {
	this.name = name;
};

util.inherits(Person, events.EventEmitter);

var devendra = new Person('Devendra');
var dave = new Person('Dave');
var tinku =  new Person('Tinku');

var persons = [devendra,dave,tinku];

persons.forEach(function(person){
	person.on('speak', function(msg){
		console.log(person.name + ' said: '+msg);
	});
});

devendra.emit('speak', 'hey dudes');


