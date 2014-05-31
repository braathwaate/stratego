

///////////////////////////////////////////////////////////////////////////////////////
//
// functions
//
///////////////////////////////////////////////////////////////////////////////////////

function initializeGameInfo()
{
	//game engine constants
	GameEngineConstants = new Object();
	GameEngineConstants.InitialState = 0;
	GameEngineConstants.SetupState = 1;
	GameEngineConstants.GameState = 2;
	
	GameEngineConstants.NoneStatus = 0;
	GameEngineConstants.MovedStatus = 1;
	GameEngineConstants.DetectedStatus = 2;
	
	//create game info object
	GameInfo = new Object();

	//create figure matrix
	GameInfo.FigureMatrix = new Array();

	for (var row = 0; row < 10; row++)
	{
		GameInfo.FigureMatrix[row] = new Array();	
	}	

	//initialize game figure counters
	GameInfo.FigureCounter1 = new Object();
	GameInfo.FigureCounter2 = new Object();
	
	resetFigureCounters();

	//create collision info
	GameInfo.CurrentCapturedFigures = new Array();
	GameInfo.CurrentCapturedFigureCount = 0;

	//initialize game state
	GameInfo.State = GameEngineConstants.InitialState;
}


function initializeService()
{
	if (navigator.appName == "Netscape")
	{
		Service = new XMLHttpRequest();
	}

	if (null == Service)
	{
		Service = new ActiveXObject("Msxml2.XMLHTTP");
	}
	
	if (null == Service)
	{
		Service = new ActiveXObject("Microsoft.XMLHTTP");
	}

	if (null == Service)
	{
		alert("No web service suport!");
	}	
}

function createGenerateSetupRequest()
{
	var request = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
		"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
  		"	<soap:Body>\n" +
    		"		<GenerateSetup xmlns=\"http://www.jayoogee.com/StrategyWebGame/\"/>\n" +
  		"	</soap:Body>\n" +
		"</soap:Envelope>";

	return request;
}

function createCreateGameRequest()
{
	var webPlayerID = "MF Web Site Guest";
	
	var request = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
		"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
		"	<soap:Body>\n" +
		"		<CreateGame xmlns=\"http://www.jayoogee.com/StrategyWebGame/\">\n" +
		"			<webPlayerID>" + webPlayerID + "</webPlayerID>\n" +
		"			<computerPlayerID>Human Combat Player</computerPlayerID>\n" +
		"		</CreateGame>\n" +
		"	</soap:Body>\n" +
		"</soap:Envelope>\n";

	return request;
}

function createSetSetupRequest()
{
	var request = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
		"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
		"	<soap:Body>\n" +
		"		<SetSetup xmlns=\"http://www.jayoogee.com/StrategyWebGame/\">\n" +
      	"			<gameKey>" + GameInfo.GameKey + "</gameKey>\n" +
      	"			<figures>\n";
		
	for (var row = 0; row < 4; row ++)
	{
		for (var col = 0; col < 10; col++)
		{	
			request +=	"				<FigureInfo>\n" +
        				"  					<Type>" + GameInfo.FigureMatrix[row][col].Type + "</Type>\n" +
        				"  					<Row>" + row + "</Row>\n" +
        				"  					<Col>" + col + "</Col>\n" +
        				"				</FigureInfo>\n";
		}
	}
	
    request += "			</figures>\n" +
    	"		</SetSetup>\n" +
  		"	</soap:Body>\n" +
		"</soap:Envelope>";

	return request;
}

function createSetWebMoveRequest(startRow, startCol, endRow, endCol, type)
{
	var request = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
		"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
  		"	<soap:Body>\n" +
    	"		<SetWebMove xmlns=\"http://www.jayoogee.com/StrategyWebGame/\">\n" +
      	"			<gameKey>" + GameInfo.GameKey + "</gameKey>\n" +
      	"			<move>\n" +
        "				<StartRow>" + startRow + "</StartRow>\n" +
        "				<StartCol>" + startCol + "</StartCol>\n" +
        "				<EndRow>" + endRow + "</EndRow>\n" +
        "				<EndCol>" + endCol + "</EndCol>\n" +
        "				<Type>" + type + "</Type>\n" +
      	"			</move>\n" +
    	"		</SetWebMove>\n" +
  		"	</soap:Body>\n" +
		"</soap:Envelope>\n";
		
	return request;
}

function createSetComputerMoveRequest()
{
	var request = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
		"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
  		"	<soap:Body>\n" +
    	"		<SetComputerMove xmlns=\"http://www.jayoogee.com/StrategyWebGame/\">\n" +
      	"			<gameKey>" + GameInfo.GameKey + "</gameKey>\n" +
    	"		</SetComputerMove>\n" +
  		"	</soap:Body>\n" +
		"</soap:Envelope>";
		
	return request;
}

function applyMove(startRow, startCol, endRow, endCol, attackerType, defenderType, attackerRemains, defenderRemains)
{
	var attackerFigure = GameInfo.FigureMatrix[startRow][startCol];
	var defenderFigure = GameInfo.FigureMatrix[endRow][endCol];
	
	//update figure types
	if ("Unknown" == attackerFigure.Type)
	{
		if ("None" != attackerType  &&  "Unknown" != attackerType)
		{
			attackerFigure.Type = attackerType;
			updateFigureStatus(attackerFigure, GameEngineConstants.DetectedStatus);
		}
		else if (1 < Math.abs(endRow - startRow) + Math.abs(endCol - startCol))
		{
			attackerFigure.Type = "Two";
			updateFigureStatus(attackerFigure, GameEngineConstants.DetectedStatus);
		}
	}
	
	if (null != defenderFigure)
	{
		if ("Unknown" == defenderFigure.Type  &&  "None" != defenderType  &&  "Unknown" != defenderType)
		{
			defenderFigure.Type = defenderType;
		}
		
		updateFigureStatus(attackerFigure, GameEngineConstants.DetectedStatus);
		updateFigureStatus(defenderFigure, GameEngineConstants.DetectedStatus);
	}
	
	//move figures
	if (attackerRemains)
	{
		GameInfo.FigureMatrix[endRow][endCol] = attackerFigure;
		updateFigureStatus(attackerFigure, GameEngineConstants.MovedStatus)
	}
	else if (!defenderRemains)
	{
		GameInfo.FigureMatrix[endRow][endCol] = null;
	}
	
	GameInfo.FigureMatrix[startRow][startCol] = null;
	
	//update captured figures
	if ("None" != defenderType)
	{
		if (!attackerRemains)
		{
			addCapturedFigure(attackerFigure);
		}
		
		if (!defenderRemains)
		{
			addCapturedFigure(defenderFigure);
		}
	}
	
	//calculate counters
	updateFigureCounters(attackerFigure.Player, attackerFigure.Type, defenderType, attackerRemains, defenderRemains);
}

function resetFigureCounters()
{
	GameInfo.FigureCounter1.Flag = 1;
	GameInfo.FigureCounter1.One = 1;
	GameInfo.FigureCounter1.Two = 8;
	GameInfo.FigureCounter1.Three = 5;
	GameInfo.FigureCounter1.Four = 4;
	GameInfo.FigureCounter1.Five = 4;
	GameInfo.FigureCounter1.Six = 4;
	GameInfo.FigureCounter1.Seven = 3;
	GameInfo.FigureCounter1.Eight = 2;
	GameInfo.FigureCounter1.Nine = 1;
	GameInfo.FigureCounter1.Ten = 1;
	GameInfo.FigureCounter1.Bomb = 6;
	
	GameInfo.FigureCounter2.Flag = 1;
	GameInfo.FigureCounter2.One = 1;
	GameInfo.FigureCounter2.Two = 8;
	GameInfo.FigureCounter2.Three = 5;
	GameInfo.FigureCounter2.Four = 4;
	GameInfo.FigureCounter2.Five = 4;
	GameInfo.FigureCounter2.Six = 4;
	GameInfo.FigureCounter2.Seven = 3;
	GameInfo.FigureCounter2.Eight = 2;
	GameInfo.FigureCounter2.Nine = 1;
	GameInfo.FigureCounter2.Ten = 1;
	GameInfo.FigureCounter2.Bomb = 6;
}


function updateFigureCounters(attackerPlayer, attackerType, defenderType, attackerRemains, defenderRemains)
{
	if (attackerRemains)
	{
		if ("None" != defenderType)
		{
			if (1 == attackerPlayer)
			{
				var figureCount2 = GameInfo.FigureCounter2[defenderType];
				GameInfo.FigureCounter2[defenderType] = figureCount2 - 1;
			}
			else
			{
				var figureCount1 = GameInfo.FigureCounter1[defenderType];
				GameInfo.FigureCounter1[defenderType] = figureCount1 - 1;
			}
			
		}
	}
	else if (defenderRemains)
	{
		if (1 == attackerPlayer)
		{
			var figureCount1 = GameInfo.FigureCounter1[attackerType];
			GameInfo.FigureCounter1[attackerType] = figureCount1 - 1;
		}
		else
		{
			var figureCount2 = GameInfo.FigureCounter2[attackerType];
			GameInfo.FigureCounter2[attackerType] = figureCount2 - 1;
		}
	}
	else
	{
		if (1 == attackerPlayer)
		{
			var figureCount1 = GameInfo.FigureCounter1[attackerType];
			GameInfo.FigureCounter1[attackerType] = figureCount1 - 1;
			
			var figureCount2 = GameInfo.FigureCounter2[defenderType];
			GameInfo.FigureCounter2[defenderType] = figureCount2 - 1;
		}
		else
		{
			var figureCount1 = GameInfo.FigureCounter1[defenderType];
			GameInfo.FigureCounter1[defenderType] = figureCount1 - 1;
			
			var figureCount2 = GameInfo.FigureCounter2[attackerType];
			GameInfo.FigureCounter2[attackerType] = figureCount2 - 1;
		}
	}
}

function addCapturedFigure(figure)
{
	GameInfo.CurrentCapturedFigures[GameInfo.CurrentCapturedFigureCount] = figure;
	GameInfo.CurrentCapturedFigureCount = GameInfo.CurrentCapturedFigureCount + 1;
}

function updateFigureStatus(figure, status)
{
	if (null != figure)
	{
		if (GameEngineConstants.DetectedStatus == status)
		{
			figure.Status = GameEngineConstants.DetectedStatus;
		}
		else if (GameEngineConstants.MovedStatus == status)
		{
			if (figure.Status != GameEngineConstants.DetectedStatus)
			{
				figure.Status = GameEngineConstants.MovedStatus;
			}
		}
	}
}


///////////////////////////////////////////////////////////////////////////////////////
//
// global variables
//
///////////////////////////////////////////////////////////////////////////////////////

var GameInfo;
var Service;
var GameEngineConstants;
