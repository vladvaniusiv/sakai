import "../sakai-announcements.js";
import * as data from "./data.js";
import {
  TITLE_A_TO_Z,
  TITLE_Z_TO_A,
  SITE_A_TO_Z,
  SITE_Z_TO_A,
  EARLIEST_FIRST,
  LATEST_FIRST,
  INSTRUCTOR_ORDER,
} from "../src/sakai-announcements-constants.js";
import * as sitePickerData from "../../sakai-site-picker/test/data.js";
import { elementUpdated, expect, fixture, html } from "@open-wc/testing";
import fetchMock from "fetch-mock";
describe("sakai-announcements tests", () => {

  beforeEach(async () => {
    fetchMock.mockGlobal();
    fetchMock.get(data.i18nUrl, data.i18n);
  });

  afterEach(() => {
    fetchMock.hardReset();
  });

  it ("renders in user mode correctly", async () => {

    fetchMock.get(data.announcementsUrl, { announcements: data.announcements, sites: sitePickerData.sites });
    fetchMock.get(sitePickerData.i18nUrl, sitePickerData.i18n);

    // In user mode, we'd expect to get announcements from multiple sites.
    const el = await fixture(html`
      <sakai-announcements user-id="${data.userId}"></sakai-announcements>
    `);

    await elementUpdated(el);

    await expect(el).to.be.accessible();

    expect(el.shadowRoot.querySelectorAll("div.title").length).to.equal(3);

    expect(el.shadowRoot.querySelectorAll(".header").length).to.equal(3);

    expect(el.shadowRoot.querySelector(".title").innerHTML).to.contain(data.announcements[0].subject);

    await expect(el).to.be.accessible();
  });

  it ("renders in site mode correctly", async () => {

    fetchMock.get(data.siteAnnouncementsUrl, { announcements: data.siteAnnouncements });

    const el = await fixture(html`
      <sakai-announcements site-id="${data.siteId}"></sakai-announcements>
    `);

    await elementUpdated(el);

    await expect(el).to.be.accessible();

    expect(el.shadowRoot.querySelectorAll(".title").length).to.equal(2);
    expect(el.shadowRoot.querySelectorAll(".header").length).to.equal(2);
  });
});
