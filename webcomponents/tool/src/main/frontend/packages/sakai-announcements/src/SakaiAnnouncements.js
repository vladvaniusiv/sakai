import { css, html, nothing } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";
import "@sakai-ui/sakai-icon";
import { SakaiPageableElement } from "@sakai-ui/sakai-pageable-element";
import { SakaiSitePicker } from "@sakai-ui/sakai-site-picker";
import "@sakai-ui/sakai-site-picker/sakai-site-picker.js";
import {
  TITLE_A_TO_Z,
  TITLE_Z_TO_A,
  SITE_A_TO_Z,
  SITE_Z_TO_A,
  EARLIEST_FIRST,
  LATEST_FIRST,
  INSTRUCTOR_ORDER,
} from "./sakai-announcements-constants.js";

export class SakaiAnnouncements extends SakaiPageableElement {

  constructor() {

    super();

    this.showPager = true;
    this.loadTranslations("announcements");
  }

  async loadAllData() {

    const url = this.siteId ? `/api/sites/${this.siteId}/announcements`
      : "/api/users/me/announcements";

    return fetch(url)
      .then(r => {

        if (r.ok) {
          return r.json();
        }
        throw new Error(`Failed to get announcements from ${url}`);

      })
      .then(data => {

        this.data = data.announcements;
        this.data.forEach(a => a.visible = true);
        !this.siteId && (this._sites = data.sites);
      })
      .catch (error => console.error(error));
  }

  _sitesSelected(e) {

    if (e.detail.value === SakaiSitePicker.ALL) {
      this.data.forEach(a => a.visible = true);
    } else {
      this.data.forEach(a => a.visible = a.siteId === e.detail.value);
    }
    this.requestUpdate();
  }

  _sortChanged(e) {

    switch (e.target.value) {
      case TITLE_A_TO_Z:
        this.data.sort((a1, a2) => a1.subject.localeCompare(a2.subject));
        break;
      case TITLE_Z_TO_A:
        this.data.sort((a1, a2) => a2.subject.localeCompare(a1.subject));
        break;
      case SITE_A_TO_Z:
        this.data.sort((a1, a2) => a1.siteTitle.localeCompare(a2.siteTitle));
        break;
      case SITE_Z_TO_A:
        this.data.sort((a1, a2) => a2.siteTitle.localeCompare(a1.siteTitle));
        break;
      case EARLIEST_FIRST:
        this.data.sort((a1, a2) => {

          if (a1.date < a2.date) return -1;
          if (a1.date < a2.date) return 1;
          return 0;
        });
        break;
      case LATEST_FIRST:
        this.data.sort((a1, a2) => {

          if (a1.date < a2.date) return 1;
          if (a1.date > a2.date) return -1;
          return 0;
        });
        break;
      case INSTRUCTOR_ORDER:
        this.data.sort((a1, a2) => {

          if (a1.order < a2.order) return 1;
          if (a1.order > a2.order) return -1;
          return 0;
        });
        break;
      default:
        console.warn(`Invalid sort option: ${e.target.value}`);
    }

    this.repage();
  }

  _getFiltersTemplate() {
    return html`
      <style>
        sakai-site-picker {
          display: block;
          width: 100%;
        }
        sakai-site-picker::part(select) {
          width: 100% !important;
        }
      </style>
      <lion-dialog id="filters-dialog">
        <button slot="invoker" type="button" class="btn btn-icon" title="${this._i18n.filter_label}">
          <i class="bi bi-filter fs-5"></i>
        </button>
        <div slot="content" class="p-3 bg-white border rounded shadow-sm" style="min-width: 280px;">
          <div class="d-flex mb-3 border-bottom pb-2">
            <button type="button" class="btn-close ms-auto"
              @click=${e => e.target.closest("lion-dialog").opened = false} aria-label="Close">
            </button>
          </div>

          ${!this.siteId ? html`
            <label class="form-label small fw-bold">${this._i18n.filter_sites_label}</label>
            <div class="mb-3">
              <sakai-site-picker
                  .sites=${this._sites}
                  @sites-selected=${e => this._sitesSelected(e)}>
              </sakai-site-picker>
            </div>
          ` : nothing}

          <label class="form-label small fw-bold">${this._i18n.announcement_sort_label}</label>
          <select class="w-100" .value=${this._currentSort} @change=${e => { this._currentSort = e.target.value; this._sortChanged(e); }}>
            <option value="${LATEST_FIRST}">${this._i18n.latest_first}</option>
            <option value="${EARLIEST_FIRST}">${this._i18n.earliest_first}</option>
            <option value="${TITLE_A_TO_Z}">${this._i18n.title_a_to_z}</option>
            <option value="${TITLE_Z_TO_A}">${this._i18n.title_z_to_a}</option>
            ${!this.siteId || this.siteId === "home" ? html`
            <option value="${SITE_A_TO_Z}">${this._i18n.site_a_to_z}</option>
            <option value="${SITE_Z_TO_A}">${this._i18n.site_z_to_a}</option>
            ` : nothing}
            <option value="${INSTRUCTOR_ORDER}">${this._i18n.instructor_order}</option>
          </select>
        </div>
      </lion-dialog>
    `;
  }

  firstUpdated() {
    this.dispatchEvent(new CustomEvent("register-header-action", {
      detail: () => this._getFiltersTemplate(),
      bubbles: true,
      composed: true
    }));
  }

  updated(changedProperties) {
    super.updated(changedProperties);
    const refreshProps = [ "_sites", "_currentSort" ];
    if (refreshProps.some(prop => changedProperties.has(prop))) {
      this.dispatchEvent(new CustomEvent("request-header-update", {
        bubbles: true,
        composed: true
      }));
    }
  }

  content() {

    if (!this.data || this.data.length === 0) {
      return html`<span>${this._i18n.no_announcements}</span>`;
    }
    return html`
      <div id="viewing">${this._i18n.viewing}</div>
      <div class="announcements ${!this.siteId || this.siteId === "home" ? "home" : "course"}">
        <div class="header">
          <a href="javascript:;"
              title="${this._i18n.sort_by_title_tooltip}"
              aria-label="${this._i18n.sort_by_title_tooltip}"
              @click=${this.sortByTitle}>
            ${this._i18n.title}
          </a>
        </div>
        ${!this.siteId || this.siteId === "home" ? html`
          <div class="header">
            <a href="javascript:;"
                title="${this._i18n.sort_by_site_tooltip}"
                aria-label="${this._i18n.sort_by_site_tooltip}"
                @click=${this.sortBySite}>
              ${this._i18n.site}
            </a>
          </div>
        ` : nothing}
        <div class="header">${this._i18n.view}</div>
      ${this.dataPage.filter(a => a.visible).map((a, i) => html`
        <div class="title cell ${i % 2 === 0 ? "even" : "odd"}">
          ${a.highlighted ? html`
          <sakai-icon type="favourite" size="small"></sakai-icon>
          ` : nothing}
          <span class="${ifDefined(a.highlighted ? "highlighted" : undefined)}">${a.subject}</span>
        </div>
        ${!this.siteId || this.siteId === "home" ? html`
          <div class="site cell ${i % 2 === 0 ? "even" : "odd"}">${a.siteTitle}</div>
        ` : nothing}
        <div class="url cell ${i % 2 === 0 ? "even" : "odd"}">
          <a href="${a.url}"
              title="${this._i18n.url_tooltip}"
              aria-label="${this._i18n.url_tooltip}">
            <sakai-icon type="right" size="small"></sakai-icon>
          </a>
        </div>
      `)}
      </div>
    `;
  }

  static styles = [
    SakaiPageableElement.styles,
    css`
      a {
        color: var(--link-color);
      }
      a:hover { 
        color: var(--link-hover-color);
      }
      a:active {
        color: var(--link-active-color);
      }
      a:visited {
        color: var(--link-visited-color);
      }

      #viewing {
        margin-bottom: 20px;
        font-size: var(--sakai-grades-title-font-size, 14px);
      }
      .announcements {
        display:grid;
        grid-auto-rows: minmax(10px, auto);
      }

      .home {
        grid-template-columns: 4fr 1fr 0fr;
      }

      .course {
        grid-template-columns: 4fr 0fr;
      }
        .announcements > div:nth-child(-n+3) {
          padding-bottom: 14px;
        }
        .header {
          font-weight: bold;
          padding: 0 5px 0 5px;
        }
          .header a {
            text-decoration: none;
            color: var(--sakai-text-color-1, #000);
          }
        .title {
          flex: 2;
        }
        .cell {
          display: flex;
          align-items: center;
          padding: 8px;
          font-size: var(--sakai-grades-title-font-size);
        }
        .even {
          background-color: var(--sakai-table-even-color);
        }
        .site {
          flex: 1;
        }
        .url {
          flex: 1;
        }
    `,
  ];
}
