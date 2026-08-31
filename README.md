# Software Architecture – Case 1: Versandbeauftragung (Camunda External Task Worker)

Studienarbeit im Modul **Vertiefung Software Engineering: Software Architecture**
FHNW Hochschule für Wirtschaft, Olten – FS 2026 – **Group 6**

Autoren: Kapischan Sriganthan, Mladen Radovanovic, Loic Bösch

---

## Worum geht es?

Die Accolaia AG wickelt ihre Versandbeauftragung heute überwiegend manuell und
papierbasiert ab (Formular A38, interne Post, telefonische Beauftragung der
Spedition). Das führt zu Medienbrüchen, hohem Personaleinsatz und Wartezeiten
an der Hotline.

Im SOLL-Konzept übernimmt die **Camunda Workflow Engine 7** die Orchestrierung
des Versandprozesses. Der Schritt «Spedition anfragen» wird als **External
Service Task** modelliert und von diesem Worker ausgeführt: Er ruft den
REST-Service der Spedition auf und meldet das fachliche Ergebnis an den Prozess
zurück. Ein XOR-Gateway entscheidet anschliessend zwischen Weiterführung und
Fallback auf die Hotline.

Dieses Repository enthält **ausschliesslich den Worker** – nicht das
BPMN-Modell und nicht die Camunda-Installation.

Die vollständige Herleitung (IST-Analyse, Lernfragen, SOLL-Architektur) ist im
Lösungsartefakt `Lösungsartefakt_Case 1_Group6.pdf` dokumentiert.

---

## Architektur

Der Worker ist bewusst in **drei Schichten** getrennt. Ändert die Spedition ihre
API, ist nur Schicht 3 betroffen; Fachlogik und Prozessanbindung bleiben
unverändert.

```
                    ┌──────────────────┐
                    │  CAMUNDA 7       │
                    │  Workflow Engine │
                    └────────┬─────────┘
       Prozessvariablen  │   ▲  accepted, orderId, …
                         ▼   │
  ┌────────────────────────────────────────────────────┐
  │ Schicht 1  ShippingExternalTaskHandler             │
  │            Camunda-Anbindung, complete/handleFailure│
  ├────────────────────────────────────────────────────┤
  │ Schicht 2  ShippingService                         │
  │            Validierung, Mapping, Idempotenz         │
  ├────────────────────────────────────────────────────┤
  │ Schicht 3  SpeditionApiClient                      │
  │            HTTP/JSON, URL, Statuscodes              │
  └─────────────────────────┬──────────────────────────┘
         ShippingRequest │  ▲  JSON-Payload
                         ▼  │
                    ┌──────────────────┐
                    │  Spedition API   │
                    └──────────────────┘
```

| Schicht | Klasse | Verantwortung | Kennt *nicht* |
|---|---|---|---|
| 1 – Orchestrierung | `Worker/ShippingExternalTaskHandler` | Prozessvariablen lesen/schreiben, Task abschliessen, Retry-Steuerung | HTTP, JSON |
| 2 – Fachlogik | `Service/ShippingService` | Fachliche Validierung, Mapping, Idempotenz, fachlich vs. technisch unterscheiden | Camunda, HTTP-Details |
| 3 – Technische Abstraktion | `RestClient/SpeditionApiClient` | REST-Aufruf (POST, JSON, Fehler durchreichen) | Fachlogik, Camunda |

`Worker/ShippingWorker` ist reines Bootstrapping: Client konfigurieren,
Abhängigkeiten verdrahten, Topic abonnieren. Keine Fachlogik.

### DTOs

| DTO | Richtung | Rolle |
|---|---|---|
| `NewConsignment` | Request → Spedition | `destination`, `customerReference`, `recepientPhone`, `weight` |
| `Consignment` | Response ← Spedition | u. a. `orderId`, `pickupdate`, `deliverydate` |
| `ShippingResult` | intern, Service → Handler | `accepted` + optional `orderId`, `pickupdate`, `deliverydate` |

`ShippingResult` entkoppelt den Handler von der konkreten Spedition-Response.

---

## Projektstruktur

```
src/main/java/com/example/software_architecture_case1/
├── DTO/
│   ├── Consignment.java          # Response-DTO der Spedition
│   ├── NewConsignment.java       # Request-DTO an die Spedition
│   └── ShippingResult.java       # internes fachliches Ergebnis
├── RestClient/
│   └── SpeditionApiClient.java   # Schicht 3 – REST/JSON
├── Service/
│   └── ShippingService.java      # Schicht 2 – Fachlogik + Idempotenz
└── Worker/
    ├── ShippingExternalTaskHandler.java  # Schicht 1 – Camunda
    └── ShippingWorker.java               # main() / Bootstrap
```

---

## Prozessvariablen

Die Namen müssen exakt mit dem BPMN-Modell übereinstimmen.

**Eingehend (aus dem Prozess):**

| Variable | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `customerReference` | String | ja | Auftrags-/Kundenreferenz |
| `destination` | String | ja | Lieferadresse |
| `recepientPhone` | String | ja | Telefon Empfänger |
| `weight` | Long | ja | muss > 0 sein, wird auf `Integer` gemappt |
| `email` | String | nein | wird aktuell nur geloggt |

**Zurückgeschrieben (an den Prozess):**

| Variable | Typ | Wann |
|---|---|---|
| `accepted` | Boolean | immer – steuert das XOR-Gateway |
| `orderId` | String | nur bei `accepted = true` (Sendungsnummer der Spedition) |
| `pickupdate` | String | nur bei `accepted = true` |
| `deliverydate` | String | nur bei `accepted = true` |

---

## Fehlerbehandlung

Kern der Lösung ist die konsequente Trennung von **fachlichen** und
**technischen** Fehlern:

| Fall | Erkennung | Reaktion | Prozessfolge |
|---|---|---|---|
| Spedition lehnt ab (z. B. schwer zugängliches Gebiet) | HTTP **501** | `accepted = false`, `complete()`, **kein** Retry | Gateway → Fallback Hotline |
| Ungültige Prozessdaten | `IllegalArgumentException` in Schicht 2 | `handleFailure(retries = 0)` | Incident → manuelle Korrektur |
| Technische Störung (Timeout, Verbindung, HTTP 5xx) | `ProcessingException` / `WebApplicationException` | `handleFailure(retries − 1, 60 s)` | bis zu 3 Versuche, danach Incident |

Eine fachliche Ablehnung ist damit ein **reguläres Prozessergebnis** und kein
Fehler – sie erzeugt weder Retry noch Incident.

### Idempotenz

Ist der POST an die Spedition erfolgreich, schlägt aber anschliessend
`externalTaskService.complete(...)` fehl, liefert Camunda denselben External
Task erneut aus. Ohne Schutz würde ein **Doppelauftrag** entstehen.

Der `ShippingService` hält deshalb eine `ConcurrentHashMap` als
Idempotenz-Cache. Schlüssel ist die **External-Task-ID**, die über Retries
hinweg stabil bleibt. Liegt bereits ein Ergebnis vor, wird es zurückgegeben,
ohne die Spedition erneut aufzurufen. Auch abgelehnte Aufträge werden gecacht.

Die GET-Methode `/consignment/{consignmentId}` der Spedition eignet sich dafür
nicht: Die `consignmentId` entsteht erst als Antwort auf den erfolgreichen
POST – zum Prüfzeitpunkt existiert sie noch nicht.

Bewusst gegen ein JPA-Repository entschieden: Für einen einzelnen
Worker-Prozess ohne horizontale Skalierung ist ein In-Memory-Cache angemessen.
Der Cache ist damit allerdings nicht neustartfest (siehe Einschränkungen).

---

## Voraussetzungen

- **JDK 21**
- Maven (Wrapper `mvnw` / `mvnw.cmd` liegt bei)
- erreichbare **Camunda 7 Engine** mit deployter Prozessdefinition
- erreichbarer **REST-Service der Spedition**

Wichtigste Abhängigkeiten: Spring Boot Parent 4.0.3,
`camunda-external-task-client` 1.3.1, Jersey Client 4.0.2 (JAX-RS), Jackson,
SLF4J Simple.

## Start

```bash
./mvnw clean package
```

```bash
./mvnw exec:java -Dexec.mainClass=com.example.software_architecture_case1.Worker.ShippingWorker
```

Alternativ in der IDE `ShippingWorker.main()` ausführen.

Der Worker läuft anschliessend dauerhaft, pollt das Topic und protokolliert
Prozessvariablen, Spedition-Response, Idempotenz-Treffer und Retry-Verhalten
auf der Konsole.

## Konfiguration

Endpunkte und Zugangsdaten liest `ShippingWorker` aus **Umgebungsvariablen** –
so liegen keine Zugangsdaten im Repository:

| Variable | Standard | Bedeutung |
|---|---|---|
| `CAMUNDA_BASE_URL` | `http://localhost:8080/engine-rest` | REST-API der Camunda-Engine |
| `CAMUNDA_USER` | – | Benutzername für Basic Auth; leer = ohne Authentifizierung |
| `CAMUNDA_PASSWORD` | – | zugehöriges Passwort |
| `SPEDITION_URL` | `http://localhost:8080/v1/consignment/request` | Endpunkt des Speditions-Service |

Beispiel:

```bash
export CAMUNDA_BASE_URL=http://<camunda-host>:8080/engine-rest
```

```bash
export CAMUNDA_USER=group6 CAMUNDA_PASSWORD=<passwort>
```

```bash
export SPEDITION_URL=http://<spedition-host>:8080/v1/consignment/request
```

Basic Auth wird nur aktiviert, wenn `CAMUNDA_USER` gesetzt ist. Fest im Code
stehen weiterhin die betrieblichen Parameter:

| Einstellung | Wert |
|---|---|
| Topic | `group6_transportauftrag` |
| Lock Duration | 1 000 ms |
| Async Response Timeout | 1 000 ms |
| Retries / Backoff | 3 Versuche / 60 000 ms |

---

## Bekannte Einschränkungen

Bewusst getroffene Vereinfachungen im Rahmen des Cases:

- **Zugangsdaten in der Git-Historie.** Camunda-Benutzer und Passwort standen
  ursprünglich im Klartext in `ShippingWorker` und sind deshalb weiterhin in
  älteren Commits enthalten. Der aktuelle Stand liest sie aus
  Umgebungsvariablen; wer die alten Zugangsdaten schützen will, muss sie beim
  Betreiber der Engine ändern lassen.
- **Kein Spring-Boot-Kontext.** Das Projekt nutzt den Spring-Boot-Parent, es
  gibt aber keine mit `@SpringBootApplication` annotierte Klasse. Der
  vorhandene `contextLoads`-Test (`@SpringBootTest`) läuft deshalb nicht
  durch; Einstiegspunkt ist die reguläre `main()`-Methode.
- **Keine fachlichen Unit-Tests.** Validierung, Mapping und Idempotenz sind
  nicht durch Tests abgesichert.
- **Idempotenz-Cache nur im Arbeitsspeicher.** Nach einem Neustart des Workers
  ist der Schutz vor Doppelaufträgen verloren; bei mehreren Worker-Instanzen
  greift er gar nicht.
- **Logging über `System.out`** statt über einen Logger.
- **`email` wird nur geloggt** – der E-Mail-Versand ist gemäss Case ein
  Dummy-Schritt im BPMN-Modell.
- **Manuelle Schritte bleiben bestehen.** Das Kundenverwaltungssystem besitzt
  keine Schnittstelle; die Datenerfassung bleibt laut Case ein User Task.

## Annahmen aus dem Lösungsartefakt

Die API-Modellierung deckt sich nicht vollständig mit der Prozessbeschreibung
des Cases. Für die Implementierung gilt daher:

- Die Auftragsnummer wird **nicht** durch die Auftragsabwicklung im Formular
  A38 ergänzt.
- Die Auftragsnummer entspricht der von der Spedition vergebenen
  Sendungsnummer (`orderId` = Sendungsnummer).
