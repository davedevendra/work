var val1 = 0;
var val2 = 0;
var isOperationButtonClicked = false;
var operator = '';

function update(value) {
  //Type the code here.
  var txtValue = document.getElementById('screen').value;
    /*switch (value) {
      case '+':
        isOperationButtonClicked = true;
        operator = '+';
        val1 = parseInt(txtValue); 
        document.getElementById('screen').value = '';
        break;
      case '-':
        isOperationButtonClicked = true;
        operator = '-';
        val1 = parseInt(txtValue);
        document.getElementById('screen').value = '';
        break;
      case '*':
        isOperationButtonClicked = true;
        operator = '*';
        val1 = parseInt(txtValue);
        document.getElementById('screen').value = '';
        break;
      case '/':
        isOperationButtonClicked = true;
        operator = '/';
        val1 = parseInt(txtValue);
        document.getElementById('screen').value = '';
        break;
      default:
        txtValue =  txtValue + value;
        document.getElementById('screen').value = txtValue;
        break;
    }*/
    txtValue =  txtValue + value;
    document.getElementById('screen').value = txtValue;
}

function result() {
  //Type the code here.
  /*txtValue = document.getElementById('screen').value;
  val2 = parseInt(txtValue);
  var result = 0;
  if(operator === "+"){
    result = val1 + val2;
  } else if(operator === "-") {
    result = val1 - val2;
  } else if(operator === "*") {
    result = val1 * val2;
  } else if(operator === "/") {
    if(val2 != 0) {
      result = val1 / val2;
    } else {
      result = "";
    }
  }
  document.getElementById('screen').value =result;
  form_reset();
  */
   var result = 0;
   var txtValue = document.getElementById('screen').value;
   var operatorGiven = false;
   var nums =[];
   if(txtValue.indexOf('+') > 0) {
    nums =  txtValue.split('+');
    result = parseInt(nums[0]) + parseInt(nums[1]);
   } else if(txtValue.indexOf('-') > 0) {
    nums =  txtValue.split('-');
    result = parseInt(nums[0]) - parseInt(nums[1]);
   } else if(txtValue.indexOf('*') > 0) {
    nums =  txtValue.split('*');
    result = parseInt(nums[0]) * parseInt(nums[1]);
   } else if(txtValue.indexOf('/') > 0) {
    nums =  txtValue.split('/');
    if(nums[1] > 0)
      result = parseInt(nums[0]) / parseInt(nums[1]);
    else 
      result = 0;
   }
   document.getElementById('screen').value = result;
}

function form_reset() {
  //Type the code here.
  val1 = 0;
  val2 = 0;
  isOperationButtonClicked =  false;
  operator = '';
  document.getElementById('screen').value = '';
}
