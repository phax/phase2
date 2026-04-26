# Pre-flight Readiness Checklist — FGF → PHASE2 AS2 Inbound Test

**Audience:** EDI/DevOps operator on the PHASE2 side, running the final
go/no-go check before asking FGF to transmit the inbound test message
(`AS2-From: FGFCAAS21295T` → `AS2-To: PHASE2`).

**Scope:** A single-pass checklist that resolves every prerequisite the AS2
exchange depends on **before** any traffic flows. Four prerequisite domains:

1. Network / firewall reachability of the Container App
2. TLS termination on Azure Container Apps ingress
3. Certificate format and exchange channel between FGF and PHASE2
4. Algorithm compatibility (specifically SHA-256 signing + MIC)

Sections §1 and §2 are *deliberately thin* — they cite the deep-dive material
in `ingress-configuration.md` so this document remains a checklist, not a
duplicate. Sections §3 and §4 are *self-contained* because no other
deliverable owns the cert-exchange and algorithm-negotiation pre-flight
mechanics.

Out of scope: building a test client, running the test, post-test triage.
Cross-references to `partner-coordination-FGF.md`,
`server-config-verification.md`, `ingress-configuration.md`,
`post-test-verification.md` are noted where the same fact is asserted from a
different angle.

---

## 1. Network / firewall readiness

### 1.1 What must be true

| Requirement | Required value | Source of truth |
|-------------|----------------|-----------------|
| Container app is **running** (not stopped, not scaled to zero) | `properties.runningStatus = Running` and at least one healthy replica | `deploy/azure-deploy.sh:83` (`--min-replicas 1`); verify per §3.5 of `ingress-configuration.md` |
| Ingress is **external** (publicly reachable) | `properties.configuration.ingress.external = true` | `deploy/azure-deploy.sh:82`; verify per §3.1 of `ingress-configuration.md` |
| **No IP allowlist** (per task constraint) | `properties.configuration.ingress.ipSecurityRestrictions` empty/null | task constraint "No IP allowlist required"; verify per §3.4 of `ingress-configuration.md` |
| FGF egress can reach `*.canadacentral.azurecontainerapps.io` on TCP/443 | FGF-side firewall confirmation | Out-of-band confirmation from FGF EDI/B2B team; no PHASE2-side check |

### 1.2 Verify on the live Container App

Authoritative deep-dive — including exact `az containerapp show` queries —
lives in `ingress-configuration.md` §3 ("Verification on the live Container
App"). Do not duplicate the commands here; run them from that document and
return to §5 of this checklist with the result.

The single one-liner that must succeed end-to-end before the test:

```bash
source deploy/.last-deploy.env
curl -sS -o /dev/null -w "HTTP %{http_code} · TLS %{ssl_verify_result} · ip %{remote_ip}\n" \
  "https://${FQDN}/as2"
```

Expected output:

```
HTTP 405 · TLS 0 · ip <Azure ACA front-door IP>
```

Why **405** is the success marker: `GET /as2` is intentionally rejected by
`ServletConfig.java:75` (POST-only handler), so reachability is confirmed
without running the AS2 receive pipeline. **Anything other than 405** —
`Connection refused`, TLS error, 403, 404, 503 — falls into the failure-mode
matrix in `ingress-configuration.md` §4.

---

## 2. TLS requirements

### 2.1 What must be true

| Requirement | Required value | Source of truth |
|-------------|----------------|-----------------|
| TLS terminates at the Container Apps **front door** (not in the JVM) | Envoy fleet on `--ingress external --transport http` | `deploy/azure-deploy.sh:82`; explained in `ingress-configuration.md` §2.1 |
| Public certificate is **Microsoft-issued** for `*.canadacentral.azurecontainerapps.io` | Auto-rotated platform cert; FGF must NOT pin it | `ingress-configuration.md` §2.2 |
| **TLS 1.2 or higher** is the only accepted protocol | TLS 1.0/1.1 rejected by Container Apps default policy | `ingress-configuration.md` §3.2 |
| **HTTP → HTTPS redirect** active (so a misconfigured FGF client gets a clear signal, not silent success on port 80) | `allowInsecure: false` | `deploy/azure-deploy.sh:82` (default); `ingress-configuration.md` §1.1, §3.3 |
| **No mTLS** at the transport layer | `clientCertificateMode: Ignore` (default) | `ingress-configuration.md` §1.1 — AS2 auth is at the message layer, not the transport |

### 2.2 Verify on the live Container App

The deep-dive verification commands (TLS handshake probe, redirect check,
TLS-1.0 negative test) are in `ingress-configuration.md` §3.2 and §3.3. Run
those, then return here.

A single composite probe that exercises the redirect, the TLS handshake, and
the cert subject in one shot:

```bash
source deploy/.last-deploy.env

# (a) HTTPS handshake + cert SAN
echo | openssl s_client -connect "${FQDN}:443" -servername "${FQDN}" 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates

# (b) HTTP → HTTPS redirect
curl -sS -o /dev/null -w "HTTP %{http_code} → %{redirect_url}\n" "http://${FQDN}/as2"
```

Expected:

- (a) Issuer is one of the Microsoft-managed CAs (`Microsoft Azure RSA TLS
  Issuing CA …`) and the cert is in-date.
- (b) `HTTP 301` (or `308`) redirecting to `https://${FQDN}/as2`.

---

## 3. Certificate format and exchange channel

This section is the authoritative pre-flight reference for cert exchange. It
is *not* duplicated elsewhere — `partner-coordination-FGF.md` §2 covers what
PHASE2 sends to FGF, but the *channel* and *both-direction* fingerprint
verification mechanics live here.

### 3.1 Accepted certificate formats

phase2's `keytool -importcert` (used in `partner-coordination-FGF.md` §2.6 on
FGF's side and on the PHASE2 side for FGF's cert) accepts both encodings.
phase2 itself does not care about the file extension — only the bytes.

| Format | Extension | Encoding | First bytes | When to use |
|--------|-----------|----------|-------------|-------------|
| **DER** | `.cer` / `.der` / `.crt` | Binary | Starts with `0x30 0x82 …` (raw ASN.1) | Default for Windows / IBM / Cleo / Mendelson exports. Used as `keytool -file PHASE2.cer` and `keytool -file FGFCAAS21295T.cer`. |
| **PEM** | `.pem` / `.crt` | Base64-wrapped DER | Starts with `-----BEGIN CERTIFICATE-----` | Default for OpenSSL / Linux / unix-y AS2 stacks. Used when emailing certs (printable text survives MIME more reliably). |

Both `deploy/partner-certs/PHASE2.cer` (DER, 835 B) and
`deploy/partner-certs/PHASE2.pem` (PEM, 1208 B) were exported via
`keytool -exportcert` (DER) and `keytool -exportcert -rfc` (PEM). FGF can
import either; the keystore content after import is byte-identical.

**What MUST NOT be exchanged:**

- `.p12` / `.pfx` files containing private keys — these belong only to the
  cert *owner*; `partner-coordination-FGF.md` §2.1 marks PHASE2's `.p12` as
  "do not transmit". Same applies to FGF's keystore.
- `.jks` (Java Keystore) — phase2's keystore is PKCS12 (`config.xml:22`
  `type="pkcs12"`); the JKS format is deprecated in JDK 17+ and is not the
  exchange format anyway.
- Cert chain bundles (PEM concatenated with intermediates / CA roots) when
  the cert is **self-signed** (which both ours and FGF's are). The chain
  collapses to a single self-issued cert; bundling roots adds zero trust
  and forces the receiver to decide which to trust.

### 3.2 Out-of-band exchange channel

Cert exchange must happen *before* the first AS2 message — neither side can
verify the other's signature otherwise. phase2 has no in-band cert-exchange
mechanism (`SelfFillingPartnershipFactory` does NOT auto-trust unknown
signing certs; see `partner-coordination-FGF.md` §1, §4.7).

| Channel | Suitability | Notes |
|---------|-------------|-------|
| **Email attachment (`.cer` or `.pem`)** | ✅ Acceptable for *public* certs only | Public certs carry no secret material. The fingerprint (§3.3) is the integrity check; email tampering is out-of-scope because the receiver verifies fingerprint independently. |
| **Secure file transfer (SFTP, S3 pre-signed URL, OneDrive/SharePoint signed link)** | ✅ Recommended | Adds authenticity (only the link recipient can fetch). Still requires fingerprint verification on the receiving side. |
| **In-person / phone hash dictation** | ✅ Recommended for fingerprints | Pair with a file-transfer channel: send the bytes via email, dictate the SHA-256 fingerprint by phone. The receiver compares on-call. |
| **Public web URL** | ⚠️ Acceptable with a fingerprint | Anyone can fetch; treat exactly like email — verify fingerprint independently. |
| **Slack / Teams DM** | ⚠️ Same as email | Adequate for non-secret material; preserves fingerprint integrity only if the recipient verifies independently. |

For the FGF ↔ PHASE2 inbound test, the documented exchange artifacts are:

- **PHASE2 → FGF:** `deploy/partner-certs/PHASE2.cer` (or `.pem`) — see
  `partner-coordination-FGF.md` §2.1.
- **FGF → PHASE2:** `deploy/partner-certs/FGFCAAS21295T.cer` (already
  delivered and imported into `phase2-demo-spring-boot/config/certs.p12`
  under alias `fgfcaas21295t`).

### 3.3 Fingerprint verification (both directions)

The cryptographic acceptance criterion: each side recomputes the SHA-256
fingerprint of the cert it received and compares it against the value
asserted by the sender via a separate channel. A mismatch = abort, regardless
of how authoritative the file source looked.

**PHASE2 side — FGF's cert (already imported):**

```bash
# (a) Independent SHA-256 of the file delivered by FGF
openssl x509 -in deploy/partner-certs/FGFCAAS21295T.cer -inform DER -noout \
  -fingerprint -sha256

# (b) SHA-256 of the alias actually live in the keystore
keytool -list -keystore phase2-demo-spring-boot/config/certs.p12 \
  -storetype PKCS12 -storepass test -alias FGFCAAS21295T 2>/dev/null \
  | grep -i 'SHA-?256' || \
keytool -list -v -keystore phase2-demo-spring-boot/config/certs.p12 \
  -storetype PKCS12 -storepass test -alias FGFCAAS21295T 2>/dev/null \
  | grep -E 'SHA256:'
```

(a) and (b) must produce the same `SHA-256` colon-separated hex string,
**and** that string must match what FGF dictated/published out-of-band. If
all three agree, the FGF cert is authentic on the PHASE2 side.

**FGF side — PHASE2's cert (FGF must run before importing):**

The exact commands FGF should run, plus the authoritative PHASE2 fingerprint
to compare against, live in `partner-coordination-FGF.md` §2.3 and §2.5. The
authoritative SHA-256 fingerprint of `PHASE2.cer` is repeated here for the
checklist:

```
SHA-256: AE:81:63:60:D4:D8:01:04:63:FF:F3:3E:6B:1E:F4:AA:F4:35:D2:05:1C:6B:3B:84:02:EB:8D:A1:F0:63:2A:74
```

Pre-flight requires written confirmation (email reply, ticket comment,
phone log) from FGF that they recomputed this fingerprint independently and
it matches.

### 3.4 Cert exchange pre-flight items

- [ ] FGF's cert delivered to PHASE2 in DER or PEM (no `.p12`/`.pfx`).
- [ ] FGF's cert imported into
      `phase2-demo-spring-boot/config/certs.p12` under alias `FGFCAAS21295T`
      (case-insensitive in keystore, but message header is case-sensitive —
      see `partner-coordination-FGF.md` §1).
- [ ] Updated `certs.p12` uploaded to the Azure Files `config` share (see
      `server-config-verification.md` §1.2 for the upload command and
      verification).
- [ ] Container app revision restarted **or** ≥300 s elapsed since the
      upload (`config.xml:25` `interval="300"`).
- [ ] PHASE2's `PHASE2.cer` delivered to FGF (DER or PEM).
- [ ] FGF independently recomputed PHASE2.cer's SHA-256 fingerprint and it
      matches the value above.
- [ ] FGF imported PHASE2.cer into their AS2 product under the alias /
      identifier their product expects to be the *encryption / verification*
      target for `AS2-To: PHASE2`.
- [ ] PHASE2 independently recomputed FGFCAAS21295T.cer's SHA-256 and matches
      the value FGF published out-of-band.

---

## 4. Algorithm compatibility (SHA-256 signing + MIC)

This section is the authoritative pre-flight reference for algorithm
negotiation. The deep-dive on *why* SHA-256 is required and *how* the
`Disposition-Notification-Options` header is built lives in
`partner-coordination-FGF.md` §4 and `server-config-verification.md` §4. This
checklist confirms both sides are configured consistently.

### 4.1 What the protocol requires for this test

| Aspect | Required value | Why |
|--------|----------------|-----|
| Sender (FGF) S/MIME signing algorithm | SHA-256 with RSA | Constraint: AS2 1.2, SHA-256 throughout. SHA-1 is rejected by modern AS2 stacks and explicitly out of scope. |
| MIC algorithm in the synchronous MDN | `sha-256` (lowercase, hyphen — not `SHA256` or `sha256`) | RFC 5751 + AS2 1.2 token format. `partner-coordination-FGF.md` §4.3, §5.5 cover the casing trap. |
| `Disposition-Notification-Options` header — `signed-receipt-protocol` | `required, pkcs7-signature` | Forces a signed MDN. |
| `Disposition-Notification-Options` header — `signed-receipt-micalg` | `required, sha-256` | Forces the receiver to use SHA-256 for the MIC, overriding `SelfFillingPartnershipFactory`'s SHA-1 default. **This is the single most load-bearing pre-flight item — see §5.5 of `partner-coordination-FGF.md`.** |
| Encryption algorithm (if FGF encrypts) | Out of scope for this test, but if FGF chooses to encrypt, AES-128-CBC, AES-192-CBC, AES-256-CBC, AES-128-GCM, AES-256-GCM all decrypt cleanly via BouncyCastle (`phase2-lib/.../BCCryptoHelper.java`). | AS2 permits unencrypted bodies when the transport is HTTPS; FGF may either encrypt or rely on TLS. Either is acceptable for the test. |
| Compression | Optional — `compress-before-signing` and `compress-after-signing` both supported (`AS2ClientSettings.DEFAULT_COMPRESS_BEFORE_SIGNING`). FGF can choose. | Not load-bearing for the receive verdict. |

### 4.2 The exact `Disposition-Notification-Options` header FGF must send

The single canonical value, copy-pasteable into FGF's AS2 product
configuration:

```
Disposition-Notification-Options: signed-receipt-protocol=required,pkcs7-signature; signed-receipt-micalg=required,sha-256
```

Token-level breakdown (must match exactly — phase2 parses tokens
case-insensitively but several partner stacks are case-sensitive):

| Token | Required form | Notes |
|-------|---------------|-------|
| `signed-receipt-protocol` | `required` (importance) `,` `pkcs7-signature` (value) | Forces a signed MDN; alternatives `optional` would let phase2 fall back to unsigned. |
| `signed-receipt-micalg` | `required` (importance) `,` `sha-256` (value) | Lowercase `sha-256`; **not** `sha256`, `SHA-256`, `SHA1`, etc. `partner-coordination-FGF.md` §5.5 documents the case-sensitivity trap. |

If FGF's AS2 product uses a GUI checkbox / dropdown rather than a raw header
field, the equivalent settings are typically labeled:

- "Request signed MDN" / "Sign return receipt" → enabled
- "MIC algorithm" / "Signed receipt MIC algorithm" / "Hash algorithm for
  receipt" → `SHA-256` (the GUI label is usually upper-case; the wire value
  it generates is `sha-256`)

### 4.3 What the keystore must already support

| Required Aspect | Required value | How verified |
|-----------------|----------------|--------------|
| Server-side private key for SHA-256 signing of the MDN | Alias `PHASE2`, RSA-2048, cert SHA-256 with RSA | `keytool -list -v -keystore phase2-demo-spring-boot/config/certs.p12 -storepass test -alias PHASE2` → `Signature algorithm name: SHA256withRSA`, `Subject Public Key Algorithm: 2048-bit RSA key`. Verified at cert-generation time on 2026-04-24. |
| Trusted partner cert for verifying FGF's signature | Alias `fgfcaas21295t`, trustedCertEntry | `keytool -list -keystore … -alias FGFCAAS21295T` → `trustedCertEntry`. See `server-config-verification.md` §1.2. |
| Keystore reachable by the running container | Mounted at `/app/config/certs.p12` | `server-config-verification.md` §1.2 (b). |

### 4.4 What `SelfFillingPartnershipFactory` will do on the first message

Documented in detail in `server-config-verification.md` §4.1 and
`partner-coordination-FGF.md` §5.5. The single failure mode worth restating
here:

> If FGF's first message **omits** `signed-receipt-micalg` (or sends
> `optional` instead of `required`), `SelfFillingPartnershipFactory`
> auto-creates the partnership with the SHA-1 default
> (`SelfFillingPartnershipFactory.java:78-79`). The MIC FGF computes will
> not match the MIC the server reports back in the MDN, and FGF's stack
> will reject the MDN signature.

The fix is *prevention* via §4.2 above — once a SHA-1 partnership has been
auto-filled, recovery requires deleting `/app/data/partnerships.xml` (if
present) **and** restarting the container.

### 4.5 Algorithm compatibility pre-flight items

- [ ] FGF confirmed (in writing) that their AS2 product is configured for
      AS2 1.2.
- [ ] FGF confirmed their signing algorithm is SHA-256 with RSA.
- [ ] FGF confirmed they will send the exact
      `Disposition-Notification-Options` value from §4.2 (or its GUI
      equivalent).
- [ ] FGF confirmed they request a *signed* MDN (not unsigned) via
      `signed-receipt-protocol=required,pkcs7-signature`.
- [ ] PHASE2-side keystore lists alias `PHASE2` with `Signature algorithm
      name: SHA256withRSA` and a 2048-bit RSA public key.
- [ ] No stale `partnerships.xml` exists at `/app/data/partnerships.xml`
      (`server-config-verification.md` §4.3 (a)). Delete via
      `az storage file delete --account-name "$SA" --share-name data
      --path partnerships.xml` if it does.
- [ ] If the partnerships file was deleted, the container revision has been
      restarted (`az containerapp revision restart …`).

---

## 5. Single-page pre-flight roll-up

When every box below is checked, the FGF inbound test is cleared to run.
Each box references the deeper section that owns it; check the boxes here,
not the prose.

### Network / firewall

- [ ] §1.1 — Container app `runningStatus = Running`, ≥1 healthy replica.
- [ ] §1.1 — Ingress `external = true`, no IP allowlist.
- [ ] §1.2 — `curl https://${FQDN}/as2` returns **HTTP 405** (POST-only
      handler, but reachable).

### TLS

- [ ] §2.1 — Public cert is Microsoft-managed, in-date, covers FQDN.
- [ ] §2.2 — TLS handshake with `openssl s_client` succeeds; TLS 1.0
      negative test fails.
- [ ] §2.2 — `http://${FQDN}/as2` redirects to `https://`.
- [ ] §2.1 — `clientCertificateMode = Ignore` (no mTLS).

### Certificate exchange (§3.4)

- [ ] FGF cert delivered, imported, alias matches AS2 ID.
- [ ] PHASE2 keystore uploaded to Azure Files share, restart applied.
- [ ] PHASE2 cert delivered to FGF.
- [ ] Both sides independently recomputed and confirmed both fingerprints.

### Algorithm compatibility (§4.5)

- [ ] FGF confirmed AS2 1.2 + SHA-256 with RSA + signed MDN.
- [ ] FGF will send the exact `Disposition-Notification-Options` header
      from §4.2.
- [ ] PHASE2 keystore lists alias `PHASE2` as `SHA256withRSA` /
      2048-bit RSA.
- [ ] No stale `partnerships.xml` on the server (and a restart was applied
      if one was deleted).

When every box is checked, hand off to
`post-test-verification.md` §1.3 (live-tail logs) and ask FGF to send.

---

## 6. Cross-references

| Concern | This document covers | See also |
|---------|----------------------|----------|
| Ingress configuration deep-dive | §1.1 references | `ingress-configuration.md` §1, §3 |
| TLS handshake / redirect verification commands | §2.2 reference | `ingress-configuration.md` §3.2, §3.3 |
| Cert formats accepted by phase2 keystore | §3.1 (authoritative) | — |
| Out-of-band cert exchange channels | §3.2 (authoritative) | `partner-coordination-FGF.md` §2.6 |
| FGF-side fingerprint verification commands | §3.3 reference | `partner-coordination-FGF.md` §2.5 |
| `Disposition-Notification-Options` header construction | §4.2 (authoritative for the exact header) | `partner-coordination-FGF.md` §4.2 (audience: FGF), §5.5 (failure mode) |
| `SelfFillingPartnershipFactory` SHA-1 fallback risk | §4.4 reference | `server-config-verification.md` §4.1, §4.3; `partner-coordination-FGF.md` §5.5 |
| Stale partnerships.xml cleanup | §4.5 reference | `server-config-verification.md` §4.3 (a) |
| What to do *after* FGF sends | (out of scope here) | `post-test-verification.md` §1, §2, §3 |

---

## 7. File map for everything cited above

| Path | Why it appears here |
|------|---------------------|
| `deploy/.last-deploy.env` | Sourced by every CLI command in §1 and §2; sets `RG`, `APP`, `SA`, `FQDN`. |
| `deploy/azure-deploy.sh` | Authoritative source for ingress flags (line 82) and replica count (line 83). |
| `deploy/partner-certs/PHASE2.cer` / `PHASE2.pem` | The artifacts handed to FGF (§3.2). |
| `deploy/partner-certs/FGFCAAS21295T.cer` | FGF's cert, already imported into the keystore (§3.4). |
| `phase2-demo-spring-boot/config/certs.p12` | The PKCS12 keystore queried by §3.3 and §4.3. |
| `phase2-demo-spring-boot/config/config.xml` | Defines `interval="300"` (line 25) cited in §3.4. |
| `phase2-demo-spring-boot/src/main/java/com/helger/as2demo/springboot/ServletConfig.java` | Line 75 (POST-only handler); cited in §1.2. |
| `phase2-lib/src/main/java/com/helger/phase2/partner/SelfFillingPartnershipFactory.java` | Lines 78-79 (SHA-1 default); cited in §4.4. |
| `partner-coordination-FGF.md` | FGF-facing companion; §2 (cert delivery), §4 (algorithms), §5.5 (failure mode). |
| `server-config-verification.md` | Server-side verification companion; §1, §3, §4 referenced from §3 and §4 here. |
| `ingress-configuration.md` | Ingress deep-dive; §1, §2, §3 referenced from §1, §2 here. |
| `post-test-verification.md` | The next deliverable in the sequence — what to do once §5 is fully checked. |
