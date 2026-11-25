# Deployment

Jeder Push bzw. Merge auf `main` wird automatisch deployt – aber nur, wenn vorher Backend-Tests, Frontend-Build und Frontend-Tests grün sind. Pull Requests werden nur geprüft, nicht deployt.

| Teil | Plattform | Name |
|------|-----------|------|
| Backend | Render (Free, Docker) | Web Service `thomas-order-backend`, Region Frankfurt |
| Datenbank | Neon (Free) | PostgreSQL mit pgvector, Region AWS eu-central-1 (Frankfurt) |
| Frontend | Netlify | Site `order-management-front` |

Alle drei Dienste sind im kostenlosen Plan. Dabei gilt:

- Das Backend schläft nach 15 Minuten ohne Anfragen ein. Der erste Aufruf danach weckt es auf und braucht ein bis zwei Minuten.
- Die Datenbank schläft nach 5 Minuten ohne Verbindung ein und wacht beim nächsten Verbindungsaufbau in unter einer Sekunde wieder auf.
- Neon erlaubt im Free-Plan 1 GB Speicher und 100 CU-Stunden Rechenzeit pro Monat.

---

## Automatischer Deploy (GitHub Actions)

Der Workflow `.github/workflows/ci.yml` läuft bei jedem Push auf `main`:

1. `backend` und `frontend`: Tests und Produktions-Build
2. `deploy-backend`: startet über die Render-API einen Deploy genau dieses Commits, wartet, bis er live ist, und prüft danach `/actuator/health`
3. `deploy-frontend`: Produktions-Build und `netlify deploy --prod`

Erneut deployen ohne neuen Commit: GitHub → Actions → CI → „Run workflow“ auf `main`.

Benötigte Einstellungen im Repository (GitHub → Settings → Secrets and variables → Actions):

| Art | Name | Inhalt |
|-----|------|--------|
| Secret | `RENDER_API_KEY` | Render API Key (Account Settings → API Keys) |
| Variable | `RENDER_SERVICE_ID` | ID des Web Service, beginnt mit `srv-` (steht in der Dashboard-URL des Service) |
| Secret | `NETLIFY_AUTH_TOKEN` | Netlify Personal Access Token (User settings → Applications → Personal access tokens) |

Setzen per CLI, ohne dass der Token im Terminal-Verlauf landet:

```bash
gh secret set RENDER_API_KEY       # fragt den Wert ab
gh variable set RENDER_SERVICE_ID --body "srv-..."
gh secret set NETLIFY_AUTH_TOKEN
```

---

## Einrichtung

Einmalig nötig, wenn Backend oder Datenbank neu angelegt werden.

### 1. Datenbank (Neon)

1. Auf [neon.com](https://neon.com) ein Projekt anlegen: PostgreSQL 16 oder neuer, Region **AWS Europe Central 1 (Frankfurt)**.
2. Unter „Connect“ die Verbindungsdaten der Datenbank `neondb` ablesen. Die Adresse **ohne** `-pooler` im Hostnamen verwenden, denn Flyway und Hibernate brauchen eine direkte Verbindung.
3. Daraus die JDBC-Adresse bilden:

   ```
   jdbc:postgresql://ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require
   ```

Das Schema legt Flyway beim ersten Start selbst an, einschließlich der pgvector-Erweiterung (`V1__baseline_schema.sql`).

### 2. Backend (Render)

1. Auf [render.com](https://render.com) → New → **Blueprint** → dieses Repository wählen. Render liest `render.yaml` und legt den Web Service `thomas-order-backend` an.
2. Die abgefragten Werte eintragen: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (aus Neon) und `OPENAI_API_KEY`.
3. Die Service-URL prüfen. Ist der Name schon vergeben, hängt Render ein Suffix an. Dann die URL in `order-management-frontend/src/environments/environment.prod.ts`, in `BACKEND_URL` in `.github/workflows/ci.yml`, in `OpenApiConfig` und in der README anpassen.
4. `RENDER_API_KEY` und `RENDER_SERVICE_ID` in GitHub hinterlegen (siehe oben).

Render baut das Image aus `order-management/Dockerfile`. Auto-Deploy ist ausgeschaltet, damit nur getestete Commits live gehen.

### Konfiguration

| Umgebungsvariable | Wert |
|-------------------|------|
| `SPRING_PROFILES_ACTIVE` | `cloud` (aus `render.yaml`) |
| `DATABASE_URL` | JDBC-Adresse von Neon mit `sslmode=require` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Zugangsdaten von Neon |
| `JWT_SECRET` | Signaturschlüssel für Anmeldungen, erzeugt Render beim Anlegen (mindestens 32 Zeichen) |
| `OPENAI_API_KEY` | für die KI-Funktionen (ohne Schlüssel startet die App trotzdem) |
| `CORS_ALLOWED_ORIGINS` | optional; Standard im Profil `cloud` ist die Netlify-Domain inklusive Deploy-Previews |
| `APP_DEV_RESET_ON_START` | `false` – Daten bleiben bei Neustarts erhalten |
| `APP_DEV_GENERATE_EMBEDDINGS` | optional `true`, damit die semantische Suche nach einem Neuaufbau der Demo-Daten sofort funktioniert |

`JWT_SECRET` muss gesetzt sein. Ohne ihn erzeugt das Backend bei jedem Start einen zufälligen Schlüssel, und alle Anmeldungen werden nach einem Neustart ungültig.

Das Dockerfile begrenzt den Heap auf 60 % des Speichers (rund 300 MB der 512 MB) und startet die JVM mit `-XX:+UseCompactObjectHeaders`.

### Demo-Daten

Ist die Datenbank leer, simuliert das Backend nach dem Start ein Geschäftsjahr als Demo-Daten. Die App ist währenddessen schon erreichbar und füllt sich nach und nach. Fertig ist sie, wenn im Log `Demo data loaded in … s` erscheint.

Auf der Free-Instanz mit 0,1 CPU dauert das sehr lange. Schneller geht es, wenn das Backend die Daten einmal lokal in die Neon-Datenbank schreibt:

```bash
cd order-management
SPRING_PROFILES_ACTIVE=cloud \
DATABASE_URL="jdbc:postgresql://ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require" \
DATABASE_USERNAME="..." DATABASE_PASSWORD="..." \
OPENAI_API_KEY="..." \
./mvnw spring-boot:run
```

Sobald `Demo data loaded` im Log steht, den lokalen Prozess beenden. Das Backend auf Render findet die Daten dann beim nächsten Start vor.

### Prüfen

```bash
curl https://thomas-order-backend.onrender.com/actuator/health
curl https://thomas-order-backend.onrender.com/api/auth/demo-users
```

Logs: Render-Dashboard → `thomas-order-backend` → Logs.

---

## Frontend (Netlify)

Von Hand veröffentlichen (sonst übernimmt das der Workflow). Die API-Adresse für den Produktions-Build steht in `src/environments/environment.prod.ts`. `src/_redirects` leitet alle Routen auf `index.html` (Single-Page-App).

```bash
cd order-management-frontend
npm ci
npx ng build
netlify deploy --prod --dir=dist/order-management-frontend/browser --site 61edabe6-b160-4b89-b337-f51983aa9d94
```

---

## Checkliste für ein Release

1. CI auf dem Pull Request grün (Backend-Tests, Frontend-Build und -Tests)
2. Pull Request per Merge-Commit nach `main` mergen – der Deploy startet automatisch
3. In GitHub → Actions prüfen, dass `deploy-backend` und `deploy-frontend` grün sind
4. Anmeldung mit einem Demo-Zugang testen, z. B. `MREUTER` / `demo1234`
