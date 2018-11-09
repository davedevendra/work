var fs = require('fs');

/*var readMe = fs.readFileSync('readMe.txt', 'utf8');
console.log(readMe);
fs.writeFileSync('writeMe.txt', readMe);
*/

// async 
fs.readFile('readMe.txt', 'utf8', function(err,data){
	console.log(data);
	fs.writeFile('writeMe.txt', data);
});
console.log('>>>TEST');
