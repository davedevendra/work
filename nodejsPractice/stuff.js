var counter = function(arr) {
	console.log('There are ' + arr.length + ' elements in the array');
};


var adder = function(a,b) {
	return `The sum of 2 numbers is ${a+b}`;
}

var pi=3.142;

/*
module.exports.counter = counter;
module.exports.adder = adder;
module.exports.pi = pi;
*/

module.exports = {
	counter : counter, 
	adder : adder, 
	pi : pi
}; 

