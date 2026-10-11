import { CodeText } from '../core/models';

/** Semantische Farben nach SAP Fiori (ValueState/TagDesign). */
export type Semantic = 'Positive' | 'Negative' | 'Critical' | 'Information' | 'Neutral';

const SEMANTICS: Record<string, Semantic> = {
  // Auftragsstatus
  OPEN: 'Information',
  IN_PROCESS: 'Critical',
  COMPLETED: 'Positive',
  CANCELLED: 'Negative',
  // Teilstatus (Lieferung, Faktura)
  NOT_RELEVANT: 'Neutral',
  NOT_PROCESSED: 'Neutral',
  PARTIALLY_PROCESSED: 'Critical',
  FULLY_PROCESSED: 'Positive',
  // Kreditstatus
  NOT_CHECKED: 'Neutral',
  APPROVED: 'Positive',
  BLOCKED: 'Negative',
  RELEASED: 'Information',
  // Lieferung
  CREATED: 'Information',
  GOODS_ISSUED: 'Positive',
  // Faktura
  PAID: 'Positive',
  POSTED: 'Positive',
  REVERSED: 'Negative',
  CLEARED: 'Positive',
  // Bestand
  OK: 'Positive',
  LOW: 'Critical',
  OUT: 'Negative',
  OVER: 'Information'
};

export function semanticOf(status: CodeText | string | null | undefined): Semantic {
  if (!status) return 'Neutral';
  const code = typeof status === 'string' ? status : status.code;
  if (typeof status !== 'string' && status.text === 'Überfällig') return 'Negative';
  return SEMANTICS[code] ?? 'Neutral';
}

export const STOCK_STATUS_TEXT: Record<string, string> = {
  OK: 'Ausreichend',
  LOW: 'Unter Meldebestand',
  OUT: 'Kein Bestand',
  OVER: 'Überbestand'
};

export const PAYMENT_TERMS = [
  { code: 'ZB01', text: '14 Tage 2 % Skonto, 30 Tage netto' },
  { code: 'ZB30', text: '30 Tage netto' },
  { code: 'ZB14', text: '14 Tage netto' },
  { code: 'ZB00', text: 'Sofort fällig ohne Abzug' }
];

export const REJECTION_REASONS = [
  { code: 'R01', text: 'Lieferung zu spät' },
  { code: 'R02', text: 'Qualität mangelhaft' },
  { code: 'R03', text: 'Zu teuer' },
  { code: 'R04', text: 'Wettbewerber besser' },
  { code: 'R05', text: 'Material nicht mehr lieferbar' },
  { code: 'R10', text: 'Kundenwunsch' }
];

export const TAX_CODES = [
  { code: 'FULL', text: 'Voller Steuersatz (19 %)' },
  { code: 'REDUCED', text: 'Ermäßigter Steuersatz (7 %)' },
  { code: 'EXEMPT', text: 'Steuerfrei (0 %)' }
];

export const MOVEMENT_TYPES = [
  { type: 'GOODS_RECEIPT', code: '101', text: 'Wareneingang', needsSource: false, needsTarget: true },
  { type: 'GOODS_ISSUE', code: '201', text: 'Warenausgang Kostenstelle', needsSource: true, needsTarget: false },
  { type: 'TRANSFER', code: '311', text: 'Umlagerung Lagerort an Lagerort', needsSource: true, needsTarget: true },
  { type: 'INVENTORY_ADJUSTMENT', code: '701/702', text: 'Inventurdifferenz (gezählte Menge)', needsSource: false, needsTarget: true },
  { type: 'RETURN', code: '651', text: 'Retoure vom Kunden', needsSource: false, needsTarget: true },
  { type: 'SCRAP', code: '551', text: 'Verschrottung', needsSource: true, needsTarget: false },
  { type: 'INITIAL_ENTRY', code: '561', text: 'Initialer Bestandsaufbau', needsSource: false, needsTarget: true }
] as const;

/** Heutiges Datum als ISO-String (yyyy-MM-dd) in lokaler Zeit. */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
