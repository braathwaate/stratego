// var XMLHttpRequest = require("../../Downloads/node-XMLHttpRequest-master/lib/XMLHttpRequest.js").XMLHttpRequest;
// var XMLHttpRequest = require('w3c-xmlhttprequest').XMLHttpRequest;
//var XMLHttpRequest = require('xmlhttprequest').XMLHttpRequest;
var XMLHttpRequest = require('xhr2');
var fs = require('fs');

// file is included here:
eval(fs.readFileSync('basefunctions.js')+'');
eval(fs.readFileSync('gameui.js')+'');
eval(fs.readFileSync('gameengine.js')+'');
eval(fs.readFileSync('textresources.js')+'');


var navigator={ appName:"Netscape" } ;
onInitialize();
onSetupButtonClick();
// onStartButtonClick();

