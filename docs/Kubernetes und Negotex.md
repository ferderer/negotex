## User
**2026-05-22T09:01:10.204Z**

Was mich im Negotex-/WorkflowForge-Kontext besonders interessiert, ist, wie man Deployment programmatisch steuern kann. Heißt: Der Admin öffnet die Negotex Enterprise Control Plane, und weist die Knoten den K8s‑Nodes und ‑Pods zu. Eine Negotex-Runtime-Instanz wäre immer ein Pod und die Prozess-Knoten kann man beliebig auf diese Runtimes verteilen (gleiche Knotensprache vorausgesetzt – F#-Runtime kann nur F#-Knoten haben). Dann wäre es aber wünschenswert, wenn ECP die Kubernetes-Pod-Deployments selbst anstößt.

## Claude
**2026-05-22T09:02:05.763Z**

Sehr durchdachtes Architekturkonzept — das ist im Wesentlichen ein **Kubernetes Operator**. Lass mich die Fragen der Reihe nach nehmen.

---

**Das Negotex ECP Architekturkonzept**

Was du beschreibst ist präzise ein **Custom Operator mit Custom Resource Definitions (CRDs)**. Das ist der offizielle Kubernetes-Weg für genau diesen Anwendungsfall.

Die Idee: Du erweiterst die Kubernetes API um eigene Ressourcentypen — `NegotexRuntime`, `NegotexNode`, `NegotexWorkflow` — und schreibst einen Controller, der diese Ressourcen beobachtet und daraus echte Pods/Deployments erzeugt. Die ECP redet dann mit der Kubernetes API wie `kubectl`, nur programmatisch.

```
ECP UI
  → erstellt NegotexRuntime-Objekte via K8s API
  → Operator beobachtet diese Objekte
  → Operator erstellt Deployments, Services, ConfigMaps
  → Kubernetes scheduled die Pods
```

**Die CRD-Struktur für dein Szenario**

```yaml
apiVersion: negotex.io/v1
kind: NegotexRuntime
metadata:
  name: fsharp-runtime-1
spec:
  language: fsharp          # Sprachconstraint
  replicas: 2
  resources:
    requests:
      memory: "256Mi"
      cpu: "500m"
  nodes:                    # welche Prozessknoten hier laufen
    - name: pricing-node
      version: "2.1.0"
    - name: validation-node
      version: "1.4.0"
status:
  phase: Running
  podRefs:
    - fsharp-runtime-1-7d9f8b-xkp2q
    - fsharp-runtime-1-7d9f8b-mn4r1
```

```yaml
apiVersion: negotex.io/v1
kind: NegotexNode
metadata:
  name: pricing-node
spec:
  language: fsharp
  image: negotex/fsharp-node:2.1.0
  assignedRuntime: fsharp-runtime-1
```

Der Operator übersetzt `NegotexRuntime` automatisch in ein `Deployment` mit dem richtigen Image, den richtigen Node-Affinitäten, und einem `ConfigMap` der die zugewiesenen Prozessknoten beschreibt.

**Der Operator — programmatische Deployment-Steuerung**

Ein Operator ist ein Controller, der in deinem Cluster läuft und deine CRDs beobachtet. Du schreibst ihn mit dem Operator SDK (Go) oder mit dem Java Operator SDK:

```java
// Java Operator SDK
@ControllerConfiguration
public class NegotexRuntimeReconciler 
    implements Reconciler<NegotexRuntime> {

  private final KubernetesClient client;

  @Override
  public UpdateControl<NegotexRuntime> reconcile(
      NegotexRuntime runtime, Context<NegotexRuntime> ctx) {

    // Gewünschten Zustand berechnen
    Deployment desired = buildDeployment(runtime);
    
    // Ist-Zustand holen
    Optional<Deployment> existing = client.apps()
        .deployments()
        .inNamespace(runtime.getMetadata().getNamespace())
        .withName(runtime.getMetadata().getName())
        .get();
    
    // Differenz ausgleichen
    if (existing.isEmpty()) {
      client.apps().deployments().create(desired);
    } else if (!matches(existing.get(), desired)) {
      client.apps().deployments().patch(desired);
    }
    
    // Status updaten
    runtime.getStatus().setPhase("Running");
    return UpdateControl.updateStatus(runtime);
  }

  private Deployment buildDeployment(NegotexRuntime runtime) {
    return new DeploymentBuilder()
        .withNewMetadata()
          .withName(runtime.getMetadata().getName())
          .withNamespace(runtime.getMetadata().getNamespace())
        .endMetadata()
        .withNewSpec()
          .withReplicas(runtime.getSpec().getReplicas())
          .withNewTemplate()
            .withNewSpec()
              // Sprachconstraint als Node-Selektor
              .addNewContainer()
                .withName("runtime")
                .withImage(imageFor(runtime.getSpec().getLanguage()))
                .withEnv(nodesAsEnvVars(runtime.getSpec().getNodes()))
              .endContainer()
              // F#-Runtime nur auf F#-fähige Nodes
              .withNodeSelector(Map.of(
                  "negotex.io/language", runtime.getSpec().getLanguage()))
            .endSpec()
          .endTemplate()
        .endSpec()
        .build();
  }
}
```

Die ECP selbst braucht dann nur den Kubernetes API Client — sie erstellt und updated `NegotexRuntime`-Objekte, der Operator erledigt den Rest.

**Der Sprachconstraint — Node Labels und Affinity**

Deine Anforderung "F#-Runtime kann nur F#-Knoten haben" ist direkt auf Kubernetes Node Labels abbildbar. Du labelst deine Nodes:

```bash
kubectl label node worker-1 negotex.io/language=fsharp
kubectl label node worker-2 negotex.io/language=csharp
```

Und im Deployment setzt du einen `nodeSelector` oder eine `nodeAffinity`:

```yaml
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
        - matchExpressions:
            - key: negotex.io/language
              operator: In
              values: [fsharp]
```

`required` bedeutet: der Scheduler darf diesen Pod *nur* auf gelabelten Nodes platzieren. Kein passender Node verfügbar → Pod bleibt `Pending`. Das ist die harte Constraint.

---

**Die ECP als Kubernetes-Client**

Die ECP redet direkt mit dem Kubernetes API Server — kein `kubectl` in der Mitte. Fabric8 ist die ausgereifte Java-Bibliothek dafür:

```java
// Fabric8 KubernetesClient
KubernetesClient client = new KubernetesClientBuilder().build();

// NegotexRuntime erstellen (ECP UI-Aktion: "Deploy Runtime")
NegotexRuntime runtime = new NegotexRuntimeBuilder()
    .withNewMetadata()
      .withName("fsharp-runtime-prod-1")
      .withNamespace("negotex-prod")
    .endMetadata()
    .withNewSpec()
      .withLanguage("fsharp")
      .withReplicas(3)
      .withNodes(List.of(
          new NodeAssignment("pricing-node", "2.1.0"),
          new NodeAssignment("validation-node", "1.4.0")))
    .endSpec()
    .build();

client.resource(runtime).create();

// Status beobachten (reaktiv, kein Polling)
client.resources(NegotexRuntime.class)
    .inNamespace("negotex-prod")
    .withName("fsharp-runtime-prod-1")
    .watch(new Watcher<>() {
      public void eventReceived(Action action, NegotexRuntime r) {
        ecpUI.updateRuntimeStatus(r.getStatus().getPhase());
      }
    });
```

Der Watch-Mechanismus ist eine langlebige HTTP-Verbindung zum API Server — Kubernetes pushed Änderungen sobald der Operator den Status updated. Die ECP bekommt Echtzeit-Feedback ohne Polling.

---

**Gesamtbild der Architektur**

```
ECP UI (Admin weist Knoten zu)
  │
  │  erstellt/updated NegotexRuntime CRDs
  ▼
Kubernetes API Server
  │
  │  Watch-Event
  ▼
Negotex Operator (Controller im Cluster)
  │
  ├── erstellt Deployments (Runtime-Pods)
  ├── erstellt Services (Runtime-Erreichbarkeit)
  ├── erstellt ConfigMaps (Knoten-Konfiguration)
  └── enforced Sprachconstraints via NodeAffinity
  
Worker Node (labeled: negotex.io/language=fsharp)
  └── Pod: fsharp-runtime
        ├── Container: negotex-runtime (F#-Engine)
        └── Container: envoy-sidecar (optional: mTLS zwischen Runtimes)
```

Das ist eine saubere, Kubernetes-native Architektur. Der Operator ist der richtige Abstraktionslevel — die ECP denkt in Negotex-Konzepten (Runtimes, Knoten, Sprachen), der Operator übersetzt das in Kubernetes-Konzepte (Deployments, Labels, Services). Kein Anwendungscode muss wissen wie Kubernetes intern funktioniert.

Soll ich als nächstes zeigen wie man das lokal aufsetzt und einen ersten echten Pod deployed — oder direkt tiefer in den Operator-Mechanismus (Reconciliation Loop, Status Management, Fehlerbehandlung)?

## User
**2026-05-22T09:13:44.777Z**

Das ist perfekt. Die Runtimes haben dann Victoria-Exporter als Sidecars, Victoria/TimescaleDB/Kafka und die ECP selbst laufen als separate Pods/Nodes. Und die Bereitstellung von Nodes macht der Admin separat mit Terraform, oder wie sieht das in der Praxis aus?

## Claude
**2026-05-22T09:14:26.738Z**

Genau richtig gedacht — das ist der übliche Split in der Praxis.

---

**Die zwei Ebenen der Infrastruktur**

```
Ebene 1: Cluster-Infrastruktur      → Terraform / Pulumi
Ebene 2: Cluster-Inhalt (Workloads) → Kubernetes / Helm / Operator
```

Diese Trennung ist bewusst und wichtig. Terraform weiß nicht was in Pods läuft. Kubernetes weiß nicht wie VMs entstehen. Beide tun so, als wäre die andere Ebene stabil.

**Ebene 1 — Terraform provisioniert die Nodes**

Terraform erstellt die Infrastruktur: VMs, Netzwerke, Firewall-Regeln, den Kubernetes-Cluster selbst. Bei Cloud-Providern gibt es managed Kubernetes — du beschreibst nur die Node-Pools, der Provider kümmert sich um Control Plane, etcd, Updates:

```hcl
# AWS EKS Beispiel
resource "aws_eks_node_group" "fsharp_workers" {
  cluster_name    = aws_eks_cluster.negotex.name
  node_group_name = "fsharp-workers"
  
  instance_types = ["c6i.xlarge"]
  
  scaling_config {
    desired_size = 3
    min_size     = 1
    max_size     = 10
  }
  
  labels = {
    "negotex.io/language" = "fsharp"
  }
  
  taint {
    key    = "negotex.io/dedicated"
    value  = "fsharp"
    effect = "NO_SCHEDULE"    # nur Pods mit Tolerierung landen hier
  }
}

resource "aws_eks_node_group" "infra_workers" {
  cluster_name    = aws_eks_cluster.negotex.name
  node_group_name = "infra-workers"
  instance_types  = ["r6i.2xlarge"]   # RAM-optimiert für Victoria/Kafka
  
  labels = {
    "negotex.io/role" = "infrastructure"
  }
}
```

Terraform setzt also die Node-Labels direkt beim Erstellen — die Nodes kommen fertig gelabelt in den Cluster. Kein manuelles `kubectl label` nötig.

**Taints** sind dabei wichtig: ein Taint auf einem Node bedeutet "kein normaler Pod landet hier, außer er toleriert das explizit". So stellst du sicher dass deine teuren F#-Nodes nicht mit zufälligen System-Pods vollgestopft werden.

---

**Ebene 2 — Was wie deployed wird**

Deine Infrastruktur-Komponenten (Victoria, Kafka, TimescaleDB, ECP) sind klassische Helm-Charts — etablierte, produktionsreife Pakete die du nur konfigurieren musst:

```bash
# VictoriaMetrics Cluster
helm repo add vm https://victoriametrics.github.io/helm-charts
helm install victoria vm/victoria-metrics-cluster \
  --namespace monitoring \
  --set vminsert.replicaCount=2 \
  --set vmstorage.replicaCount=3 \
  --set vmstorage.persistentVolume.size=100Gi

# Kafka (Strimzi Operator)
helm install strimzi strimzi/strimzi-kafka-operator \
  --namespace kafka

# TimescaleDB
helm install timescale timescale/timescaledb-single \
  --namespace data \
  --set replicaCount=2
```

Kafka läuft idealerweise über den **Strimzi Operator** — der managed Kafka in Kubernetes nativ, inklusive Rolling Updates, Topic-Management, TLS. Das ist dasselbe Operator-Pattern wie dein Negotex-Operator.

---

**Victoria-Exporter als Sidecar — konkret**

Jede Negotex-Runtime bekommt einen `vmagent`-Sidecar, der Metrics scraped und an VictoriaMetrics schickt. Da sie im gleichen Netzwerk-Namespace sitzen, redet `vmagent` über `localhost` mit der Runtime — kein Service-Discovery nötig:

```yaml
containers:
  - name: negotex-runtime
    image: negotex/fsharp-runtime:2.1.0
    ports:
      - containerPort: 8080   # app
      - containerPort: 9090   # metrics endpoint

  - name: vmagent-sidecar
    image: victoriametrics/vmagent:v1.96.0
    args:
      - -remoteWrite.url=http://victoria-vminsert.monitoring:8480/insert/0/prometheus
      - -promscrape.config=/etc/vmagent/config.yaml
    volumeMounts:
      - name: vmagent-config
        mountPath: /etc/vmagent

volumes:
  - name: vmagent-config
    configMap:
      name: vmagent-runtime-config
```

```yaml
# ConfigMap: vmagent scrapet localhost
scrape_configs:
  - job_name: negotex-runtime
    static_configs:
      - targets: ['localhost:9090']
    metric_relabel_configs:
      - source_labels: [__name__]
        target_label: runtime
        replacement: '${POD_NAME}'
```

Der Operator generiert diese ConfigMap automatisch pro Runtime-Instanz — der Admin muss das nicht manuell pflegen.

---

**Wo was landet — Namespace-Struktur**

In der Praxis trennt man Workloads über Kubernetes Namespaces, nicht über separate Cluster (außer für Prod/Staging-Trennung):

```
negotex-prod/
  ├── fsharp-runtime-* (Pods, vom Operator erstellt)
  ├── csharp-runtime-*
  └── negotex-ecp       (ECP selbst als Deployment)

negotex-infra/
  ├── kafka-cluster
  ├── timescaledb
  └── victoria-cluster  (vminsert, vmstorage, vmselect)

monitoring/
  ├── vmagent-daemonset  (Node-Level Metrics)
  └── grafana

kube-system/
  └── (Kubernetes-interne Komponenten)
```

Namespaces geben dir RBAC-Grenzen — der Negotex-Operator darf nur in `negotex-prod` Deployments erstellen, nicht in `negotex-infra`. Das ist eine saubere Security-Boundary.

---

**Das vollständige Toolchain-Bild**

```
Terraform
  └── provisioniert: VMs, Node-Pools, Labels, Taints,
                     Netzwerk, IAM-Rollen, Storage-Classes

Helm (via CI/CD oder ArgoCD)
  └── deployed: Victoria, Kafka, TimescaleDB, ECP,
                Negotex-Operator, Grafana

Negotex-Operator (läuft im Cluster)
  └── managed: NegotexRuntime-Pods, ConfigMaps,
               Services, vmagent-Sidecars

ECP (Admin-UI)
  └── erstellt: NegotexRuntime CRDs
                (Operator übernimmt den Rest)
```

In der Praxis kommt noch **ArgoCD** oder **Flux** dazwischen — das ist GitOps: alle Helm-Charts und Kubernetes-Manifeste liegen in Git, ArgoCD synct den Cluster-Zustand automatisch damit. Kein manuelles `helm install` in Produktion. Der Admin macht einen Git-Commit, ArgoCD deployed.

---

**Was der Admin tatsächlich tut**

```
Neue Sprache (z.B. Python) soll unterstützt werden:

1. Terraform: neuen Node-Pool "python-workers" mit Label
              negotex.io/language=python → terraform apply

2. Git-Commit: neues NegotexRuntime-CRD-Schema für Python,
               neues Container-Image → ArgoCD synct Operator-Update

3. ECP UI: Admin zieht Python-Knoten auf eine Python-Runtime →
           ECP erstellt NegotexRuntime-Objekt →
           Operator deployed Pod auf python-workers Node →
           vmagent-Sidecar scraped automatisch Metrics
```

Der Admin denkt nie in Pods oder Deployments — nur in Runtimes und Knoten. Kubernetes ist vollständig unter der Abstraktionsebene der ECP verborgen. Genau so soll es sein.
