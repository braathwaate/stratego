
function createHttpClient()
{
	var httpClient = null;
	
	if (navigator.appName == "Netscape")
	{
		httpClient = new XMLHttpRequest();
	}

	if (null == httpClient)
	{
		httpClient = new ActiveXObject("Msxml2.XMLHTTP");
	}
	
	if (null == httpClient)
	{
		httpClient = new ActiveXObject("Microsoft.XMLHTTP");
	}

	if (null == httpClient)
	{
		alert("No web service suport!");
	}	
	
	return httpClient;
}

function isMobileBrowser()
{
	if (/iphone|ipad|ipod|android|blackberry|mini|windows\sce|palm/i.test(navigator.userAgent.toLowerCase()))
	{
		return true;
	}
	else
	{
		return false;
	}
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

function setHtmlElementText(id, text)
{
	var htmlElement = document.getElementById(id);	
	htmlElement.innerHTML = text;
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