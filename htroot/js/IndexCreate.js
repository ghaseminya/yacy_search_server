/*
* @licstart  The following is the entire license notice for the 
* JavaScript code in this file.
* 
* Copyright (C) 2006 - 2014 Alexander Schier, Martin Thelian, Michael Peter Christen,
* Florian Richter, Stefan Förster, David Wieditz
* 
*         
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <http://www.gnu.org/licenses/>.
* 
* @licend  The above is the entire license notice
* for the JavaScript code in this file.
*/

var AJAX_OFF="env/grafics/empty.gif";
var AJAX_ON="env/grafics/ajax.gif";
var timeout="";

function handleResponse(){
    if (http.readyState == 4){
        var response = http.responseXML;

		// get the document title
        var doctitle="";		
        if (response.getElementsByTagName("title")[0].firstChild!=null){
	        doctitle=response.getElementsByTagName("title")[0].firstChild.nodeValue;
	    }
		//document.getElementById("title").innerHTML=doctitle;
		document.getElementById("bookmarkTitle").value=doctitle;
		
		// determine if crawling is allowed by the robots.txt
        var docrobotsOK="";		
        if(response.getElementsByTagName("robots")[0].firstChild!=null){
	        docrobotsOK=response.getElementsByTagName("robots")[0].firstChild.nodeValue;
	    }
        var robotsOKspan=document.getElementById("robotsOK");
        if(robotsOKspan.firstChild){
	        robotsOKspan.removeChild(robotsOKspan.firstChild);
        }
        if (docrobotsOK==1){
        	var img=document.createElement("img");
        	img.setAttribute("src", "env/grafics/ok.png");
        	img.setAttribute("width", "32px");
        	img.setAttribute("height", "32px");
			img.setAttribute("alt", "robots.txt - OK");
        	robotsOKspan.appendChild(img);
        } else if(docrobotsOK==0){
			var img=document.createElement("img");
        	img.setAttribute("src", "env/grafics/bad.png");
        	img.setAttribute("width", "32px");
        	img.setAttribute("height", "32px");
			img.setAttribute("alt", "robots.txt - Bad");
        	robotsOKspan.appendChild(img);
        	// robotsOKspan.appendChild(img);
        } else {
	        robotsOKspan.appendChild(document.createTextNode(""));
	        document.getElementById("robotsOK").innerHTML="";
        }		
		
		// get the sitemap URL contained in the robots.txt
		if (document.getElementsByName("sitemapURL").length > 0) {
			var sitemap="";
			// there can be zero, one or many sitemaps
			var sitemapElement = response.getElementsByTagName("sitemap");
	        if (sitemapElement != null && sitemapElement.length > 0 && sitemapElement[0].firstChild != null) {
	        	// if there are several, we take only the first
		        sitemap = sitemapElement[0].firstChild.nodeValue;
		    }
			document.getElementsByName("sitemapURL")[0].value = sitemap;
			if (sitemap) document.getElementById("sitemap").disabled = false;
		}
		var sitelist="";		
	    if (response.getElementsByTagName("sitelist")[0].firstChild!=null){
	    	sitelist=response.getElementsByTagName("sitelist")[0].firstChild.nodeValue;
		}
		document.getElementById("sitelistURLs").innerHTML = sitelist;
		var expandButton = document.getElementById("expandSiteListBtn");
		var siteListRadio = document.getElementById("sitelist");
		if (sitelist) {
			siteListRadio.disabled = false;
			var hasMoreLinksElement = response.getElementsByTagName("hasMoreLinks");
			if(hasMoreLinksElement != null && hasMoreLinksElement.length > 0 
					&& hasMoreLinksElement[0].firstChild != null && hasMoreLinksElement[0].firstChild.nodeValue == "true") {
				expandButton.style.visibility = "visible";
				expandButton.disabled = false;
			} else {
				expandButton.style.visibility = "hidden";
			}
		} else {
			siteListRadio.disabled = true;
			siteListRadio.checked = false;
			var urlModeRadio = document.getElementById("url");
			if(urlModeRadio != null) {
				urlModeRadio.checked = true;	
			}
			if(expandButton != null) {
				expandButton.style.visibility = "hidden";
			}
		}
        
		// clear the ajax image
		document.getElementById("ajax").setAttribute("src", AJAX_OFF);
    }
}

function changed() {
	window.clearTimeout(timeout);
	timeout=window.setTimeout(loadInfos, 1500);
}

/**
 * @param loadAll {Boolean} when true, load all links, else limit to the 100 first
 */
function loadInfos(loadAll) {
	// displaying ajax image
	document.getElementById("ajax").setAttribute("src",AJAX_ON);	
	
	var url=document.getElementById("crawlingURL").value;
	if (url.indexOf("ftp") == 0 || url.indexOf("smb") == 0) document.getElementById("crawlingQ").checked = true; // since the pdf parser update for page separation, we need to set this
	sndReq('api/getpageinfo_p.xml?actions=title,robots' + (loadAll ? '' : '&maxLinks=50') + '&url='+url);
	document.getElementById("api").innerHTML = "<a href='api/getpageinfo_p.xml?actions=title,robots&url=" + url + "' id='apilink'><img src='env/grafics/api.png' width='60' height='40' alt='API'/></a><span>See the page info about the start url.</span>";
}
