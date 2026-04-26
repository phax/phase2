# Post-Test Verification — phase2-as2 (Azure Container Apps)

**Audience:** EDI/DevOps operator who has just had FGF send a single inbound AS2
message (`AS2-From: FGFCAAS21295T` → `AS2-To: PHASE2`) at the deployed
`phase2-as2` Container App, and now needs to confirm — from the **server side
only** — whether:

1. The HTTPS request actually reached our `/as2` servlet, and
2. The full AS2 receive pipeline (decrypt → verify → MIC → MDN-sign → MDN-send)
   ran to completion, and
3. A **synchronous** signed SHA-256 MDN was dispatched on the same HTTP
   transaction.

**Scope of this section (§1):** The `az containerapp logs` query procedure —
which CLI commands to run, which filters to apply, and exactly which log lines
to look for. Subsequent sections of this deliverable cover storage-side
verification, partner reconciliation, and stale-state cleanup.

This section is grounded in the SLF4J log statements actually emitted by the
deployed code (`phase2-servlet/…/AbstractAS2ReceiveBaseXServletHandler.java`,
`phase2-lib/…/AS2ReceiverHandler.java`, `phase2-lib/…/AS2Helper.java`). Every
expected log string below cites the file:line that produces it so a successor
can re-derive the search if the SLF4J text drifts in a future rebuild.

---

## 1. Server-side log query procedure

### 1.1 Prerequisites

| Need | Source |
|------|--------|
| `az` CLI logged in to the FGF subscription that owns `FGF-EDI-SANDBOX` | `az login`; verify with `az account show -o tsv --query name` |
| `containerapp` extension installed | `az extension add --name containerapp --upgrade` (one-time, per `deploy/azure-deploy.sh:7`) |
| Resource names exported into the shell | `source deploy/.last-deploy.env` (sets `RG`, `APP`, `CAE`, `SA`, `FQDN`) |
| The FGF send timestamp (UTC) | Captured before/after the test by the FGF operator; you will narrow log ranges to `[t-5min, t+5min]` around it |
| Optional: a Log Analytics workspace bound to `$CAE` | Defaulted by the Container Apps environment if logs are configured. Verify with `az monitor log-analytics workspace list -g $RG -o table` |

If `deploy/.last-deploy.env` is missing or stale (re-deploy under a new
`$RANDOM` prefix), refresh it before running the queries below — every command
in this section is parameterized on those variables.

### 1.2 Two log surfaces, two CLI shapes

The Container Apps platform separates per-replica console output (what the JVM
writes to stdout/stderr — i.e. the `logback`/Log4j2 stream from phase2) from
platform events (replica restarts, image pulls, scale events). Both are
relevant to "did it receive the message and dispatch the MDN?".

| Surface | Contains | Query | Persistence |
|---------|----------|-------|-------------|
| **Console** | Application stdout/stderr — every SLF4J log line emitted by `phase2-lib` and `phase2-servlet` runs through here | `az containerapp logs show … --type console …` | Streamed live from the running replica; queryable persistently via Log Analytics `ContainerAppConsoleLogs_CL` |
| **System** | Container Apps platform events — `Pulling image`, `Started container`, `Replica failed`, etc. | `az containerapp logs show … --type system …` | Same — also persisted to `ContainerAppSystemLogs_CL` in Log Analytics |

For "did the AS2 message arrive?" the answer is always in the **console**
stream. For "did the replica even survive long enough to receive the message?"
the answer is in the **system** stream.

### 1.3 Live-tail during the test (recommended)

Open this command in a terminal **before** asking FGF to push the message. It
follows the running replica's console stream in real time, so the operator
watches the receive pipeline unfold against the wall clock:

```bash
source deploy/.last-deploy.env

az containerapp logs show \
  --resource-group "$RG" \
  --name "$APP" \
  --type console \
  --follow \
  --tail 100 \
  --format text
```

Notes:

- `--follow` keeps the connection open and streams new lines as they are
  written by the replica. Cancel with `Ctrl-C` when the test ends.
- `--tail 100` primes the stream with the most recent 100 lines so the operator
  immediately sees the startup banner / current quiescent state, not a blank
  screen.
- `--format text` collapses the JSON wrapper Container Apps adds and prints
  one log line per line — much easier to eyeball than the default JSON.
- `--type console` is explicit even though it is the default; the documents in
  this repo set it explicitly so a successor can copy-paste without surprises.

If the app is running multiple replicas (the deploy script forces
`--min-replicas 1 --max-replicas 1`, see `deploy/azure-deploy.sh:83`, so this
is not currently a concern), pin to one with `--replica <name>`. List replicas
with `az containerapp replica list -g "$RG" -n "$APP" -o table`.

### 1.4 Retrospective query (for after-the-fact triage)

If the live-tail was missed, query the persisted log buffer instead. The
Container Apps platform retains the last ~20 MB / ~24 h of console output
per revision in the streaming buffer, which is what `az containerapp logs
show` (without `--follow`) reads from:

```bash
source deploy/.last-deploy.env

az containerapp logs show \
  --resource-group "$RG" \
  --name "$APP" \
  --type console \
  --tail 500 \
  --format text \
  > /tmp/phase2-as2-console-$(date -u +%Y%m%dT%H%M%SZ).log
```

Save the output rather than scroll-back-buffering it — the file is the
artefact that goes into the test record.

For longer windows or precise time slicing, query Log Analytics directly. The
Container Apps environment bound to `$CAE` writes to a Log Analytics
workspace. Resolve it once, cache the GUID, and run KQL:

```bash
source deploy/.last-deploy.env

# (a) Resolve the workspace ID of the environment's bound Log Analytics workspace.
WS_RESOURCE=$(az containerapp env show -g "$RG" -n "$CAE" \
  --query 'properties.appLogsConfiguration.logAnalyticsConfiguration.customerId' \
  -o tsv)
echo "Workspace customerId: $WS_RESOURCE"

# (b) Query everything emitted by the phase2-as2 app in the last 30 minutes.
az monitor log-analytics query \
  --workspace "$WS_RESOURCE" \
  --analytics-query "
ContainerAppConsoleLogs_CL
| where TimeGenerated > ago(30m)
| where ContainerAppName_s == 'phase2-as2'
| project TimeGenerated, RevisionName_s, ReplicaName_s, Log_s
| sort by TimeGenerated asc
" \
  -o table
```

Narrow to the test window once the FGF operator gives you the send timestamp.
Replace `ago(30m)` with `between(datetime(2026-04-24T19:50:00Z) .. datetime(2026-04-24T20:10:00Z))`.

> If `customerId` comes back empty, the environment was provisioned without
> Log Analytics export. The streaming buffer (`az containerapp logs show`)
> remains the only source of truth — copy it to a file immediately as in the
> example above.

### 1.5 The eight log entries that confirm a successful inbound + sync MDN

Run each of the filters below against the captured log. They are presented in
the order they appear in the receive pipeline; together they prove the flow
ran end-to-end.

#### 1.5.1 HTTP request entered the servlet

```bash
grep -E 'Starting to handle incoming AS2 request' /tmp/phase2-as2-console-*.log
```

Expected line (exactly one occurrence per inbound message):

```
Starting to handle incoming AS2 request - <ip>:<port>
```

Source: `AbstractAS2ReceiveBaseXServletHandler.java:186`. The `<ip>:<port>` is
the Container Apps front-door's view of FGF's HTTP client (typically a
Microsoft-internal NAT address — this is *not* FGF's public egress IP, do not
use this field as an audit trail of the partner identity; the partner is
identified by the AS2 headers, not the TCP source).

**Pass criteria:**

- [ ] Exactly one line matches per FGF send.
- [ ] Timestamp is within ±5 s of the FGF operator's send timestamp.

If absent: the request never reached the servlet. Check ingress (§3 of
`server-config-verification.md`), the FQDN FGF dialled, and the system log
stream for replica restarts that may have cycled the listener.

#### 1.5.2 Body bytes were read off the socket

```bash
grep -E '^received [0-9]|^received message from' /tmp/phase2-as2-console-*.log
```

Expected (exactly one — the byte-array path is taken because the demo Spring
Boot app reads via `ServletInputStream` into a `ByteArrayDataSource`):

```
received <N> KB/s from <ip>:<port> [<msg-id>]
```

Source: `AbstractAS2ReceiveBaseXServletHandler.java:256-260`. The `<msg-id>`
is the value of the `Message-ID` HTTP header FGF set on the inbound message.
**Capture this string** — it is the primary correlation key for everything
downstream, including the filename written to `/app/data` and the
`Original-Message-ID` echoed back in the MDN.

**Pass criteria:**

- [ ] Exactly one line matches.
- [ ] `[<msg-id>]` is non-empty (a value of `[no message ID set]` indicates
      FGF omitted the `Message-ID` header — a protocol violation but not
      necessarily a test failure; flag it for the partner conversation).

#### 1.5.3 Decryption succeeded (only if FGF encrypted the payload)

The FGF↔PHASE2 partnership for this test does **not** require encryption (the
partner-coordination doc lists only signing as a hard requirement; encryption
is optional). If FGF did encrypt, this line will be present; if they didn't,
it will be absent — both are acceptable for this test.

```bash
grep -E 'Successfully decrypted incoming AS2 message' /tmp/phase2-as2-console-*.log
```

Expected (zero or one occurrence):

```
Successfully decrypted incoming AS2 message [<msg-id>]
```

Source: `AS2ReceiverHandler.java:247`. The `<msg-id>` matches §1.5.2.

**Pass criteria:**

- [ ] Either zero matches (unencrypted payload — fine) or one match with the
      same `<msg-id>` from §1.5.2.
- [ ] No `Error decrypting` line (see §1.5.9 failure markers).

#### 1.5.4 Signature verification succeeded

```bash
grep -E 'Successfully verified signature of incoming AS2 message' /tmp/phase2-as2-console-*.log
```

Expected (exactly one):

```
Successfully verified signature of incoming AS2 message [<msg-id>]
```

Source: `AS2ReceiverHandler.java:333`. This proves:

- FGF signed the payload with their private key.
- The `fgfcaas21295t` trusted-cert entry in `/app/config/certs.p12` matches
  the cert FGF signed with (i.e. the public-cert handshake from
  `partner-coordination-FGF.md` §2 was performed correctly).
- The signature itself is intact (not corrupted by a MIME-rewriting proxy).

**Pass criteria:**

- [ ] Exactly one match with the `<msg-id>` from §1.5.2.

If absent, look for `Error verifying signature` (see §1.5.9) — the test fails
here.

#### 1.5.5 MIC was computed with SHA-256 (the critical guardrail)

```bash
grep -E 'createMICOnReception:' /tmp/phase2-as2-console-*.log
```

Expected (two lines per message — the source-selection log and the
algorithm-decision log):

```
createMICOnReception: Using captured MIC source from signature verification
createMICOnReception: signingAlgorithm=sha-256, contentType=multipart/signed; …
```

Source: `AS2Helper.java:285` and `AS2Helper.java:288`.

**Pass criteria — non-negotiable:**

- [ ] First line matches (proves the MIC is computed over the bytes that were
      actually signed by FGF, not over a re-canonicalized copy).
- [ ] Second line shows `signingAlgorithm=sha-256` *literally*. **`sha-1` here
      means the silent SHA-1 downgrade has happened** — re-read
      `partner-coordination-FGF.md` §4.2 and `server-config-verification.md`
      §4 to remediate. The MDN that comes back will not match FGF's MIC and
      they will report "integrity check failed" even though everything else
      on our side looks fine.

If you see `signingAlgorithm=null`, FGF's `Disposition-Notification-Options`
header was malformed *and* the partnership had no signing-algorithm fallback —
this is rare with `SelfFillingPartnershipFactory` (which always installs
SHA-1) and indicates the partnership state file is corrupt. Wipe
`/app/config/partnerships.xml` per `server-config-verification.md` §4.3 and
re-run.

#### 1.5.6 Outgoing MDN was signed with PHASE2's key

```bash
grep -E 'Successfully signed outgoing MDN message|Failed to sign MDN|Because our signing certificate expired' \
     /tmp/phase2-as2-console-*.log
```

Expected (exactly one — the success line):

```
Successfully signed outgoing MDN message [<mdn-msg-id>]
```

Source: `AS2Helper.java:220`. The `<mdn-msg-id>` is a *new* message ID
generated by PHASE2 for the MDN itself; it is NOT the same as `<msg-id>` from
§1.5.2 (which is echoed in the MDN's `Original-Message-ID` field, not its
`Message-ID`).

**Pass criteria:**

- [ ] Exactly one match for `Successfully signed outgoing MDN message`.
- [ ] No occurrence of `Failed to sign MDN - using an unsigned MDN instead`
      (`AS2Helper.java:226`) — this is a PHASE2-side keystore regression and
      means the `phase2` PrivateKeyEntry was unreachable when signing started.
      Investigate per `server-config-verification.md` §1.
- [ ] No occurrence of `Because our signing certificate expired per …`
      (`AS2Helper.java:202`) — the `phase2` cert is valid 2026-04-25 →
      2028-04-24, so this should not fire during this test, but verify.

If the success line is **absent** with no failure line — i.e. the receiver
returned `200 OK` but never logged `Successfully signed outgoing MDN
message` — then `bSignMDN` was `false` upstream because FGF's
`Disposition-Notification-Options` header was missing or malformed. See
`partner-coordination-FGF.md` §5.2.4 (silently-unsigned-MDN regression).

#### 1.5.7 MDN was dispatched **synchronously**

```bash
grep -E 'Sending back sync MDN|Setup to send async MDN' /tmp/phase2-as2-console-*.log
```

Expected (exactly one match — the sync line):

```
Sending back sync MDN [automatic-action/MDN-sent-automatically; processed] <ip>:<port> [<msg-id>]
```

Source: `AS2ReceiverHandler.java:446-450`. The bracketed string is
`DispositionType.getAsString()` — for the success path it is exactly
`automatic-action/MDN-sent-automatically; processed` (per
`AS2ReceiverHandler.java:737` `DispositionType.createSuccess()` and the
disposition format defined in `phase2-lib/.../disposition/DispositionType.java`).

**Pass criteria — non-negotiable:**

- [ ] Exactly one match for `Sending back sync MDN`.
- [ ] **Zero matches** for `Setup to send async MDN` (the async branch at
      `AS2ReceiverHandler.java:434`). If async fired, FGF's stack accidentally
      sent `Receipt-Delivery-Option` on the inbound — see
      `partner-coordination-FGF.md` §5.1.6. The test fails because async MDN
      sending is not deployed on this container; FGF will time out waiting
      for an MDN that never arrives.
- [ ] Disposition string contains `processed` and not an error token like
      `failed`, `unexpected-processing-error`, `decryption-failed`,
      `authentication-failed`, etc. (Error tokens come from
      `AbstractActiveNetModule.DISP_*` constants and indicate the receiver
      rejected the message; the MDN is still sent sync, but it carries an
      error disposition.)

#### 1.5.8 HTTP response was sent and the transaction closed

```bash
grep -E '^sent MDN' /tmp/phase2-as2-console-*.log
```

Expected (exactly one — appears immediately after the §1.5.7 line):

```
sent MDN [automatic-action/MDN-sent-automatically; processed] <ip>:<port> [<msg-id>]
```

Source: `AS2ReceiverHandler.java:473`. This is logged **after** the HTTP
response has been flushed back to FGF's socket. Its presence proves the
synchronous response actually left the container.

**Pass criteria:**

- [ ] Exactly one match.
- [ ] Disposition string matches §1.5.7 (sanity check that
      §1.5.7 and §1.5.8 are paired by the same message).
- [ ] Timestamp delta vs. §1.5.1 is reasonable (single-digit seconds for a
      typical EDI X12 payload — anything >30 s suggests a cold-start or
      network anomaly worth flagging in the test record).

### 1.6 The single combined query (recommended for the test record)

Rather than running eight separate `grep`s, capture the full pipeline in one
filter so the log excerpt is contiguous and easy to attach to the test
record:

```bash
grep -nE 'Starting to handle incoming AS2 request|^received |Successfully decrypted incoming AS2 message|Successfully verified signature of incoming AS2 message|createMICOnReception:|Successfully signed outgoing MDN message|Sending back sync MDN|^sent MDN' \
     /tmp/phase2-as2-console-*.log
```

The "what good looks like" reference output for a single FGF→PHASE2 inbound
message (timestamps and IDs synthetic):

```
2026-04-24T20:01:14.221Z  Starting to handle incoming AS2 request - 100.64.0.7:54312
2026-04-24T20:01:14.319Z  received 12.4 KB/s from 100.64.0.7:54312 [<8f3c…@as2.fgfbrands.com>]
2026-04-24T20:01:14.486Z  Successfully verified signature of incoming AS2 message [<8f3c…@as2.fgfbrands.com>]
2026-04-24T20:01:14.487Z  createMICOnReception: Using captured MIC source from signature verification
2026-04-24T20:01:14.488Z  createMICOnReception: signingAlgorithm=sha-256, contentType=multipart/signed; protocol="application/pkcs7-signature"; micalg=sha-256; …
2026-04-24T20:01:14.553Z  Successfully signed outgoing MDN message [<a17e…@phase2-as2.…>]
2026-04-24T20:01:14.561Z  Sending back sync MDN [automatic-action/MDN-sent-automatically; processed] 100.64.0.7:54312 [<8f3c…@as2.fgfbrands.com>]
2026-04-24T20:01:14.572Z  sent MDN [automatic-action/MDN-sent-automatically; processed] 100.64.0.7:54312 [<8f3c…@as2.fgfbrands.com>]
```

Six to seven lines (eight if FGF encrypted), in this order, with the same
`<msg-id>` threading through `received`, `Successfully verified signature`,
`Sending back sync MDN`, and `sent MDN` — this is the canonical "AS2 receive
+ sync signed SHA-256 MDN succeeded" signature on the server side.

### 1.7 Equivalent KQL for Log Analytics (persistent record)

If the streaming buffer has rolled (e.g. you triage hours later, or the
revision was restarted between the test and the triage), run the same
filter against `ContainerAppConsoleLogs_CL`:

```kusto
ContainerAppConsoleLogs_CL
| where TimeGenerated between (datetime(2026-04-24T19:50:00Z) .. datetime(2026-04-24T20:10:00Z))
| where ContainerAppName_s == "phase2-as2"
| where Log_s matches regex
    @"Starting to handle incoming AS2 request|^received |Successfully decrypted incoming AS2 message|Successfully verified signature of incoming AS2 message|createMICOnReception:|Successfully signed outgoing MDN message|Sending back sync MDN|^sent MDN"
| project TimeGenerated, RevisionName_s, ReplicaName_s, Log_s
| sort by TimeGenerated asc
```

Run via `az monitor log-analytics query --workspace "$WS_RESOURCE"
--analytics-query "<paste KQL>" -o table` (workspace ID resolved per §1.4).

The KQL output is the **artefact of record** for the test — paste it
verbatim into the test report. The streaming-buffer text from §1.6 is a
convenience for live triage but is not durable.

### 1.8 Failure-mode marker reference

These are the log fragments that indicate a specific failure during the
inbound flow. Run one combined `grep` for them to triage a failing test
quickly:

```bash
grep -nE 'Not having a data source to operate on|Error decrypting|Error verifying signature|Error decompressing received message|Failed to sign MDN|Because our signing certificate expired|Setup to send async MDN|AS2NoModuleException|AS2DispositionException|Failed to retrieve .AS2ServletReceiverModule.|AS2ServletXMLSession configuration file' \
     /tmp/phase2-as2-console-*.log
```

| Marker | Source | Meaning | Where to look next |
|--------|--------|---------|--------------------|
| `Not having a data source to operate on` | `AbstractAS2ReceiveBaseXServletHandler.java:249` | Servlet could not read the request body. Usually a malformed HTTP request from FGF, or a chunked-encoding issue. | Check FGF's egress logs for the same transaction. |
| `Error decrypting <…>` | `AS2ReceiverHandler.java:257` | The PKCS#7 envelope decryption failed. Either FGF encrypted to the wrong public cert, or our `phase2` PrivateKeyEntry no longer matches the cert FGF used. | `server-config-verification.md` §1, then ask FGF to confirm which cert they encrypted to. |
| `Error verifying signature <…>: <reason>` | `AS2ReceiverHandler.java:338` | The PKCS#7 detached signature did not validate. Either FGF signed with a different key than the cert in `fgfcaas21295t`, or the body was modified in transit. | `server-config-verification.md` §1.2 to compare fingerprints. |
| `Error decompressing received message` | `AS2ReceiverHandler.java:394` | Compression layer (RFC 5402) failed. Almost certainly FGF using a non-zlib codec; out of scope for this test (compression is optional and not requested). | Ask FGF to disable compression if present. |
| `Failed to sign MDN - using an unsigned MDN instead` | `AS2Helper.java:226` | `phase2` private key unreachable when MDN signing began. **PHASE2-side regression.** | `server-config-verification.md` §1, plus `keytool -list` against `/app/config/certs.p12`. |
| `Because our signing certificate expired per …` | `AS2Helper.java:202` | The `phase2` cert is past `notAfter`. (Should not fire during this test — cert is valid 2026-04-25 → 2028-04-24.) | Re-issue PHASE2 cert per the procedure that produced the current `deploy/partner-certs/PHASE2.cer`. |
| `Setup to send async MDN [<…>]` | `AS2ReceiverHandler.java:434` | FGF requested an async MDN. **Out of scope for this test** — async sender is not deployed on this container, so the MDN will never reach FGF. | `partner-coordination-FGF.md` §5.1.6 — FGF must remove `Receipt-Delivery-Option` and resend. |
| `AS2NoModuleException` (often paired with the async marker above) | thrown from `AS2MessageProcessor.handle()` | A processor module was requested but is not registered. The most common cause in this deployment is the async-MDN sender being absent (because it is intentionally not configured). | Same as above — FGF must request sync. |
| `AS2DispositionException` | thrown around `AS2ReceiverHandler.java:756` | A disposition error fired (decryption/verify/storage/validate) and the receiver is sending an error MDN. The next `Sending back sync MDN` line will carry the error disposition string. | Look at the immediately preceding `Error …` line — that is the root cause. |
| `Failed to retrieve 'AS2ServletReceiverModule'` | `AbstractAS2ReceiveXServletHandler.java:68` | Startup-time failure: `config.xml` did not declare the receiver module. The replica will not have served any AS2 traffic at all. | Re-upload `phase2-demo-spring-boot/config/config.xml` to the `config` file share. |
| `AS2ServletXMLSession configuration file '…' does not exist!` | `AS2ServletXMLSession.java:62` | Startup-time failure: the file share is not mounted at `/app/config` or `certs.p12`/`config.xml` is missing from it. | Re-run `az containerapp env storage set …` per `deploy/azure-deploy.sh:67-72`. |

### 1.9 System-stream sanity check (optional)

If the console stream shows no AS2 activity at all around the FGF send time,
confirm the replica was actually running and not in the middle of a restart:

```bash
source deploy/.last-deploy.env

az containerapp logs show \
  --resource-group "$RG" \
  --name "$APP" \
  --type system \
  --tail 50 \
  --format text
```

Pass criteria:

- [ ] No `Replica failed` or `Replica killed` events around the FGF send time.
- [ ] If a `Started container` event fires within 5 minutes of the send, the
      revision was just (re)starting — give it a moment and have FGF resend,
      because the request may have been refused while the JVM was still
      initialising the SLF4J `Successfully initialized AS2 configuration`
      banner (`AbstractAS2ReceiveXServletHandler.java:77`).

### 1.10 Pass-criteria roll-up for §1

Single-pass checklist that flips this section's verdict to PASS or FAIL:

- [ ] **§1.5.1** `Starting to handle incoming AS2 request` line found, within
      ±5 s of FGF's send timestamp.
- [ ] **§1.5.2** `received <…> from <ip>:<port> [<msg-id>]` line found;
      `<msg-id>` captured for cross-checking the MDN.
- [ ] **§1.5.4** `Successfully verified signature of incoming AS2 message
      [<msg-id>]` found with the captured `<msg-id>`.
- [ ] **§1.5.5** `createMICOnReception: signingAlgorithm=sha-256` found —
      *literal `sha-256`*, not `sha-1` or `null`.
- [ ] **§1.5.6** `Successfully signed outgoing MDN message [<mdn-msg-id>]`
      found.
- [ ] **§1.5.7** `Sending back sync MDN [automatic-action/MDN-sent-automatically;
      processed] …` found, **and** no `Setup to send async MDN` line found.
- [ ] **§1.5.8** `sent MDN [automatic-action/MDN-sent-automatically;
      processed] … [<msg-id>]` found, with `<msg-id>` matching §1.5.2.
- [ ] **§1.8** combined-failure-marker `grep` returns no matches.

When every box is checked, the server-side log evidence confirms the
deployed `phase2-as2` Container App received the FGF inbound message and
dispatched a synchronous, signed, SHA-256 MDN. The remaining sub-sections of
this deliverable cross-check that result against the storage side
(`/app/data` filenames), the keystore hot-reload state, and FGF's
acknowledgement.

---

## File map for §1

| Path | What it contributes |
|------|---------------------|
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/AbstractAS2ReceiveBaseXServletHandler.java` | Lines 186, 256, 264 — the entry-point / bytes-read log lines. |
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/AbstractAS2ReceiveXServletHandler.java` | Lines 68, 77 — startup success/failure markers. |
| `phase2-lib/src/main/java/com/helger/phase2/processor/receiver/net/AS2ReceiverHandler.java` | Lines 247, 333, 389 — per-stage success markers; lines 434, 446, 473 — sync vs. async MDN dispatch markers; lines 257, 338, 394 — failure markers. |
| `phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java` | Lines 220, 226, 202 — MDN signing markers; lines 281, 285, 288 — `createMICOnReception` algorithm-decision markers. |
| `phase2-lib/src/main/java/com/helger/phase2/processor/receiver/AbstractActiveNetModule.java` | Lines 115-126 — `DISP_SUCCESS` and the error disposition strings that appear in the bracketed disposition of §1.5.7/§1.5.8 lines. |
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/util/AS2ServletXMLSession.java` | Line 62 — startup config-not-found failure marker. |
| `deploy/.last-deploy.env` | Source-of-truth for `RG`, `APP`, `CAE`, `SA`, `FQDN` values referenced in every CLI command above. |
| `deploy/azure-deploy.sh` | Lines 64, 67-72, 78-84 — shows that the environment is provisioned with the default Log Analytics export (which is what enables the KQL path in §1.4 and §1.7). |

---

## 2. Azure Storage file inspection

**Scope of this section (§2):** Confirm — independently of the log evidence
in §1 — that the AS2 receive pipeline actually persisted FGF's payload and
its headers to the Azure Files `data` share that the container mounts at
`/app/data`. This is the durable, byte-level evidence that the message body
made it past decryption, signature verification, and the storage processor
module without truncation or substitution.

Where things land is hard-coded in `phase2-demo-spring-boot/config/config.xml`
(lines 36, 41-42, 44) and emitted by
`phase2-lib/src/main/java/com/helger/phase2/processor/storage/MessageFileModule.java`
(lines 102, 127). Both are deterministic — there is no race or
nondeterminism to design around.

### 2.1 Where the receive pipeline writes

| Artefact | Path on disk (in container) | Path in Azure Files share `data` | Source of truth |
|----------|-----------------------------|----------------------------------|-----------------|
| **Decrypted payload body** | `/app/data/inbox/FGFCAAS21295T-PHASE2-<message-id>` | `inbox/FGFCAAS21295T-PHASE2-<message-id>` | `config.xml:41` `filename="data/inbox/$msg.sender.as2_id$-$msg.receiver.as2_id$-$msg.headers.message-id$"` |
| **Inbound HTTP+AS2 headers** | `/app/data/inbox/msgheaders/<yyyy>/<MM>/FGFCAAS21295T-PHASE2-<message-id>` | `inbox/msgheaders/<yyyy>/<MM>/FGFCAAS21295T-PHASE2-<message-id>` | `config.xml:42` `header="data/inbox/msgheaders/$date.uuuu$/$date.MM$/…"` |
| **Failed-receive payloads** | `/app/data/inbox/error/<formatted-message-id>` | `inbox/error/<formatted-message-id>` | `config.xml:36` `errordir="data/inbox/error"` and `errorformat="$msg.sender.as2_id$, $msg.receiver.as2_id$, $msg.headers.message-id$"` |
| **Pending async-MDN state** (sync MDN path leaves these empty) | `/app/data/pendingMDN/`, `/app/data/pendinginfoMDN/` | `pendingMDN/`, `pendinginfoMDN/` | `config.xml:30-31` |
| **Tempfiles for in-flight processing** (cleared on success) | `/app/data/temp/` | `temp/` | `config.xml:44` `tempdir="data/temp"` |

Two file-system facts that follow from this and matter for verification:

- The body-file name is **the AS2 message-id verbatim**, framed by the
  sender + receiver AS2 IDs. Because phase2 strips the angle-bracket form
  (`<…>`) of `Message-ID` headers before substitution, the path equals
  literally `inbox/FGFCAAS21295T-PHASE2-` + the inner part of the message-id
  FGF generated. The same id appears bracketed in the §1.5.2 / §1.5.8 log
  lines, which lets the operator cross-link log → file deterministically.
- `<yyyy>/<MM>` in the headers path is **UTC** (driven by phase2's
  `$date.uuuu$` token, which uses `OffsetDateTime.now(ZoneOffset.UTC)` per
  `phase2-commons` token-substitution helper). On a test that crosses
  midnight UTC, the body file may sit under the previous day's headers
  partition — neither is wrong; both reflect the same message.

### 2.2 az storage CLI prerequisites

```bash
source deploy/.last-deploy.env
SA_KEY=$(az storage account keys list -g "$RG" -n "$SA" --query '[0].value' -o tsv)
```

Every command in §2.3 / §2.4 expects `$SA` and `$SA_KEY` to be set in the
environment. The key is fetched at the start, so subsequent calls do not
re-hit Azure RBAC.

### 2.3 Live-state inspection (after FGF sends)

Run these in order from the same shell that has `$SA_KEY` exported.

**(a) Confirm the inbox has a new file framed `FGFCAAS21295T-PHASE2-…`:**

```bash
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox \
  --query "[?starts_with(name, 'FGFCAAS21295T-PHASE2-')].{name:name, size:properties.contentLength, modified:properties.lastModified}" \
  -o table
```

Expected: exactly **one** row whose `modified` timestamp is within ±1 min of
the §1.5.1 entry-point log line. The `size` column is the byte length of the
*decrypted* payload (i.e. the X12 document FGF intended to send), not the
size of the wire-level S/MIME envelope.

**(b) Confirm the headers file was written to the matching UTC partition:**

```bash
YEAR=$(date -u +%Y); MONTH=$(date -u +%m)
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path "inbox/msgheaders/${YEAR}/${MONTH}" \
  --query "[?starts_with(name, 'FGFCAAS21295T-PHASE2-')].{name:name, size:properties.contentLength}" \
  -o table
```

Expected: one row, file name **identical** to the (a) result minus the
`<yyyy>/<MM>/` partition prefix. If (a) returned a row but (b) did not,
either the test crossed midnight UTC (try yesterday's `${MONTH}`) or
`MessageFileModule.store()` for the headers half failed silently
(`MessageFileModule.java:127` log line in §1 will say so).

**(c) Confirm the error directory is empty for this message-id:**

```bash
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox/error \
  --query "[?contains(name, 'FGFCAAS21295T')].{name:name, modified:properties.lastModified}" \
  -o table
```

Expected: **empty** result. Any row here is the smoking gun for a failed
receive — the AS2 framework wrote the wire-level body to `error/` rather than
`inbox/`. Re-read the corresponding §1.8 failure-marker log line for the
reason; do **not** delete the error file before debugging.

**(d) Confirm temp/ is clean (success-path invariant):**

```bash
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path temp \
  --query "length(@)" -o tsv
```

Expected: `0` (or, on a heavily-used environment, only files older than the
test). A nonzero count of files **newer than** the §1.5.1 timestamp implies
the receive pipeline began processing but did not call
`MessageFileModule.store()` cleanly — investigate via §1 logs.

### 2.4 Byte-level cross-check (optional but recommended)

If the operator has access to whatever payload FGF *intended* to send (a
copy from FGF's outbox, an EDI gateway export, etc.), download the live
inbox file and compare:

```bash
# Resolve the actual filename from §2.3 (a)
FILE=$(az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox \
  --query "[?starts_with(name, 'FGFCAAS21295T-PHASE2-')].name | [0]" -o tsv)

mkdir -p /tmp/phase2-inbox
az storage file download \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path "inbox/$FILE" \
  --dest "/tmp/phase2-inbox/$FILE"

# Byte-compare against the FGF-side reference file
sha256sum "/tmp/phase2-inbox/$FILE" /path/to/fgf-side-reference.x12
```

The two SHA-256s **must** match. A mismatch indicates either FGF did not
hand over the same bytes they signed, or the AS2 receive pipeline applied
an unexpected transformation (decompression, charset re-encoding) — both
warrant a full re-derive of the crypto chain before accepting the test.

### 2.5 Stale-state hygiene (run before, not after, the test)

If a previous test left files behind in `inbox/`, they will sit alongside
the new one and complicate `--query` filters. The pre-test cleanup the
operator may want to apply:

```bash
# List anything currently in inbox before FGF sends
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox \
  --query "[?type=='file'].{name:name, modified:properties.lastModified}" -o table

# (Optional) move them to an archive directory rather than deleting outright.
# Replace <name> with a specific value rather than wildcarding to avoid
# nuking a fresh receive that beat the operator to the share.
az storage directory create \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --name "inbox/_archive_<yyyymmdd>"
```

Note: do NOT delete `inbox/error/` contents before triage even on a re-run
— the previous failure's evidence is what tells the operator whether the
fix landed.

### 2.6 Pass-criteria roll-up for §2

- [ ] **§2.3 (a)** Exactly one new file in `inbox/` framed
      `FGFCAAS21295T-PHASE2-…` with a recent timestamp.
- [ ] **§2.3 (b)** Matching headers file under
      `inbox/msgheaders/<yyyy-utc>/<MM-utc>/`.
- [ ] **§2.3 (c)** `inbox/error/` empty (or unchanged from pre-test
      baseline).
- [ ] **§2.3 (d)** `temp/` has no files newer than the §1.5.1 timestamp.
- [ ] **§2.4** (optional) byte-level SHA-256 of the inbox file matches
      FGF's reference copy.

When every box is checked, the storage side independently confirms what
§1's log evidence already asserts: the message arrived, was decrypted (or
decompressed) correctly, was MIC-verified, and was persisted under the
expected, predictable filename.

### File map for §2

| Path | What it contributes |
|------|---------------------|
| `phase2-demo-spring-boot/config/config.xml` | Lines 30, 31, 36, 41, 42, 44 — every `data/` path used in §2.1. |
| `phase2-lib/src/main/java/com/helger/phase2/processor/storage/MessageFileModule.java` | Lines 102, 127 — emit the `stored message to …` / `stored headers to …` log lines that §1.5 reports and §2 verifies on disk. |
| `deploy/azure-deploy.sh` | Lines 53-55, 70-72 — provision the `data` Azure Files share and bind it to the Container Apps environment so the container's `/app/data` is the share queried by §2.3. |
| `deploy/.last-deploy.env` | Provides `$SA` and the storage account scope for §2.2's key fetch. |

---

## 3. End-to-end verification check (combining log + storage evidence)

**Scope of this section (§3):** Bring the §1 and §2 evidence streams
together with the partner-side acknowledgement to produce a single binary
verdict — *did the inbound AS2 test pass, end-to-end?* — that the operator
can stamp on a ticket and move on.

§3 owns the cross-correlation logic that no single sibling section sees:
the same `<message-id>` must appear in the entry-point log (§1.5.1), the
MIC-decision log (§1.5.5), the MDN-dispatch log (§1.5.7/§1.5.8), the
storage filename (§2.3 (a)/(b)), AND in FGF's own MDN receipt confirmation.
A break in *any* of those linkages is a fail even if each individual
section's checklist is green.

### 3.1 Cross-correlation procedure

The work here is bookkeeping. The operator captures four pieces of evidence
and confirms they all reference the same message-id.

**Step 1 — Capture the entry-point message-id from §1.5.2.**

```bash
source deploy/.last-deploy.env
MSG_ID=$(az containerapp logs show \
  --resource-group "$RG" --name "$APP" --type console --tail 200 --format text \
  | grep -oE 'received .* \[<[^>]+>\]' \
  | tail -1 \
  | sed -E 's/.*\[<([^>]+)>\].*/\1/')
echo "Captured MSG_ID: $MSG_ID"
```

If `$MSG_ID` is empty: the §1 receive evidence never landed —
the test failed at the network/TLS/servlet layer; jump straight to §3.4
("Failure-mode reconciliation").

**Step 2 — Confirm the same message-id surfaces in §1.5.7's MDN line.**

```bash
az containerapp logs show \
  --resource-group "$RG" --name "$APP" --type console --tail 500 --format text \
  | grep -F "$MSG_ID" \
  | grep -E 'sync MDN|sent MDN|signed outgoing MDN'
```

Expected: at least 3 lines (signed-MDN, sync-dispatch, transport-sent), each
containing `$MSG_ID`. Anything fewer indicates the pipeline halted between
verification and dispatch.

**Step 3 — Confirm the storage filename matches.**

```bash
SA_KEY=$(az storage account keys list -g "$RG" -n "$SA" --query '[0].value' -o tsv)
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox \
  --query "[?contains(name, '$MSG_ID')].name" -o tsv
```

Expected: exactly one filename, ending in `$MSG_ID`. The framing prefix
will be `FGFCAAS21295T-PHASE2-`. If empty: the storage processor module
did not run (configuration error in `config.xml:40-45` or the receive
pipeline aborted before the storage stage — see §1.5.5/§1.5.6 logs).

**Step 4 — Confirm FGF's MDN-receipt acknowledgement.**

This is *not* a server-side query — it is partner-side confirmation that
they (a) received the synchronous MDN on the same HTTP transaction, (b)
verified its signature against `PHASE2.cer`, (c) extracted the
`Original-Message-ID` field from the MDN body, and (d) the value matches
`$MSG_ID`. Capture this as a written/recorded confirmation:

| Item | Captured from FGF |
|------|-------------------|
| MDN HTTP status | 200 OK (synchronous response on the original POST) |
| MDN signature verification | passed against `PHASE2.cer` (SHA-256 fingerprint matches §3.3 of `partner-coordination-FGF.md`) |
| MDN MIC algorithm | `sha-256` (lowercase, hyphenated) |
| MDN MIC value | matches what FGF computed locally pre-send |
| MDN `Original-Message-ID` | equals `$MSG_ID` from Step 1 |
| MDN `Disposition` value | `automatic-action/MDN-sent-automatically; processed` |

If any of these is red, refer to the failure-mode reconciliation in §3.4.

### 3.2 Single combined query (the canonical verdict line)

For the test record / ticket attachment, the single command that produces
the per-test evidence in one pane:

```bash
source deploy/.last-deploy.env
SA_KEY=$(az storage account keys list -g "$RG" -n "$SA" --query '[0].value' -o tsv)

echo "=== §1 LOG EVIDENCE ==="
az containerapp logs show \
  --resource-group "$RG" --name "$APP" --type console --tail 500 --format text \
  | grep -E 'Starting to handle|received .* \[<|signingAlgorithm=|signed outgoing MDN|sync MDN|sent MDN'

echo ""
echo "=== §2 STORAGE EVIDENCE ==="
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox \
  --query "[?starts_with(name, 'FGFCAAS21295T-PHASE2-')].{name:name, size:properties.contentLength, modified:properties.lastModified}" -o table

echo ""
echo "=== §2 ERROR DIRECTORY (must be empty) ==="
az storage file list \
  --account-name "$SA" --account-key "$SA_KEY" \
  --share-name data --path inbox/error \
  --query "[?contains(name, 'FGFCAAS21295T')].name" -o tsv
```

The log section must contain ≥6 of the 8 expected markers from §1.5; the
storage section must contain exactly 1 row; the error section must be
empty. If all three conditions hold *and* §3.1 Step 4 (FGF's
acknowledgement) is green, the test passes.

### 3.3 End-to-end pass/fail decision matrix

| §1 logs | §2 storage | FGF MDN ack | Verdict | Action |
|---------|------------|-------------|---------|--------|
| All 8 markers | inbox row + matching headers row | All §3.1 Step 4 items green | **PASS** | Record `$MSG_ID`, fingerprints, and timestamps in the test record. The deployment is cleared for production AS2 traffic from FGF. |
| All 8 markers | inbox row + matching headers row | FGF reports MDN signature failure | **FAIL — MDN crypto** | Most likely root cause: PHASE2-cert fingerprint dictated to FGF differs from the one served from the live keystore. Re-check `partner-coordination-FGF.md` §2.3 against `keytool -list -v -keystore … -alias PHASE2`. |
| All 8 markers | inbox row + matching headers row | FGF reports MIC mismatch | **FAIL — SHA-1 fallback** | The single most-likely failure mode (`partner-coordination-FGF.md` §5.5). Confirm the §1.5.5 `signingAlgorithm=sha-256` line was actually `sha-256` and not `sha-1`. If `sha-1`, FGF's `Disposition-Notification-Options` did not request `sha-256` strongly enough — partnership auto-filled with the default. |
| §1.5.4 verify-signature failure | n/a | n/a | **FAIL — partner cert mismatch** | The `fgfcaas21295t` keystore alias's public cert does not match the private key FGF actually signed with. Re-derive both fingerprints per `preflight-checklist.md` §3.3. |
| §1.5.3 decrypt failure | n/a | n/a | **FAIL — encryption mismatch** | If FGF chose to encrypt, they encrypted to a different cert than `PHASE2.cer`. Re-check `partner-coordination-FGF.md` §2 cert-delivery; force-restart the container if a fresh keystore upload was missed. |
| Entry-point §1.5.1 missing | n/a | n/a | **FAIL — never reached the servlet** | Network / TLS / ingress problem; jump to `ingress-configuration.md` §4 "Failure modes traceable to ingress misconfiguration". |
| §1 entry-point present, §1.5.7 sync-MDN missing, §1.5.7 has `Setup to send async MDN` | n/a | n/a | **FAIL — async path triggered** | FGF supplied `Receipt-Delivery-Option` requesting an async MDN. Constraint says sync only — coordinate with FGF to drop the header. |
| All 8 markers | inbox row missing | n/a | **PARTIAL — receive succeeded, persistence failed** | Storage processor module either failed silently or has a stale config. Check `MessageFileModule.java:102` log line; verify `data` Azure Files share is mounted at `/app/data` (`server-config-verification.md` §1.2). |

If a row not in this matrix appears (e.g., logs green + storage green + FGF
ack green but a *byte-level* mismatch in §2.4), the test fails by the
strictest reading of AC 5 ("Trading partner FGF can successfully send an
AS2 message … and receive a valid synchronous MDN back") — the receive
verdict is "the *protocol* succeeded but the *data* did not survive intact",
which is below the bar for production handoff.

### 3.4 Failure-mode reconciliation index

For every failure verdict in §3.3, the deeper diagnostic guidance lives in
exactly one sibling document. This is the index — do not duplicate the
guidance here:

| Failure verdict | Where to go next |
|-----------------|------------------|
| MDN crypto / cert fingerprint mismatch | `partner-coordination-FGF.md` §2.3, §2.5; `preflight-checklist.md` §3.3 |
| SHA-1 fallback / MIC mismatch | `partner-coordination-FGF.md` §5.5; `server-config-verification.md` §4.1, §4.3 |
| Partner cert mismatch / signature verify fails | `preflight-checklist.md` §3.3; `server-config-verification.md` §1.2 |
| Encryption to wrong cert | `partner-coordination-FGF.md` §2; `preflight-checklist.md` §3.4 |
| Never reached the servlet (ingress / TLS / 4xx-5xx) | `ingress-configuration.md` §4 |
| Async MDN triggered when sync expected | `partner-coordination-FGF.md` §5.1 |
| Persistence failure (logs green, storage empty) | `server-config-verification.md` §1.2; `phase2-lib/.../MessageFileModule.java:102` |

### 3.5 Pass-criteria roll-up for §3

- [ ] **§3.1 Step 1** `$MSG_ID` captured from a §1.5.2 log line.
- [ ] **§3.1 Step 2** Same `$MSG_ID` appears in §1.5.6, §1.5.7, §1.5.8.
- [ ] **§3.1 Step 3** Same `$MSG_ID` is the suffix of exactly one inbox
      filename.
- [ ] **§3.1 Step 4** FGF confirmed (in writing) all six MDN-receipt items.
- [ ] **§3.2** Single combined query produced ≥6 log markers, exactly 1
      inbox row, and 0 error-dir rows.
- [ ] **§3.3** Verdict row in the decision matrix is **PASS**.

When every box is checked, the test record can be closed with
**"PASS — FGF inbound AS2 reception verified end-to-end on
phase2-as2"**, and the deployment is cleared for further traffic.

### File map for §3

| Path | What it contributes |
|------|---------------------|
| `partner-coordination-FGF.md` | §3.4 references §2.3/§2.5 (cert fingerprints) and §5.5 (SHA-1 trap) for failure-mode triage. |
| `server-config-verification.md` | §3.4 references §1.2 (keystore mount) and §4 (SHA-256 guardrails). |
| `preflight-checklist.md` | §3.4 references §3.3 (fingerprint cross-checks) and §3.4 (cert exchange checklist). |
| `ingress-configuration.md` | §3.4 references §4 (ingress-layer failure-mode matrix). |
| `phase2-lib/src/main/java/com/helger/phase2/processor/storage/MessageFileModule.java` | Line 102 — the `stored message to …` log line whose absence implies persistence failure (§3.3 last row). |
| `deploy/.last-deploy.env` | Provides `$RG`, `$APP`, `$SA` for every CLI invocation in §3.1, §3.2. |

---

## Consolidated file map (all sections)

| Path | Sections that cite it |
|------|------------------------|
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/AbstractAS2ReceiveBaseXServletHandler.java` | §1.5 |
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/AbstractAS2ReceiveXServletHandler.java` | §1.5 |
| `phase2-lib/src/main/java/com/helger/phase2/processor/receiver/net/AS2ReceiverHandler.java` | §1.5, §1.8 |
| `phase2-lib/src/main/java/com/helger/phase2/util/AS2Helper.java` | §1.5 |
| `phase2-lib/src/main/java/com/helger/phase2/processor/receiver/AbstractActiveNetModule.java` | §1.5 |
| `phase2-servlet/src/main/java/com/helger/phase2/servlet/util/AS2ServletXMLSession.java` | §1.5 |
| `phase2-lib/src/main/java/com/helger/phase2/processor/storage/MessageFileModule.java` | §2.1, §2.3 (a/b), §3.3 |
| `phase2-demo-spring-boot/config/config.xml` | §2.1 |
| `deploy/.last-deploy.env` | §1.1, §2.2, §3.1, §3.2 |
| `deploy/azure-deploy.sh` | §1.4 (Log Analytics provisioning), §2 (data share provisioning) |
| `partner-coordination-FGF.md` | §3.3, §3.4 |
| `server-config-verification.md` | §3.3, §3.4 |
| `preflight-checklist.md` | §3.4 |
| `ingress-configuration.md` | §3.4 |
