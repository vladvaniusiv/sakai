import { html } from "lit";
import { SakaiDashboardWidget } from "./SakaiDashboardWidget.js";
import { sakaiWidgets } from "./SakaiWidgets.js";

export class SakaiWidgetPicker extends SakaiDashboardWidget {

  static properties = {

    all: { type: Array },
    current: { type: Array },
    available: { type: Array },
    toolnames: { type: Object },
  };

  constructor() {

    super();

    this.widgetId = "widget-picker";
    this.all = sakaiWidgets.getIds();
    this.current = [];
    this.available = [];
    this.draggable = false;
    this.hasOptions = false;

    this.loadTranslations(this.widgetId);
    this.loadTranslations("toolnames").then(r => this.toolnames = r);
  }

  set all(value) {

    this._all = value;

    if (this.current) {
      this.available = value.filter(v => !this.current.includes(v));
    }
  }

  get all() { return this._all; }

  set current(value) {

    this._current = value;
    if (this.all) {
      this.available = this.all.filter(v => !value.includes(v));
    }
  }

  get current() { return this._current; }

  lookupWidgetName(id) {
    return this.toolnames[id] || this.toolnames.unknown;
  }

  widgetPicked(e) {
    this.dispatchEvent(new CustomEvent("widget-picked", { detail: { id: e.target.id }, bubbles: true }));
  }

  remove() {
    this.dispatchEvent(new CustomEvent("remove", { detail: { newState: "view" }, bubbles: true }));
  }

  firstUpdated() {
    super.firstUpdated();
  }

  shouldUpdate(changed) {
    return super.shouldUpdate(changed) && this.toolnames;
  }

  content() {

    return html`
      ${this.available.length ? html`
        <div class="alert alert-primary py-2 mb-2 fw-semibold" role="status">${this._i18n.pick_instruction}</div>
        <div class="d-grid gap-2 mb-2">
          ${this.available.map(w => html`
            <button type="button"
                class="btn btn-primary text-start"
                id="${w}"
                @click=${this.widgetPicked}>
              <span class="bi bi-plus"></span>
              ${this.lookupWidgetName(w)}
            </button>
          `)}
        </div>
      ` : html`
        <div class="alert alert-info py-2 mb-0">${this._i18n.all_displayed}</div>
      `}
    `;
  }
}
