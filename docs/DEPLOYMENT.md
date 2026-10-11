# Deployment

Backend und Frontend werden manuell per CLI veröffentlicht. Es gibt keinen automatischen Deploy aus GitHub; die GitHub Actions führen nur Build und Tests aus.

| Teil | Plattform | Name |
|------|-----------|------|
| Backend | Heroku | App `thomas-order-backend` mit Heroku Postgres (`essential-0`) |
| Frontend | Netlify | Site `order-management-front` |

Voraussetzungen: `heroku` und `netlify` CLI installiert und angemeldet.

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

### Veröffentlichen

```bash
git push heroku main
heroku logs -a thomas-order-backend --tail
```

Ist die Datenbank leer, simuliert das Backend nach dem Start ein Geschäftsjahr als Demo-Daten. Das dauert einige Minuten. Die App ist währenddessen schon erreichbar und füllt sich nach und nach. Fertig ist sie, wenn im Log `Demo data loaded in … s` erscheint.

### Einmalig beim Umstieg auf Flyway

Bis Release v31 hat Hibernate das Schema erzeugt (`ddl-auto`). Seitdem verwaltet Flyway das Schema (`db/migration`), und das alte Schema ist damit nicht kompatibel. Beim ersten Deploy dieses Stands muss die Datenbank deshalb einmal geleert werden:

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

Die API-Adresse für den Produktions-Build steht in `src/environments/environment.prod.ts`. `src/_redirects` leitet alle Routen auf `index.html` (Single-Page-App).

```bash
cd order-management-frontend
npm ci
npx ng build
netlify deploy --prod --dir=dist/order-management-frontend/browser --site 61edabe6-b160-4b89-b337-f51983aa9d94
```

---

## Checkliste für ein Release

1. CI auf dem Pull Request grün (Backend-Tests, Frontend-Build und -Tests)
2. Pull Request nach `main` mergen
3. Backend veröffentlichen (`git push heroku main`) und Log beobachten
4. Frontend bauen und per `netlify deploy --prod` veröffentlichen
5. Anmeldung mit einem Demo-Zugang testen, z. B. `MREUTER` / `demo1234`
