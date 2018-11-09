var http = require('http');
var fs = require('fs');


var server = http.createServer(function(req,res){
	console.log('Request was made: ' + req.url);
	res.writeHead(200, {'Content-Type': 'application/json'});
	
	var myObj = {
		name: 'Devendra',
		job: 'Developer',
		age: 43
	};
	res.end(JSON.stringify(myObj));
});

server.listen(3000,'127.0.0.1');
console.log("Hello server listening on 3000");

