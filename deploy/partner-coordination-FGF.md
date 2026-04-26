# Partner Coordination Document — FGF ↔ PHASE2 AS2 Inbound Test

**Document purpose:** Hand-off packet for FGF to configure their AS2 sender and exchange a single inbound test message against our Azure-hosted PHASE2 AS2 receiver. Scope is **inbound only** (FGF → PHASE2) with a **synchronous MDN** returned on the same HTTP response.

**Audience:** FGF EDI / B2B integration team
**Owner (PHASE2 side):** Alex Choi — `Alex.Choi@fgfbrands.com`
**Test date target:** 2026-04-24 onward
**Document version:** 1.0

---

## 1. AS2 Identifiers

Both parties MUST use these exact string values in the AS2 message headers. AS2 IDs are **case-sensitive** in the wire protocol (RFC 4130 §6) and our receiver (`SelfFillingPartnershipFactory`) will auto-populate the partnership from the headers of the first inbound message, so any mismatch — including capitalization, leading/trailing whitespace, or quoting — will either be rejected or create a wrong partnership that FGF cannot self-correct without our involvement.

| Role | AS2 ID (exact value) | HTTP Header | Party |
|------|----------------------|-------------|-------|
| **Sender** (originator of the AS2 message and payload) | `FGFCAAS21295T` | `AS2-From` | **FGF** (trading partner) |
| **Receiver** (endpoint receiving the message and issuing the MDN) | `PHASE2` | `AS2-To` | **PHASE2** (our Azure Container Apps service) |

### 1.1 Role Definitions

- **Sender / Originator (`AS2-From: FGFCAAS21295T`)** — FGF. Responsible for:
  - Building the S/MIME signed message (see §4 for algorithm).
  - Populating `AS2-From`, `AS2-To`, `Message-ID`, `Subject`, `Disposition-Notification-To`, and `Disposition-Notification-Options` headers.
  - Signing the message body with FGF's private key whose matching public certificate has been delivered to PHASE2 and imported under keystore alias `fgfcaas21295t`.
  - POSTing the message over HTTPS to the receiver URL in §2.
  - Parsing the synchronous MDN returned on the same HTTP response and verifying its signature against PHASE2's public certificate (delivered as `PHASE2.cer`).

- **Receiver / Recipient (`AS2-To: PHASE2`)** — PHASE2 AS2 service hosted on Azure Container Apps. Responsible for:
  - Accepting the inbound `POST /as2` HTTP request.
  - Looking up FGF's public certificate in keystore alias `fgfcaas21295t` (case-insensitive alias match — the keystore stores aliases lowercase regardless of AS2 ID casing).
  - Verifying the S/MIME signature on the inbound message.
  - Computing the Message Integrity Check (MIC) over the received payload.
  - Generating a signed MDN with PHASE2's private key (alias `phase2`, public fingerprint SHA-256 `AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74`).
  - Returning the MDN **synchronously** in the HTTP response body (not via a separate callback).

### 1.2 Keystore Alias ↔ AS2 ID Mapping (PHASE2 side, for FGF awareness)

On the PHASE2 receiver, the `SelfFillingPartnershipFactory` sets `senderX509Alias = senderAS2ID` and `receiverX509Alias = receiverAS2ID` the first time a new partnership is seen. This means the PKCS12 keystore entries MUST exist with aliases that match the AS2 IDs (Java keystores normalize aliases to lowercase, so matching is effectively case-insensitive):

| AS2 ID (wire value) | Keystore alias | Entry type | Purpose |
|---------------------|----------------|------------|---------|
| `FGFCAAS21295T` | `fgfcaas21295t` | `trustedCertEntry` | FGF public cert, used by PHASE2 to verify FGF's signature |
| `PHASE2` | `phase2` | `PrivateKeyEntry` | PHASE2 private key + cert chain, used by PHASE2 to sign the MDN |

FGF does not need to replicate these alias names internally — FGF's AS2 software can store our `PHASE2.cer` under any alias it prefers. The only cross-party contract is the AS2 ID strings in §1 above.

### 1.3 Do-Not-Change Rules

- Do **not** quote the AS2 IDs in headers (e.g. send `AS2-From: FGFCAAS21295T`, NOT `AS2-From: "FGFCAAS21295T"`). Quoting is legal per RFC 4130 but our `SelfFillingPartnershipFactory` will treat a quoted ID as a different partnership.
- Do **not** substitute `PHASE-2`, `Phase2`, `phase2`, `PHASE2_PROD`, or any other variant. The receiver alias lookup is case-insensitive but the AS2-To header value is logged and stored verbatim in message filenames (`$msg.sender.as2_id$-$msg.receiver.as2_id$-$msg.headers.message-id$`), so consistency matters for traceability.
- Do **not** change the sender AS2 ID between test runs without coordinating first — a changed sender ID triggers a new self-filled partnership with default SHA-1 signing (see §4), which would break a SHA-256 test.

---

## 2. PHASE2 Public Certificate (Server Identity)

This section provides everything FGF needs to install PHASE2's public certificate into their AS2 software so that (a) they can **encrypt outbound payload to PHASE2** if encryption is later enabled, and (b) they can **verify the signature on the synchronous MDN** returned by PHASE2.

The certificate was freshly minted on 2026-04-25 (UTC) for this trading partnership. The previous legacy OpenAS2 test certs (`openas2a_alias`, `openas2b_alias`) are SHA-1 and are **not** the receiver's AS2 identity — ignore them if FGF sees them referenced anywhere.

### 2.1 Certificate Files Delivered to FGF

Two functionally identical files are provided; choose whichever format FGF's AS2 software accepts. Both files contain the same single X.509 v3 self-signed certificate with **no private key**.

| File | Format | Size | Typical consumer |
|------|--------|------|------------------|
| `PHASE2.cer` | DER (binary) | 835 bytes | Windows (double-click imports into cert store), most Java `keytool -importcert`, Mendelson, Cleo, IBM Sterling |
| `PHASE2.pem` | PEM (Base64, `-----BEGIN CERTIFICATE-----`) | 1 208 bytes | OpenSSL, Linux tooling, BoomiAtom, Seeburger, most Node.js / Python libs |

Delivery channel: out-of-band (email attachment to FGF's EDI team contact, or secure file share). **Do not** transmit via the AS2 channel itself before it is established.

### 2.2 Certificate Details — Cross-Check Values

FGF MUST verify that the certificate they installed matches the values below **before** sending any production-signing test. A mismatched or tampered certificate would cause the MDN signature verification on FGF's side to fail, masking a successful MIC computation on ours.

| Field | Value |
|-------|-------|
| Keystore alias (PHASE2 side) | `phase2` (lowercase, PKCS12-normalized) |
| AS2 ID on the wire | `PHASE2` |
| Subject DN | `CN=Vibe, O=Vibe EDI, C=CA` |
| Issuer DN (self-signed) | `CN=Vibe, O=Vibe EDI, C=CA` |
| Serial number (hex) | `1F0AE46433EB26E6057E708FE3C36BD91228D3B3` |
| Version | 3 |
| Signature algorithm | `sha256WithRSAEncryption` |
| Public key algorithm | RSA |
| Public key size | 2048 bits |
| Public exponent | 65537 (0x10001) |
| Not Before | `2026-04-25 01:18:19 UTC` |
| Not After | `2028-04-24 01:18:19 UTC` (≈ 2-year validity) |
| Subject Key Identifier | `82:C3:C1:EA:34:0D:F5:9E:CA:94:DC:55:D3:A3:E3:28:D1:44:87:D4` |
| Authority Key Identifier | `82:C3:C1:EA:34:0D:F5:9E:CA:94:DC:55:D3:A3:E3:28:D1:44:87:D4` |
| Basic Constraints | `CA:TRUE, critical` (self-signed root; used only for this AS2 pairing, not as a general-purpose CA) |

### 2.3 Authoritative Fingerprints

The **SHA-256 fingerprint is the primary identity value**. If any other fingerprint disagrees with the values below after import, the file in FGF's hands is **not** our certificate and MUST be re-obtained via the out-of-band channel.

| Algorithm | Fingerprint | Status |
|-----------|-------------|--------|
| **SHA-256** (primary) | `AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74` | **Authoritative — confirm this value.** |
| SHA-1 (secondary) | `49:3D:44:70:22:44:C8:D3:B3:BD:71:93:13:C0:F5:D2:63:7D:CA:CB` | Legacy UIs may show this; OK to cross-check but not primary. |
| MD5 (informational) | `C9:B6:D8:D1:9E:E3:46:BD:AE:47:F1:79:4F:7B:CF:F1` | Informational only — some AS2 GUIs display this. |

The SHA-256 fingerprint is **also** embedded verbatim in §1.1 of this document as the receiver's cryptographic identity. Those two occurrences MUST match character-for-character (uppercase hex, colon-separated, 32 octets). If they diverge in any future revision of this document, treat the document revision as corrupt and stop.

### 2.4 Export Procedure (PHASE2 side — for audit trail only)

FGF does **not** need to execute these steps. This subsection is a reproducible record of how PHASE2 exported the cert from the production keystore so that the provenance is auditable and the process can be repeated exactly (for example, when the cert rolls over in 2028).

**Environment:** Windows 10/11 host with Git Bash + JDK 17+ on PATH (for `keytool`) and OpenSSL available. Working directory: repository root `phase2/`.

**Inputs:**
- Source keystore: `phase2-demo-spring-boot/config/certs.p12`
- Keystore type: PKCS12
- Keystore password: `test` (same as configured in `phase2-demo-spring-boot/config/config.xml` → `<certificates ... password="test" .../>`)
- Alias to export: `phase2` (`PrivateKeyEntry` — but only the public certificate is extracted; the private key never leaves the keystore)

**Step 1 — Export the public certificate in DER format:**

```bash
keytool -exportcert \
  -keystore phase2-demo-spring-boot/config/certs.p12 \
  -storetype PKCS12 \
  -storepass test \
  -alias phase2 \
  -file deploy/partner-certs/PHASE2.cer
```

Expected output: `Certificate stored in file <deploy/partner-certs/PHASE2.cer>`. The resulting file is binary DER (not Base64).

**Step 2 — Convert DER to PEM (for consumers that prefer Base64):**

```bash
openssl x509 -inform DER -in deploy/partner-certs/PHASE2.cer \
  -outform PEM -out deploy/partner-certs/PHASE2.pem
```

**Step 3 — Capture fingerprints and identity fields for this document:**

```bash
openssl x509 -in deploy/partner-certs/PHASE2.pem -noout \
  -fingerprint -sha256 \
  -subject -issuer -dates -serial
openssl x509 -in deploy/partner-certs/PHASE2.pem -noout -fingerprint -sha1
openssl x509 -in deploy/partner-certs/PHASE2.pem -noout -fingerprint -md5
```

The SHA-256 fingerprint produced by Step 3 is then copied verbatim into §2.3 above and §1.1 of this document.

**Step 4 — Sanity-check that what was exported matches the live keystore entry:**

```bash
keytool -list -v \
  -keystore phase2-demo-spring-boot/config/certs.p12 \
  -storetype PKCS12 -storepass test \
  -alias phase2 | grep -iE "SHA-?256"
```

Expected output (note: `keytool` prints `SHA256` without a hyphen):

```
	 SHA256: AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74
Signature algorithm name: SHA256withRSA
```

The fingerprint line reported by `keytool -list` MUST equal the SHA-256 value in §2.3. If they disagree, the `.cer` file is stale relative to the keystore; re-run Step 1 and rebuild the Azure Files share content so the deployed container sees the same cert that FGF holds.

**Step 5 — Confirm no private-key material leaked:**

The `.cer` / `.pem` files MUST contain exactly one `CERTIFICATE` block and no `PRIVATE KEY`, `ENCRYPTED PRIVATE KEY`, or `RSA PRIVATE KEY` blocks. Quick check:

```bash
grep -c "BEGIN CERTIFICATE" deploy/partner-certs/PHASE2.pem   # must print 1
grep -c "PRIVATE KEY"       deploy/partner-certs/PHASE2.pem   # must print 0
```

If the second command prints anything other than `0`, **stop and do not send the file** — destroy it and re-export with `keytool -exportcert` (which is public-cert-only) rather than any `-importkeystore`-style command that could include private material.

### 2.5 Verification Steps (FGF side — MUST perform before import)

Run at least **one** of the following verification paths after receiving `PHASE2.cer` / `PHASE2.pem`. Path A is preferred because it works on any platform; Path B is a Windows-native fallback; Path C is a Java fallback.

#### Path A — OpenSSL (Linux / macOS / Windows with OpenSSL)

```bash
# Against the PEM file
openssl x509 -in PHASE2.pem -noout -fingerprint -sha256

# Against the DER file
openssl x509 -in PHASE2.cer -inform DER -noout -fingerprint -sha256
```

Expected output (both commands):

```
sha256 Fingerprint=AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74
```

#### Path B — Windows `certutil` (no OpenSSL required)

```cmd
certutil -hashfile PHASE2.cer SHA256
```

`certutil -hashfile` hashes the **file bytes**, not the certificate structure, so for a DER file it is equivalent to the cert fingerprint and MUST produce the same 32-byte hex value as Path A (formatting will be without colons — compare after stripping `:` from the reference). Do **not** run `certutil -hashfile` against the PEM file; the PEM wrapping would change the hash.

Alternatively, double-click `PHASE2.cer` in File Explorer → **Details** tab → select **Thumbprint (SHA-256)** if available (Windows 10 22H2+ / Windows 11 shows SHA-256; older Windows defaults to SHA-1 thumbprint, in which case fall back to the SHA-1 value in §2.3 or use `certutil`).

#### Path C — Java `keytool` (if FGF imports into a Java keystore)

```bash
keytool -printcert -file PHASE2.cer
```

The output includes a line:

```
SHA256: AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74
```

#### Acceptance criteria

- **PASS** — The SHA-256 fingerprint produced by FGF's chosen path equals the reference value in §2.3, **character-for-character, case-insensitive on the hex digits**. Subject DN, issuer DN, serial number, and validity dates in §2.2 also match what FGF's tooling shows.
- **FAIL** — Any fingerprint, subject, issuer, or serial mismatch. In this case FGF MUST NOT import the cert. Contact the PHASE2 owner (§ document header) to re-transmit the file over a verified channel.

### 2.6 Import Hint for FGF's AS2 Software

Alias / label FGF uses internally is free choice (e.g. `phase2_prod`, `fgfbrands_receiver`, `vibeedi_ca`) — the cross-party contract is the AS2 ID string `PHASE2` (§1), not the keystore alias. The cert is self-signed and should be imported as a **trusted certificate entry**, not chained to a public CA.

Partnership configuration on FGF's side for this inbound-only test:

- Partner AS2 ID: `PHASE2`
- Partner URL: see §3 Endpoint URL below
- Partner signature-verification cert: this certificate (`PHASE2.cer` / `PHASE2.pem`)
- Partner encryption cert: same certificate (not required for this first test since encryption is out of scope, but safe to pre-configure)
- MDN return mode: synchronous
- MDN signing algorithm expected from PHASE2: SHA-256 (matches the cert's own `sha256WithRSAEncryption` signature algorithm)

---

## 3. Endpoint URL (Where FGF POSTs the AS2 Message)

This section gives FGF the **exact** transport coordinates to configure as the partner URL on their AS2 sender. There is one and only one URL FGF needs for this inbound test — the receive endpoint that accepts the signed AS2 payload and returns the synchronous MDN on the same HTTP response. No callback URL, no second hop, no IP-allowlist registration is required.

### 3.1 Authoritative Endpoint Values

| Field | Value | Notes |
|-------|-------|-------|
| **Full URL (copy/paste)** | `https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2` | This is the single value FGF should paste into the "Partner URL" / "Receiver URL" / "Endpoint URL" field of their AS2 software. |
| Scheme | `https` | TLS only. Plain `http://` will be rejected by Azure Container Apps ingress (it 301-redirects to HTTPS, but most AS2 clients do not follow redirects on a `POST` body — treat any redirect as a misconfiguration on FGF's side). |
| Host (FQDN) | `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io` | Azure Container Apps auto-generated FQDN. The `mangowave-907b9f53` segment is the unique environment domain assigned to our `phase259805-env` Container Apps environment in the `FGF-EDI-SANDBOX` resource group, region `canadacentral`. |
| Port | `443` (implicit) | Azure Container Apps external ingress is configured `--ingress external --transport http --target-port 8080` per `deploy/azure-deploy.sh` lines 78–84. ACA terminates TLS on **port 443** and proxies cleartext HTTP to the container's port 8080 internally. **FGF connects only to 443.** Do not specify the URL as `:8080` or `:80`. |
| Path | `/as2` | Mounted by `ServletConfig.servletRegistrationBeanAS2()` in `phase2-demo-spring-boot/src/main/java/com/helger/as2demo/springboot/ServletConfig.java` line 118: `new ServletRegistrationBean<>(new MyAS2ReceiveServlet(), "/as2")`. The path is **case-sensitive** — `/AS2`, `/As2`, or trailing-slash `/as2/` will return HTTP 404. |
| HTTP method | `POST` | The receive servlet registers only `EHttpMethod.POST` (`ServletConfig.java` line 75). `GET` will return HTTP 405 / 404 and is sometimes used as a liveness probe — **do not** treat a 405/404 on `GET /as2` as the endpoint being down; that is the documented behaviour. |
| HTTP version | HTTP/1.1 | HTTP/2 is supported by ACA ingress but the AS2 receiver speaks HTTP/1.1 semantics. Either is acceptable; FGF's AS2 stack will negotiate. |

### 3.2 URL Components — Parsed View

For FGF AS2 software that splits the URL into separate fields rather than accepting a single string:

```
                          ┌──────────────────────────────────────────────────────────────────────┐
  scheme  ──►  https      │                                                                      │
  host    ──►  phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io                 │
  port    ──►  443        │  (do not enter — implicit from https://)                              │
  path    ──►  /as2       │                                                                      │
  method  ──►  POST       │                                                                      │
                          └──────────────────────────────────────────────────────────────────────┘
```

If the AS2 product asks separately for "context root" / "base path" and "endpoint name", use:

- Base / context root: `/`
- Endpoint name: `as2`

(There is **no** servlet context prefix in front of `/as2`. The Spring Boot demo registers the servlet at the root context `/`.)

### 3.3 The `/as2mdn` Companion Path — Why FGF Does NOT Use It

The same Spring Boot app also exposes a second path, `/as2mdn` (`ServletConfig.servletRegistrationBeanMDN()` line 126), at:

`https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2mdn`

This path exists only to receive **asynchronous MDNs**, i.e. the case where PHASE2 is the *sender* and the trading partner POSTs a callback MDN back to us on a separate HTTP request. **For this inbound-only test it is irrelevant** — the MDN we return to FGF is synchronous (in the body of the response to FGF's `POST /as2`), so FGF must **not** put `/as2mdn` anywhere in their configuration. Specifically:

- Do **not** set `Receipt-Delivery-Option` on the outbound message (omitting that header is what tells our receiver to return the MDN synchronously).
- Do **not** point `Disposition-Notification-To` at `/as2mdn` on our domain. Use any RFC 5322 mailbox-style value (e.g. `mdn@fgf.example`) — it is informational only when the MDN is synchronous.

### 3.4 TLS / HTTPS Details

| Aspect | Value | Source |
|--------|-------|--------|
| TLS termination point | Azure Container Apps managed ingress front-end | ACA-managed; not configured by us. |
| Server TLS certificate | Wildcard cert for `*.mangowave-907b9f53.canadacentral.azurecontainerapps.io` issued by **Microsoft** (managed by Azure) | Auto-renewed by Azure; FGF does **not** need to pin or import this cert — public CA chain is trusted by default in any modern AS2 client. |
| Minimum TLS version | TLS 1.2 (TLS 1.3 supported and preferred) | Azure Container Apps default. |
| Cipher suites | Azure-managed modern suite list (ECDHE + AEAD) | No SSL 3 / TLS 1.0 / TLS 1.1 / RC4 / 3DES. |
| Server name indication (SNI) | **Required** | FGF's TLS client MUST send SNI with the host value above. Most AS2 stacks do this automatically; if FGF uses a low-level HTTPS library, confirm SNI is enabled. |
| Mutual TLS (client cert) | **Not required** | Our endpoint does not request a client TLS certificate. AS2 message-level signing (S/MIME) is the only authentication, per §4. |

> Note on the TLS cert vs. the AS2 signing cert: the cert covering `*.azurecontainerapps.io` is **completely separate** from the `PHASE2.cer` AS2 identity certificate in §2. The TLS cert authenticates the *transport*; `PHASE2.cer` authenticates the *AS2 identity* via S/MIME. FGF must trust the public-CA-issued TLS cert (automatic) **and** install `PHASE2.cer` as the AS2 partner cert (manual, §2.5 / §2.6).

### 3.5 Network Access Requirements

| Requirement | Status for this test |
|-------------|----------------------|
| Source IP allowlist on PHASE2 side | **Not enforced.** FGF may send from any public-internet egress IP. (Constraint per project scope: "No IP allowlist required".) |
| Destination IP from FGF's egress | Resolve `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io` via DNS at send time. Azure may rotate the underlying IP. **Do not pin a numeric IP** in FGF's firewall — open egress to the FQDN or to the broader `*.azurecontainerapps.io` / Azure Canada Central service tag if FGF's firewall requires explicit egress rules. |
| Outbound port from FGF | TCP 443 to the destination FQDN |
| Inbound from PHASE2 to FGF | **None for this test.** Sync MDN means PHASE2 never opens a connection back to FGF. |
| Proxy / NAT | Transparent — AS2 over HTTPS works through standard outbound HTTPS proxies provided they support `CONNECT` / TLS pass-through and the proxy does not re-sign the body (an MITM proxy that re-signs would break the S/MIME signature on the request body). |

### 3.6 Liveness / Reachability Pre-Flight

Before sending a real signed AS2 message, FGF can confirm reachability with either of the following one-liners. Both are **expected to NOT return HTTP 200** — they are simply checking that the host resolves, the TLS handshake succeeds, and the path exists. The actual POST handler will be exercised by the real test message.

**Option 1 — `curl` (bash / WSL / macOS):**

```bash
curl -v -X POST \
  -H "AS2-Version: 1.2" \
  -H "AS2-From: FGFCAAS21295T" \
  -H "AS2-To: PHASE2" \
  -H "Content-Type: text/plain" \
  --data "ping" \
  https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2
```

Expected: TLS handshake completes, server responds with an HTTP error status (e.g. 4xx) and an AS2-style negative MDN or error body — that confirms the endpoint is **reachable** and the AS2 receiver is alive. A connection refusal, DNS failure, or TLS error is the only outcome that indicates an actual transport problem.

**Option 2 — `Invoke-WebRequest` (PowerShell):**

```powershell
try {
  Invoke-WebRequest -Uri "https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2" `
                    -Method POST -Body "ping" -ContentType "text/plain" -ErrorAction Stop
} catch {
  $_.Exception.Response.StatusCode
}
```

Expected: a status code is printed (any 4xx is fine) — this proves the host is reachable. Any DNS / TLS exception thrown before a status code is returned indicates a network-side issue on FGF's egress.

> Do **not** rely on `ping` (ICMP) for reachability — Azure Container Apps blocks ICMP echo. A failed ping does **not** mean the endpoint is down.

### 3.7 Endpoint Stability and Change Protocol

| Property | Current value | Stability |
|----------|---------------|-----------|
| FQDN | `phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io` | Stable for the lifetime of the `phase259805-env` Azure Container Apps environment. ACA does **not** rotate the auto-generated FQDN. |
| Underlying IPs | Azure-managed pool | May rotate without notice. Resolve via DNS, do not pin. |
| Path `/as2` | Hardcoded in `ServletConfig.java` line 118 | Will not change without a coordinated FGF notification. |
| Port 443 | ACA ingress default | Will not change. |

If the URL changes for any reason (custom domain cut-over, environment rebuild, region migration), PHASE2 will issue a revised version of this document with §3.1 updated and the SHA-256 fingerprint in §2.3 re-confirmed (new TLS cert is auto-renewed and unrelated to AS2 identity, but a fresh document acts as a forcing function for FGF to re-verify everything). **Do not act on a verbal or email-only URL change** — require a versioned document update.

### 3.8 Quick-Reference Card (FGF AS2 Configuration Form)

| AS2 software field | Value to enter |
|---|---|
| Partner / Recipient name | `PHASE2` |
| Partner AS2 ID | `PHASE2` |
| Partner URL | `https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2` |
| HTTP method | `POST` |
| HTTPS / TLS | Enabled (use system trust store) |
| Client TLS cert | None |
| HTTP authentication (basic / bearer) | None |
| MDN delivery mode | Synchronous |
| MDN URL (async only — leave blank for this test) | *(blank)* |

---

## 4. Cryptographic Algorithm Expectations

This section is the single source of truth for the crypto parameters FGF MUST use on the outbound message and the parameters PHASE2 will use on the returned synchronous MDN. **Every parameter in §4.1 and §4.2 is contractual** — mismatches will either trigger an MDN with a failure disposition (e.g. `authentication-failed` or `integrity-check-failed`), or — more dangerously for a first test — succeed server-side while failing FGF's MDN verification, creating an "it looked like it worked but didn't" outcome. Read §4.2 carefully; the default signing algorithm in our receiver's `SelfFillingPartnershipFactory` is **SHA-1**, and if FGF omits the `Disposition-Notification-Options` header the MDN's MIC will be computed with SHA-1 even though FGF signed with SHA-256.

### 4.1 Required Algorithms for This Inbound Test

| Capability | Required value | Rationale / source |
|------------|----------------|--------------------|
| AS2 protocol version | `1.2` (send `AS2-Version: 1.2` header) | Project constraint; matches RFC 4130 §6.2. |
| **Signing algorithm (S/MIME body signature)** | **SHA-256 with RSA** — MIME micalg parameter `sha-256` (RFC 5751 form, hyphenated) | Project constraint. `ECryptoAlgorithmSign.DIGEST_SHA_256` in `phase2-lib/src/main/java/com/helger/phase2/crypto/ECryptoAlgorithmSign.java` line 117, BC algorithm `SHA256WITHRSA`, OID `2.16.840.1.101.3.4.2.1`. |
| Signing is | **Required** (not optional) | Unsigned messages would bypass signature verification and leave authenticity ambiguous — do not use for this first trust-establishment test. |
| Encryption (S/MIME CMS envelope) | **None** for this test — send the message **signed but not encrypted** | Project constraint: "encryption out of scope". Transport-layer TLS (§3.4) already provides confidentiality in flight. See §4.5 for future-enablement notes. |
| Compression (RFC 5402) | **None** for this test | Not required; keeps the first run's failure modes minimal. The receiver supports it if enabled later (see `phase2-lib` `AS2CompressedGenerator`). |
| Payload `Content-Type` | `text/plain` (optionally with `charset=utf-8` or `charset=us-ascii`) | Project constraint + FGF's X12 payload fits; we will treat it as opaque bytes. Do NOT send `application/edi-x12` for this test — the receiver does not content-type-route and a mismatch only muddies diagnostics. |
| Payload format | ANSI X12 | Project constraint. Segment terminator, element separator, and ISA/IEA envelopes are FGF's to define; PHASE2 does not parse X12, it only verifies the signature and stores the file. |
| Character encoding of payload | US-ASCII or UTF-8 (no UTF-16/UTF-32, no EBCDIC) | X12 traditionally uses US-ASCII; UTF-8 works for any ASCII-only content and is the safer default for any byte that strays outside 0x20–0x7E. |
| Asymmetric key algorithm on both ends | RSA | Both our `phase2` key (RSA-2048, §2.2) and FGF's `fgfcaas21295t` cert must be RSA. ECDSA / Ed25519 keys are NOT supported by the `SelfFillingPartnershipFactory` alias-only auto-population path. |
| Minimum RSA key size | **2048 bits** | Matches our `phase2` cert (§2.2). Keys shorter than 2048 bits will fail modern BouncyCastle provider policy on the JDK 17 container. |
| Certificate signature | `sha256WithRSAEncryption` on both FGF's and PHASE2's certs | Our cert uses it (§2.2); FGF's `FGFCAAS21295T.cer` is expected to as well. A SHA-1-signed partner cert would be accepted by BouncyCastle (no `jdk.certpath.disabledAlgorithms` enforcement in the cert chain since the certs are self-signed and trust-anchored), but we will reject it by policy if observed — re-issue with SHA-256. |
| **Sync MDN signing algorithm (PHASE2 → FGF)** | **Same as the sender's requested MIC algorithm** — i.e. SHA-256 when FGF requests SHA-256 per §4.2 | `createMDNData` call in `phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java` line 418 passes `aDispositionOptions.getFirstMICAlg()` as the MDN signing algorithm. The MDN is signed with our `phase2` private key. |
| MDN signature protocol | `pkcs7-signature` (the only value currently supported; see `DispositionOptions.PROTOCOL_PKCS7_SIGNATURE` in `phase2-lib` `DispositionOptions.java` line 73) | Write this value literally into `Disposition-Notification-Options` — see §4.2. |

### 4.2 The `Disposition-Notification-Options` Header — MANDATORY for SHA-256

This is the single most important interoperability point in this document. **Read it before configuring FGF's sender.**

#### 4.2.1 Why this header matters

Our receiver uses `SelfFillingPartnershipFactory`, which auto-populates a new partnership the first time it sees `AS2-From: FGFCAAS21295T` + `AS2-To: PHASE2`. The factory has a fallback signing-algorithm rule in `SelfFillingPartnershipFactory.ensureUsablePartnership`:

```java
// phase2-lib/src/main/java/com/helger/phase2/partner/SelfFillingPartnershipFactory.java, lines 74-79
// Ensure a signing algorithm is present in the partnership. This is
// relevant for MIC calculation, so that the headers are included
// The algorithm itself does not really matter as for sending the algorithm
// is specified anyway and for the MIC it is specified explicitly
if (aPartnership.getSigningAlgorithm () == null)
  aPartnership.setSigningAlgorithm (ECryptoAlgorithmSign.DIGEST_SHA_1);
```

That fallback is **SHA-1**, not SHA-256. Left unchallenged, it would cause our MIC computation on reception (`AS2Helper.createMICOnReception`, `phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java` line 246 onward) to use SHA-1, and our MDN would carry a SHA-1 MIC that FGF's SHA-256-expecting stack would reject as a mismatch.

The fix — required of FGF, not of PHASE2 — is for FGF's sender to include a `Disposition-Notification-Options` header that explicitly requests SHA-256. `createMICOnReception` reads this header first and only falls back to the partnership's (SHA-1) algorithm if the header is absent:

```java
// phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java, lines 250-262 (excerpt)
final String sDispositionOptions = aMsg.getHeader (CHttpHeader.DISPOSITION_NOTIFICATION_OPTIONS);
final DispositionOptions aDispositionOptions = DispositionOptions.createFromString (sDispositionOptions);

ECryptoAlgorithmSign eSigningAlgorithm = aDispositionOptions.getFirstMICAlg ();
if (eSigningAlgorithm == null)
{
  // Try from partnership (#93)
  final String sSigningAlgorithm = aPartnership.getSigningAlgorithm ();
  eSigningAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull (sSigningAlgorithm);
  ...
}
```

So: **when FGF sends a properly-formed `Disposition-Notification-Options` header requesting `sha-256`, everything lines up. When FGF omits it, our receiver silently downgrades the MIC to SHA-1 even though FGF signed with SHA-256.** This will almost certainly break the trip without producing a loud server-side error — the MDN comes back with a SHA-1 MIC that does not match FGF's SHA-256 MIC, and FGF's stack reports "integrity check failed" even though the server logged a successful verify.

#### 4.2.2 Exact header value to send

Send the `Disposition-Notification-Options` header **verbatim as one line** with exactly this content (line wrapping in this document is for readability only — it is a single HTTP header value):

```
Disposition-Notification-Options: signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha-256
```

Components, parsed per RFC 3798 §2.1 and our `DispositionOptions.createFromString` (`phase2-lib` `DispositionOptions.java` line 360):

| Part | Value | Must it be this? |
|------|-------|------------------|
| Parameter 1 attribute | `signed-receipt-protocol` | Yes — case-insensitive. |
| Parameter 1 importance | `optional` (or `required`) | Either works; `optional` is conventional. |
| Parameter 1 value | `pkcs7-signature` | Yes — this is the **only** protocol value the receiver understands (`DispositionOptions.PROTOCOL_PKCS7_SIGNATURE`). |
| Parameter 2 attribute | `signed-receipt-micalg` | Yes — case-insensitive. |
| Parameter 2 importance | `optional` (or `required`) | Either works. |
| Parameter 2 value | `sha-256` (hyphenated, lowercase — RFC 5751 form) | **Yes.** See §4.2.3 for why `sha256` without the hyphen is accepted but not preferred. |

The `;` separates the two parameters; the `,` separates the importance from the value within each parameter; whitespace around `;` `=` and `,` is tolerated.

#### 4.2.3 `sha-256` (hyphenated) vs `sha256` (no hyphen)

`ECryptoAlgorithmSign` defines *both* IDs and both resolve to the same algorithm — SHA-256 with RSA — via `ECryptoAlgorithmSign.getFromIDOrNull(...)` (case-insensitive, `phase2-lib` `ECryptoAlgorithmSign.java` line 321):

| Header value FGF sends | Enum that matches | RFC | Status in this library |
|------------------------|-------------------|-----|-----------------------|
| `sha-256` | `DIGEST_SHA_256` (line 117) | RFC 5751 | **Preferred.** Current standard. |
| `sha256` | `DIGEST_SHA256` (line 88) | RFC 3851 | Deprecated-but-accepted. Marked `@Deprecated` in the enum. |
| `SHA-256`, `Sha-256`, `SHA256`, etc. | Either of the above | — | Accepted (case-insensitive match). |
| `sha1`, `sha-1`, `sha_1` | `DIGEST_SHA1` / `DIGEST_SHA_1` | — | **DO NOT SEND.** Would match a SHA-1 algorithm and make the test regress. |

**Use `sha-256` (hyphenated).** Some partner stacks (Mendelson, IBM Sterling) default to the RFC 5751 form; some (older OpenAS2, some Seeburger builds) default to `sha256`. Both will work against our receiver but the hyphenated form is what this document treats as canonical and what appears in MDN `Received-Content-MIC` responses from us.

#### 4.2.4 Complete request-header set — copy/paste reference

For a signed-only, sync-MDN, SHA-256, X12 inbound message, the minimum AS2 header set FGF must emit (order does not matter; values are illustrative except where noted "MUST"):

```
POST /as2 HTTP/1.1
Host: phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io
AS2-Version: 1.2                                       ; MUST
AS2-From: FGFCAAS21295T                                ; MUST — exact value, no quotes
AS2-To: PHASE2                                         ; MUST — exact value, no quotes
Message-ID: <2026-04-24-FGF-PHASE2-0001@fgfbrands.com> ; MUST — must be RFC 5322 msg-id, angle-bracketed, unique
Subject: FGF → PHASE2 AS2 SHA-256 sync-MDN test       ; informational
Date: Fri, 24 Apr 2026 13:00:00 GMT                   ; MUST — RFC 5322 date
From: edi-ops@fgfbrands.com                           ; informational
Disposition-Notification-To: mdn@fgfbrands.com        ; MUST (value informational for sync MDN but header presence triggers MDN request)
Disposition-Notification-Options: signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha-256   ; MUST — see §4.2.2
Content-Type: multipart/signed; protocol="application/pkcs7-signature"; micalg="sha-256"; boundary="----=_Part_0_xxxxx"       ; MUST — outer S/MIME wrapper
MIME-Version: 1.0                                     ; MUST
```

The S/MIME body then contains the X12 payload as the first MIME part (`Content-Type: text/plain`) and the PKCS7 detached signature as the second MIME part (`Content-Type: application/pkcs7-signature; name="smime.p7s"`). The `micalg` parameter on the outer `multipart/signed` Content-Type MUST match the `signed-receipt-micalg` in §4.2.2 — both are `sha-256`.

#### 4.2.5 What happens if FGF omits `Disposition-Notification-Options`

- The receiver still accepts the message and verifies the signature (that only depends on the `multipart/signed` structure + FGF's public cert).
- `aDispositionOptions.getFirstMICAlg()` returns `null` → code falls back to `aPartnership.getSigningAlgorithm()` → which was set by `SelfFillingPartnershipFactory.ensureUsablePartnership` to `DIGEST_SHA_1`.
- The MIC in the returned MDN is computed with **SHA-1**.
- The MDN is signed — `createSyncMDN` in `AS2Helper.java` lines 387-409 only signs when the disposition options contain `Protocol` and either `isProtocolRequired()` or `hasMICAlg()`. Without `Disposition-Notification-Options`, `aDispositionOptions.getProtocol()` is `null`, so `bSignMDN = false` and the MDN comes back **unsigned**.
- On FGF's side: (a) no MDN signature to verify (regression from the required behaviour), and (b) the MIC in the MDN is SHA-1 instead of the SHA-256 FGF computed on send. FGF's reconciler will log "received MIC does not match sent MIC" or equivalent.

**Outcome: silent regression to a SHA-1-style MDN. Always include the header.**

### 4.3 MIC (Message Integrity Check) Mechanics

The MIC is an integrity digest computed over the signed body (per RFC 4130 §7.3.1). It is independent of the S/MIME signature itself — the signature authenticates the sender, the MIC lets the sender confirm the receiver got the same bytes.

| Aspect | Value / behavior |
|--------|------------------|
| Algorithm in use | SHA-256 (driven by §4.2 `signed-receipt-micalg`). Receiver code: `AS2Helper.createMICOnReception` calls `BCCryptoHelper.calculateMIC` with `ECryptoAlgorithmSign.DIGEST_SHA_256`. |
| Bytes hashed | The pre-signature MIME body as captured during signature verification (see `AS2ReceiverHandler.java` lines 306-322 — the `MICSourceHolder` callback captures the exact bytes BouncyCastle verified). |
| Header inclusion | Headers ARE included in the MIC when the message was signed OR encrypted OR compressed (`AS2Helper.java` lines 270-272: `bIncludeHeadersInMIC = signingAlgorithm != null \|\| encryptAlgorithm != null \|\| compressionType != null`). For this test the message is signed, so the MIME part's `Content-Type` and `Content-Transfer-Encoding` headers ARE included in the hash. FGF's sender must do the same (standard S/MIME behaviour). |
| MIC format on the wire | `<base64-hash>, <micalg>` — e.g. `K0FpH...6Q==, sha-256`. The receiver writes this into the MDN's `Received-Content-MIC` field via `MIC.getAsAS2String()`. |
| MIC field in MDN | `Received-Content-MIC:` machine-disposition field (RFC 3798). |
| What FGF compares against | FGF's own pre-signature computed MIC over the same bytes. If they disagree, the payload was altered in transit — treat as a failed test, do not ignore. |

### 4.4 S/MIME Content-Type Details

The outer `Content-Type` of the HTTP request body is a `multipart/signed` per RFC 5751 §3.4:

```
Content-Type: multipart/signed;
              protocol="application/pkcs7-signature";
              micalg="sha-256";
              boundary="----=_Part_0_<random>"
```

- `protocol` MUST be exactly `application/pkcs7-signature` (quoted string). `application/x-pkcs7-signature` is an older form that some legacy receivers accept but our BouncyCastle-backed stack treats as non-standard — use the unprefixed form.
- `micalg` MUST equal the `signed-receipt-micalg` value (§4.2.2) — both are `sha-256`. A disagreement (e.g. `micalg="sha-1"` on the outer type while `signed-receipt-micalg=optional, sha-256` in the header) is a spec violation that some stacks tolerate but produces unpredictable MDN behaviour — do not ship with the two diverging.
- `boundary` is arbitrary per MIME rules; must be a valid RFC 2046 boundary string and must not appear in the payload bytes.

The two MIME parts inside the envelope:

1. **The signed payload part** — `Content-Type: text/plain` (optionally with `charset=us-ascii` or `charset=utf-8`), `Content-Transfer-Encoding: 7bit` or `binary` for ASCII-only X12. No inner signing, no inner encryption.
2. **The detached signature part** — `Content-Type: application/pkcs7-signature; name="smime.p7s"`, `Content-Transfer-Encoding: base64`, `Content-Disposition: attachment; filename="smime.p7s"`. FGF's AS2 stack generates this automatically from its private key + the payload bytes.

### 4.5 Encryption — Out of Scope for This Test, but Documented for Phase 2

**For this first test FGF MUST NOT encrypt the S/MIME envelope.** Send a cleartext `multipart/signed` message (transport privacy is provided by TLS per §3.4).

That said, the receiver *is* capable of decryption and FGF may want to enable it in a subsequent round — this subsection exists so FGF does not have to guess at the supported parameters when that happens.

#### 4.5.1 Receiver's decryption capability (reference only — do NOT enable for this test)

Supported CMS symmetric algorithms, from `phase2-lib/src/main/java/com/helger/phase2/crypto/ECryptoAlgorithmCrypt.java`:

| Algorithm ID | Notes |
|-------------|-------|
| `3des` | Legacy; do not enable for new work. |
| `cast5`, `idea`, `rc2` | Legacy; do not enable. |
| `aes128-cbc`, `aes192-cbc`, `aes256-cbc` | RFC 5751-compliant AES-CBC. Mendelson accepts these. |
| `aes128-gcm`, `aes192-gcm`, `aes256-gcm` | AES-GCM (added in phase2 4.2.0). Preferred for new AS2 deployments but not universally supported by older partner stacks. |

If and when encryption is enabled, the recommended parameter set is:
- Symmetric algorithm: **`aes256-cbc`** (broadest compatibility) or `aes256-gcm` (better security, if FGF's stack supports it).
- Key-wrap: RSA-OAEP is the RFC 5751-preferred form; legacy RSA-PKCS#1 v1.5 is also accepted by the BouncyCastle `CMSEnvelopedDataParser`. BCCryptoHelper does not constrain this — FGF's side picks.
- Recipient cert: the public cert from `PHASE2.cer` (§2). Our `phase2` RSA-2048 key is both signing and key-transport capable, so no separate encryption cert is needed.

#### 4.5.2 Triggering encryption later

When encryption is enabled, FGF wraps the already-signed message in an additional CMS EnvelopedData layer so the outer `Content-Type` becomes:

```
Content-Type: application/pkcs7-mime; smime-type=enveloped-data; name="smime.p7m"
```

The receiver's `AS2ReceiverHandler.decrypt` method (`AS2ReceiverHandler.java` line 209 onward) will then unwrap using our `phase2` private key from `certs.p12` before signature verification proceeds. No config change is required on the PHASE2 side — the behaviour is automatic when an enveloped message arrives, because `SelfFillingPartnershipFactory` treats encryption as a content-driven concern (the presence of an enveloped-data content type drives the decryption branch).

**For this first test** we are avoiding that wrapper entirely — keep it simple, verify signature + MIC + MDN first, add encryption in a follow-up round.

### 4.6 MDN (Receipt) Cryptographic Properties — What FGF Will Get Back

When FGF's message arrives with §4.2's headers, the synchronous MDN PHASE2 returns on the same HTTP response has these properties:

| MDN property | Value | Source |
|--------------|-------|--------|
| HTTP status | `200 OK` (on success) or `4xx/5xx` with an MDN body carrying a negative disposition (on failure) | `AS2ReceiverHandler` line 419 (`createSyncMDN`). |
| Body `Content-Type` | `multipart/signed; protocol="application/pkcs7-signature"; micalg="sha-256"; boundary="..."` | Same structure as the inbound message, signed by PHASE2's `phase2` private key. |
| Signing key used | `phase2` `PrivateKeyEntry` from `certs.p12` (alias lookup via `senderX509Alias = aMsg.receiverX509Alias = "PHASE2"` → lowercase-normalized to `phase2`, matches the stored alias) | `AS2Helper.createSyncMDN` lines 338-339 swap sender/receiver aliases for the MDN. |
| Signature algorithm | **SHA-256 with RSA** when FGF requested `sha-256` per §4.2 | `AS2Helper.createSyncMDN` line 418 passes `aDispositionOptions.getFirstMICAlg()` directly to `createMDNData`. |
| Include signing cert in signature | Per partnership default; in practice, yes — BouncyCastle embeds the `phase2` cert in the CMS SignedData so FGF can verify without a pre-provisioned cert, *but* FGF should still pre-install `PHASE2.cer` (§2) and pin the trust anchor to it to prevent trust-on-first-use weaknesses. | `AS2Helper.createSyncMDN` lines 397-407; global default `isCryptoSignIncludeCertificateInBodyPart()`. |
| MIC in MDN | `Received-Content-MIC: <base64-sha256>, sha-256` | `AS2Helper.java` lines 380-382 writes `aRealIncomingMIC.getAsAS2String()` into `MDNA_MIC`. |
| Disposition field | `Disposition: automatic-action/MDN-sent-automatically; processed` on success; `...; processed/error: <reason>` on failure | RFC 3798 §3.2.6.3. |
| `AS2-From` / `AS2-To` on MDN | Swapped vs the request — MDN carries `AS2-From: PHASE2`, `AS2-To: FGFCAAS21295T` | `AS2Helper.createSyncMDN` lines 330-331. |
| MDN is encrypted? | **No.** Encryption of MDNs is not configured and would require partnership-level settings we are not enabling. | `createMDNData` path in `AS2Helper` does not invoke the encrypt helper for MDN generation. |

### 4.7 Certificate Signature Algorithm (Cert-on-Cert, Not Message-on-Cert)

Distinct from the S/MIME *message* signing algorithm, each end's X.509 certificate has its own signature algorithm (the CA signature on the cert itself). Both must be SHA-256-based for a clean modern deployment:

| Cert | Signature algorithm | Evidence |
|------|--------------------|----------|
| `PHASE2.cer` (ours) | `sha256WithRSAEncryption` | §2.2 of this document; `keytool -list -v` output includes `Signature algorithm name: SHA256withRSA` (§2.4 Step 4). |
| `FGFCAAS21295T.cer` (FGF's) | Expected: `sha256WithRSAEncryption` | FGF to confirm. If the cert is SHA-1-signed, re-issue before the test — BouncyCastle will accept it because the cert is self-signed and used as a trust anchor, but we will treat it as a policy violation and fail the test. |

FGF can confirm their cert's signature algorithm with:

```bash
openssl x509 -in FGFCAAS21295T.cer -inform DER -noout -text | grep -i "Signature Algorithm"
```

Expected output (appears twice — once in the cert body, once on the outer signature): `Signature Algorithm: sha256WithRSAEncryption`.

### 4.8 Algorithm Configuration Checklist — What FGF Enters in Their AS2 Product

| Product field | Value | Source §  |
|---------------|-------|----------|
| Outbound signing algorithm | `SHA-256` (or label the product uses: `sha-256`, `SHA256`, `RSA-SHA256`) | §4.1 |
| Outbound encryption algorithm | **None / Disabled** | §4.1, §4.5 |
| Outbound compression | **None / Disabled** | §4.1 |
| MDN request mode | Synchronous | §3 |
| MDN signing required (expected from partner) | Yes — PHASE2 will sign the MDN with SHA-256 | §4.6 |
| MIC algorithm (signed-receipt-micalg) | `sha-256` | §4.2 |
| Signed-receipt-protocol | `pkcs7-signature` | §4.2 |
| Payload Content-Type | `text/plain` | §4.1 |
| Content-Transfer-Encoding | `7bit`, `8bit`, or `binary` (product default is fine; avoid `base64` for this test so the X12 file is diff-able in our `data/inbox/` store) | RFC 2045 |
| TLS | Enabled, system trust store, TLS 1.2+ | §3.4 |

### 4.9 Algorithm Mismatch Troubleshooting Matrix (Forward-Reference)

The **post-test verification procedure** deliverable (sibling document) covers detection and remediation of crypto mismatches in depth. As a forward-reference, these are the three highest-probability failure modes specific to §4:

| Symptom on FGF side | Likely cause | Where to look in PHASE2 logs |
|---------------------|--------------|------------------------------|
| "MDN MIC does not match my sent MIC" but server returned 200 | FGF omitted `Disposition-Notification-Options` → receiver used SHA-1 fallback (§4.2.5) | Container log line `createMICOnReception: signingAlgorithm=sha-1` → confirmation of the fallback. Fix: add the header per §4.2.2. |
| "MDN is not signed" (FGF expected a signed receipt) | Same as above — missing `Disposition-Notification-Options` causes `bSignMDN = false` | Container log — absence of `Signing MDN` log line. Fix: same as above. |
| "MDN signature verify failed" (bad signature, not missing) | Stale `PHASE2.cer` on FGF side, or keystore on PHASE2 side rotated without re-exporting the cert | Compare SHA-256 fingerprint from `keytool -list -v -alias phase2` against §2.3. Fix: re-export and re-deliver per §2.4. |

---

## 5. Synchronous MDN Settings (Consolidated Reference)

This section is the **single, consolidated specification** for the MDN (Message Disposition Notification) PHASE2 returns to FGF on the inbound test. It consolidates the MDN-specific guidance scattered across §3.3, §4.2, and §4.6 into one source of truth covering three contractual concerns:

1. **Sync mode** — the MDN is returned **synchronously** in the body of the HTTP response to FGF's `POST /as2`. There is no async callback, no second hop, no follow-up HTTP request.
2. **Signed MDN requirements** — the MDN MUST be signed by PHASE2 with SHA-256/RSA, and the conditions under which our receiver actually signs vs. silently returns an unsigned MDN.
3. **MIC algorithm expectations** — the MIC the MDN carries back to FGF MUST be computed with SHA-256 and MUST match the MIC FGF computed on send.

Every `MUST` in this section maps to a specific receiver behaviour that has been verified against the codebase at `phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java` (`createSyncMDN`, lines 312–429; `createMICOnReception`, lines 246–291) and `phase2-lib/src/main/java/com/helger/phase2/processor/receiver/net/AS2ReceiverHandler.java` (`sendSyncMDN`, lines 402–500).

### 5.1 Sync Mode — Required Behaviour and How To Trigger It

#### 5.1.1 Definition

A **synchronous MDN** is an MDN that PHASE2 returns to FGF **on the same HTTP response** to FGF's `POST /as2` request, in the body of that response, with HTTP status `200 OK`. FGF's HTTP client receives the MDN as the body of the same HTTP transaction it opened to send the AS2 message — there is no separate inbound callback to FGF.

The alternative — an **asynchronous MDN** — would have PHASE2 acknowledge with HTTP `200 OK` (empty body) on the original `POST`, and then open a *second* outbound HTTP request from PHASE2 → FGF carrying the MDN. **This is out of scope for this test** and is explicitly disabled by the configuration described in §5.1.3.

#### 5.1.2 Why sync mode is required for this test

| Reason | Detail |
|--------|--------|
| Project constraint | The task scope explicitly states: "MDN mode is synchronous only." |
| Network simplicity | Sync mode avoids any need to allowlist FGF's inbound MDN endpoint on PHASE2's egress (per the "outbound sending is out of scope" constraint, PHASE2's container is not configured with FGF's MDN URL or credentials). |
| Failure isolation | With sync MDN, a transport failure happens once on FGF's `POST` and is immediately observable by FGF's stack. Async would split the failure surface across two transactions, complicating triage. |
| First-test minimisation | Sync MDN exercises one HTTP transaction end-to-end; async would exercise two and double the diagnostic surface for what is fundamentally a trust-establishment test. |

#### 5.1.3 How sync mode is triggered (the contract on FGF's side)

Our receiver decides sync vs. async on a **per-message basis** by inspecting two HTTP headers on the inbound `POST /as2`. The decision logic lives in `phase2-lib/src/main/java/com/helger/phase2/message/AS2Message.java` lines 113–128:

```java
public boolean isRequestingAsynchMDN ()
{
  // Requesting by partnership?
  // Same as regular MDN + PA_AS2_RECEIPT_OPTION
  if (partnership ().getAS2MDNTo () != null && partnership ().getAS2ReceiptDeliveryOption () != null)
    return true;

  // Requesting by request?
  // Same as regular MDN + HEADER_RECEIPT_DELIVERY_OPTION
  if (containsHeader (CHttpHeader.DISPOSITION_NOTIFICATION_TO) &&
      containsHeader (CHttpHeader.RECEIPT_DELIVERY_OPTION))
    return true;

  // Default: no
  return false;
}
```

So async is triggered **only** when **both** of the following are present on the message:

1. `Disposition-Notification-To` header (which requests *some* MDN, sync or async), **AND**
2. `Receipt-Delivery-Option` header (which says "send it asynchronously to this URL"),

OR when the partnership has both `as2_mdn_to` and `as2_receipt_option` attributes set (which `SelfFillingPartnershipFactory` will **not** set — it does not auto-populate `as2_receipt_option`, see `phase2-lib/src/main/java/com/helger/phase2/partner/SelfFillingPartnershipFactory.java` lines 39–82).

Therefore sync mode is the receiver's **default and required behaviour** as long as FGF follows §5.1.4.

#### 5.1.4 The required FGF-side configuration for sync mode

| Header / setting | Required value | Why |
|------------------|----------------|-----|
| `Disposition-Notification-To` | Any RFC 5322 mailbox value (e.g. `mdn@fgfbrands.com`). The value is **informational only** for sync MDN — the wire location is the HTTP response body, not the email-address content of this header. **The header MUST be present** because it is what tells the receiver to generate an MDN at all (`AS2Message.isRequestingMDN()` line 102–110). | Without this header, the receiver returns `200 OK` with **no MDN body**, leaving FGF unable to verify delivery. |
| `Receipt-Delivery-Option` | **MUST be omitted entirely.** Do not send this header. | Presence of this header (with any value) flips `isRequestingAsynchMDN()` to `true`, which closes the original HTTP response with an empty body and queues an async MDN — async MDN sending is **not configured** on PHASE2 for this test, so FGF would receive nothing back, ever. |
| Software-level "MDN delivery mode" toggle | Set to **Synchronous** (sometimes labelled "On the same connection", "HTTP response", "Sync receipt", or "Inline MDN") | Most AS2 stacks (Mendelson, IBM Sterling, Cleo Lexicom, Axway B2Bi, OpenAS2, BoomiAtom) expose this as a partnership-level radio button. Picking "Synchronous" in the UI is what causes the stack to omit `Receipt-Delivery-Option`. |
| Async MDN URL field | **Leave blank.** | Most products auto-populate this only when async is selected; double-check it is empty before sending. |

#### 5.1.5 What FGF observes on the wire when sync mode succeeds

The HTTP response to FGF's `POST /as2` is an `HTTP/1.1 200 OK` carrying the MDN as a `multipart/signed` body, captured in `AS2ReceiverHandler.sendSyncMDN` lines 444–461:

```java
// otherwise, send sync MDN back on same connection
LOGGER.info ("Sending back sync MDN [" + ... + "]" + ... );

// Get data and therefore content length for sync MDN
try (final NonBlockingByteArrayOutputStream aData = ...)
{
  final MimeBodyPart aPart = aMdn.getData ();
  StreamHelper.copyInputStreamToOutputStream (aPart.getInputStream (), aData);
  aMdn.headers ().setContentLength (aData.size ());

  // start HTTP response
  aResponseHandler.sendHttpResponse (CHttp.HTTP_OK, aMdn.headers (), aData);
}
```

In practice, FGF's HTTP client reads:

```
HTTP/1.1 200 OK
Date: <RFC 5322 date>
Server: phase2/<version>
MIME-Version: 1.0
AS2-Version: 1.2
AS2-From: PHASE2                                    ; swapped vs. request
AS2-To: FGFCAAS21295T                               ; swapped vs. request
Message-ID: <new-mdn-message-id@phase2-as2.…>
Subject: Your Requested MDN Response                ; or per partnership.MDNSubject
Content-Type: multipart/signed; protocol="application/pkcs7-signature"; micalg="sha-256"; boundary="…"
Content-Transfer-Encoding: 7bit
Content-Length: <bytes>

<MIME body containing the human-readable text + machine-disposition + pkcs7 signature>
```

(MDN header construction: `AS2Helper.createSyncMDN` lines 326–334. AS2-From/AS2-To swap: lines 330–331. Subject default: lines 357–360. Content-Length set in `sendSyncMDN` line 457.)

#### 5.1.6 What FGF observes if FGF accidentally requested async

If FGF's stack sends `Receipt-Delivery-Option: <some-url>` on the inbound message, our receiver instead returns:

```
HTTP/1.1 200 OK
Content-Length: 0

<empty body>
```

(see `AS2ReceiverHandler.sendSyncMDN` lines 425–431) and then attempts to dispatch the MDN via `IProcessorSenderModule.DO_SEND_ASYNC_MDN`. **That async sender is not deployed on this container** (see `phase2-demo-spring-boot/config/config.xml` — only `AS2ServletReceiverModule`, `MessageFileModule`, and `AS2ServletMDNReceiverModule` are configured). The result is that FGF's stack reports "MDN not received within timeout" while the PHASE2 container logs an `AS2NoModuleException` for the async dispatch attempt. Recovery is straightforward: FGF removes `Receipt-Delivery-Option` from their partnership config and resends. **This is a pure FGF-side configuration issue and requires no PHASE2-side change.**

### 5.2 Signed MDN Requirements

The MDN PHASE2 returns MUST be **signed** by PHASE2's `phase2` private key with SHA-256/RSA, and FGF's stack MUST verify that signature against the `PHASE2.cer` certificate from §2 before accepting the MDN. An unsigned MDN is a **regression** for this test — see §5.2.4 for why and how it can happen.

#### 5.2.1 The conditions under which our receiver signs the MDN

The signing decision is made in `AS2Helper.createSyncMDN` lines 384–409:

```java
final String sDispositionOptions = aMsg.getHeader (CHttpHeader.DISPOSITION_NOTIFICATION_OPTIONS);
final DispositionOptions aDispositionOptions = DispositionOptions.createFromString (sDispositionOptions);

boolean bSignMDN = false;
boolean bIncludeCertificateInSignedContent = false;
if (aDispositionOptions.getProtocol () != null)
{
  if (aDispositionOptions.isProtocolRequired () || aDispositionOptions.hasMICAlg ())
  {
    // Sign if required or if optional and a MIC algorithm is present
    bSignMDN = true;
    ...
  }
}
```

Translating that into a contract:

| Inbound `Disposition-Notification-Options` header on FGF's `POST /as2` | `getProtocol()` | `isProtocolRequired() \|\| hasMICAlg()` | Outcome |
|------------------------------------------------------------------------|-----------------|-----------------------------------------|---------|
| **Header sent per §4.2.2** (`signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha-256`) | `pkcs7-signature` (non-null) | `true` (because `signed-receipt-micalg` is present → `hasMICAlg()`) | **`bSignMDN = true` — MDN is signed with SHA-256.** ✅ This is the required path. |
| Header sent with only `signed-receipt-protocol=required, pkcs7-signature` (no micalg) | `pkcs7-signature` (non-null) | `true` (because `isProtocolRequired() == true`) | `bSignMDN = true` — MDN is signed. **But** without a micalg, the MDN signing algorithm falls back to whatever `aDispositionOptions.getFirstMICAlg()` returns (which is `null`), and `createMDNData` is called with a null `eMICAlg` parameter → BCCryptoHelper picks the default. Avoid this path; always include `signed-receipt-micalg`. |
| Header sent with only `signed-receipt-micalg=optional, sha-256` (no protocol) | `null` | n/a (the outer `if` short-circuits) | **`bSignMDN = false` — MDN is UNSIGNED.** ❌ Regression. |
| Header omitted entirely | `null` | n/a | **`bSignMDN = false` — MDN is UNSIGNED.** ❌ Regression — silent because the AS2 transaction otherwise looks successful. |

**Net contract for FGF**: send the full `Disposition-Notification-Options` header per §4.2.2 verbatim. Both parameters (`signed-receipt-protocol` and `signed-receipt-micalg`) MUST be present.

#### 5.2.2 What "signed MDN" actually looks like on the wire

When `bSignMDN = true`, the receiver invokes `BCCryptoHelper.sign(...)` (called from `AS2Helper.createMDNData` lines 209–217) on the human-readable + machine-disposition MIME parts using the `phase2` private key, with `eMICAlg = aDispositionOptions.getFirstMICAlg() = ECryptoAlgorithmSign.DIGEST_SHA_256` (resolved from FGF's `signed-receipt-micalg=…, sha-256`).

The resulting MDN body is a `multipart/signed` envelope structured exactly like the inbound message's S/MIME wrapper:

```
Content-Type: multipart/signed;
              protocol="application/pkcs7-signature";
              micalg="sha-256";
              boundary="----=_Part_<random>"

------=_Part_<random>
Content-Type: multipart/report; report-type=disposition-notification; boundary="...inner..."

--...inner...
Content-Type: text/plain; charset=us-ascii
Content-Transfer-Encoding: 7bit

The message sent to recipient PHASE2 has been received,
the EDI Interchange was successfully decoded …

--...inner...
Content-Type: message/disposition-notification

Reporting-UA: phase2/<version>@<dest-ip>:<dest-port>
Original-Recipient: rfc822; PHASE2
Final-Recipient: rfc822; PHASE2
Original-Message-ID: <fgf-message-id@…>
Disposition: automatic-action/MDN-sent-automatically; processed
Received-Content-MIC: <base64-of-sha256>, sha-256
--...inner...--
------=_Part_<random>
Content-Type: application/pkcs7-signature; name="smime.p7s"
Content-Transfer-Encoding: base64
Content-Disposition: attachment; filename="smime.p7s"

<base64-encoded PKCS#7 detached signature over the multipart/report part>
------=_Part_<random>--
```

Sources: outer `multipart/signed` shape from `BCCryptoHelper.sign`; machine-disposition fields from `AS2Helper.createSyncMDN` lines 367–377 (`MDNA_REPORTING_UA`, `MDNA_ORIG_RECIPIENT`, `MDNA_FINAL_RECIPIENT`, `MDNA_ORIG_MESSAGEID`, `MDNA_DISPOSITION`); MIC field from line 382 (`MDNA_MIC`).

#### 5.2.3 What FGF must do to verify the signed MDN

| Step | Action | Notes |
|------|--------|-------|
| 1 | Parse the `multipart/signed` response body. | Standard S/MIME parsing — any AS2 stack does this automatically. |
| 2 | Extract the detached `application/pkcs7-signature` MIME part. | The `boundary` parameter on the outer Content-Type identifies the part separators. |
| 3 | Verify the PKCS#7 signature against **PHASE2's public certificate** (the cert delivered as `PHASE2.cer` per §2, fingerprint SHA-256 `AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74`). | The signing cert is also embedded in the PKCS#7 SignedData by default (per `aSession.isCryptoSignIncludeCertificateInBodyPart()`, `AS2Helper.createSyncMDN` line 406), but **FGF MUST validate against the pre-installed `PHASE2.cer`**, not the embedded copy alone — otherwise an attacker who replaced our MDN response could substitute their own cert and signature. Trust-on-first-use is unsafe for AS2. |
| 4 | Confirm the SHA-256 fingerprint of the verifying cert matches §2.3. | Some AS2 products do this automatically when the cert is configured as the partner's signing cert; some require manual pinning. |
| 5 | Confirm the MDN signature algorithm is SHA-256 (RSA). | Look for `Signature Algorithm: sha256WithRSAEncryption` in OpenSSL output, or the equivalent display in FGF's AS2 product. SHA-1 here is a regression — see §5.2.4. |

#### 5.2.4 Failure mode: silently unsigned MDN

The receiver code path for signing is **silent on failure to satisfy preconditions**: if the inbound `Disposition-Notification-Options` header is missing or malformed, `bSignMDN` stays `false` and the MDN body is set directly from the unsigned report (`AS2Helper.createMDNData` lines 230–234):

```java
else
{
  // No signing needed
  aMdn.setData (aReport);
}
```

The MDN still has HTTP `200 OK`, the `multipart/report` content, the disposition field, and the MIC — but the outer Content-Type is `multipart/report` (not `multipart/signed`) and there is no `application/pkcs7-signature` part at all. FGF's stack will (correctly) flag this as an unsigned MDN and either reject it outright or accept it with a warning. **Either outcome is a test failure** — the test contract requires a signed MDN.

The single fix is FGF's side: ensure §4.2.2's `Disposition-Notification-Options` header is sent correctly. There is no PHASE2-side toggle to enable "always sign MDN" — the receiver intentionally honours the sender's signed-receipt-protocol request.

#### 5.2.5 PHASE2-side log markers for signed-vs-unsigned

| Log line (from `AS2Helper.createMDNData` line 220) | Meaning |
|----------------------------------------------------|---------|
| `Successfully signed outgoing MDN message …` | Signed MDN dispatched. ✅ |
| (absence of the above line) + `…` proceeds normally | MDN was emitted unsigned. ❌ Look at the message's `Disposition-Notification-Options` header in the request log. |
| `Failed to sign MDN - using an unsigned MDN instead` (line 226) | Signing **was attempted** but failed because the `phase2` PrivateKeyEntry could not be retrieved (e.g. keystore alias drift or the cert expired). This is a PHASE2-side configuration regression — investigate the keystore (§2.4) and `certs.p12` mount. |

### 5.3 MIC Algorithm Expectations

The **Message Integrity Check (MIC)** is a digest the receiver computes over the bytes it actually received and reports back to the sender in the MDN's `Received-Content-MIC` field. The sender compares it against the MIC it computed when it built the message. A match is the only proof the receiver got the same bytes the sender sent — and the only protection against bit-flips, MIME-rewriting proxies, or transcoding glitches that the S/MIME signature alone cannot catch (because the signature is computed over the same potentially-mangled bytes the receiver verifies).

#### 5.3.1 Required MIC algorithm: SHA-256

For this test the MIC algorithm in the MDN MUST be **SHA-256**, reported on the wire as the canonical RFC 5751 form `sha-256` (hyphenated, lowercase). Concretely, FGF's stack should see in the MDN body:

```
Received-Content-MIC: <43 base64 chars + ==>, sha-256
```

(Field source: `AS2Helper.createSyncMDN` line 382, `aMDN.attrs().putIn(AS2MessageMDN.MDNA_MIC, aRealIncomingMIC.getAsAS2String())`. The format is `<base64-hash>, <micalg>` — see `MIC.getAsAS2String()` in `phase2-lib/src/main/java/com/helger/phase2/disposition/MIC.java`.)

#### 5.3.2 How the receiver picks the MIC algorithm

`AS2Helper.createMICOnReception` lines 246–291 implements a strict precedence order:

```
1. Read aMsg.getHeader("Disposition-Notification-Options")
2. Parse the header → DispositionOptions object
3. eSigningAlgorithm = aDispositionOptions.getFirstMICAlg()         // FROM THE HEADER FGF SENT
4. if (eSigningAlgorithm == null):                                   // header missing or no micalg
       eSigningAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull(
                              aPartnership.getSigningAlgorithm())    // PARTNERSHIP FALLBACK
5. if (eSigningAlgorithm == null): return null                       // no MIC at all
6. calculate MIC with the chosen algorithm
```

Because our deployed `SelfFillingPartnershipFactory` populates the partnership signing algorithm with the **SHA-1** fallback the very first time it sees a new `AS2-From`/`AS2-To` pair (`SelfFillingPartnershipFactory.java` lines 74–79), step 4 of the algorithm above produces SHA-1 as the MIC algorithm whenever step 3 returns `null`. This is the **silent SHA-1 downgrade** described in detail in §4.2.

#### 5.3.3 The contract for FGF — repeat for emphasis

For the receiver to compute the MIC with SHA-256, FGF MUST send:

```
Disposition-Notification-Options: signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha-256
```

The `signed-receipt-micalg=optional, sha-256` parameter is the **only** input that drives the MIC algorithm to SHA-256. There is no PHASE2-side default that does the right thing for FGF — the partnership fallback is SHA-1.

#### 5.3.4 What FGF must validate on receipt

| Validation | Expected | Action on mismatch |
|------------|----------|-------------------|
| `Received-Content-MIC` is present in the machine-disposition part | Yes (always, when the inbound message was signed) | If absent, the inbound message was treated as unsigned by our receiver — investigate FGF's outbound S/MIME wrapper. |
| Algorithm suffix on the MIC line | `sha-256` (preferred, RFC 5751) **or** `sha256` (RFC 3851 form, also accepted; see §4.2.3) | If `sha-1` or `md5` — algorithm downgrade detected. Almost certainly missing/malformed `Disposition-Notification-Options` header. Resend with §4.2.2 set correctly. |
| Base64-decoded length of the MIC | **32 bytes** (SHA-256 output) | If 20 bytes (SHA-1) or 16 bytes (MD5), confirms an algorithm downgrade even if the suffix on the line was somehow misreported. |
| MIC value | Equals FGF's locally computed SHA-256 of the same bytes the receiver hashed | A mismatch means the bytes diverged in transit. See §5.3.5 for "the same bytes" definition. |

#### 5.3.5 What bytes are hashed (sender ↔ receiver agreement)

This is critical for the MIC values to match. From `AS2Helper.createMICOnReception` lines 268–290:

```java
// If the source message was signed or encrypted, include the headers -
// see message sending for details
final boolean bIncludeHeadersInMIC = aPartnership.getSigningAlgorithm () != null ||
                                     aPartnership.getEncryptAlgorithm () != null ||
                                     aPartnership.getCompressionType () != null;

// Use the MIC source captured during signature verification (via callback)
// This mirrors the sender's callback pattern where MIC is calculated on pre-signature content
MimeBodyPart aPartToHash = aMsg.getMICSource ();
...
return getCryptoHelper ().calculateMIC (aPartToHash, eSigningAlgorithm, bIncludeHeadersInMIC);
```

Decomposed:

| Aspect | Receiver behaviour | What FGF's sender MUST do |
|--------|-------------------|--------------------------|
| Bytes hashed | The **pre-signature MIME body part** as captured by the `MICSourceHolder` callback during BouncyCastle signature verification (`AS2ReceiverHandler.java` lines 306–322). This is the *exact* canonicalised MIME body that BouncyCastle ingested before parsing the signature out — same bytes the sender's PKCS#7 was computed over. | Hash the same pre-signature body. **Most AS2 stacks do this automatically** — the MIC is computed in the same code path as the S/MIME signature, on the same bytes, with the same canonicalisation. |
| Headers included? | **Yes** — because the inbound message is signed, `bIncludeHeadersInMIC = true`. This means the MIME part's `Content-Type` and `Content-Transfer-Encoding` headers (the inner part headers, not the outer HTTP headers) are part of the hash input. | Match — set "include headers in MIC" or equivalent if FGF's product exposes a toggle. The default is correct in every modern AS2 stack. |
| Line endings | RFC 5751 / RFC 3851 canonicalisation: CRLF for line terminators in the canonical body the signature is computed over. | Ensure the AS2 stack does CRLF canonicalisation, not LF-only. Sending an X12 payload with LF-only line endings unwrapped is fine *as the payload* — what matters is the MIME canonicalisation that surrounds it, which the AS2 stack handles. |
| Trailing CRLF | Per RFC 1847, a single trailing CRLF is part of the canonical form. | Match — automatic in standard stacks. |

#### 5.3.6 Practical "MICs do not match" troubleshooting (FGF-side)

If FGF's stack reports "received MIC does not equal sent MIC" but the server returned `200 OK` with a signed MDN:

1. **First check the algorithm** — is it `sha-256` on the receiver side? If `sha-1`, the §4.2 header was not sent → fix on FGF's side, re-test.
2. **Then check the bytes** — is anything between FGF's outbound MIME building and PHASE2's ingress modifying the body? Most common culprits:
   - An outbound HTTPS proxy on FGF's side that does TLS interception and re-encodes MIME headers. AS2 over an MITM proxy is **not supported** — disable inspection on the destination FQDN.
   - Mismatched Content-Transfer-Encoding: sending the payload as `7bit` but with bytes outside 0x20–0x7E (e.g. UTF-8 multi-byte sequences) would be malformed; receiver may canonicalise differently. Use `8bit` or `binary` for non-ASCII X12, or stick to US-ASCII for the test payload.
   - Re-line-ending normalisation by an intermediate (some load balancers, some `curl` debug pipelines). Send via the AS2 stack only; do not relay through `curl --data` for the real test.

### 5.4 MDN Settings — Single-Page Summary Table

| Setting | Value | Required of | Verified in code at |
|---------|-------|-------------|---------------------|
| Delivery mode | Synchronous (in body of `200 OK` response to `POST /as2`) | FGF (omit `Receipt-Delivery-Option`) | `AS2Message.isRequestingAsynchMDN()` lines 113–128 |
| MDN request trigger | `Disposition-Notification-To` header present | FGF (must send) | `AS2Message.isRequestingMDN()` lines 99–110 |
| Async-mode header to OMIT | `Receipt-Delivery-Option` | FGF (must NOT send) | `AS2Message.java` line 122 |
| Sign MDN | Yes — RSA + SHA-256 | PHASE2 (automatic when FGF sends §4.2.2) | `AS2Helper.createSyncMDN` lines 384–409 |
| MDN signing key | `phase2` PrivateKeyEntry from `certs.p12` | PHASE2 (configured) | `AS2Helper.createSyncMDN` lines 338–339 (alias swap) |
| Include signing cert in CMS SignedData | Yes (default; helps but does NOT replace pre-installed `PHASE2.cer` on FGF side) | PHASE2 (default-on) | `AS2Helper.createSyncMDN` lines 397–407 |
| MDN Content-Type | `multipart/signed; protocol="application/pkcs7-signature"; micalg="sha-256"; boundary="…"` | PHASE2 (automatic) | `BCCryptoHelper.sign` outer wrapper |
| MDN AS2-From | `PHASE2` (swapped) | PHASE2 (automatic) | `AS2Helper.createSyncMDN` line 330 |
| MDN AS2-To | `FGFCAAS21295T` (swapped) | PHASE2 (automatic) | `AS2Helper.createSyncMDN` line 331 |
| MIC algorithm in `Received-Content-MIC` | `sha-256` | PHASE2 (driven by FGF's `signed-receipt-micalg`) | `AS2Helper.createMICOnReception` lines 246–291 |
| MIC value in `Received-Content-MIC` | Base64(SHA-256(canonicalised inbound MIME body, headers included)) | PHASE2 (automatic) | `AS2Helper.createMICOnReception` line 290 (`calculateMIC`) |
| MDN HTTP status on success | `200 OK` | PHASE2 (automatic) | `AS2ReceiverHandler.sendSyncMDN` line 460 |
| MDN HTTP status on processing failure | `200 OK` with negative disposition in body, **or** 4xx/5xx — FGF MUST parse the body, not rely on status alone | PHASE2 (automatic) | `AS2ReceiverHandler` exception path |
| Disposition on success | `automatic-action/MDN-sent-automatically; processed` | PHASE2 (automatic) | `AS2Helper.createSyncMDN` (via `aDisposition` parameter from receiver) |
| Disposition on signature verify failure | `automatic-action/MDN-sent-automatically; processed/error: authentication-failed` | PHASE2 (automatic) | RFC 3798 §3.2.6.3; receiver constructs in error handler |
| Disposition on MIC computation failure | `automatic-action/MDN-sent-automatically; processed/error: integrity-check-failed` | PHASE2 (automatic) | RFC 3798 §3.2.6.3 |
| MDN encryption | None (MDN is signed but not encrypted) | PHASE2 (default) | `AS2Helper.createMDNData` does not invoke encrypt |

### 5.5 The Single Cross-Cutting Failure Mode (MUST READ)

If FGF takes away one thing from §5, it is this: **the `Disposition-Notification-Options` header is the master switch for MDN behaviour**. With the header set per §4.2.2:

- The MDN is **signed** (✅ §5.2 contract met).
- The MIC algorithm is **SHA-256** (✅ §5.3 contract met).
- The MDN is delivered **synchronously** as long as `Receipt-Delivery-Option` is absent (✅ §5.1 contract met).

Without the header, two independent regressions trigger silently and simultaneously:

1. The MIC algorithm falls back to **SHA-1** (§4.2.5, §5.3.2).
2. The MDN is returned **unsigned** (§5.2.1, §5.2.4).

These two regressions produce **two different observable failures** on FGF's side ("MIC does not match" *and* "MDN signature missing"), which can mislead triage by suggesting two unrelated problems. They are not unrelated — they are a single root cause: the `Disposition-Notification-Options` header was missing or malformed on the inbound message. **Verify this header on the wire before declaring any MDN problem real.**
