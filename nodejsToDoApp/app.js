var express = require('express');
var todoController = require('./controllers/todoController');


var app = express();

// set the template engine
app.set('view engine', 'ejs');

// set the static files  handler 
app.use(express.static('./public'));

// fire controllers
todoController(app);

//listen to port
app.listen(3000);

console.log('Server started and listening on port 3000');
