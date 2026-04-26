# Azure Container Apps Ingress Configuration — phase2-as2

**Audience:** EDI/DevOps operator owning the `phase2-as2` Container App and
responsible for confirming inbound AS2 traffic from FGF (`AS2-From:
FGFCAAS21295T` → `AS2-To: PHASE2`) reaches the application listener exactly as
the AS2 protocol requires.

**Scope:** Document the *required* ingress settings for the deployed Container
App, the *expected* TLS termination behavior, and the *verification commands*
that prove the live ingress matches the documented requirements. Each
requirement is grounded in an artifact in this repository or in observable
Azure state — no generic Container Apps guidance is included unless it is
required to make a configuration decision unambiguous.

Out of scope: building a test client, sending an outbound message, or
debugging a failed run. Cross-references to sibling deliverables
(`partner-coordination-FGF.md`, `server-config-verification.md`) are noted
where the same fact is asserted from a different angle.

---

## 0. Ingress port vs. target port — terminology disambiguation

The task constraint phrasing "target port 443" conflates two different ports
that Azure Container Apps treats independently. Both must be correct, but they
mean different things. This section pins down the vocabulary used throughout
this document so verification commands are unambiguous.

| Term used here | Azure CLI flag / property | Value for `phase2-as2` | Visible to FGF? | Source of truth |
|----------------|---------------------------|------------------------|-----------------|-----------------|
| **Public ingress port** | (implicit; always 443 for HTTPS, 80 for HTTP redirect) | `443` | Yes — FGF posts to `https://<fqdn>:443/as2` | Container Apps platform default; not configurable when `--ingress external --transport http` is used |
| **Target port** | `--target-port` / `properties.configuration.ingress.targetPort` | `8080` | No — internal to the Container Apps environment | `deploy/azure-deploy.sh:82`, `deploy/azure-deploy.ps1:85`, `phase2-demo-spring-boot/Dockerfile:23` (`EXPOSE 8080`) |
| **Transport** | `--transport` / `properties.configuration.ingress.transport` | `http` (Container Apps front door negotiates HTTP/1.1 + HTTP/2 to the public client and HTTP/1.1 to the container) | Public side: HTTPS-wrapped HTTP. Internal: plain HTTP. | `deploy/azure-deploy.sh:82` |
| **Allow insecure** | `--allow-insecure` / `properties.configuration.ingress.allowInsecure` | `false` (default — never set) | Yes — port 80 redirects to 443 | Default behavior when flag is omitted in `deploy/azure-deploy.sh:82` |

**Rule of thumb:** "FGF posts to **443**, the front door forwards to the
container on **8080**." Whenever the rest of this document says "target port"
it means **8080**. Whenever it says "ingress port" it means the public **443**.

---

## 1. Required ingress configuration

### 1.1 Authoritative settings (matches `deploy/azure-deploy.sh`)

The single line in the deploy script that defines ingress is:

```bash
# deploy/azure-deploy.sh:82
--ingress external --target-port 8080 --transport http
```

Resolved to the full ingress configuration that must be present on the live
Container App:

| Property | Required value | Why this value (not another) |
|----------|----------------|------------------------------|
| `properties.configuration.ingress.external` | `true` | FGF is an external trading partner reaching the app from the public internet. `internal` would scope the FQDN to the Container Apps environment's VNet only and FGF could not reach it. |
| `properties.configuration.ingress.targetPort` | `8080` | The Spring Boot fat jar listens on 8080 (`Dockerfile:23` `EXPOSE 8080`; default `server.port` for Spring Boot when no `application.properties` overrides it). Setting any other targetPort would route 443 → an unbound port and fail with HTTP 503 from the Container Apps front door. |
| `properties.configuration.ingress.transport` | `http` (also accepted: `auto`) | The container speaks plain HTTP/1.1 inside the environment. `tcp` would bypass HTTP-aware features the AS2 servlet relies on (header parsing, multipart). `http2` would force HTTP/2 cleartext to the container, which Spring Boot's embedded Tomcat does not negotiate without explicit config — leave at `http`. |
| `properties.configuration.ingress.allowInsecure` | `false` (default) | Forces HTTP→HTTPS redirect on the public side. AS2 over plain HTTP is permitted by RFC 4130 §6 but FGF's stack and most modern AS2 senders (Mendelson, Cleo, Axway, IBM Sterling, OpenAS2) refuse to send to `http://`. HTTPS-only also keeps the S/MIME-encrypted payloads inside an additional TLS envelope on the public path. |
| `properties.configuration.ingress.fqdn` | `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io` | Auto-assigned by the Container Apps environment on first creation. Captured in `deploy/.last-deploy.env` as `FQDN`. |
| `properties.configuration.ingress.traffic[0].latestRevision` | `true` with `weight: 100` | Single-revision deployment (`--min-replicas 1 --max-replicas 1` in `azure-deploy.sh:83`). All traffic must hit the latest revision so config-share updates take effect after a restart. |
| `properties.configuration.ingress.ipSecurityRestrictions` | `null` / empty | Per task constraint "No IP allowlist required." Adding restrictions before the FGF interop test would introduce a 403 failure mode that masks AS2-layer issues. Lock down *after* the test passes if desired. |
| `properties.configuration.ingress.clientCertificateMode` | `Ignore` (default) | AS2 authenticates at the message layer via S/MIME signing (FGF signs with `FGFCAAS21295T` private key, server verifies via `fgfcaas21295t` trustedCertEntry — see `server-config-verification.md` §1.1). Transport-layer mTLS is intentionally **not** enabled; it would force FGF to provision an additional client cert that has no AS2-protocol meaning. |
| `properties.configuration.ingress.stickySessions.affinity` | `none` (default) | AS2 is a request/response protocol where each POST is independently authenticated. Sticky sessions provide no benefit and would create cold-start asymmetry once `--max-replicas` > 1. |

### 1.2 What this resolves to for FGF

The single fact FGF needs from this section is the URL:

> `https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2`

Implied by that URL:
- TLS 1.2+ on port 443 (Container Apps platform default; TLS 1.0/1.1 are not
  served).
- A publicly trusted Microsoft-issued certificate covers the FQDN — no manual
  CA trust action required on FGF's side.
- The path `/as2` is mapped to `MyAS2ReceiveServlet`
  (`ServletConfig.java:118`); any other path returns the platform's default
  404 page (or 405 for `GET /as2` because only `POST` is registered —
  `ServletConfig.java:75`).

This URL is repeated verbatim in `partner-coordination-FGF.md` so FGF receives
it in their integration packet.

---

## 2. TLS termination behavior

### 2.1 Where TLS terminates and what that means for AS2

Container Apps terminates TLS at the **environment-level front door** (an
Azure-managed Envoy fleet shared across apps in the environment), then
forwards the decrypted HTTP/1.1 request to the container on the configured
target port. Concretely, for one inbound AS2 message:

```
                 HTTPS (TLS 1.2+)                     HTTP/1.1 cleartext
   FGF  ───────────────────────────► [ACA front door] ──────────► [phase2-as2]
   (any IP)        port 443           (Microsoft-managed cert)      port 8080
                                       envoy-based gateway          Spring Boot + Tomcat
```

Implications for the AS2 flow:

1. **The S/MIME-signed body remains end-to-end signed.** TLS termination
   *does not* alter the request body — Envoy forwards the bytes verbatim.
   `BCCryptoHelper.verify` (called from
   `AS2ReceiverHandler.java`) recomputes the MIC over the exact bytes
   received, so the partner-side signature still verifies. This is the
   property that lets us run AS2 behind a TLS-terminating reverse proxy at
   all.
2. **`X-Forwarded-Proto: https`** and **`X-Forwarded-For: <client ip>`** are
   injected by the front door but the AS2 servlet does not consult them — AS2
   has no concept of "scheme rewriting" because the next-hop URL for an MDN is
   carried in `Receipt-Delivery-Option` (async only) or implied by the
   request's TCP socket (sync — synchronous MDN is the response body). For
   sync MDN (the only mode in scope per task constraints), TLS termination is
   transparent.
3. **No TLS handshake metadata reaches the JVM.** SNI value, cipher suite,
   and client cert (none) are consumed by the front door. The AS2 servlet
   only sees a normal `HttpServletRequest`. There is therefore *no* way to
   restrict by client cert at the JVM layer; do that at ingress
   (`clientCertificateMode = Require`) if ever needed in the future — but
   per §1.1 it is intentionally `Ignore`.
4. **The AS2 message itself is independently signed and (optionally)
   encrypted**, so TLS termination does not weaken the security model. The
   partnership still requires `AS2-From: FGFCAAS21295T` to be signed by the
   private key paired with the `fgfcaas21295t` trustedCertEntry in the
   keystore (see `server-config-verification.md` §1.1). A front-door
   compromise would still not let an attacker forge a valid AS2 message —
   they would also need FGF's private key.

### 2.2 Server certificate served on 443

| Property | Expected value |
|----------|----------------|
| Subject CN / SAN | `*.canadacentral.azurecontainerapps.io` (wildcard) |
| Issuer | `Microsoft Azure RSA TLS Issuing CA <NN>` (publicly trusted) |
| Key | RSA-2048 or ECDSA P-256 (whichever the front door rotates to; trust chain is the same) |
| Validity | Auto-rotated by the platform; never operator-managed |
| ALPN | `h2`, `http/1.1` (HTTP/2 is offered to the public client; the back end is HTTP/1.1) |

No custom domain is configured (none was requested in the deploy script), so
no operator-managed cert is in play. If a custom domain (`as2.fgfbrands.com`,
say) is added later, you must:

1. Add a TXT/CNAME validation record in DNS,
2. `az containerapp hostname add` + `az containerapp hostname bind` with a
   managed cert (or BYO cert),
3. Communicate the new URL to FGF and re-run the post-test verification.

That is a **future** activity. The current FGF interop test uses the
platform-assigned FQDN and the platform wildcard cert.

### 2.3 HTTPS-only enforcement

Public-side enforcement comes from `allowInsecure: false` (the default, not
overridden in `azure-deploy.sh`). Behavior:

- `GET http://<fqdn>/anything` → HTTP/1.1 301 redirect to
  `https://<fqdn>/anything`.
- `POST http://<fqdn>/as2` → HTTP/1.1 301 (with `Location: https://…/as2`).
  Most AS2 senders treat a 301 on POST as a hard error — they will *not*
  follow the redirect with the body re-posted. **Therefore FGF must use the
  `https://` URL from the first byte.** This requirement is reinforced in
  `partner-coordination-FGF.md` §"Endpoint".

Internal-side enforcement is N/A — the container only speaks plain HTTP on
the back end and the network path between the front door and the container
is inside the Container Apps environment's managed network plane.

---

## 3. Verification on the live Container App

All commands assume the operator has run `az login` and sourced
`deploy/.last-deploy.env`:

```bash
source deploy/.last-deploy.env   # exports RG, APP, CAE, SA, ACR, FQDN
```

### 3.1 Ingress configuration matches the requirements

```bash
az containerapp ingress show -g "$RG" -n "$APP" -o jsonc
```

Pass criteria — the output JSON must include:

- [ ] `"external": true`
- [ ] `"targetPort": 8080`
- [ ] `"transport": "Http"` (Azure normalizes the case)
- [ ] `"allowInsecure": false`
- [ ] `"fqdn": "phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io"`
- [ ] `"clientCertificateMode": "Ignore"` (or absent — same effect)
- [ ] `"stickySessions": { "affinity": "none" }` (or absent)
- [ ] `"ipSecurityRestrictions": null` (or `[]`)
- [ ] `"traffic"` array contains one entry with `"latestRevision": true` and
      `"weight": 100`

If any field drifts from the table above, re-apply by re-running
`deploy/azure-deploy.sh` (idempotent for ingress) **or** by patching the
single property:

```bash
# Example: if targetPort got changed
az containerapp ingress update -g "$RG" -n "$APP" --target-port 8080 --transport http
```

### 3.2 Public TLS handshake works and the cert covers the FQDN

```bash
echo | openssl s_client -connect "${FQDN}:443" -servername "$FQDN" 2>/dev/null \
  | openssl x509 -noout -issuer -subject -dates
```

Pass criteria:

- [ ] `subject` contains `*.canadacentral.azurecontainerapps.io` (wildcard)
      or the literal `${FQDN}`.
- [ ] `issuer` starts with `Microsoft Azure RSA TLS Issuing CA` (publicly
      trusted).
- [ ] `notAfter` is in the future.
- [ ] `notBefore` is in the past.

Optional — confirm TLS 1.2+ only:

```bash
# TLS 1.0 must fail
echo | openssl s_client -tls1   -connect "${FQDN}:443" 2>&1 | grep -E "handshake failure|wrong version" >/dev/null && echo "TLS1.0 correctly rejected"
echo | openssl s_client -tls1_1 -connect "${FQDN}:443" 2>&1 | grep -E "handshake failure|wrong version" >/dev/null && echo "TLS1.1 correctly rejected"
echo | openssl s_client -tls1_2 -connect "${FQDN}:443" 2>&1 | grep -E "Verify return code: 0" >/dev/null && echo "TLS1.2 OK"
```

### 3.3 HTTPS-only redirect is in effect

```bash
# Plain HTTP must redirect, not serve
curl -sS -o /dev/null -w "HTTP=%{http_code} Location=%{redirect_url}\n" \
  http://${FQDN}/as2
```

Pass criteria:

- [ ] HTTP code is `301` or `308`.
- [ ] `Location` header points to `https://${FQDN}/as2`.

```bash
# HTTPS must reach the servlet (which 405s a GET — see ServletConfig.java:75)
curl -sS -o /dev/null -w "%{http_code}\n" https://${FQDN}/as2
```

Pass criteria:

- [ ] HTTP code is `405` (Method Not Allowed) — confirms `MyAS2ReceiveServlet`
      is mounted at `/as2` and only handles `POST`. A `404` instead would
      indicate the servlet mapping has drifted; a connection error would
      indicate ingress is broken.

### 3.4 No IP allowlist is in effect

```bash
az containerapp ingress access-restriction list -g "$RG" -n "$APP" -o table
```

Pass criteria:

- [ ] Empty table (no rules). Default-allow is intentional pre-test.

### 3.5 Target port actually wired through to the container

This proves the `8080` in `--target-port` matches what the JVM actually binds.
A mismatch would manifest as Container Apps front door returning HTTP 503 to
FGF with `upstream connect error` in the response body.

```bash
# Container should be in Running state with replicas matching --min-replicas 1
az containerapp replica list -g "$RG" -n "$APP" -o table

# Startup log must show Tomcat bound to 8080 (not the default 80 or some other port)
az containerapp logs show -g "$RG" -n "$APP" --tail 200 \
  | grep -E "Tomcat started on port|Started .+ in [0-9]"
```

Pass criteria:

- [ ] At least one replica in `Running` state.
- [ ] Log line resembling `Tomcat started on port 8080 (http) with context path '/'`.
- [ ] Log line resembling `Started Phase2DemoSpringBootApplication in N.NNN seconds`.

If Tomcat shows a different port, the cause is almost always a stray
`server.port` in the Spring Boot environment — none is set in the current
deploy (no `--env-vars` on `az containerapp create`), so any non-8080 port is
a regression.

---

## 4. Failure modes traceable to ingress misconfiguration

These are the symptoms FGF (or the post-test verification) will see, mapped
back to which ingress setting is wrong. Use this table during triage before
diving into AS2-layer logs.

| Symptom FGF observes | Probable ingress cause | Fix |
|----------------------|-----------------------|-----|
| `Connection refused` | Container app is stopped, scaled to zero, or `external` is `false` (env-internal only). | `az containerapp show -g $RG -n $APP --query properties.runningStatus`; re-run deploy if `external` flipped. |
| TLS handshake fails / cert error | FGF is hitting the wrong FQDN or pinning to a stale cert. Platform cert is auto-rotated. | Reconfirm FQDN from `deploy/.last-deploy.env`; tell FGF to **never** pin to the platform cert (this is documented in `partner-coordination-FGF.md`). |
| HTTP 301/308 with `Location: https://…` returned to a `POST` | FGF posted to `http://` instead of `https://`. `allowInsecure: false` causes the redirect; AS2 senders won't replay the POST. | FGF must use `https://`. No server-side change. |
| HTTP 403 | An access restriction was added (IP allowlist) and FGF's egress IP is not on the list. | Per task constraint, no IP allowlist is in effect — if this appears, someone added one. Run §3.4 to confirm/clear. |
| HTTP 404 on `/as2` (not 405) | Servlet mapping changed (`ServletConfig.java:118`), or app failed to start and Container Apps is serving a default page. | Run §3.5 to check replica state and startup logs. |
| HTTP 405 on `POST /as2` | Either the request actually was a `GET` or the wrong handler is registered. POST is the only registered method (`ServletConfig.java:75`). | Confirm FGF is sending `POST`. Inspect `az containerapp logs` for the request line. |
| HTTP 503 with `upstream connect error` body | `targetPort` mismatch — front door cannot reach 8080. | Run §3.5; check `Tomcat started on port` log line. |
| HTTP 502 / `upstream reset` | Container crashed mid-request. AS2 layer issue, not ingress. | Out of scope for this document; check AS2 logs per `server-config-verification.md`. |
| HTTP 408 / read timeout | Default Container Apps request timeout is 240s. AS2 messages are typically <1s, so this only triggers for a stuck servlet thread. | Out of scope for ingress; investigate at the JVM/AS2 layer. |

---

## 5. Configuration change procedure (if ingress drifts)

If §3 verification reveals drift from the required values, the safe sequence
to restore them is:

1. **Snapshot current state** so the change is auditable:
   ```bash
   az containerapp show -g "$RG" -n "$APP" -o yaml > /tmp/phase2-as2.before.yaml
   ```
2. **Apply only the drifted property** via `az containerapp ingress update`
   rather than re-running the full `azure-deploy.sh` (which also touches
   storage and ACR and is heavier than necessary):
   ```bash
   # Examples — apply only the one that drifted
   az containerapp ingress update -g "$RG" -n "$APP" --target-port 8080
   az containerapp ingress update -g "$RG" -n "$APP" --transport http
   az containerapp ingress update -g "$RG" -n "$APP" --type external
   az containerapp ingress update -g "$RG" -n "$APP" --allow-insecure false
   ```
3. **Re-run §3** verification end-to-end. Ingress changes are applied without
   creating a new revision (they live on the app, not the revision), so no
   restart is required and the AS2 session in the JVM is preserved — useful
   if a partnership has already been auto-filled.
4. **Confirm FGF can still reach the endpoint** with a TLS handshake probe
   (§3.2). FGF need not be involved at this stage — the probe from a
   workstation is sufficient.

If the *FQDN* itself has changed (full re-deploy under a new `$RANDOM`
prefix), this is **not** a drift — it is a new deployment, and FGF must be
notified out-of-band. Update `deploy/.last-deploy.env`, send the new URL to
FGF, and rerun the partner-coordination handshake.

---

## 6. Cross-references

| Concern | This document covers | See also |
|---------|----------------------|----------|
| Public URL FGF posts to | §1.2, §3.2 | `partner-coordination-FGF.md` ("Endpoint") |
| `targetPort` 8080 and Spring Boot binding | §1.1, §3.5 | `phase2-demo-spring-boot/Dockerfile:23`, `ServletConfig.java:118` |
| HTTPS-only / no client cert | §1.1, §2.3 | `server-config-verification.md` §3 |
| TLS termination at Envoy front door | §2.1, §2.2 | `server-config-verification.md` §3.1 |
| No IP allowlist (per task constraint) | §1.1, §3.4 | `server-config-verification.md` §3.2 (c) |
| Keystore aliases, partnership defaults, SHA-256 guardrail | (out of scope here — ingress only) | `server-config-verification.md` §1, §2, §4 |
| AS2 message-level signing (independent of TLS) | §2.1 (note 4) | `partner-coordination-FGF.md` ("Signing"), `server-config-verification.md` §1.1 |

---

## 7. File map for everything cited above

| Path | Purpose |
|------|---------|
| `deploy/azure-deploy.sh` | Authoritative source for `--ingress external --target-port 8080 --transport http` (line 82). |
| `deploy/azure-deploy.ps1` | PowerShell parity script (line 85). |
| `deploy/.last-deploy.env` | Captures `RG`, `APP`, `CAE`, `SA`, `ACR`, `FQDN` from the most recent run; sourced by every command in §3. |
| `phase2-demo-spring-boot/Dockerfile` | `EXPOSE 8080` (line 23) — the contract that justifies `--target-port 8080`. |
| `phase2-demo-spring-boot/src/main/java/com/helger/as2demo/springboot/ServletConfig.java` | `/as2` mapping (line 118), POST-only handler registration (line 75). |
| `partner-coordination-FGF.md` | The FGF-facing summary of the URL and TLS posture this document specifies. |
| `server-config-verification.md` | The keystore/partnership/SHA-256 verification this document deliberately omits. |
