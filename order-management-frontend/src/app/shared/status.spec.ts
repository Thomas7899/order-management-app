import { semanticOf } from './status';

describe('semanticOf', () => {
  it('ordnet Belegstatus den Fiori-Semantiken zu', () => {
    expect(semanticOf({ code: 'OPEN', text: 'Offen' })).toBe('Information');
    expect(semanticOf({ code: 'IN_PROCESS', text: 'In Bearbeitung' })).toBe('Critical');
    expect(semanticOf({ code: 'COMPLETED', text: 'Erledigt' })).toBe('Positive');
    expect(semanticOf({ code: 'BLOCKED', text: 'Kreditsperre' })).toBe('Negative');
  });

  it('akzeptiert auch den reinen Code', () => {
    expect(semanticOf('LOW')).toBe('Critical');
  });

  it('zeigt überfällige Posten unabhängig vom Code als negativ', () => {
    expect(semanticOf({ code: 'POSTED', text: 'Überfällig' })).toBe('Negative');
  });

  it('fällt bei unbekannten oder fehlenden Werten auf neutral zurück', () => {
    expect(semanticOf({ code: 'XYZ', text: '?' })).toBe('Neutral');
    expect(semanticOf(null)).toBe('Neutral');
    expect(semanticOf(undefined)).toBe('Neutral');
  });
});
