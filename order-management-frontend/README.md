# Order Management – Frontend

Angular 22 mit UI5 Web Components (SAP Fiori, Theme Horizon hell und dunkel). Überblick über das Gesamtprojekt: [README im Hauptverzeichnis](../README.md).

## Entwicklung

```bash
npm ci
npx ng serve            # http://localhost:4200, erwartet das Backend auf http://localhost:8080
```

## Build und Tests

```bash
npx ng build                                        # Produktions-Build nach dist/order-management-frontend/browser
npx ng test --watch=false --browsers=ChromeHeadless # Unit-Tests (Karma/Jasmine)
```

## Aufbau

| Ordner | Inhalt |
|--------|--------|
| `src/app/core` | API-Services, Anmeldung und Rollen, HTTP-Interceptors (JWT, Fehlermeldungen nach RFC 7807), Modelle |
| `src/app/layout` | Shell mit ShellBar, Seitennavigation, Benutzermenü und Meldungen |
| `src/app/features` | Fachseiten: Launchpad, Aufträge, Lieferungen, Fakturierung, Debitoren, Materialien, Bestand, Analysen, Benutzer |
| `src/app/shared` | Registrierung der UI5-Komponenten und Icons, Status-Tags, Kacheln, Diagramm-Theme |

Neue UI5-Komponenten oder Icons müssen in `src/app/shared/ui5.ts` importiert werden, sonst bleiben die Tags im Template wirkungslos.
