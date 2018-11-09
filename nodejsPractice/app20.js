var express = require('express');

var app = express();

app.set('view engine', 'ejs');
app.use('/assets', express.static('assets'));

app.get('/', function(req,res){
	res.render('index');
});

app.get('/contact', function(req,res){
	console.log(req.query);
	res.render('contact', {qrp: req.query});
});

app.get('/profile/:name', function(req,res){
	var data = {age: 43, job: 'Developer', hobbies: ['eating', 'sleeping', 'listening']};
	res.render('profile', {person: req.params.name, data:data});	
});

app.listen(3000);