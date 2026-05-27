# Negotex – High-Value Feature Proposals

**Status:** Entwurf  
**Datum:** März 2026  
**Kontext:** Ergänzende Features für die Positionierung als *Deterministic Process Execution Platform*

---

## 1. Envelope Hash Chains (Tamper-Evident Audit Trail)

Jeder Envelope erhält einen kryptographischen Hash, der eine unveränderbare Kette über die gesamte Prozessinstanz bildet:

```
EnvelopeHash = Hash(previousEnvelopeHash + executionResult + timestamp + pluginVersion)
```

**Warum das wichtig ist:**  
Regulierte Industrien (Banking, Insurance, Healthcare) benötigen nachweisbar manipulationssichere Audit Trails. Basel III, SOX und GDPR-Audits verlangen Nachvollziehbarkeit jeder einzelnen Zustandsänderung. Hash Chains liefern genau das – ohne externe Systeme, ohne Blockchain-Overhead.

**Differenzierung:**  
Weder Camunda noch Temporal bieten natives kryptographisches Chaining. Negotex kann damit als einzige Workflow-Engine tamper-evident Execution nachweisen.

**Architektonischer Fit:**  
Passt direkt auf das bestehende Immutable-Envelope-Modell (ADR-003). Der Hash wird als Metadatum im Envelope mitgeführt und beim Schreiben in TimescaleDB persistiert. Verifikation läuft über einen separaten Audit-Consumer, der die Kette jederzeit prüfen kann.

**Priorisierung:** PoC-Phase – bildet das Fundament für alle Compliance-Features.

---

## 2. Execution Contracts (Governance Policies + Plugin Capability Registry)

Ein einheitliches Vertragsmodell, das Governance Policies und Plugin-Sicherheit zusammenführt. Jeder Node bzw. jedes Plugin deklariert explizit, was es darf und was es braucht.

### 2.1 Governance Policies (Pro Node / Pro Prozess)

```json
{
  "maxExecutionTime": "2s",
  "maxMemoryUsage": "64MB",
  "allowedExternalCalls": ["payment-gateway"],
  "auditRetention": "7y",
  "retryPolicy": "idempotent"
}
```

Policies sind First-Class Citizens – sie werden nicht in Konfigurationsdateien versteckt, sondern sind Teil der Prozessdefinition und damit versioniert, auditierbar und zur Laufzeit durchsetzbar.

### 2.2 Runtime Capability Registry (Pro Plugin)

Jedes Plugin deklariert seine benötigten Capabilities:

| Capability | Beispielwert |
|---|---|
| Network Access | `yes` / `no` |
| Outbound Services Allowed | `["payment-gateway", "credit-bureau"]` |
| CPU Limit | `500m` |
| Memory Limit | `64MB` |
| Filesystem Access | `none` / `read-only` / `read-write` |
| Cryptography | `["AES-256", "SHA-256"]` |

**Warum zusammengehörig:**  
Governance Policies definieren die Regeln auf Prozessebene, die Capability Registry definiert die Fähigkeiten auf Plugin-Ebene. Zur Laufzeit prüft Negotex, ob die deklarierten Capabilities eines Plugins die Governance Policy des Nodes erfüllen. Ein Plugin, das `payment-gateway` aufruft, wird nur in Nodes zugelassen, deren Policy diesen External Call erlaubt.

**Architektonischer Fit:**  
Ergänzt das bestehende Plugin-Modell (pure Functions, ADR-004/005) um ein deklaratives Sicherheitsmodell. Die Contracts werden bei der Topology-Kompilierung validiert – Verstöße werden vor dem Deployment erkannt, nicht erst zur Laufzeit.

**Priorisierung:** PoC-Phase – definiert das Security-Modell von Anfang an.

---

## 3. Node-Level Consistency Contracts

Jede Node-Definition wählt explizit ihr Konsistenzmodell:

- **Deterministic Contract:** Exakt reproduzierbares Ergebnis bei gleichem Input. Kein Netzwerkzugriff, keine Zeitabhängigkeit, keine Randomness. Geeignet für Berechnungen, Validierungen, Transformationen.
- **Eventual Consistency Contract:** Erlaubt Seiteneffekte (externe Calls, DB-Zugriffe). Ergebnis kann bei Re-Execution abweichen. Idempotenz wird vom Plugin garantiert.

Zusätzlich existiert ein Deployment-Level-Schalter:

- **Audit Certification Mode:** Alle Nodes müssen Deterministic Contracts verwenden. Jede Prozessinstanz ist vollständig reproduzierbar. Für regulatorische Zertifizierung und Compliance-Nachweise.

**Warum das wichtig ist:**  
Gibt Process Developers explizite Kontrolle über Garantien pro Node. Architekten können auf einen Blick sehen, welche Nodes deterministisch sind und welche Seiteneffekte haben. Im Audit Certification Mode kann ein Regulator jeden Prozesslauf exakt nachspielen.

**Architektonischer Fit:**  
Nutzt die bestehende Trennung von Handler (pure Function) und Processor (Infrastructure Wrapper). Der Contract wird in der NodeDefinition deklariert und vom Processor zur Laufzeit durchgesetzt.

**Priorisierung:** PoC-Phase – Node-Konfiguration.

---

## 4. Process Replay Debugging (Enterprise Feature)

Eine UI in der Enterprise Control Plane, die vergangene Prozessinstanzen Schritt für Schritt nachspielbar macht:

- Zeitleiste aller Envelope-Transitionen
- Payload-Inspektion pro Node (Input/Output)
- Hash-Chain-Verifikation inline
- Diff-Ansicht bei Re-Execution (Soll vs. Ist)
- Sprung zu beliebigem Zeitpunkt im Prozesslauf

**Voraussetzungen:**  
Envelope Hash Chains (#1) müssen implementiert sein. Die Event-Speicherung in TimescaleDB muss vollständig sein (alle ENTERED/EXITED/FAILED Events mit Payload-Snapshots).

**Warum Enterprise:**  
Hoher Implementierungsaufwand (UI, Event-Replay-Logik, Diff-Engine). Starkes Verkaufsargument für die Enterprise-Lizenz – Operations-Teams und Compliance-Officers in Banken werden dafür bezahlen.

**Priorisierung:** Post-PoC – Enterprise Control Plane Phase.

---

## 5. Positionierung: „Deterministic Process Execution Platform"

Die Features #1–#3 ergeben zusammen ein kohärentes Narrativ für Marketing und Vertrieb:

> **Jeder Prozessschritt ist kryptographisch verifizierbar (#1), jedes Plugin hat einen expliziten Capability-Vertrag (#2), und du wählst pro Node dein Konsistenzmodell (#3).**

Kernbotschaften:

- **Compliance by Architecture** – Audit-Garantien sind keine Konfiguration, sondern Architekturprinzip.
- **Deterministic Execution** – Prozesse, die beweisbar korrekt ablaufen.
- **Enterprise Automation** – Von der Kreditprüfung bis zur Schadensregulierung.

Diese Positionierung grenzt Negotex klar von Camunda (BPMN-fokussiert, aber kein kryptographischer Audit Trail) und Temporal (Code-first, keine BPMN-Compliance) ab.
