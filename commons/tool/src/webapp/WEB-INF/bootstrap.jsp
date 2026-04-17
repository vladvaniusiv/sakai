<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html
      xmlns="http://www.w3.org/1999/xhtml"
      xml:lang="${isolanguage}"
      lang="${isolanguage}">
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <script>

            var commons = {
                i18n: {},
                commonsId: '${commonsId}',
                siteId: '${siteId}',
                embedder: '${embedder}',
                userId: '${userId}',
                isUserSite: ${isUserSite},
                postId: '${postId}',
                maxUploadSize: '${maxUploadSize}'
            };

        </script>
        ${sakaiHtmlHead}
    </head>
    <body>

        <script>includeLatestJQuery("commons");</script>
        <script>includeWebjarLibrary("handlebars");</script>
        <script src="/commons-tool/templates/templates.js${portalCDNQuery}"></script>
        <script src="/commons-tool/lib/autosize.min.js${portalCDNQuery}"></script>
        <script src="/commons-tool/js/commons_utils.js${portalCDNQuery}"></script>
        <script src="/commons-tool/js/commons_permissions.js${portalCDNQuery}"></script>

        <div id="Mrphs-sakai-commons" class="portletBody commons-portletBody">
            <div class="dropdown-toggle dropdown-navIntraTool" id="toolOptionsLink"></div>
            <ul id="commons-toolbar" class="navIntraTool actionToolBar navIntraToolMobile hidden" role="menu"></ul>
            <div id="commons-main-container">
                <div id="commons-content"></div>
            </div>

        </div> <!-- /portletBody-->

        <script src="/commons-tool/js/commons.js${portalCDNQuery}"></script>
        <script>
            $(document).ready(function() {
                var label = commons.i18n.toolOptions || 'Tool Options';
                $('#toolOptionsLink').text(label);
            });
        </script>
    </body>
</html>
