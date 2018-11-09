// normal function

function sayHi() {
	console.log('sayHi');
}

sayHi();

function callFunction(fun) {
	fun();
}

// function expression

var sayBye = function() {
	console.log('sayBye');
};

//sayBye();
callFunction(sayBye);