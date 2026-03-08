# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Phase2 (formerly "as2-lib") is a Java implementation of the AS2 protocol (RFC 4130) for secure document transport. It includes S/MIME signing/encryption, MDN handling (RFC 3798), and compression (RFC 5402). Forked from OpenAS2. Uses BouncyCastle for cryptography.

## Build Commands

```bash
# Build all modules (skip tests for speed)
mvn clean install -DskipTests

# Build with tests
mvn clean install

# Run a single test
mvn -pl phase2-lib test -Dtest=BCCryptoHelperTest

# Build a single module
mvn -pl phase2-lib clean install

# Run demo webapp in Jetty (from phase2-demo-webapp)
mvn jetty:run -pl phase2-demo-webapp
```

Java 17+ required. Uses Maven multi-module build with parent POM `com.helger:parent-pom`.

## Module Architecture

```
phase2-lib          Core library: crypto, message model, processors, partnerships, client
phase2-servlet      Servlet integration (AS2ReceiveServlet, MDN servlet)
phase2-server       Standalone socket-based AS2 server with command system
phase2-partnership-mongodb   MongoDB-backed IPartnershipFactory
phase2-demo-webapp  Example WAR using phase2-servlet
phase2-demo-spring-boot     Example Spring Boot app using phase2-servlet
```

Dependencies flow: `phase2-lib` ← `phase2-servlet` ← demo apps. `phase2-server` and `phase2-partnership-mongodb` depend only on `phase2-lib`.

## Key Abstractions (phase2-lib)

- **IAS2Session** (`com.helger.phase2.session`) — Central DI container providing access to certificate factory, partnership factory, and message processor. `AS2Session` is the concrete implementation.
- **IMessage / IMessageMDN** (`com.helger.phase2.message`) — Message model hierarchy. `AS2Message` and `AS2MessageMDN` are the concrete types.
- **IMessageProcessor** (`com.helger.phase2.processor`) — Orchestrates message processing through pluggable modules (sender, receiver, resender, storage). Uses chain-of-responsibility via `IProcessorModule` subtypes.
- **ICertificateFactory** (`com.helger.phase2.cert`) — Certificate/keystore management. Implementations: `CertificateFactory` (keystore-based), `PredefinedCertificateFactory`.
- **IPartnershipFactory** (`com.helger.phase2.partner`) — Partnership data store. Implementations: `XMLPartnershipFactory`, `SelfFillingPartnershipFactory`, `MongoDBPartnershipFactory`.
- **ICryptoHelper / BCCryptoHelper** (`com.helger.phase2.crypto`) — Sign, verify, encrypt, decrypt, compress, MIC calculation.
- **AS2Client** (`com.helger.phase2.client`) — High-level client API for sending AS2 messages. Uses `AS2ClientRequest`, `AS2ClientSettings`, `AS2ClientResponse`.

## Processor Module Types

Under `com.helger.phase2.processor`:
- **Receiver**: `AS2ReceiverModule`, `AS2MDNReceiverModule`, `AS2DirectoryPollingModule`
- **Sender**: `AS2SenderModule`, `AsynchMDNSenderModule`
- **Resender**: `DirectoryResenderModule`, `InMemoryResenderModule`, `ImmediateResenderModule`
- **Storage**: `MessageFileModule`, `MDNFileModule`

## Configuration Patterns

- **XML config** (server/servlet): `config.xml` defines certificates, partnerships, commands, and processor modules. See `phase2-server/src/main/resources/config/`.
- **Programmatic** (client): Build `AS2ClientSettings` + `AS2ClientRequest`, call `AS2Client.sendMessage()`.
- **Servlet handlers**: `AS2ReceiveXServletHandlerFileBasedConfig` (XML-based) or `AS2ReceiveXServletHandlerConstantSession` (code-based).

## Test Patterns

- JUnit 4/5 tests in standard `src/test/java` locations
- `SPITest` classes in multiple modules verify service provider interface registrations
- `Main*` classes are manual/integration test runners (not JUnit), e.g. `MainRunTestPhase2Server`, `MainTestClient`
- Test utilities: `MockAS2KeyStore`, `MockCertificateFactoryByteArray` in phase2-lib tests
- MongoDB tests use embedded Flapdoodle MongoDB

## Code Conventions

- Package root: `com.helger.phase2` (except Spring Boot demo uses `com.helger.as2demo.springboot`)
- Follows [Philip Helger's Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md)
- Heavy use of `@Nonnull`, `@Nullable` (JSR 305/JSpecify) annotations
- Relies on `ph-commons` library ecosystem (ph-commons, ph-web, ph-oton)
- SLF4J for logging throughout; Log4j2 in server/demo modules
