// Registriert die verwendeten UI5 Web Components (SAP Fiori, Theme "Horizon").
// Jede Komponente wird nur einmal importiert; die Templates nutzen sie als Custom Elements.

import { setTheme } from '@ui5/webcomponents-base/dist/config/Theme.js';
import { setLanguage } from '@ui5/webcomponents-base/dist/config/Language.js';

// Themes, Übersetzungen und Länderdaten (werden bei Bedarf nachgeladen)
import '@ui5/webcomponents/dist/Assets.js';
import '@ui5/webcomponents-fiori/dist/Assets.js';
import '@ui5/webcomponents-icons/dist/Assets.js';

// Hauptbibliothek
import '@ui5/webcomponents/dist/Avatar.js';
import '@ui5/webcomponents/dist/Bar.js';
import '@ui5/webcomponents/dist/BusyIndicator.js';
import '@ui5/webcomponents/dist/Button.js';
import '@ui5/webcomponents/dist/Card.js';
import '@ui5/webcomponents/dist/CardHeader.js';
import '@ui5/webcomponents/dist/CheckBox.js';
import '@ui5/webcomponents/dist/ComboBox.js';
import '@ui5/webcomponents/dist/ComboBoxItem.js';
import '@ui5/webcomponents/dist/DatePicker.js';
import '@ui5/webcomponents/dist/Dialog.js';
import '@ui5/webcomponents/dist/Form.js';
import '@ui5/webcomponents/dist/FormGroup.js';
import '@ui5/webcomponents/dist/FormItem.js';
import '@ui5/webcomponents/dist/Icon.js';
import '@ui5/webcomponents/dist/Input.js';
import '@ui5/webcomponents/dist/Label.js';
import '@ui5/webcomponents/dist/Link.js';
import '@ui5/webcomponents/dist/List.js';
import '@ui5/webcomponents/dist/ListItemStandard.js';
import '@ui5/webcomponents/dist/MessageStrip.js';
import '@ui5/webcomponents/dist/MultiComboBox.js';
import '@ui5/webcomponents/dist/MultiComboBoxItem.js';
import '@ui5/webcomponents/dist/Option.js';
import '@ui5/webcomponents/dist/Panel.js';
import '@ui5/webcomponents/dist/Popover.js';
import '@ui5/webcomponents/dist/ProgressIndicator.js';
import '@ui5/webcomponents/dist/RatingIndicator.js';
import '@ui5/webcomponents/dist/SegmentedButton.js';
import '@ui5/webcomponents/dist/SegmentedButtonItem.js';
import '@ui5/webcomponents/dist/Select.js';
import '@ui5/webcomponents/dist/StepInput.js';
import '@ui5/webcomponents/dist/Switch.js';
import '@ui5/webcomponents/dist/Tab.js';
import '@ui5/webcomponents/dist/TabContainer.js';
import '@ui5/webcomponents/dist/Table.js';
import '@ui5/webcomponents/dist/TableCell.js';
import '@ui5/webcomponents/dist/TableGrowing.js';
import '@ui5/webcomponents/dist/TableHeaderCell.js';
import '@ui5/webcomponents/dist/TableHeaderRow.js';
import '@ui5/webcomponents/dist/TableRow.js';
import '@ui5/webcomponents/dist/TableRowAction.js';
import '@ui5/webcomponents/dist/TableRowActionNavigation.js';
import '@ui5/webcomponents/dist/TableSelectionMulti.js';
import '@ui5/webcomponents/dist/Tag.js';
import '@ui5/webcomponents/dist/Text.js';
import '@ui5/webcomponents/dist/TextArea.js';
import '@ui5/webcomponents/dist/Title.js';
import '@ui5/webcomponents/dist/Toast.js';
import '@ui5/webcomponents/dist/Toolbar.js';
import '@ui5/webcomponents/dist/ToolbarButton.js';
import '@ui5/webcomponents/dist/ToolbarSpacer.js';

// Fiori-Bibliothek
import '@ui5/webcomponents-fiori/dist/DynamicPage.js';
import '@ui5/webcomponents-fiori/dist/DynamicPageHeader.js';
import '@ui5/webcomponents-fiori/dist/DynamicPageTitle.js';
import '@ui5/webcomponents-fiori/dist/IllustratedMessage.js';
import '@ui5/webcomponents-fiori/dist/illustrations/NoData.js';
import '@ui5/webcomponents-fiori/dist/illustrations/NoSearchResults.js';
import '@ui5/webcomponents-fiori/dist/illustrations/NoEntries.js';
import '@ui5/webcomponents-fiori/dist/illustrations/NoChartData.js';
import '@ui5/webcomponents-fiori/dist/illustrations/UnableToLoad.js';
import '@ui5/webcomponents-fiori/dist/NavigationLayout.js';
import '@ui5/webcomponents-fiori/dist/ShellBar.js';
import '@ui5/webcomponents-fiori/dist/ShellBarBranding.js';
import '@ui5/webcomponents-fiori/dist/ShellBarItem.js';
import '@ui5/webcomponents-fiori/dist/SideNavigation.js';
import '@ui5/webcomponents-fiori/dist/SideNavigationGroup.js';
import '@ui5/webcomponents-fiori/dist/SideNavigationItem.js';
import '@ui5/webcomponents-fiori/dist/SideNavigationSubItem.js';
import '@ui5/webcomponents-fiori/dist/Timeline.js';
import '@ui5/webcomponents-fiori/dist/TimelineItem.js';
import '@ui5/webcomponents-fiori/dist/UserMenu.js';
import '@ui5/webcomponents-fiori/dist/UserMenuAccount.js';
import '@ui5/webcomponents-fiori/dist/UserMenuItem.js';
import '@ui5/webcomponents-fiori/dist/Wizard.js';
import '@ui5/webcomponents-fiori/dist/WizardStep.js';

// Icons (nur die verwendeten, damit das Bundle klein bleibt)
import '@ui5/webcomponents-icons/dist/accept.js';
import '@ui5/webcomponents-icons/dist/activity-2.js';
import '@ui5/webcomponents-icons/dist/add.js';
import '@ui5/webcomponents-icons/dist/ai.js';
import '@ui5/webcomponents-icons/dist/alert.js';
import '@ui5/webcomponents-icons/dist/bar-chart.js';
import '@ui5/webcomponents-icons/dist/bell.js';
import '@ui5/webcomponents-icons/dist/business-objects-experience.js';
import '@ui5/webcomponents-icons/dist/cart.js';
import '@ui5/webcomponents-icons/dist/credit-card.js';
import '@ui5/webcomponents-icons/dist/customer.js';
import '@ui5/webcomponents-icons/dist/decline.js';
import '@ui5/webcomponents-icons/dist/delete.js';
import '@ui5/webcomponents-icons/dist/document.js';
import '@ui5/webcomponents-icons/dist/edit.js';
import '@ui5/webcomponents-icons/dist/employee.js';
import '@ui5/webcomponents-icons/dist/error.js';
import '@ui5/webcomponents-icons/dist/factory.js';
import '@ui5/webcomponents-icons/dist/feedback.js';
import '@ui5/webcomponents-icons/dist/filter.js';
import '@ui5/webcomponents-icons/dist/history.js';
import '@ui5/webcomponents-icons/dist/home.js';
import '@ui5/webcomponents-icons/dist/inventory.js';
import '@ui5/webcomponents-icons/dist/journey-arrive.js';
import '@ui5/webcomponents-icons/dist/journey-depart.js';
import '@ui5/webcomponents-icons/dist/kpi-corporate-performance.js';
import '@ui5/webcomponents-icons/dist/lightbulb.js';
import '@ui5/webcomponents-icons/dist/line-chart.js';
import '@ui5/webcomponents-icons/dist/locked.js';
import '@ui5/webcomponents-icons/dist/log.js';
import '@ui5/webcomponents-icons/dist/menu2.js';
import '@ui5/webcomponents-icons/dist/message-information.js';
import '@ui5/webcomponents-icons/dist/money-bills.js';
import '@ui5/webcomponents-icons/dist/monitor-payments.js';
import '@ui5/webcomponents-icons/dist/navigation-right-arrow.js';
import '@ui5/webcomponents-icons/dist/palette.js';
import '@ui5/webcomponents-icons/dist/payment-approval.js';
import '@ui5/webcomponents-icons/dist/pdf-attachment.js';
import '@ui5/webcomponents-icons/dist/pending.js';
import '@ui5/webcomponents-icons/dist/print.js';
import '@ui5/webcomponents-icons/dist/process.js';
import '@ui5/webcomponents-icons/dist/product.js';
import '@ui5/webcomponents-icons/dist/receipt.js';
import '@ui5/webcomponents-icons/dist/refresh.js';
import '@ui5/webcomponents-icons/dist/request.js';
import '@ui5/webcomponents-icons/dist/sales-order.js';
import '@ui5/webcomponents-icons/dist/sales-order-item.js';
import '@ui5/webcomponents-icons/dist/search.js';
import '@ui5/webcomponents-icons/dist/shipping-status.js';
import '@ui5/webcomponents-icons/dist/status-positive.js';
import '@ui5/webcomponents-icons/dist/supplier.js';
import '@ui5/webcomponents-icons/dist/user-settings.js';
import '@ui5/webcomponents-icons/dist/unlocked.js';
import '@ui5/webcomponents-icons/dist/warning.js';
import '@ui5/webcomponents-icons/dist/wallet.js';

export type ThemeName = 'sap_horizon' | 'sap_horizon_dark';

const THEME_KEY = 'om.theme';

export function initUi5(): void {
  setLanguage('de');
  setTheme(loadTheme());
}

export function loadTheme(): ThemeName {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored === 'sap_horizon' || stored === 'sap_horizon_dark') return stored;
  } catch {
    // Speicher nicht verfügbar
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'sap_horizon_dark' : 'sap_horizon';
}

export function applyTheme(theme: ThemeName): void {
  setTheme(theme);
  document.documentElement.dataset['theme'] = theme === 'sap_horizon_dark' ? 'dark' : 'light';
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    // Speicher nicht verfügbar
  }
}
