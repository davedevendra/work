parking.filter("plate", function(){
    return function(input, separator){
        var firstPart = input.substring(0,3);
        var seconfPart = input.substring(3);
        return firstPart + separator + seconfPart;
    };
});