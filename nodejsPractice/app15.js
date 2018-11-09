var express = require('express');

var app = express();

app.get('/', function(req,res){
	res.send('This is home page');
});

app.get('/contact', function(req,res){
	res.send('This is Contact page');
});

app.get('/profile/:name', function(req,res){
	res.send('You requested to see the profile of ' + req.params.name);
});

app.listen(3000);