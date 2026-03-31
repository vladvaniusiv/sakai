package org.sakaiproject.e2e.tests;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.AriaRole;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Assumptions;
import org.sakaiproject.e2e.support.SakaiEnvironment;
import org.sakaiproject.e2e.support.SakaiUiTestBase;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VideoTrainingTest extends SakaiUiTestBase {

    private static String sakaiUrl;
    private static final String VIDEO_TITLE = "Playwright Video Training " + System.currentTimeMillis();
    private static final String VIDEO_TITLE_SECOND = VIDEO_TITLE + " B";

    @Test
    @Order(1)
    void canCreateNewCourse() {
        Assumptions.assumeTrue(isSakaiReachable(), "Sakai instance not reachable at " + SakaiEnvironment.baseUrl());
        sakai.login("instructor1");

        try {
            sakaiUrl = sakai.createCourse("instructor1", List.of("sakai\\.video\\.training"));
        } catch (Throwable ex) {
            String configuredSite = configuredSiteUrl();
            Assumptions.assumeTrue(configuredSite != null && !configuredSite.isBlank(),
                "No course could be auto-created and PLAYWRIGHT_VIDEO_TRAINING_SITE_URL was not provided");
            sakaiUrl = configuredSite;
        }
    }

    @Test
    @Order(2)
    void instructorCanPersistSelectedCardsView() {
        Assumptions.assumeTrue(sakaiUrl != null && !sakaiUrl.isBlank(), "Video Training site URL not available");

        sakai.login("instructor1");
        sakai.gotoPath(sakaiUrl);
        sakai.toolClick("Video Training");

        Locator addVideoButton = page.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions().setName(Pattern.compile("Add video", Pattern.CASE_INSENSITIVE))).first();
        assertThat(addVideoButton).isVisible();
        addVideoButton.click(new Locator.ClickOptions().setForce(true));

        createExternalVideo(VIDEO_TITLE, "Created by Playwright test");

        Locator viewModeSelect = page.locator("#viewMode").first();
        assertThat(viewModeSelect).isVisible();
        assertThat(viewModeSelect).hasValue("table");
        assertThat(page.locator(".vt-video-table")).isVisible();

        viewModeSelect.selectOption("cards");
        page.waitForLoadState();

        assertThat(page.locator("#viewMode")).hasValue("cards");
        assertThat(page.locator(".vt-video-grid")).isVisible();
        assertThat(page.locator(".vt-video-card").filter(new Locator.FilterOptions().setHasText(VIDEO_TITLE))).hasCount(1);

        page.reload();
        assertThat(page.locator("#viewMode")).hasValue("cards");
        assertThat(page.locator(".vt-video-grid")).isVisible();
    }

    @Test
    @Order(3)
    void studentCanPersistSelectedTableView() {
        Assumptions.assumeTrue(sakaiUrl != null && !sakaiUrl.isBlank(), "Video Training site URL not available");

        sakai.login("student0011");
        sakai.gotoPath(sakaiUrl);
        sakai.toolClick("Video Training");

        Locator viewModeSelect = page.locator("#viewMode").first();
        assertThat(viewModeSelect).isVisible();
        assertThat(viewModeSelect).hasValue("cards");
        assertThat(page.locator(".vt-video-grid")).isVisible();
        assertThat(page.locator(".vt-video-card").filter(new Locator.FilterOptions().setHasText(VIDEO_TITLE))).hasCount(1);

        viewModeSelect.selectOption("table");
        page.waitForLoadState();

        assertThat(page.locator("#viewMode")).hasValue("table");
        assertThat(page.locator(".vt-video-table")).isVisible();

        page.reload();
        assertThat(page.locator("#viewMode")).hasValue("table");
        assertThat(page.locator(".vt-video-table")).isVisible();
    }

    @Test
    @Order(4)
    void instructorCanUseAllSourceModesInForm() {
        Assumptions.assumeTrue(sakaiUrl != null && !sakaiUrl.isBlank(), "Video Training site URL not available");

        sakai.login("instructor1");
        sakai.gotoPath(sakaiUrl);
        sakai.toolClick("Video Training");

        Locator addVideoButton = page.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions().setName(Pattern.compile("Add video", Pattern.CASE_INSENSITIVE))).first();
        addVideoButton.click(new Locator.ClickOptions().setForce(true));

        assertThat(page.locator("#sourceModeExternal")).isVisible();
        assertThat(page.locator("#sourceModeUpload")).isVisible();
        assertThat(page.locator("#sourceModeResources")).isVisible();

        page.locator("#sourceModeExternal").click(new Locator.ClickOptions().setForce(true));
        assertThat(page.locator("#externalSourceSection")).isVisible();
        assertThat(page.locator("#uploadSourceSection")).isHidden();
        assertThat(page.locator("#resourcesSourceSection")).isHidden();

        page.locator("#sourceModeUpload").click(new Locator.ClickOptions().setForce(true));
        assertThat(page.locator("#externalSourceSection")).isHidden();
        assertThat(page.locator("#uploadSourceSection")).isVisible();
        assertThat(page.locator("#resourcesSourceSection")).isHidden();

        page.locator("#title").fill(VIDEO_TITLE_SECOND);
        page.locator("#description").fill("Upload mode test");
        page.setInputFiles("#nativeFile", new FilePayload("video.webm", "video/webm", "dummy".getBytes(StandardCharsets.UTF_8)));
        page.locator("button[type=\"submit\"]").first().click(new Locator.ClickOptions().setForce(true));

        assertThat(page.locator("body")).containsText(Pattern.compile(VIDEO_TITLE_SECOND, Pattern.CASE_INSENSITIVE));
    }

    @Test
    @Order(5)
    void instructorCanSearchAndUseCursorMode() {
        Assumptions.assumeTrue(sakaiUrl != null && !sakaiUrl.isBlank(), "Video Training site URL not available");

        sakai.login("instructor1");
        sakai.gotoPath(sakaiUrl);
        sakai.toolClick("Video Training");

        Locator search = page.locator("#q").first();
        assertThat(search).isVisible();
        search.fill(VIDEO_TITLE);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Search", Pattern.CASE_INSENSITIVE))).first()
            .click(new Locator.ClickOptions().setForce(true));

        assertThat(page.locator("body")).containsText(Pattern.compile(VIDEO_TITLE, Pattern.CASE_INSENSITIVE));
        assertThat(page.locator("body")).not().containsText(Pattern.compile("No videos are available yet", Pattern.CASE_INSENSITIVE));

        Locator cursorModeButton = page.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions().setName(Pattern.compile("Cursor mode", Pattern.CASE_INSENSITIVE))).first();
        if (cursorModeButton.count() > 0) {
            cursorModeButton.click(new Locator.ClickOptions().setForce(true));
            page.waitForLoadState();
            assertThat(page.locator("body")).containsText(Pattern.compile("Page mode|Load more|You reached the end of the list", Pattern.CASE_INSENSITIVE));
        }
    }

    @Test
    @Order(6)
    void analyticsMenuRespectsPermissions() {
        Assumptions.assumeTrue(sakaiUrl != null && !sakaiUrl.isBlank(), "Video Training site URL not available");

        sakai.login("student0011");
        sakai.gotoPath(sakaiUrl);
        sakai.toolClick("Video Training");

        assertThat(page.locator(".navIntraTool")).not().containsText(Pattern.compile("Analytics", Pattern.CASE_INSENSITIVE));
    }

    private void createExternalVideo(String title, String description) {
        page.locator("#title").fill(title);
        page.locator("#description").fill(description);
        page.locator("#sourceModeExternal").click(new Locator.ClickOptions().setForce(true));
        page.locator("#sourceReference").fill("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        page.locator("button[type=\"submit\"]").first().click(new Locator.ClickOptions().setForce(true));
    }

    private String configuredSiteUrl() {
        String fromProperty = System.getProperty("PLAYWRIGHT_VIDEO_TRAINING_SITE_URL");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }

        String fromEnv = System.getenv("PLAYWRIGHT_VIDEO_TRAINING_SITE_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        return null;
    }

    private boolean isSakaiReachable() {
        try {
            URL url = new URL(SakaiEnvironment.baseUrl() + "/portal/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            int status = connection.getResponseCode();
            return status > 0;
        } catch (Exception e) {
            return false;
        }
    }
}