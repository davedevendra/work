var bodyParser = require('body-parser');

var mongoose = require('mongoose');

// connect to database 
//mongodb://admin:admin123@ds245512.mlab.com:45512/sample
mongoose.connect('mongodb://admin:admin123@ds245512.mlab.com:45512/sample');

// create a schema - this like blueprint

var todoSchema = new mongoose.Schema({
    item: String
});

var Todo = mongoose.model('Todo', todoSchema);
/* var itemOne= Todo({item:'buy flowers'}).save(function(err){
    if(err) throw err;
    console.log('item saved');
});*/

// var data = [{item: 'Get some milk'}, {item: 'Read some book'}, {item: 'Watch a movie'}];

var urlencodeParser = bodyParser.urlencoded({extended: false});

module.exports = function (app) {
    app.get('/todo', function(req,res){
        console.log('GET /todo');
        // get the all data from mongo db
        Todo.find({}, function(err,data){
            if(err) throw err;
            res.render('todo', {todos: data});
        });
        /* res.render('todo', {todos: data}); */
    });

    app.post('/todo', urlencodeParser, function(req,res){
        console.log('POST /todo');
        // add the item to mongodb
        var newTodo = Todo(req.body).save(function(err,data){
            if(err) throw err;
            res.json(data);
        });

        /*data.push(req.body);
        res.json(data);*/
    });

    app.delete('/todo/:item', function(req,res){
        console.log('DELETE /todo'); 
        // delete and item from mongodb
        Todo.find({item: req.params.item.replace(/\-/g, " ")}).remove(function(err,data){
            if(err) throw err;
            res.json(data);
        });

        /*data = data.filter(function(todo){
            return todo.item.replace(/ /g, '-') != req.params.item;
        });
        res.json(data);*/
    });

};