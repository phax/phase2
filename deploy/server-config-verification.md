# Server-side Configuration Verification — phase2-as2 (Azure Container Apps)

**Audience:** EDI/DevOps operator preparing the deployed phase2 AS2 listener for the
inbound test from FGF (`AS2-From: FGFCAAS21295T` → `AS2-To: PHASE2`).

**Scope:** Confirm the *running* container has the right keystore aliases, the right
config wiring (`SelfFillingPartnershipFactory`, XML-driven session), the right
ingress/TLS termination, and that the SHA-256 signing/MIC behavior is understood.
This document is a *verification* checklist — every step lists the expected value
sourced from this repo plus the exact command/observation to confirm it on the live
container.

Out of scope: building a test client, sending an outbound message, or debugging a
failed run. (See sibling deliverables for partner coordination, pre-flight, and
post-test verification.)

---

## 0. Snapshot of the deployed environment

| Item | Value | Source of truth |
|------|-------|-----------------|
| Resource group | `FGF-EDI-SANDBOX` | `deploy/.last-deploy.env` |
| Container Apps env | `phase259805-env` | `deploy/.last-deploy.env` |
| Container app | `phase2-as2` | `deploy/.last-deploy.env` |
| Storage account | `phase259805sa` | `deploy/.last-deploy.env` |
| Public FQDN | `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io` | `deploy/.last-deploy.env` |
| AS2 receive URL | `https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2` | `deploy/azure-deploy.sh` line 124 + `ServletConfig.java:118` |
| MDN receive URL (sync MDN does NOT use this) | `https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2mdn` | `ServletConfig.java:126` |
| Image | `phase259805acr.azurecr.io/phase2-demo-spring-boot:v1` | `deploy/azure-deploy.sh` line 36 |
| Mounted file shares | `config` → `/app/config`, `data` → `/app/data` | `deploy/azure-deploy.sh` lines 95–101 |

If any of these have drifted (re-deploy under a new `$RANDOM` prefix), refresh
them from `deploy/.last-deploy.env` before running the checks below.

---

## 1. Keystore (`/app/config/certs.p12`) — required aliases & properties

### 1.1 Expected state (per task constraints)

| Alias (as queried) | Type | Purpose | Expected SHA-256 fingerprint |
|--------------------|------|---------|------------------------------|
| `phase2`           | `PrivateKeyEntry` | Server (receiver) identity, RSA-2048, sha256WithRSAEncryption, valid 2026-04-25 → 2028-04-24 | `AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74` |
| `fgfcaas21295t`    | `trustedCertEntry` | FGF (sender) public cert used to verify inbound signature, RSA, valid 2026-01-21 → 2027-02-22 | `F1:99:D3:AA:24:0B:57:42:14:28:BF:5F:A2:B0:E2:1D:80:BD:74:4B:9E:CE:24:C6:D9:5C:EE:3C:21:D4:97:FE` |
| `openas2a_alias`, `openas2b_alias` | `PrivateKeyEntry` | Legacy demo keys carried over from the upstream sample. **Not used** by the FGF↔PHASE2 partnership and may be removed in a later cleanup pass; leave them in place for now to avoid keystore reload churn. | (unused) |

> **Why the alias appears lowercase in `keytool` even though the task documents
> them as `PHASE2` and `FGFCAAS21295T`.** Oracle/OpenJDK's SUN PKCS12 keystore
> normalizes aliases to lowercase on store, and `keystore.getCertificate(alias)`
> is case-insensitive. `phase2-lib`'s `AbstractCertificateFactory.getUnifiedAlias`
> is a hook for further unification (`AbstractCertificateFactory.java:262-276`),
> and `CertificateFactory` does **not** override it — i.e. it relies on the JDK's
> built-in case-insensitive matching. So when the inbound message's `AS2-From:
> FGFCAAS21295T` is fed through `SelfFillingPartnershipFactory.ensureUsablePartnership`
> (`SelfFillingPartnershipFactory.java:64-68`) and used as the X509 alias, the
> lookup against the lowercase stored alias still succeeds. **No re-import or
> alias rename is required.**

### 1.2 Verify on the running container

Run from a workstation with `az` logged in to the FGF subscription:

```bash
# Pull the env state
source deploy/.last-deploy.env

# (a) List storage-share contents — quick sanity check
az storage file list --account-name "$SA" --share-name config -o table

# (b) Download the live keystore from the share to verify it byte-for-byte
TMP=$(mktemp -d)
az storage file download --account-name "$SA" --share-name config \
   --path certs.p12 --dest "$TMP/certs.p12" >/dev/null

keytool -list -keystore "$TMP/certs.p12" -storetype PKCS12 -storepass test
```

Expected output (entries may appear in any order; ignore the SHA-1 warnings about
the legacy `openas2a_alias`/`openas2b_alias`):

```
fgfcaas21295t, …, trustedCertEntry,
Certificate fingerprint (SHA-256): F1:99:D3:AA:24:0B:57:42:14:28:BF:5F:A2:B0:E2:1D:80:BD:74:4B:9E:CE:24:C6:D9:5C:EE:3C:21:D4:97:FE
phase2,        …, PrivateKeyEntry,
Certificate fingerprint (SHA-256): AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74
```

Pass criteria:

- [ ] `phase2` appears as `PrivateKeyEntry` with the expected SHA-256 fingerprint.
- [ ] `fgfcaas21295t` appears as `trustedCertEntry` with the expected SHA-256 fingerprint.
- [ ] Keystore password `test` opens it (matches `config.xml` line 24).

If either alias is missing or fingerprints differ, **stop** — re-upload the
correct `phase2-demo-spring-boot/config/certs.p12` to the `config` file share
and restart the revision (`az containerapp revision restart …`). The keystore is
hot-reloaded by `CertificateFactory` every 300 s (`config.xml` line 25
`interval="300"`), so a restart is the deterministic path.

---

## 2. AS2 / receiver IDs and endpoint wiring

### 2.1 AS2 ID is partnership-driven, not server-static

The deployed app uses **`SelfFillingPartnershipFactory`** (see `config.xml`
line 26). That factory has no static `MyCompanyAS2ID` setting — the receiver AS2
ID is whatever the partner addresses the message to. The factory takes
`AS2-From` / `AS2-To` from the inbound headers, and on first contact creates a
partnership with:

- `senderAS2ID = "FGFCAAS21295T"`, `receiverAS2ID = "PHASE2"` (from headers,
  populated in `AS2ReceiverHandler.java:575-579`).
- `senderX509Alias = "FGFCAAS21295T"`, `receiverX509Alias = "PHASE2"`
  (`SelfFillingPartnershipFactory.java:64-68`).
- A unique partnership name `FGFCAAS21295T-PHASE2`.
- A *fallback* signing algorithm of **SHA-1** (`SelfFillingPartnershipFactory.java:78-79`).
  This is the auto-fill default; see §4 for why it must not be relied on for SHA-256.

This means the only things "PHASE2" must match against on the server side are
(a) the keystore alias for the server's PrivateKeyEntry (covered in §1) and
(b) what FGF agrees to send in `AS2-To` (covered in `partner-coordination-FGF.md`).

### 2.2 Servlet wiring (Spring Boot)

| Wired component | Code reference | Expected value |
|-----------------|----------------|----------------|
| Session class | `phase2-demo-spring-boot/src/main/java/com/helger/as2demo/springboot/ServletConfig.java:52` | `new AS2ServletXMLSession(new File("config/config.xml"))` |
| Receive servlet handler | `ServletConfig.java:75-78` | `AS2ReceiveXServletHandlerConstantSession` (constant-session variant; **not** the file-based `AS2ReceiveXServletHandlerFileBasedConfig` referenced generically in the goal text) |
| `/as2` mapping | `ServletConfig.java:118` | `MyAS2ReceiveServlet` → `/as2` |
| `/as2mdn` mapping | `ServletConfig.java:126` | `MyAS2MDNReceiveServlet` → `/as2mdn` (only used for **async** MDN flow, which is out of scope) |
| Base directory for `%home%` resolution | `AS2ServletXMLSession.java:65` | `aFile.getParentFile().getAbsolutePath()` → `/app/config` inside the container |
| Keystore filename resolved | `config.xml:23` `%home%/certs.p12` → `/app/config/certs.p12` | matches the Azure file-share mount |
| Partnerships filename resolved | `config.xml:27` `%home%/partnerships.xml` → `/app/config/partnerships.xml` | file does **not** need to pre-exist with `SelfFillingPartnershipFactory`; partnerships are created in-memory on first contact |

> Note: the goal text mentions `AS2ReceiveXServletHandlerFileBasedConfig`. The
> demo Spring Boot app uses `AS2ReceiveXServletHandlerConstantSession` instead;
> both wrap the same `AS2ServletXMLSession` underneath
> (`AS2ReceiveXServletHandlerFileBasedConfig.java:83`), so behavior is identical
> for our purposes. No code change required.

### 2.3 Verify on the running container

```bash
source deploy/.last-deploy.env

# Confirm the app started and parsed the XML config
az containerapp logs show -g "$RG" -n "$APP" --tail 200 \
  | grep -E "Loading AS2 configuration file|Loading certificates|Loading partnerships|Loaded processor module"
```

Expected log lines (from `AS2ServletXMLSession.java:66, 84, 94, 110`):

```
Loading AS2 configuration file '/app/config/config.xml'
Loading certificates
Loading partnerships
Loading message processor
  Loaded processor module com.helger.phase2.servlet.util.AS2ServletReceiverModule
  Loaded processor module com.helger.phase2.processor.storage.MessageFileModule
  Loaded processor module com.helger.phase2.servlet.util.AS2ServletMDNReceiverModule
```

Pass criteria:

- [ ] All four log lines above appear in the most recent revision's startup log.
- [ ] No `AS2Exception: AS2ServletXMLSession configuration file '…' does not exist!`
      (would indicate the file share isn't mounted at `/app/config`).
- [ ] No `AS2Exception: Undefined tag:` (would indicate config.xml drift from the
      schema in `AS2ServletXMLSession._load`).

Reachability check:

```bash
# Liveness: AS2 servlet must be reachable & must reject GET (it only handles POST)
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2
```

Expected: HTTP 405 (Method Not Allowed) or 404/403 — *anything other than a
connect/TLS error*. The servlet only registers a `POST` handler
(`ServletConfig.java:75`), so a `GET` is the expected negative probe.

---

## 3. TLS termination and ingress

### 3.1 Expected configuration

From `deploy/azure-deploy.sh` line 82 (and the equivalent PowerShell script line 85):

```
--ingress external --target-port 8080 --transport http
```

This means:

- **Public ingress** is HTTPS on **port 443** at the Container Apps FQDN, with
  the platform-managed wildcard certificate for
  `*.canadacentral.azurecontainerapps.io`.
- **TLS terminates at the Container Apps front door**, *not* inside the JVM. The
  Spring Boot container listens HTTP on **port 8080** behind the front door
  (`Dockerfile` line 23 `EXPOSE 8080`).
- **No client-cert / mTLS** is configured. AS2 message-level S/MIME signing
  carries authentication; transport-level TLS is one-way only.
- **No IP allowlist** is configured (per task constraint and per the absence of
  any `--ip-security-restrictions` flag in `azure-deploy.sh`). FGF can reach the
  endpoint from any source IP.

### 3.2 Verify on the running container

```bash
source deploy/.last-deploy.env

# (a) Ingress shape
az containerapp ingress show -g "$RG" -n "$APP" -o table
```

Pass criteria (from the table output):

- [ ] `External` = `True`
- [ ] `TargetPort` = `8080`
- [ ] `Transport` = `Http`
- [ ] `AllowInsecure` = `False` (default — HTTPS-only)
- [ ] FQDN matches `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io`

```bash
# (b) Confirm TLS handshake works and platform cert is presented
echo | openssl s_client -connect "${FQDN}:443" -servername "$FQDN" 2>/dev/null \
  | openssl x509 -noout -issuer -subject -dates
```

Pass criteria:

- [ ] `subject` contains `*.canadacentral.azurecontainerapps.io` or
      `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io`.
- [ ] Issuer is a Microsoft public CA (e.g. `Microsoft Azure RSA TLS Issuing CA …`)
      — i.e. publicly trusted, no manual trust action needed by FGF.
- [ ] `notAfter` is in the future.

```bash
# (c) Confirm no IP allowlist is in effect (would 403 the partner)
az containerapp ingress access-restriction list -g "$RG" -n "$APP" -o table
```

Pass criteria:

- [ ] Empty table (no rules) — the default-allow posture is what we want.

> If you intend to lock this down later, do it *after* the FGF interop test
> succeeds. Adding restrictions before the test introduces an extra failure mode.

---

## 4. SHA-256 signing/MIC algorithm — guidance & guardrails

This is the trickiest piece of the verification because of how
`SelfFillingPartnershipFactory` interacts with MDN MIC computation.

### 4.1 What the codebase actually does

There are three distinct cryptographic operations in the inbound flow, each with
its own algorithm source:

1. **Verifying FGF's signature on the inbound payload.**
   Algorithm is read from the S/MIME structure FGF sent (the `micalg=` parameter
   of the multipart/signed Content-Type and the OID inside the PKCS#7 signature).
   `BCCryptoHelper.verify` selects the algorithm from the signed message itself
   — partnership config is *not* consulted here. ⇒ As long as FGF signs with
   SHA-256, verification will use SHA-256.

2. **Computing the MIC that goes back in the synchronous MDN.**
   This is `AS2Helper.createMICOnReception` (`AS2Helper.java:246-291`). The
   selection logic is:
   ```
   eSigningAlgorithm = DispositionOptions.fromHeader(...).getFirstMICAlg();   // (a)
   if (eSigningAlgorithm == null)                                              // (b)
       eSigningAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull(
                              partnership.getSigningAlgorithm());
   ```
   - (a) is the partner-supplied `Disposition-Notification-Options` header,
     specifically the `signed-receipt-micalg` parameter.
   - (b) is the partnership fallback. With `SelfFillingPartnershipFactory`, that
     fallback is **`SHA-1`** on the very first message
     (`SelfFillingPartnershipFactory.java:78-79`).

3. **Signing the synchronous MDN.**
   Driven by the same disposition options + partnership signing algorithm via
   `AS2Helper.createSyncMDN`/sender pipeline. Same SHA-1 fallback risk.

### 4.2 The single guardrail to communicate to FGF

**FGF must include `signed-receipt-micalg=optional, sha-256` (and
`signed-receipt-protocol=optional, pkcs7-signature`) in the
`Disposition-Notification-Options` request header on every outbound message.**

If they do, both the MIC in the MDN and the MDN signature use SHA-256, MIC will
match, and the MDN will be valid. Standard AS2 senders (Mendelson, OpenAS2,
Cleo, IBM Sterling, Axway, etc.) emit this header by default when SHA-256 is
selected; this is just a "verify it's set, don't suppress it" instruction.

If FGF *omits* the header on the first message, the partnership is auto-filled
with SHA-1 and **subsequent** messages also default to SHA-1 unless the
partnership is reset (delete `/app/config/partnerships.xml` if present, restart
the revision). This is why §4.3 below is run **before** the first FGF message.

This guardrail is repeated verbatim in `partner-coordination-FGF.md` (sibling
deliverable) so FGF receives it in their integration packet.

### 4.3 Verify a clean partnership state before the test

```bash
source deploy/.last-deploy.env

# (a) Confirm there is no stale partnerships.xml carrying a SHA-1 fallback from
#     a previous test. If absent, SelfFillingPartnershipFactory starts empty.
az storage file list --account-name "$SA" --share-name config \
  --query "[?name=='partnerships.xml']" -o table
```

Pass criteria:

- [ ] Empty result *(preferred — first contact is clean)*, **or**
- [ ] `partnerships.xml` exists and a manual inspection (download + open) shows
      either no `FGFCAAS21295T-PHASE2` partnership, **or** that partnership has
      `as2_signing_algorithm="sha-256"`.

If a stale SHA-1 partnership exists, delete the file and restart the revision:

```bash
az storage file delete --account-name "$SA" --share-name config --path partnerships.xml
az containerapp revision restart -g "$RG" -n "$APP" \
  --revision "$(az containerapp revision list -g "$RG" -n "$APP" --query '[0].name' -o tsv)"
```

### 4.4 Verify SHA-256 was used after the test (post-flight, optional preview)

The post-test verification document (sibling deliverable) covers this in detail.
For server-side configuration purposes only, confirm logs show SHA-256:

```bash
az containerapp logs show -g "$RG" -n "$APP" --tail 500 \
  | grep -E "createMICOnReception|signingAlgorithm"
```

Expected line (from `AS2Helper.java:288`):

```
createMICOnReception: signingAlgorithm=sha-256, contentType=…
```

If you see `signingAlgorithm=sha-1` or `signingAlgorithm=null`, the disposition
options header was missing/malformed on FGF's side — go back to §4.2.

---

## 5. End-to-end verification summary (single-pass checklist)

Run through this list once, top to bottom, before declaring the server "ready
for FGF":

- [ ] **§1.2** keystore download → 2 expected aliases with expected fingerprints.
- [ ] **§2.3** startup logs show all four "Loading …" lines and three "Loaded
      processor module" lines.
- [ ] **§2.3** `GET /as2` returns a non-TLS HTTP error (405/404/403), proving
      the servlet is mapped and the listener is alive.
- [ ] **§3.2 (a)** `az containerapp ingress show` reports
      External/HTTPS/TargetPort 8080, AllowInsecure False.
- [ ] **§3.2 (b)** TLS handshake to `${FQDN}:443` succeeds with a Microsoft-issued
      cert covering the FQDN.
- [ ] **§3.2 (c)** No access restrictions configured.
- [ ] **§4.3** No stale `partnerships.xml` in the config share (or a clean one).
- [ ] Partner-coordination doc (`deploy/partner-coordination-FGF.md`) explicitly
      tells FGF to set
      `Disposition-Notification-Options: signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha-256`.

When every box is checked, the deployed `phase2-as2` Container App is
configuration-verified for the FGFCAAS21295T → PHASE2 inbound test with
synchronous SHA-256 MDN.

---

## 6. Quick reference — file map for everything cited above

| Path | Purpose |
|------|---------|
| `phase2-demo-spring-boot/config/config.xml` | Wiring of CertificateFactory, SelfFillingPartnershipFactory, processor modules. |
| `phase2-demo-spring-boot/config/certs.p12` | Source-of-truth keystore uploaded to the `config` file share by `azure-deploy.sh` line 58–61. |
| `phase2-demo-spring-boot/src/main/java/com/helger/as2demo/springboot/ServletConfig.java` | Spring Boot wiring of `/as2` and `/as2mdn` servlets onto `AS2ServletXMLSession`. |
| `phase2-demo-spring-boot/Dockerfile` | Builds the fat jar, exposes port 8080, mounts `/app/config` and `/app/data` at runtime. |
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/util/AS2ServletXMLSession.java` | Resolves `%home%` to `parentFile(config.xml)` (i.e. `/app/config`); loads cert/partnership/processor components. |
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/AS2ReceiveXServletHandlerConstantSession.java` | Handler used by the demo (vs. file-based variant referenced in the goal text). |
| `phase2-lib/src/main/java/com/helger/phase2/partner/SelfFillingPartnershipFactory.java` | Auto-creates partnership; **defaults signing algo to SHA-1** unless overridden — see §4. |
| `phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java` | `createMICOnReception` shows the disposition-options-then-partnership selection order. |
| `phase2-lib/src/main/java/com/helger/phase2/cert/AbstractCertificateFactory.java` | `getUnifiedAlias` hook explaining PKCS12 case-insensitive alias matching. |
| `phase2-lib/src/main/java/com/helger/phase2/processor/receiver/net/AS2ReceiverHandler.java` | Lines 575–582: how `AS2-From`/`AS2-To` populate the partnership before factory lookup. |
| `deploy/azure-deploy.sh` / `deploy/azure-deploy.ps1` | Provisioning script — the source of truth for the ingress, target port, file shares, and FQDN. |
| `deploy/.last-deploy.env` | Captures resource names from the most recent run; sourced by the verification commands above. |
