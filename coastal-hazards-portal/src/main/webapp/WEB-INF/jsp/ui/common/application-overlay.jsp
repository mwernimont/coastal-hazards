<%
	Boolean development = Boolean.parseBoolean(request.getParameter("debug-qualifier"));
	String referer = request.getParameter("original-referer");
	String baseUrl = request.getParameter("base-url");
	String version = request.getParameter("version");
	Boolean isOnSiteRequest = referer.contains(baseUrl);
	
%>

<link type="text/css" rel="stylesheet" href="<%=baseUrl%>/css/overlay/application-overlay<%= development ? "" :"-" + version + "-min"%>.css" property="stylesheet" />

<div id="application-overlay">
	<div id="application-initial-load-splash" class="<%= isOnSiteRequest ? "hidden" : ""%>">
		<div class="application-overlay-content-wrap">
			<div id="application-overlay-image-container">
				<img src="<%=request.getParameter("base-url")%>/images/splash/usgs.svg" alt="United States Geological Survey"/>
			</div>
			<div id="application-overlay-description-container">
				<div id="application-overlay-description-container-title">Coastal Change Hazards Portal</div>
				<div id="application-overlay-description-container-version">Version <%=request.getParameter("version")%></div>
				<div class="application-overlay-description-container-spinner-container">
					<img class="splash-spinner img-responsive" src="<%=baseUrl%>/images/spinner/spinner3.gif" alt="Spinner Image"/><span class="splash-status-update"></span>
				</div>
			</div>
		</div>
	</div>
	<div id="application-subsequent-load-splash" class="<%= isOnSiteRequest ? "" : "hidden"%>">
		<div class="application-overlay-content-wrap">
			<div class="application-overlay-description-container-spinner-container">
				<img class="splash-spinner img-responsive" src="<%=baseUrl%>/images/spinner/spinner3.gif" alt="Spinner Image"/>
				<span class="splash-status-update"></span>
			</div>
		</div>
	</div>
</div>

<script type="text/javascript">
	window.CCH = (window.CCH === undefined ? {} : window.CCH);
	
    window.CCH.splashUpdate = function(message) {
		if('function' === typeof message.html){
			message = message.html();
		}
        $('.splash-status-update').html(message);
    };
	window.CCH.splashAppend = function(elements) {
		$('.splash-status-update').append(elements);
	};
	CCH.splashUpdate("Working...");
</script>