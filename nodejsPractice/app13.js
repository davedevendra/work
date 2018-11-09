var http = require('http');
var fs = require('fs');


var server = http.createServer(function(req,res){
	console.log('Request was made: ' + req.url);

	if(req.url === '/home' || req.url === '/'){
		res.writeHead(200, {'Content-Type': 'text/html'});
		var homeReadStream = fs.createReadStream(__dirname + '/index.html', 'utf8').pipe(res);
	} else if(req.url === '/contact-us') {
		res.writeHead(200, {'Content-Type': 'text/html'});
		var homeReadStream = fs.createReadStream(__dirname + '/contact.html', 'utf8').pipe(res);
	} else if(req.url === '/api/devendra') {
		var devObj  = [{name: 'Devendra', age: 43}, {name: 'Tinku', age:38}];
		res.writeHead(200, {'Content-Type': 'application/json'});
		res.end(JSON.stringify(devObj));
	} else {
		res.writeHead(404, {'Content-Type': 'text/html'});
		var homeReadStream = fs.createReadStream(__dirname + '/404.html', 'utf8').pipe(res);
	}
	
});

server.listen(3000,'127.0.0.1');
console.log("Hello server listening on 3000");

