/*
 * This sample changes a message attribute on virtual device and triggers a message 
 * to the Cloud Service with the updated attribute value.
 *
 * The client is a directly connected device using the virtual device API.
 */

dcl = require("device-library.node");
dcl = dcl({debug: true});

dcl.oracle.iot.tam.store = (process.argv[2]);
dcl.oracle.iot.tam.storePassword = (process.argv[3]);

var myModel;
var virtualDev;

const csv=require('csvtojson/v1')
const csvFilePath='./Dataset_02.csv'

function startVirtualHWDevice(device, id) {
    var virtualDev = device.createVirtualDevice(id, myModel);
    var count = 0;
    var newValues = {
	"Location_ID": "RYINDCH-150717",
	"TOD": "Temperature",
	"Latitude": 12.122223,
	"Longitude" : 81.221133,
	"Device_ID":"RYTHDS04",
	"Time":"20.20",
	"Data":20.20
    };

    // Change the attribute message and trigger an update on the vitual device

csv().fromFile(csvFilePath).on('end_parsed',(jsonArrayObj)=>{

var x = jsonArrayObj.length;
var j=jsonArrayObj;
console.log(j);
console.log(x);
timer = setInterval(function () {
      
        newValues.Location_ID = 	j[count].Location_ID;
		newValues.TOD 		= 	j[count].TOD;
	newValues.Latitude  	= 	parseFloat(j[count].Latitude);
	newValues.Longitude 	= 	parseFloat(j[count].Longitude);
	newValues.Device_ID = 	j[count].Device_ID;
	newValues.Time		=	j[count].Time;
	newValues.Data		=	parseFloat(j[count].Data);
	
        virtualDev.update(newValues);
	count++;
	
	if(count==x)
	{
            virtualDev.close();
            clearInterval(timer);
	}
    
    }, 1000);

});
    
}
// Display the device model
function getHWModel(device){
    device.getDeviceModel('urn:oracle:iot:device:Temperature_Humidity', function (response) {
        console.log('-----------------MY DEVICE MODEL----------------------------');
        console.log(JSON.stringify(response,null,4));
        console.log('------------------------------------------------------------');
        myModel = response;
        startVirtualHWDevice(device, device.getEndpointId());
    });
}

// Create a directly connected device and activate it if not already activated
var dcd = new dcl.device.DirectlyConnectedDevice();
if (dcd.isActivated()) {
    getHWModel(dcd);
} else {
    dcd.activate(['urn:oracle:iot:device:Temperature_Humidity'], function (device) {
        dcd = device;
        console.log(dcd.isActivated());
        if (dcd.isActivated()) {
            getHWModel(dcd);
        }
    });
}