# Deploying Ollama alongside AI Shift Planner

Ollama is **not** part of the application chart, and that is deliberate. It has a completely
different resource profile — several gigabytes of memory for the model weights, optionally a
GPU, and a slow, stateful first start while a model is pulled. Bundling it would mean scaling
one to scale the other, and would tie a five-minute model pull to every application rollout.

The backend needs no GPU and stays small; it only needs to know where Ollama is
(`ollama.baseUrl` in the chart values).

## The simplest working deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ollama
  namespace: ai
spec:
  replicas: 1
  selector:
    matchLabels: { app: ollama }
  template:
    metadata:
      labels: { app: ollama }
    spec:
      containers:
        - name: ollama
          image: ollama/ollama:latest
          ports:
            - containerPort: 11434
          resources:
            requests:
              cpu: "2"
              memory: 8Gi
            limits:
              memory: 16Gi
          volumeMounts:
            - name: models
              mountPath: /root/.ollama
      volumes:
        - name: models
          persistentVolumeClaim:
            claimName: ollama-models
---
apiVersion: v1
kind: Service
metadata:
  name: ollama
  namespace: ai
spec:
  selector: { app: ollama }
  ports:
    - port: 11434
      targetPort: 11434
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ollama-models
  namespace: ai
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 50Gi
```

The PersistentVolumeClaim matters more than it looks. Without it, every pod restart re-pulls
several gigabytes of model weights, so a routine node drain turns into ten minutes of AI
features being unavailable.

## Pulling the model

```bash
kubectl exec -n ai deploy/ollama -- ollama pull llama3.1
```

Do this once after the first deployment. The application deliberately never pulls a model at
startup: a multi-gigabyte download in a pod's startup path is how a rollout times out.

## Pointing the application at it

```bash
helm upgrade --install ai-shift-planner ./helm/ai-shift-planner \
  --set ollama.baseUrl=http://ollama.ai.svc.cluster.local:11434 \
  --set ollama.model=llama3.1
```

## GPU (optional)

The backend never needs a GPU. Ollama benefits from one substantially. On a cluster with the
NVIDIA device plugin installed:

```yaml
resources:
  limits:
    nvidia.com/gpu: 1
```

plus a `nodeSelector` or toleration matching your GPU node pool. This is worth doing when
interactive chat latency starts to matter; it is not needed to get the system working.

## Running without Ollama at all

Perfectly supported:

```bash
helm upgrade --install ai-shift-planner ./helm/ai-shift-planner --set ollama.enabled=false
```

Scheduling, availability, staffing, manual editing, replacement search and publishing all
work unchanged. Only comment interpretation and chat report
`AI_TEMPORARILY_UNAVAILABLE`.
