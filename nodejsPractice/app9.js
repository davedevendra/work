var http = require('http');
var fs = require('fs');

var myReadStream = fs.createReadStream(__dirname + '/readMe.txt', 'utf8');
//var myWriteStream = fs.createWriteStream(__dirname + '/writeMe.txt');

/*
myReadStream.on('data', function(chunk){
	console.log('New Chunk Received: ');
	//console.log(chunk);
	myWriteStream.write(chunk);
});
*/

//myReadStream.pipe(myWriteStream);

var server = http.createServer(function(req,res){
	console.log('Request was made: ' + req.url);
	res.writeHead(200, {'Content-Type': 'text/plain'});
	var myReadStream = fs.createReadStream(__dirname + '/readMe.txt', 'utf8');
	myReadStream.pipe(res);
	
	//res.end('Hello World!!!');
});

server.listen(3000,'127.0.0.1');
console.log("Hello server listening on 3000");

