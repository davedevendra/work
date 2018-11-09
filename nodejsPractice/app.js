var express = require('express');
var bodyParser = require('body-parser')

// create application/json parser
//var jsonParser = bodyParser.json()
 
// create application/x-www-form-urlencoded parser
var urlencodedParser = bodyParser.urlencoded({ extended: false })


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

app.post('/contact', urlencodedParser, function(req,res){
	console.log(req.body);
	res.render('contact-success', {data: req.body});
});

app.get('/profile/:name', function(req,res){
	var data = {age: 43, job: 'Developer', hobbies: ['eating', 'sleeping', 'listening']};
	res.render('profile', {person: req.params.name, data:data});	
});

app.listen(3000);