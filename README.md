<div align="center">

# 📦 Order Management – Auftragsabwicklung im SAP-Stil

[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-22-red?style=for-the-badge&logo=angular)](https://angular.dev/)
[![UI5](https://img.shields.io/badge/UI5%20Web%20Components-Horizon-0a6ed1?style=for-the-badge)](https://sap.github.io/ui5-webcomponents/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-blue?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)

**Order-to-Cash wie in SAP SD – vom Kundenauftrag über Lieferung und Warenausgang bis zur Faktura und zum Zahlungseingang, mit Fiori-Oberfläche und KI-Auswertung der Kundenbewertungen**

[Live-Demo](#-live-demo) • [Funktionen](#-funktionen) • [Demo-Zugänge](#-demo-zugänge) • [Lokal starten](#-lokal-starten) • [Architektur](#-architektur) • [Deployment](#-deployment)

</div>

---

## 🌐 Live-Demo

| | URL |
|---|---|
| **Frontend** | https://order-management-front.netlify.app |
| **Backend / API** | https://thomas-order-backend-f8a0728f603a.herokuapp.com |
| **API-Dokumentation** | https://thomas-order-backend-f8a0728f603a.herokuapp.com/swagger-ui.html |

Die Demo-Daten entstehen durch die Simulation eines kompletten Geschäftsjahres über die echten Fachservices: rund 500 Kundenaufträge mit Lieferungen, Warenausgängen, Fakturen, Zahlungseingängen, Stornos, Kreditsperren und Nachschub.

---

## ✨ Funktionen

### Vertrieb (SD)
- **Kundenauftrag (Terminauftrag TA)** mit Positionen 10/20/30, Assistent zum Anlegen (vergleichbar VA01) und Änderungsbelegen
- **Preisfindung** mit Konditionsarten: `PR00` Preis, `K007` Kundenrabatt, `ZMEN` Mengenstaffel (3 % ab 10, 5 % ab 25 Stück), `RA01` manueller Rabatt, `MWST` Ausgangssteuer, `VPRS` Verrechnungspreis (statistisch, für die Marge)
- **Verfügbarkeitsprüfung (ATP)** auf Werksebene: Bestand − bestätigte Auftragsmengen − Lieferungen ohne Warenausgang
- **Kreditprüfung** gegen das Kreditlimit (Obligo aus offenen Aufträgen und offenen Posten) mit Kreditsperre und Freigabe
- **Absagegründe** (R01–R10), **Rückstandsbearbeitung** und **Belegfluss**
- Abgeleitete Status wie in SAP: Gesamt-, Liefer-, Faktura- und Kreditstatus

### Logistik (LE / MM-IM)
- **Auslieferung (LF)** mit Kommissionierliste und Lieferschein, Lagerortfindung, Begrenzung auf freien Bestand
- **Warenausgang** (Bewegungsart 601) und Storno (602)
- **Materialbelege** mit Positionen und SAP-Bewegungsarten (561, 101, 201, 311, 551, 651, 701/702 …), Storno je Beleg
- **Bestandsübersicht** je Lagerort mit Melde- und Höchstbestand

### Fakturierung und Debitorenbuchhaltung (SD-BIL / FI-AR)
- **Fakturavorrat** (vergleichbar VF04): mehrere Lieferungen in einem Schritt fakturieren
- **Rechnung (F2)** und **Storno (S1)**, Rechnung als PDF
- **Zahlungsbedingungen** (ZB01 mit Skonto, ZB30, ZB14, ZB00), offene und überfällige Posten, Zahlungseingang

### Analyse und KI
- **Kennzahlen**: Umsatz, Bruttomarge, DSO, Liefertreue, Durchlaufzeit, Lagerumschlag, Reichweite
- **ABC-Analyse** für Materialien und Debitoren, **Umsatzprognose** per linearer Regression
- **Semantische Suche** in Kundenbewertungen (OpenAI-Embeddings + pgvector)
- **KI-Trends, Auffälligkeiten und Stimmungsanalyse** der Bewertungen, **KI-Bestandsanalyse**

### Plattform
- **Fiori-Oberfläche** mit UI5 Web Components (Theme Horizon, hell und dunkel): Launchpad, List Reports, Object Pages, Dynamic Page, Wizard
- **Anmeldung per JWT** mit Rollen; Schreibrechte je Fachbereich
- **Nummernkreise** (Auftrag 10000001, Lieferung 80000001, Faktura 90000001, Materialbeleg 4900000001 …)
- **Änderungsbelege** (vergleichbar CDHDR/CDPOS) und **Fehlermeldungen mit Nachrichtennummer** (RFC 7807, z. B. `V1 382`)
- **Schemaverwaltung mit Flyway**

---

## 👤 Demo-Zugänge

Kennwort für alle: **`demo1234`** – auf der Anmeldeseite genügt ein Klick auf den Benutzer.

| Benutzer | Rolle | Darf ändern |
|----------|-------|-------------|
| `ADMIN` | Administrator | alles, inkl. Benutzerverwaltung |
| `MREUTER` | Vertrieb | Kundenaufträge, Debitoren, Materialien |
| `JMEIER` | Lager & Versand | Lieferungen, Warenausgang, Bestandsbuchungen, Materialien |
| `PRICHTER`, `AKOCH` | Fakturierung & Debitorenbuchhaltung | Fakturen, Zahlungseingänge, Kreditfreigaben, Debitoren |
| `SWEBER` | Controlling | KI-Analysen starten |

Lesen dürfen alle angemeldeten Benutzer.

---

## 🛠 Tech-Stack

| Bereich | Technologie |
|---------|-------------|
| Backend | Java 25 (virtuelle Threads), Spring Boot 3.5, Spring Data JPA, Spring Security (OAuth2 Resource Server, JWT), Flyway, MapStruct, Lombok, OpenPDF |
| KI | Spring AI 1.0 mit OpenAI (Chat: `gpt-4o-mini`, Embeddings mit 1536 Dimensionen), pgvector |
| Datenbank | PostgreSQL 16 mit pgvector |
| Frontend | Angular 22 (Standalone, Signals), UI5 Web Components 2 (Fiori Horizon), ECharts |
| Tests | JUnit 5, Spring Boot Test (Backend); Karma/Jasmine (Frontend) |
| Betrieb | Heroku (Backend), Netlify (Frontend), GitHub Actions (CI) |

---

## 🚀 Lokal starten

### Voraussetzungen
- JDK 25 (z. B. Fedora: `sudo dnf install java-25-openjdk-devel`), Node.js 22
- PostgreSQL mit pgvector, z. B. per Docker/Podman
- optional ein OpenAI-API-Schlüssel – ohne Schlüssel läuft alles außer den KI-Funktionen

### 1. Datenbank

```bash
docker run -d --name order-management-db -p 5432:5432 \
  -e POSTGRES_DB=order_management -e POSTGRES_PASSWORD=postgres \
  pgvector/pgvector:pg16
```

oder `docker compose up -d postgres-dev` im Ordner `order-management`. Das Schema legt Flyway beim Start an.

### 2. Backend

```bash
cd order-management
export OPENAI_API_KEY=sk-...        # optional
./mvnw spring-boot:run
```

Beim ersten Start mit leerer Datenbank wird das Demo-Geschäftsjahr simuliert (einige Minuten). Backend: http://localhost:8080, API-Dokumentation: http://localhost:8080/swagger-ui.html

### 3. Frontend

```bash
cd order-management-frontend
npm ci
npx ng serve
```

Frontend: http://localhost:4200

### Konfiguration

| Variable | Bedeutung | Standard |
|----------|-----------|----------|
| `OPENAI_API_KEY` | Schlüssel für die KI-Funktionen | nicht gesetzt (KI antwortet mit 503) |
| `JWT_SECRET` | Signaturschlüssel für Anmeldungen, mindestens 32 Zeichen | zufällig je Start (Anmeldungen gelten dann nur bis zum Neustart) |
| `CORS_ALLOWED_ORIGINS` | erlaubte Frontend-Adressen, kommagetrennt, Platzhalter erlaubt | localhost:4200 und die Netlify-Domain |
| `APP_DEMO_SEED_DATA` | Demo-Daten bei leerer Datenbank erzeugen | `true` |
| `APP_DEV_RESET_ON_START` | Demo-Daten bei jedem Start neu erzeugen | `false` |
| `APP_DEV_GENERATE_EMBEDDINGS` | beim Erzeugen der Demo-Daten auch die Embeddings für die semantische Suche anlegen (braucht `OPENAI_API_KEY`) | `false` |

Embeddings lassen sich auch nachträglich erzeugen: `POST /api/reviews/maintenance/reembed` (Rolle Controlling oder Administrator).

---

## 🏗 Architektur

```
order-management-frontend/  Angular 22 + UI5 Web Components
  src/app/core/             API-Services, Anmeldung, Interceptors, Modelle
  src/app/layout/           Shell: ShellBar, Seitennavigation, Meldungen
  src/app/features/         launchpad, orders, deliveries, billing, customers,
                            materials, inventory, analytics, admin
  src/app/shared/           UI5-Registrierung, Status-Tags, Kacheln, Diagramm-Theme

order-management/           Spring Boot
  controller/               REST-API (/api/orders, /deliveries, /invoices, /inventory, …)
  service/sales/            Preisfindung, ATP, Kreditprüfung, Auftrag, Lieferung,
                            Faktura, Belegfluss, Rechnungs-PDF
  service/                  Bestandsbuchungen, Nummernkreise, Änderungsbelege,
                            Reporting, Benutzer
  ai/                       Embeddings, Trend-, Anomalie- und Stimmungsanalyse
  config/                   Security (JWT, Rollen), Demo-Daten und Geschäftsjahressimulation
  resources/db/migration/   Flyway-Migrationen
```

Der Order-to-Cash-Ablauf im Backend:

```
Kundenauftrag (TA) ──► Auslieferung (LF) ──► Warenausgang (601) ──► Faktura (F2) ──► Zahlungseingang
   │  Preisfindung          │  Lagerortfindung      │  Materialbeleg         │  Zahlungsbedingung
   │  ATP-Prüfung           │  Kommissionierung     │  Bestandsabbau         │  Skonto
   └  Kreditprüfung         └  Storno               └  Storno (602)          └  Storno (S1)
```

---

## 🧪 Tests

```bash
cd order-management && ./mvnw test                                            # Backend
cd order-management-frontend && npx ng test --watch=false --browsers=ChromeHeadless   # Frontend
```

Die CI (`.github/workflows/ci.yml`) führt beide Testläufe und den Produktions-Build bei jedem Pull Request aus.

---

## 📦 Deployment

Backend (Heroku) und Frontend (Netlify) werden manuell per CLI veröffentlicht – Details in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

---

<div align="center">

**Made with ❤️ and ☕ by Thomas Osterlehner**

[![GitHub](https://img.shields.io/badge/GitHub-Thomas7899-black?style=flat-square&logo=github)](https://github.com/Thomas7899)

</div>
