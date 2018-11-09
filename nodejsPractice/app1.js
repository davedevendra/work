console.log('Hello world!!!');
console.log(__dirname);
console.log(__filename);
var time = 0;
var timer = setInterval(function() {
	time += 2;
	console.log(time + ' seconds has passed...');
	if(time > 5) {
		clearInterval(timer);
	}
}, 2000);

