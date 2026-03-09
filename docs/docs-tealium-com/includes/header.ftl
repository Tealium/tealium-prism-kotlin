<#import "source_set_selector.ftl" as source_set_selector>
<#import "tealium_logo.ftl" as teal_logo>

<#macro display>
  <nav id="navigation-wrapper" class="navigation theme-dark w-100 sticky-t bg-primary-color-light bb b--light-gray dn-p" role="banner">
    <div class="flex flex-wrap items-center justify-start mw9">
      <div class="lh-solid ml0-ns mr0 mr4-l mv3 pl15 dib db-ns relative" style="height: 40px;">
        <a href="/"><@teal_logo.display/></a>
      </div>
    </div>
    <div class="navigation-controls pa3">
      <@source_set_selector.display/>
      <div class="navigation-controls--btn navigation-controls--btn_search" id="searchBar" role="button">Search Kotlin docs</div>
    </div>
  </nav>
</#macro>