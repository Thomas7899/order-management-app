# Deployment

Jeder Push bzw. Merge auf `main` wird automatisch deployt – aber nur, wenn vorher Backend-Tests, Frontend-Build und Frontend-Tests grün sind. Pull Requests werden nur geprüft, nicht deployt.

| Teil | Plattform | Name |
|------|-----------|------|
| Backend | Heroku | App `thomas-order-backend` mit Heroku Postgres (`essential-0`) |
| Frontend | Netlify | Site `order-management-front` |

---

## Automatischer Deploy (GitHub Actions)

Der Workflow `.github/workflows/ci.yml` läuft bei jedem Push auf `main`:

1. `backend` und `frontend`: Tests und Produktions-Build
2. `deploy-backend`: `git push` des Commits nach Heroku, danach Health-Check auf `/actuator/health`
3. `deploy-frontend`: Produktions-Build und `netlify deploy --prod`

Erneut deployen ohne neuen Commit: GitHub → Actions → CI → „Run workflow“ auf `main`.

Benötigte Repository-Secrets (GitHub → Settings → Secrets and variables → Actions):

| Secret | Inhalt |
|--------|--------|
| `HEROKU_API_KEY` | eigener Heroku-Token für den Deploy: `heroku authorizations:create --description "GitHub Actions Deploy"` |
| `NETLIFY_AUTH_TOKEN` | Netlify Personal Access Token (User settings → Applications → Personal access tokens) |

Setzen per CLI, ohne dass der Token im Terminal-Verlauf landet:

```bash
gh secret set NETLIFY_AUTH_TOKEN   # fragt den Wert ab
```

Die folgenden Abschnitte beschreiben die Konfiguration und den Deploy von Hand (Voraussetzung: `heroku` und `netlify` CLI angemeldet).

---

## Backend (Heroku)

### Konfiguration

| Config Var | Wert |
|------------|------|
| `SPRING_PROFILES_ACTIVE` | `heroku` |
| `PROJECT_PATH` | `order-management` (Monorepo: Unterordner mit dem Maven-Projekt) |
| `MAVEN_CUSTOM_GOALS` | `clean install` |
| `DATABASE_URL` | wird vom Postgres-Add-on gesetzt |
| `JWT_SECRET` | Signaturschlüssel für Anmeldungen, mindestens 32 Zeichen |
| `OPENAI_API_KEY` | für die KI-Funktionen (ohne Schlüssel startet die App trotzdem) |
| `CORS_ALLOWED_ORIGINS` | optional; Standard im Profil `heroku` ist die Netlify-Domain inklusive Deploy-Previews |
| `APP_DEV_RESET_ON_START` | `false` – Daten bleiben bei Neustarts erhalten |
| `APP_DEV_GENERATE_EMBEDDINGS` | optional `true`, damit die semantische Suche nach einem Neuaufbau der Demo-Daten sofort funktioniert |

Die Java-Version für Heroku steht in `system.properties` (`java.runtime.version=25`). Das `Procfile` startet die JVM mit `-XX:+UseCompactObjectHeaders`, das spart auf dem 512-MB-Dyno Heap.

`JWT_SECRET` muss gesetzt sein. Ohne ihn erzeugt das Backend bei jedem Start einen zufälligen Schlüssel, und alle Anmeldungen werden nach einem Neustart ungültig.

```bash
heroku config:set -a thomas-order-backend JWT_SECRET="$(openssl rand -base64 48)"
```

### Von Hand veröffentlichen

```bash
git remote add heroku https://git.heroku.com/thomas-order-backend.git   # einmalig
git push heroku main
heroku logs -a thomas-order-backend --tail
```

Ist die Datenbank leer, simuliert das Backend nach dem Start ein Geschäftsjahr als Demo-Daten. Das dauert einige Minuten. Die App ist währenddessen schon erreichbar und füllt sich nach und nach. Fertig ist sie, wenn im Log `Demo data loaded in … s` erscheint.

### Umstieg auf Flyway (am 11.10.2026 erledigt)

Bis Release v31 hat Hibernate das Schema erzeugt (`ddl-auto`). Seitdem verwaltet Flyway das Schema (`db/migration`). Beim ersten Deploy wurde die Datenbank deshalb einmal geleert; das Backup davor ist `b001` (`heroku pg:backups -a thomas-order-backend`). Für eine neue App wären die Schritte:

```bash
heroku maintenance:on -a thomas-order-backend
heroku pg:reset DATABASE -a thomas-order-backend --confirm thomas-order-backend
git push heroku main
heroku maintenance:off -a thomas-order-backend
```

Die pgvector-Erweiterung legt die erste Migration selbst an. Danach laufen Schemaänderungen nur noch über neue Flyway-Migrationen (`V2__…sql`), ohne Reset.

### Prüfen

```bash
curl https://thomas-order-backend-f8a0728f603a.herokuapp.com/actuator/health
curl https://thomas-order-backend-f8a0728f603a.herokuapp.com/api/auth/demo-users
```

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
