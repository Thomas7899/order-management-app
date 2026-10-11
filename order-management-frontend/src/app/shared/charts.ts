// Gemeinsame Diagramm-Einstellungen. Farben kommen aus den SAP-Theming-Parametern,
// damit die Diagramme im hellen und dunklen Horizon-Theme lesbar sind.

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

export interface ChartTheme {
  palette: string[];
  text: string;
  label: string;
  grid: string;
  base: Record<string, unknown>;
  categoryAxis: Record<string, unknown>;
  valueAxis: Record<string, unknown>;
  currency: (value: number) => string;
  number: (value: number) => string;
}

export function chartTheme(): ChartTheme {
  const text = cssVar('--sapTextColor', '#1d2d3e');
  const label = cssVar('--sapContent_LabelColor', '#556b82');
  const grid = cssVar('--sapList_BorderColor', '#e5e5e5');
  const font = cssVar('--sapFontFamily', 'Arial, sans-serif');

  // SAP-Fiori-Diagrammpalette (sapChart_OrderedColor_1..6)
  const palette = [
    cssVar('--sapChart_OrderedColor_1', '#168eff'),
    cssVar('--sapChart_OrderedColor_2', '#c87b00'),
    cssVar('--sapChart_OrderedColor_3', '#75980b'),
    cssVar('--sapChart_OrderedColor_4', '#df1278'),
    cssVar('--sapChart_OrderedColor_5', '#8b47d7'),
    cssVar('--sapChart_OrderedColor_6', '#049f9a')
  ];

  const currencyFormat = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
  const numberFormat = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 1 });

  return {
    palette,
    text,
    label,
    grid,
    base: {
      backgroundColor: 'transparent',
      color: palette,
      textStyle: { fontFamily: font, color: text },
      legend: { show: false, textStyle: { color: label } }
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: grid } },
      axisTick: { show: false },
      axisLabel: { color: label }
    },
    valueAxis: {
      axisLine: { show: false },
      splitLine: { lineStyle: { color: grid } },
      axisLabel: {
        color: label,
        formatter: (v: number) => (Math.abs(v) >= 1000 ? numberFormat.format(v / 1000) + ' Tsd.' : numberFormat.format(v))
      }
    },
    currency: v => currencyFormat.format(v),
    number: v => numberFormat.format(v)
  };
}
