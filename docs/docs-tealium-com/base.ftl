<#import "includes/page_metadata.ftl" as page_metadata>
<#import "includes/header.ftl" as header>
<#import "includes/footer.ftl" as footer>
<!DOCTYPE html>
<html class="no-js" lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1" charset="UTF-8">
    <link rel="stylesheet" href="https://use.typekit.net/ilp4lxb.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Montserrat:ital,wght@0,100..900;1,100..900&display=swap" rel="stylesheet">

    <@page_metadata.display/>
    <@template_cmd name="pathToRoot">
        <script>var pathToRoot = "${pathToRoot}";</script></@template_cmd>
    <script>document.documentElement.classList.replace("no-js", "js");</script>
    <#-- This script doesn't need to be there but it is nice to have
    since app in dark mode doesn't 'blink' (class is added before it is rendered) -->
    <script>const storage = localStorage.getItem("dokka-dark-mode")
      if (storage == null) {
        const osDarkSchemePreferred = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
        if (osDarkSchemePreferred === true) {
          document.getElementsByTagName("html")[0].classList.add("theme-dark")
        }
      } else {
        const savedDarkMode = JSON.parse(storage)
        if (savedDarkMode === true) {
          document.getElementsByTagName("html")[0].classList.add("theme-dark")
        }
      }
    </script>
    <#-- Resources (scripts, stylesheets) are handled by Dokka.
    Use customStyleSheets and customAssets to change them. -->
    <@resources/>
    
    <link href="/dist/css/app.css" rel='stylesheet' type="text/css" />
</head>
<body class="ma0 sans-serif">
<script>
var utag_data = {
    tealium_event : "page_view"
}
</script>

<script>
  (function(a,b,c,d){
    a="//tags.tiqcdn.com/utag/tealium/docs/qa/utag.js";
    b=document;c='script';d=b.createElement(c);d.src=a;d.type='text/java'+c;d.async=true;
    a=b.getElementsByTagName(c)[0];a.parentNode.insertBefore(d,a);
  })();
</script>
<div class="root">
    <@header.display/>
      
    <main role="main" class="content-with-sidebar pb0-ns">
      <div class="w-100 pr4-ns">
        <div class="flex">
          <div id="menu" class="order-0 w-auto max-vh-100 overflow-hidden overflow-y-auto dn sticky-l pt3" style="min-width: 280px;">
            <nav class="side-nav f6 pb6 pl125" role="navigation" id="leftColumn" class="sidebar" data-item-type="SECTION">
              <ul class="list pa0 mt0 mb1">
                <li class="w-100 fw4 f5 text-color-primary pv1 pl2"><a href="/platforms/" class="w-100 link text-color-primary hover-primary-color pv2 pl0 pr2"><img src="/images/icons/icon-arrow-left.svg" height="10" width="16" class="di v-mid pr1">Platforms</a></li>
              </ul>
              <ul class="list pa0" data-nav-section="ios-kotlin">
                <li data-level="1" class="relative w-100 fw8 f4 mv2 text-color-primary pv1 ind2">  
                  <span class="pb2">Tealium Prism Kotlin</span>
                </li>
              </ul>
              <div class="sidebar--inner" id="sideMenu"></div>
            </nav>
          </div>
          
          <div id="container">
            <article id="main-content" class="order-1 overflow-auto vh-100 max-vh-100 pt4 ph15 ph4-ns mid-gray mt0-ns w-100 pb6" style="flex:1;scroll-padding-top:96px;">
              <div class="documentation-copy ph4-ns">          
                <div class="prose nested-links" id="prose">
                  <div id="resizer" class="resizer" data-item-type="BAR"></div>
                  <div id="main" data-item-type="SECTION" role="main">
                    <@content/>
                    <@footer.display/>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </div>
    </main>

</div>
</body>
</html>