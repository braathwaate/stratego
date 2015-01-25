
///////////////////////////////////////////////////////////////////////////////////////
//
// form event handler functions
//
///////////////////////////////////////////////////////////////////////////////////////

function onInitialize()
{
	//initialize game info object
	initializeGameInfo();

	//initialize game service
	initializeService();
	
	//initialize constants
	initializeConstants();
	
	//initialize UI
	initializeGameUI();

	//setup field images
	// setupFieldImages();
}

function serviceResponse()
{
	// console.log(Service.responseText);

	var DOMParser = require('xmldom').DOMParser;
	var doc = new DOMParser().parseFromString(
		Service.responseText
		    ,'text/xml');

	return doc;
}

function onSettingsButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	var settingsCanvas = document.getElementById("settingsCanvas");
	var settingsButton = document.getElementById("settingsButton");
	
	if (settingsCanvas.style.visibility == "hidden")
	{
		settingsCanvas.style.zIndex = 20
		settingsCanvas.style.visibility = "visible";
		settingsButton.style.backgroundColor = "#00D000";
	}
	else
	{
		settingsCanvas.style.zIndex = 0
		settingsCanvas.style.visibility = "hidden";
		settingsButton.style.backgroundColor = "navy";
	}
}

function onMobileModeButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	//set the new state
	UIState.MobileMode = !UIState.MobileMode;
	
	var commitButton = document.getElementById("commitButton");
	var clearButton = document.getElementById("clearButton");
	
	if (UIState.MobileMode)
	{
		document.getElementById("lineCanvas").style.visibility = "hidden";
		commitButton.style.visibility = "visible";
		clearButton.style.visibility = "visible";
	}
	else
	{
		commitButton.style.visibility = "hidden";
		clearButton.style.visibility = "hidden";
		document.getElementById("lineCanvas").style.visibility = "visible";
	}
	
	//set new button text
	var mobileModeButton = document.getElementById("mobileModeButton");
	
	if (UIState.MobileMode)
	{
		mobileModeButton.value = "Touch Mode: On";
	}
	else
	{
		mobileModeButton.value = "Touch Mode: Off";
	}
}

function onInvertedRanksButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	//set the new state
	UIState.InvertedRanks = !UIState.InvertedRanks;
	
	//update game screens
	updateBoard();
	fillStandingsTable();
	
	//set new button text
	var invertedRanksButton = document.getElementById("invertedRanksButton");
	
	if (UIState.InvertedRanks)
	{
		invertedRanksButton.value = "Inverted Ranks: On";
	}
	else
	{
		invertedRanksButton.value = "Inverted Ranks: Off";
	}
}

function onVisualAidsButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	//set the new state
	UIState.GameAids = !UIState.GameAids;
	
	//update game screens
	updateBoard();
	
	//set new button text
	var visualAidsButton = document.getElementById("visualAidsButton");
	
	if (UIState.GameAids)
	{
		visualAidsButton.value = "Visual Aids: On";
	}
	else
	{
		visualAidsButton.value = "Visual Aids: Off";
	}
}

function onSetupButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	//clear messsages
	displayMessage("");
	
	//show confirm dialog
	if (GameEngineConstants.GameState == GameInfo.State)
	{
		var confirmCanvas = document.getElementById("confirmCanvas");
		confirmCanvas.style.visibility = "visible";
	}
	else
	{
		sendSetupRequest();
	}
}

function onStartButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	if (GameEngineConstants.SetupState == GameInfo.State)
	{
		//hide selected field
		selectField(false);
		
		//request service access privileges
		try
		{
			if (navigator.appName == "Netscape")
			{
				netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
			}
		}
		catch(exception)
		{
		}
		
		//submit CreateGame request to web service
		Service.open("POST", Constants.ServiceURL, true);
		Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
		Service.onreadystatechange = onCreateGameResponse;
    	Service.send(createCreateGameRequest());
		
		UIState.ServiceCallInProgress = true;
	}
	else
	{
		var acknowledgeCanvas = document.getElementById("acknowledgeCanvas");
		acknowledgeCanvas.style.visibility = "visible";
	}
}

function onStandingsButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	var standingsCanvas = document.getElementById("standingsCanvas");
	var standingsButton = document.getElementById("standingsButton");
	
	if (standingsCanvas.style.visibility == "hidden")
	{
		//set values on screen
		fillStandingsTable();
		
		//show dialog
		standingsCanvas.style.visibility = "visible";
		
		//change button color
		standingsButton.style.backgroundColor = "#00D000";
	}
	else
	{
		//hide dialog
		standingsCanvas.style.visibility = "hidden";
		
		//reset button color
		standingsButton.style.backgroundColor = "navy";
	}
}

function onCommitButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	if (GameEngineConstants.SetupState == GameInfo.State)
	{
		if (isFieldSelected()  &&  isField2Selected())
		{
			//swap selected setup figures
			swapFigures(UIState.SelectedField.Row, UIState.SelectedField.Col, UIState.SelectedField2.Row, UIState.SelectedField2.Col);
			
			//unselect/hide fields
			selectField(false);
			selectField2(false);
		}
	}
	else if (GameEngineConstants.GameState == GameInfo.State)
	{
		if (isFieldSelected() && isField2Selected())
		{
			displayMessage("");
			setMove(UIState.SelectedField.Row, UIState.SelectedField.Col, UIState.SelectedField2.Row, UIState.SelectedField2.Col);
		}
	}
}

function onClearButtonClick()
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	selectField(false);
	selectField2(false);
}

function onBoardMouseClick(eventInfo)
{
	//do not process if web service call in progress
	if (UIState.ServiceCallInProgress)
	{
		return;
	}
	
	//calculate selected field	
	var row = 9 - parseInt((getEventLocationY(eventInfo) - Constants.FieldOriginY) / Constants.FieldSizeY);
	var col = parseInt((getEventLocationX(eventInfo) - Constants.FieldOriginX) / Constants.FieldSizeX);
	
	//check for outer boundaries
	if (0 > row)
	{
		row = 0;
	}
	else if (9 < row)
	{
		row = 9;
	}
	
	if (0 > col)
	{
		col = 0;
	}
	else if (9 < col)
	{
		col = 9;
	}

	//trigger field selection event
	if (isValidBoardField(row, col))
	{
		if (GameEngineConstants.SetupState == GameInfo.State)
		{
			onSetupFieldSelected(row, col);
		}
		else if (GameEngineConstants.GameState == GameInfo.State)
		{
			onGameFieldSelected(row, col);
		}
	}
}

function onBoardMouseMove(eventInfo)
{
	if (eventInfo)
	{
		eventInfo.preventDefault();
	}
}

function onSetupFieldSelected(row, col)
{
	if (4 > row)
	{
		//clear messsages
		displayMessage("");
		
		//continue with selection business rules
		if (!isFieldSelected())
		{
			selectField(true, row, col);
		}
		else
		{
			if (row == UIState.SelectedField.Row  &&  col == UIState.SelectedField.Col)
			{
				selectField(false);
			}
			else if (UIState.MobileMode)
			{
				selectField2(true, row, col);
			}
			else
			{
				//swap selected setup figures
				swapFigures(row, col, UIState.SelectedField.Row, UIState.SelectedField.Col);
				
				//unselect/hide field
				selectField(false);
			}
		}
	}
}

function onGameFieldSelected(row, col)
{
	if (!isFieldSelected())
	{
		var figure = GameInfo.FigureMatrix[row][col];
		
		if (isValidGameFieldSelection(row, col))
		{
			selectField(true, row, col);
		}
	}
	else if (row == UIState.SelectedField.Row  &&  col == UIState.SelectedField.Col)
	{
		if (UIState.MobileMode)
		{
			if (!isField2Selected())
			{
				selectField(false);
			}
		}
		else
		{
			selectField(false);
		}
	}
	else if (isValidGameField2Selection(row, col))
	{
		if (UIState.MobileMode)
		{
			if (!isField2Selected() ||  row != UIState.SelectedField2.Row || col != UIState.SelectedField2.Col)
			{
				selectField2(true, row, col);
			}
			else
			{
				selectField2(false);
			}
		}	
		else
		{
			displayMessage("");
			setMove(UIState.SelectedField.Row, UIState.SelectedField.Col, row, col);
		}
	}
}

function onConfirmCanvasOkButtonClick()
{
	//hide all canvases
	var settingsCanvas = document.getElementById("settingsCanvas");
	settingsCanvas.style.visibility = "hidden";
	
	var standingsCanvas = document.getElementById("standingsCanvas");
	standingsCanvas.style.visibility = "hidden";
		
	var confirmCanvas = document.getElementById("confirmCanvas");
	confirmCanvas.style.visibility = "hidden";
	
	//start the setup
	sendSetupRequest();
}

function onAcknowledgeCanvasOkButtonClick()
{
	//hide acknowledge canvas
	var acknowledgeCanvas = document.getElementById("acknowledgeCanvas");
	acknowledgeCanvas.style.visibility = "hidden";
}

function onCancelButtonClick()
{
	//hide confirm canvas
	var confirmCanvas = document.getElementById("confirmCanvas");
	confirmCanvas.style.visibility = "hidden";
}


function type2rank(type)
{
	switch (type) {
	case "Ten" : return "1";
	case "Bomb" : return "B";
	case "Flag" : return "F";
	case "Nine" : return "2";
	case "Eight" : return "3";
	case "Seven" : return "4";
	case "Six" : return "5";
	case "Five" : return "6";
	case "Four" : return "7";
	case "Three" : return "8";
	case "Two" : return "9";
	case "One" : return "s";
        default: return "#";
	}
}

function printBoard()
{
	for (var row = 0; row <10; row++)
	{
		var line = "";
		for (var col = 0; col < 10; col++)
		{
			var gi = GameInfo.FigureMatrix[row][col];
			if (gi == null)
				line += ".";
			else {
				var type = gi.Type;
				line += type2rank(type);
			}
		}
		console.log(line);
	}
}

function saveSetup()
{
	var fs     = require('fs');
	var stream = fs.createWriteStream('setup', { flags : 'w' });

	rank = {};
	for (var row = 3; row >= 0; row--)
	{
		for (var col = 0; col < 10; col++)
		{
			var type = GameInfo.FigureMatrix[row][col].Type;
			if(typeof rank[type] == 'undefined')
				rank[type]='';
			rank[type] += String.fromCharCode(col);
			rank[type] += String.fromCharCode(row);
		}
	}
	stream.write(rank["Flag"]);
	stream.write(rank["One"]);
	stream.write(rank["Ten"]);
	stream.write(rank["Nine"]);
	stream.write(rank["Eight"]);
	stream.write(rank["Seven"]);
	stream.write(rank["Six"]);
	stream.write(rank["Five"]);
	stream.write(rank["Four"]);
	stream.write(rank["Three"]);
	stream.write(rank["Two"]);
	stream.write(rank["Bomb"]);
}

///////////////////////////////////////////////////////////////////////////////////////
//
// web service response handler functions
//
///////////////////////////////////////////////////////////////////////////////////////

function onGenerateSetupResponse()
{
	if (Service.readyState == 4)
	{
		UIState.ServiceCallInProgress = false;
		
		if (Service.status == 200)
		{
			//clear enemy and middle fields
			for (var row = 0; row < 10; row++)
			{
				for (var col = 0; col < 10; col++)
				{
					if ((row != 4  &&  row != 5 )  ||  (col != 2  &&  col != 3  &&  col != 6  &&  col != 7))
					{
						GameInfo.FigureMatrix[row][col] = null;
					}
				}
			}
		
			//setup own figures from web service call
			// var generateSetupResponseXML = Service.responseXML;			
			var generateSetupResponseXML = serviceResponse();

			var figureInfos = generateSetupResponseXML.getElementsByTagName("FigureInfo");

			for (var figureIndex = 0; figureIndex < 40; figureIndex++)
			{
				var figureInfo = figureInfos[figureIndex];						
				var row = parseInt(getElementText(figureInfo.getElementsByTagName("Row")[0]));
				var col = parseInt(getElementText(figureInfo.getElementsByTagName("Col")[0]));
				var type = getElementText(figureInfo.getElementsByTagName("Type")[0]);
				
				var figure = new Object();
				figure.Type = type;
				figure.Player = 1;
				figure.Status = GameEngineConstants.NoneStatus;
				
				GameInfo.FigureMatrix[row][col] = figure;
			}    			
			
			
			
			//update the images on the screen			
			updateBoard();

			saveSetup();
			
			//update game state
			GameInfo.State = GameEngineConstants.SetupState;

			onStartButtonClick();
		}
		else
		{
		require('util').debug('ERROR ' + Service.status);
    		displayMessage("Error accessing game server");
		}
	}
}

function onCreateGameResponse()
{
	if (Service.readyState==4)
	{
		UIState.ServiceCallInProgress = false;
		
  		if (Service.status==200)
		{
			//setup own figures from web service call
			// var createGameResponseXML = Service.responseXML;
			var createGameResponseXML = serviceResponse();
			GameInfo.GameKey = getElementText(createGameResponseXML.getElementsByTagName("gameKey")[0]);

			if (null != GameInfo.GameKey  &&  0 < GameInfo.GameKey.length)
			{
				//request service access privileges
				try
				{
					if (navigator.appName == "Netscape")
					{
						netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
					}
				}
				catch(exception)
				{
				}
				
				//submit SetSetup request to web service
				Service.open("POST", Constants.ServiceURL, true);
				Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
				Service.onreadystatechange = onSetSetupResponse;
		    	Service.send(createSetSetupRequest());
				
				UIState.ServiceCallInProgress = true;
			}
			else
			{
				displayMessage("Cannot create game.");
			}
		}
	}
}

function onSetSetupResponse()
{
	if (Service.readyState == 4)
	{
		UIState.ServiceCallInProgress = false;
		
		if (Service.status == 200)
		{
			//setup enemy figures
			// var setSetupResponseXML = Service.responseXML;			
			var setSetupResponseXML = serviceResponse();			
			var success = getElementText(setSetupResponseXML.getElementsByTagName("SetSetupResult")[0]);
			
			if (success)
			{
				for (var row = 6; row < 10; row++)
				{
					for (var col = 0; col < 10; col++) 
					{
						var figure = new Object();
						figure.Type = "Unknown";
						figure.Player = 2;
						figure.Status = GameEngineConstants.NoneStatus;
						
						GameInfo.FigureMatrix[row][col] = figure;
					}
				}
				
				//update the board
				updateBoard();
				
				//reset figure counters
				resetFigureCounters();
				
				//set state
				GameInfo.State = GameEngineConstants.GameState;

console.log("RED MOF 10 10");
var readline = require('readline');
var rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

var n = 0;
rl.on('line', function(line){
//    console.log(line);
    if (++n >= 4) {
        if (n == 4) {
		console.log("START");
		printBoard();
	} else
		makeMove(line);
    }
})

			}
		}
	}
}

function onSetWebMoveResponse()
{
	require('util').debug('onSetWebMoveResponse ' + Service.readyState + ' ' + Service.status);
	if (Service.readyState == 4)
	{
		UIState.ServiceCallInProgress = false;
	
		if (Service.status == 0) {	
			require('util').debug('ERROR');
			//submit SetWebMove request to web service
			Service.open("POST", Constants.ServiceURL, true);
			Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
			Service.onreadystatechange = onSetWebMoveResponse;
			Service.send(createSetWebMoveRequest(UIState.SelectedMove.StartRow, UIState.SelectedMove.StartCol, UIState.SelectedMove.EndRow, UIState.SelectedMove.EndCol, GameInfo.FigureMatrix[UIState.SelectedMove.StartRow][UIState.SelectedMove.StartCol].Type));
			
			UIState.ServiceCallInProgress = true;
		} else if (Service.status == 200)
		{
			//setup own figures from web service call
			// var setWebMoveResponseXML = Service.responseXML;
			var setWebMoveResponseXML = serviceResponse();
			var success = parseBool(getElementText(setWebMoveResponseXML.getElementsByTagName("SetWebMoveResult")[0]));
			var gameOver = parseBool(getElementText(setWebMoveResponseXML.getElementsByTagName("gameOver")[0]));
			var winner = getElementText(setWebMoveResponseXML.getElementsByTagName("winner")[0]);
		
			require('util').debug("Success:" + success);
			if (success)
			{
				//clear captured figures
				GameInfo.CurrentCapturedFigureCount = 0;
			
				//set default move results
				var attackerType = GameInfo.FigureMatrix[UIState.SelectedMove.StartRow][UIState.SelectedMove.StartCol].Type;
				var defenderType = "None";
				var attackerRemains = true;
				var defenderRemains = false;
				
				//get move results from response xml if collision
				var collision = getElementText(setWebMoveResponseXML.getElementsByTagName("Collision")[0]);
				
				if (collision == "true")
				{
					attackerType = getElementText(setWebMoveResponseXML.getElementsByTagName("WebPlayerType")[0]);
					defenderType = getElementText(setWebMoveResponseXML.getElementsByTagName("ComputerPlayerType")[0]);
					attackerRemains = parseBool(getElementText(setWebMoveResponseXML.getElementsByTagName("WebPlayerRemains")[0]));
					defenderRemains = parseBool(getElementText(setWebMoveResponseXML.getElementsByTagName("ComputerPlayerRemains")[0]));
					if (attackerRemains)
						lastMove += " KILLS ";
					else if (defenderRemains)
						lastMove += " DIES ";
					else
						lastMove += " BOTHDIE ";
					lastMove += type2rank(attackerType) + " " + type2rank(defenderType);
				} else
					lastMove += " OK";
				require('util').debug(lastMove);	// mof move
				console.log(lastMove);
				//set move on board
				applyMove(UIState.SelectedMove.StartRow,
					UIState.SelectedMove.StartCol,
					UIState.SelectedMove.EndRow,
					UIState.SelectedMove.EndCol,
					attackerType,
					defenderType,
					attackerRemains,
					defenderRemains);
					
				//display enemy type
				if ("None" != defenderType  &&  defenderRemains)
				{
					UIState.DisplayMoveEndFigure = true;
				}
				
				//update fields on board
				updateBoard();
				displayCapturedFiguresMessage();
				
				//unselect hide selected field
				selectField(false);
				selectField2(false);
				
				if (!gameOver)
				{
					//request service access privileges
					try
					{
						if (navigator.appName == "Netscape")
						{
							netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
						}
					}
					catch(exception)
					{
					}
						
					//submit SetWebMove request to web service
					Service.open("POST", Constants.ServiceURL, true);
					Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
					Service.onreadystatechange = onSetComputerMoveResponse;
					Service.send(createSetComputerMoveRequest());
					UIState.ServiceCallInProgress = true;
				}
				else
				{
					displayMessage(getGameOverMessage(winner));
					GameInfo.State = GameEngineConstants.InitialState;	
				}
			}
			else
			{
				displayMessage("Invalid move.");				
			}
		}
	}
}

function onSetComputerMoveResponse()
{
	require('util').debug('onSetComputerMoveResponse ' + Service.readyState + ' ' + Service.status);
	if (Service.readyState == 4)
	{
		UIState.ServiceCallInProgress = false;
		
		if (Service.status == 0) {
			require('util').debug('ERROR');
			// retry on error
			//submit SetWebMove request to web service
			Service.open("POST", Constants.ServiceURL, true);
			Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
			Service.onreadystatechange = onSetComputerMoveResponse;
			Service.send(createSetComputerMoveRequest());
			UIState.ServiceCallInProgress = true;
		} else if (Service.status == 200)
		{
			//setup own figures from web service call
			// var setComputerMoveResponseXML = Service.responseXML;
			var setComputerMoveResponseXML = serviceResponse();
			var success = getElementText(setComputerMoveResponseXML.getElementsByTagName("SetComputerMoveResult")[0]);
			var gameOver = getElementText(setComputerMoveResponseXML.getElementsByTagName("gameOver")[0]);
			var winner = getElementText(setComputerMoveResponseXML.getElementsByTagName("winner")[0]);
			
			if (success)
			{
				//get move information from response xml
				UIState.SelectedMove2.StartRow = parseInt(getElementText(setComputerMoveResponseXML.getElementsByTagName("StartRow")[0]));
				UIState.SelectedMove2.StartCol = parseInt(getElementText(setComputerMoveResponseXML.getElementsByTagName("StartCol")[0]));
				UIState.SelectedMove2.EndRow = parseInt(getElementText(setComputerMoveResponseXML.getElementsByTagName("EndRow")[0]));
				UIState.SelectedMove2.EndCol = parseInt(getElementText(setComputerMoveResponseXML.getElementsByTagName("EndCol")[0]));
				var attackerType = getElementText(setComputerMoveResponseXML.getElementsByTagName("ComputerPlayerType")[0]);
				var defenderType = getElementText(setComputerMoveResponseXML.getElementsByTagName("WebPlayerType")[0]);
				var attackerRemains = parseBool(getElementText(setComputerMoveResponseXML.getElementsByTagName("ComputerPlayerRemains")[0]));
				var defenderRemains = parseBool(getElementText(setComputerMoveResponseXML.getElementsByTagName("WebPlayerRemains")[0]));

				var collision = getElementText(setComputerMoveResponseXML.getElementsByTagName("Collision")[0]);

				var line = UIState.SelectedMove2.StartCol + " " + UIState.SelectedMove2.StartRow;
				var y = UIState.SelectedMove2.EndRow -
					UIState.SelectedMove2.StartRow;
				var x = UIState.SelectedMove2.EndCol -
					UIState.SelectedMove2.StartCol;
				if (y > 0)
					line += " DOWN";
				else if (y < 0)
					line += " UP";
				else if (x > 0)
					line += " RIGHT";
				else if (x < 0)
					line += " LEFT";

				if (y != 0 && 1 != Math.abs(y))
					line += " " + Math.abs(y);
				else if (x != 0 && 1 != Math.abs(x))
					line += " " + Math.abs(x);

				if (collision == "true") {
					if (attackerRemains)
						line += " KILLS ";
					else if (defenderRemains)
						line += " DIES ";
					else
						line += " BOTHDIE ";
					line += type2rank(attackerType) + " " + type2rank(defenderType);
				} else
					line += " OK";
require('util').debug(line);
console.log(line);
				
				//apply move to figure matrix
				applyMove(UIState.SelectedMove2.StartRow,
					UIState.SelectedMove2.StartCol,
					UIState.SelectedMove2.EndRow,
					UIState.SelectedMove2.EndCol,
					attackerType,
					defenderType,
					attackerRemains,
					defenderRemains);
					
				//set end field as display field
				if ("None" != defenderType  &&  attackerRemains)
				{
					UIState.DisplayMove2EndFigure = true;
				}
					
				//update the board
				updateBoard();
printBoard();
				displayCapturedFiguresMessage();
				
				//check if game over
				if (parseBool(gameOver))
				{
					displayMessage(getGameOverMessage(winner));
					GameInfo.State = GameEngineConstants.InitialState;
				}
			}
		}
	}
}



///////////////////////////////////////////////////////////////////////////////////////
//
// helper functions
//
///////////////////////////////////////////////////////////////////////////////////////

function initializeConstants()
{
	Constants = new Object();
	
	Constants.ServiceURL = ServiceUrl;
	
	Constants.FieldSizeX = 38;
	Constants.FieldSizeY = 38;
	Constants.FieldOriginX = 10;
	Constants.FieldOriginY = 10;
}

function initializeGameUI()
{
	UIState = new Object();
	UIState.SelectedField = new Object();
	UIState.SelectedField2 = new Object();
	UIState.SelectedMove = new Object();
	UIState.SelectedMove2 = new Object();
	
	UIState.DisplayMoveEndFigure = false;
	UIState.DisplayMove2EndFigure = false;

	UIState.ServiceCallInProgress = false;
	
	UIState.MobileMode = false;
	UIState.InvertedRanks = false;
	UIState.GameAids = true;
	
	if (isMobileBrowser())
	{
		document.body.removeChild(document.getElementById("titleCanvas"));
	}
}

function isMobileBrowser()
{
//	if (/iphone|ipad|ipod|android|blackberry|mini|windows\sce|palm/i.test(navigator.userAgent.toLowerCase()))
//	{
//		return true;
//	}
//	else
//	{
		return false;
//	}
}

function setupFieldImages()
{
	//add images for figures
	for (var row = 0; row < 10; row++)
	{
		for (var col = 0; col < 10; col++)
		{
			if ((row != 4  &&  row != 5 )  ||  (col != 2  &&  col != 3  &&  col != 6  &&  col != 7))
			{
				//figure image
				var img = new Image();
				img.id = "fieldImage" + row + col;
				img.style.position = "absolute";
				img.style.top = (9 - row) * Constants.FieldSizeY + Constants.FieldOriginY;
				img.style.left = col * Constants.FieldSizeX + Constants.FieldOriginX;
				img.style.zIndex = 1;
				img.style.visibility = "hidden";
				
				document.getElementById("boardCanvas").appendChild(img);
				
				//figure status image
				var img2 = new Image();
				img2.id = "fieldStatusImage" + row + col;
				img2.style.position = "absolute";
				img2.style.top = (9 - row) * Constants.FieldSizeY + Constants.FieldOriginY;
				img2.style.left = col * Constants.FieldSizeX + Constants.FieldOriginX;
				img2.style.zIndex = 2;
				img2.style.visibility = "hidden";
				
				document.getElementById("boardCanvas").appendChild(img2);
			}
		}
	}
	
	//add image for selected field
	var img = new Image();
	img.id = "selectedFieldImage";
	img.src = "selectedfield.gif";
	img.style.position = "absolute";
	img.style.top = 0;
	img.style.left = 0;
	img.style.zIndex = 2;
	img.style.visibility = "hidden";
	
	document.getElementById("boardCanvas").appendChild(img);
	
	//add image for selected field
	var img2 = new Image();
	img2.id = "selectedField2Image";
	img2.src = "selectedfield.gif";
	img2.style.position = "absolute";
	img2.style.top = 0;
	img2.style.left = 0;
	img2.style.zIndex = 2;
	img2.style.visibility = "hidden";
	
	document.getElementById("boardCanvas").appendChild(img2);
}

function showSettingsCanvas(show)
{
	
}

function sendSetupRequest()
{
	//hide selected field
	selectField(false);
	
	//request service access privileges
	try
	{
		if (navigator.appName == "Netscape")
		{
			netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
		}
	}
	catch(exception)
	{
	}
	
	//send GenerateSetup request to service
	Service.open("POST", Constants.ServiceURL, true);
	Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
	Service.onreadystatechange = onGenerateSetupResponse;
	Service.send(createGenerateSetupRequest());
	
	UIState.ServiceCallInProgress = true;
}

function updateBoard()
{
/*
	//update images from field matrix
	for (var row = 0; row < 10; row++)
	{
		for (var col = 0; col < 10; col++)
		{
			if ((row != 4  &&  row != 5 )  ||  (col != 2  &&  col != 3  &&  col != 6  &&  col != 7))
			{
				var figureImageId = "fieldImage" + row + col;
				var figureImage = document.getElementById(figureImageId);
				
				var statusImageId = "fieldStatusImage" + row + col;
				var statusImage = document.getElementById(statusImageId);
				
				if (null != figureImage  &&  null != statusImage)
				{
					var figure = GameInfo.FigureMatrix[row][col];
	
					if (null != figure)
					{
						//set figure image
						if (1 == figure.Player || UIState.GameAids || isDisplayField(row, col))
						{
							figureImage.src = getImageFileName(figure.Player, figure.Type);
						}
						else
						{
							figureImage.src = getImageFileName(figure.Player, "Unknown");
						}
						
						figureImage.style.visibility = "visible";
						
						//set status image
						if (UIState.GameAids)
						{
							var statusImageFileName = getStatusImageFileName(figure.Status);
							
							if (null != statusImageFileName)
							{
								statusImage.src = statusImageFileName;
								statusImage.style.visibility = "visible";
							}
							else
							{
								statusImage.style.visibility = "hidden";
							}
						}
						else
						{
							statusImage.style.visibility = "hidden";
						}
					}
					else
					{
						figureImage.style.visibility = "hidden";
						statusImage.style.visibility = "hidden";
					}
				}
			}
		}
	}
*/
}

function selectField(select, row, col)
{	
	// var selectedFieldImage = document.getElementById("selectedFieldImage");

	if (select)
	{
		UIState.SelectedField.Row = row;
		UIState.SelectedField.Col = col;
		
		// selectedFieldImage.style.left = UIState.SelectedField.Col * Constants.FieldSizeX + Constants.FieldOriginX;
		// selectedFieldImage.style.top = (9 - UIState.SelectedField.Row) * Constants.FieldSizeY + Constants.FieldOriginY;
	
		// selectedFieldImage.style.visibility = "visible";
	}
	else
	{
		UIState.SelectedField.Row = null;
		UIState.SelectedField.Col = null;
		
		// selectedFieldImage.style.visibility = "hidden";
	}
}

function isFieldSelected()
{
	if (null != UIState.SelectedField.Row  &&  null != UIState.SelectedField.Col)
	{
		return true;
	}
	else
	{
		return false;
	}
}

function selectField2(select, row, col)
{	
	// var selectedField2Image = document.getElementById("selectedField2Image");

	if (select)
	{
		UIState.SelectedField2.Row = row;
		UIState.SelectedField2.Col = col;
		
		// selectedField2Image.style.left = UIState.SelectedField2.Col * Constants.FieldSizeX + Constants.FieldOriginX;
		// selectedField2Image.style.top = (9 - UIState.SelectedField2.Row) * Constants.FieldSizeY + Constants.FieldOriginY;
	
		// selectedField2Image.style.visibility = "visible";
	}
	else
	{
		UIState.SelectedField2.Row = null;
		UIState.SelectedField2.Col = null;
		
		// selectedField2Image.style.visibility = "hidden";
	}
}

function isField2Selected()
{
	if (null != UIState.SelectedField2.Row  &&  null != UIState.SelectedField2.Col)
	{
		return true;
	}
	else
	{
		return false;
	}
}

function isDisplayField(row, col)
{
	var isDisplay = false;
	
	if ((UIState.DisplayMoveEndFigure  &&  row == UIState.SelectedMove.EndRow  &&  col == UIState.SelectedMove.EndCol)  ||
		(UIState.DisplayMove2EndFigure  &&  row == UIState.SelectedMove2.EndRow  &&  col == UIState.SelectedMove.EndCol))
	{
		isDisplay = true;
	}
	
	return isDisplay
}

function clearDisplayFields(display, row, col)
{
	UIState.DisplayMoveEndFigure = false;
	UIState.DisplayMove2EndFigure = false;	
}

function swapFigures(row1, col1, row2, col2)
{
	//swap selected setup figures
	var tempFigure = GameInfo.FigureMatrix[row2][col2];
	GameInfo.FigureMatrix[row2][col2] = GameInfo.FigureMatrix[row1][col1];
	GameInfo.FigureMatrix[row1][col1] = tempFigure;
	
	//update the board
	updateBoard();	
}

var lastMove;
function makeMove(line)
{
	lastMove = line;
	var res = line.split(" "); 
	var x1 = parseInt(res[0]);
	var y1 = parseInt(res[1]);
	var x2 = x1;
	var y2 = y1;
	var mult = 1;
	if (typeof res[3] != "undefined") 
		mult = parseInt(res[3]);

	switch (res[2]) {
		case "DOWN" :
			y2 += 1 * mult;
			break;
		case "UP" :
			y2 -= 1 * mult;
			break;
		case "LEFT" :
			x2 -= 1 * mult;
			break;
		case "RIGHT" :
			x2 += 1 * mult;
			break;
		default:
			console.log("ILLEGAL MOVE");
			break;
	}
	if (!(x1 == x2 && y1 == y2))
		setMove(y1, x1, y2, x2);
}
function setMove(startRow, startCol, endRow, endCol)
{
require('util').debug("setMove " + startRow + " " + startCol + " " + endRow + " " + endCol + " " + type2rank(GameInfo.FigureMatrix[startRow][startCol].Type));
	if (endRow != startRow || endCol != startCol)
	{
		//validate move selection
		var validSelection = false;
		
		if (1 == Math.abs(endRow - startRow) + Math.abs(endCol - startCol))
		{
			//selection is 1 step away
			validSelection = true;
		}
		else if ("Two" == GameInfo.FigureMatrix[startRow][startCol].Type &&
				(endRow == startRow || endCol == startCol))
		{
			//selection is type 2 move
			validSelection = true;
		}
		
		if (validSelection)
		{
			//clear display fields
			clearDisplayFields();
			
			//save move selection
			UIState.SelectedMove.StartRow = startRow;
			UIState.SelectedMove.StartCol = startCol;
			UIState.SelectedMove.EndRow = endRow;
			UIState.SelectedMove.EndCol = endCol;
			
			//request service access privileges
			try
			{
				if (navigator.appName == "Netscape")
				{
					netscape.security.PrivilegeManager.enablePrivilege("UniversalBrowserRead");
				}
			} 
			catch (exception)
			{
			}
			
			//submit SetWebMove request to web service
			Service.open("POST", Constants.ServiceURL, true);
			Service.setRequestHeader("Content-Type", "text/xml;charset=utf-8");
			Service.onreadystatechange = onSetWebMoveResponse;
			Service.send(createSetWebMoveRequest(UIState.SelectedMove.StartRow, UIState.SelectedMove.StartCol, UIState.SelectedMove.EndRow, UIState.SelectedMove.EndCol, GameInfo.FigureMatrix[UIState.SelectedMove.StartRow][UIState.SelectedMove.StartCol].Type));
			
			UIState.ServiceCallInProgress = true;
		}
	}
	else
	{
		selectField(false);
	}
}

function displaySelectedRowCol(display, row, col)
{
	var selectedRowImage = document.getElementById("selectedRowImage");
	var selectedColImage = document.getElementById("selectedColImage");
	
	if (display)
	{
		selectedRowImage.style.left = Constants.FieldOriginX ;
		selectedRowImage.style.top = (9 - row) * Constants.FieldSizeY + Constants.FieldOriginY;
		selectedRowImage.style.visibility = "visible";
		
		selectedColImage.style.left = col * Constants.FieldSizeX + Constants.FieldOriginX ;;
		selectedColImage.style.top = Constants.FieldOriginY;
		selectedColImage.style.visibility = "visible";
	}
	else
	{
		selectedRowImage.style.visibility = "hidden";
		selectedColImage.style.visibility = "hidden";
	}
}

function fillStandingsTable()
{
	fillStandingsRow(0, "Ten");
	fillStandingsRow(1, "Nine");
	fillStandingsRow(2, "Eight");
	fillStandingsRow(3, "Seven");
	fillStandingsRow(4, "Six");
	fillStandingsRow(5, "Five");
	fillStandingsRow(6, "Four");
	fillStandingsRow(7, "Three");
	fillStandingsRow(8, "Two");
	fillStandingsRow(9, "One");
	fillStandingsRow(10, "Bomb");
}

function fillStandingsRow(row, type)
{
	var standingsTable = document.getElementById("standingsTable");
	
	//fill left side of the table row
	standingsTable.rows[row].cells[0].firstChild.src = getImageFileName(1, type);
	
	if (0 < GameInfo.FigureCounter1[type])
	{
		standingsTable.rows[row].cells[1].innerHTML = GameInfo.FigureCounter1[type];
	}
	else
	{
		standingsTable.rows[row].cells[1].innerHTML = "-";
	}
	
	//fill right side of the table row
	standingsTable.rows[row].cells[3].firstChild.src = getImageFileName(2, type);
	
	if (0 < GameInfo.FigureCounter2[type])
	{
		standingsTable.rows[row].cells[2].innerHTML = GameInfo.FigureCounter2[type];
	}
	else
	{
		standingsTable.rows[row].cells[2].innerHTML = "-";
	}
}

function displayCapturedFiguresMessage()
{
	if (0 == GameInfo.CurrentCapturedFigureCount)
	{
		return;
	}
	
	displayMessage("Captured: ");
	
	for (var capturedIndex = 0; capturedIndex < GameInfo.CurrentCapturedFigureCount; capturedIndex++)
	{
		var figure = GameInfo.CurrentCapturedFigures[capturedIndex];
		
		// var figureImage = new Image();
		// figureImage.src = getImageFileName(figure.Player, figure.Type);
		// figureImage.style.width = 27;
		// figureImage.style.height = 27;
		
		// document.getElementById("messageImageCell").appendChild(figureImage);
	}
}

function isValidBoardField(row, col)
{
	var valid = true;
	
	//check for outer boundaries
	if (0 > row  ||  9 < row  ||  0 > col  ||  9 < col)
	{
		valid = false;
	}
	
	//check for lakes
	if (valid)
	{
		if ((4 == row || 5 == row) && (2 == col || 3 == col || 6 == col || 7 == col))
		{
			valid = false;
		}
	}
	
	return valid;
}

function isValidGameFieldSelection(row, col)
{
	var valid = false;	
	var figure = GameInfo.FigureMatrix[row][col];
	
	//check
	if (figure != null  &&
		figure.Player == 1  &&
		figure.Type != "Flag"  &&
		figure.Type != "Bomb")
	{
		if (isValidBoardField(row - 1, col))
		{
			var bottomFigure = GameInfo.FigureMatrix[row - 1][col];
			
			if (null == bottomFigure  ||  1 != bottomFigure.Player)
			{
				valid = true;
			}
		}
		
		if (isValidBoardField(row, col - 1))
		{
			var leftFigure = GameInfo.FigureMatrix[row][col - 1];
			
			if (null == leftFigure  ||  1 != leftFigure.Player)
			{
				valid = true;
			}
		}
		
		if (isValidBoardField(row, col + 1))
		{
			var rightFigure = GameInfo.FigureMatrix[row][col + 1];
			
			if (null == rightFigure  ||  1 != rightFigure.Player)
			{
				valid = true;
			}
		}
		
		if (isValidBoardField(row + 1, col))
		{
			var topFigure = GameInfo.FigureMatrix[row + 1][col];
			
			if (null == topFigure  ||  1 != topFigure.Player)
			{
				valid = true;
			}
		}
	}
	
	return valid;
}

function isValidGameField2Selection(row, col)
{
	var valid = true;
	
	//check if it is the same as the first selected field
	if (row == UIState.SelectedField.Row  &&  col == UIState.SelectedField.Col)
	{
		valid = false;
	}
	
	//check if another own figure is on the field
	if (valid)
	{
		var figure = GameInfo.FigureMatrix[row][col];
		
		if (null != figure && figure.Player == 1)
		{
			valid = false;
		}
	}
	
	//check distance
	if (valid)
	{
		var moveDistance  = Math.abs(row - UIState.SelectedField.Row) + Math.abs(col - UIState.SelectedField.Col);
		
		if (1 < moveDistance)
		{
			var movingFigure = GameInfo.FigureMatrix[UIState.SelectedField.Row][UIState.SelectedField.Col];
			
			if (null != movingFigure  &&  "Two" == movingFigure.Type)
			{
				if (row == UIState.SelectedField.Row  ||  col == UIState.SelectedField.Col)
				{
					//check if all field in between are valid and
				}
				else
				{
					valid = false;
				}
			}
			else
			{
				valid = false;
			}
		}
	}
	
	return valid;
}

function getImageFileName(player, type)
{
	var image;

	if (1 == player  &&  !UIState.InvertedRanks)
	{
		switch (type)
		{
			case "One":
				image = "figure_1_1.gif";
				break;
			case "Two":
				image = "figure_1_2.gif";
				break;
			case "Three":
				image = "figure_1_3.gif";
				break;
			case "Four":
				image = "figure_1_4.gif";
				break;
			case "Five":
				image = "figure_1_5.gif";
				break;
			case "Six":
				image = "figure_1_6.gif";
				break;
			case "Seven":
				image = "figure_1_7.gif";
				break;
			case "Eight":
				image = "figure_1_8.gif";
				break;
			case "Nine":
				image = "figure_1_9.gif";
				break;
			case "Ten":
				image = "figure_1_10.gif";
				break;
			case "Bomb":
				image = "figure_1_B.gif";
				break;
			case "Flag":
				image = "figure_1_F.gif";
				break;
			case "Unknown":
				image = "figure_1_U.gif";
				break;
		}
	}
	else if (1 == player  &&  UIState.InvertedRanks)
	{
		switch (type)
		{
			case "One":	
				image = "figure_1_S.gif";
				break;
			case "Two":
				image = "figure_1_9.gif";
				break;
			case "Three":
				image = "figure_1_8.gif";
				break;
			case "Four":
				image = "figure_1_7.gif";
				break;
			case "Five":
				image = "figure_1_6.gif";
				break;
			case "Six":
				image = "figure_1_5.gif";
				break;
			case "Seven":
				image = "figure_1_4.gif";
				break;
			case "Eight":
				image = "figure_1_3.gif";
				break;
			case "Nine":
				image = "figure_1_2.gif";
				break;
			case "Ten":
				image = "figure_1_1.gif";
				break;
			case "Bomb":
				image = "figure_1_B.gif";
				break;
			case "Flag":
				image = "figure_1_F.gif";
				break;
			case "Unknown":
				image = "figure_1_U.gif";
				break;
		}
	}
	else if (2 == player  &&  !UIState.InvertedRanks)
	{
		switch (type)
		{
			case "One":
				image = "figure_2_1.gif";
				break;
			case "Two":
				image = "figure_2_2.gif";
				break;
			case "Three":
				image = "figure_2_3.gif";
				break;
			case "Four":
				image = "figure_2_4.gif";
				break;
			case "Five":
				image = "figure_2_5.gif";
				break;
			case "Six":
				image = "figure_2_6.gif";
				break;
			case "Seven":
				image = "figure_2_7.gif";
				break;
			case "Eight":
				image = "figure_2_8.gif";
				break;
			case "Nine":
				image = "figure_2_9.gif";
				break;
			case "Ten":
				image = "figure_2_10.gif";
				break;
			case "Bomb":
				image = "figure_2_B.gif";
				break;
			case "Flag":
				image = "figure_2_F.gif";
				break;
			case "Unknown":
				image = "figure_2_U.gif";
				break;
		}
	}
	else if (2 == player  &&  UIState.InvertedRanks)
	{
		switch (type)
		{
			case "One":
				image = "figure_2_S.gif";
				break;
			case "Two":
				image = "figure_2_9.gif";
				break;
			case "Three":
				image = "figure_2_8.gif";
				break;
			case "Four":
				image = "figure_2_7.gif";
				break;
			case "Five":
				image = "figure_2_6.gif";
				break;
			case "Six":
				image = "figure_2_5.gif";
				break;
			case "Seven":
				image = "figure_2_4.gif";
				break;
			case "Eight":
				image = "figure_2_3.gif";
				break;
			case "Nine":
				image = "figure_2_2.gif";
				break;
			case "Ten":
				image = "figure_2_1.gif";
				break;
			case "Bomb":
				image = "figure_2_B.gif";
				break;
			case "Flag":
				image = "figure_2_F.gif";
				break;
			case "Unknown":
				image = "figure_2_U.gif";
				break;
		}
	}

	return image;
}

function getGameOverMessage(winner)
{
	var gameOverMessage = "Game Over!";
	
	if (winner == "WebPlayer")
	{
		gameOverMessage = "Game Over - You win!";
	}
	else if (winner == "ComputerPlayer")
	{
		gameOverMessage = "Game Over - You lose!";
	}
	else if (winner == "Tied")
	{
		gameOverMessage = "Game Over - The game is a tie.";
	}
	
	return gameOverMessage;
}

function getStatusImageFileName(status)
{
	var statusImageFileName = null;
	
	if (GameEngineConstants.DetectedStatus == status)
	{
		statusImageFileName = "detectedsymbol.gif";
	}
	else if (GameEngineConstants.MovedStatus == status)
	{
		statusImageFileName = "movedsymbol.gif";
	}
	
	return statusImageFileName;
}

function getEventLocationX(eventInfo)
{
	var locationX = 0;
	
	if (!eventInfo)
	{
		var eventInfo = window.event;
	}
	
	if (eventInfo.pageX)
	{
		locationX = eventInfo.pageX  - document.getElementById("boardCanvas").offsetLeft
			- document.getElementById("controlsCanvas").offsetLeft
			- document.getElementById("frameCanvas").offsetLeft;
	}
	else if (eventInfo.clientX)
	{
		locationX = eventInfo.clientX
			- document.getElementById("boardCanvas").offsetLeft
			- document.getElementById("controlsCanvas").offsetLeft
			- document.getElementById("frameCanvas").offsetLeft
			+ document.body.scrollLeft
			+ document.documentElement.scrollLeft;
	}
	
	return locationX; 
}

function getEventLocationY(eventInfo)
{
	var locationY;
	
	if (!eventInfo)
	{
		var eventInfo = window.event;
	}
	
	if (eventInfo.pageY)
	{
		locationY = eventInfo.pageY - document.getElementById("boardCanvas").offsetTop
			- document.getElementById("controlsCanvas").offsetTop
			- document.getElementById("frameCanvas").offsetTop;
	}
	else if (eventInfo.clientY)
	{
		locationY = eventInfo.clientY
			- document.getElementById("boardCanvas").offsetTop
			- document.getElementById("controlsCanvas").offsetTop
			- document.getElementById("frameCanvas").offsetTop
			+ document.body.scrollTop
			+ document.documentElement.scrollTop;
	}
	
	return locationY;
}

function parseBool(stringVal)
{
	var lowerCaseStringVal = stringVal.toLowerCase();
	
	var boolVal = true;
	
	switch (lowerCaseStringVal)
	{
		case "false":
		case "0":
			boolVal = false;
			break;
			
	}
	
	return boolVal;
}

function getElementText(xmlElement)
{
	if (navigator.appName == "Netscape")
	{
		return xmlElement.textContent;
	}
	else
	{
		return xmlElement.text;
	}
}

function displayMessage(messageText)
{
	require('util').debug("displayMessage:" + messageText);
/*
	document.getElementById("messageTextCell").innerHTML = messageText;
	document.getElementById("messageImageCell").innerHTML = "";
*/
}



///////////////////////////////////////////////////////////////////////////////////////
//
// global variables
//
///////////////////////////////////////////////////////////////////////////////////////

var UIState;
var Constants;
