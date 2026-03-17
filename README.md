# phase2 (formerly known as "as2-lib")

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.phase2/phase2-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.phase2/phase2-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.phase2/phase2-lib/javadoc.svg)](https://javadoc.io/doc/com.helger.phase2/phase2-lib)
<!-- ph-badge-end -->

AS2 is a transport protocol specified in [RFC 4130](http://www.ietf.org/rfc/rfc4130.txt).
AS2 version 1.1 adding compression is specified in [RFC 5402](http://www.ietf.org/rfc/rfc5402.txt).
The MDN is specified in [RFC 3798](http://www.ietf.org/rfc/rfc3798.txt).
Algorithm names are defined in [RFC 5751](https://www.ietf.org/rfc/rfc5751.txt) (S/MIME 3.2) which supersedes [RFC 3851](https://www.ietf.org/rfc/rfc3851.txt) (S/MIME 3.1);

See the **[Wiki](https://github.com/phax/phase2/wiki)** for all details.
It also contains [License details](https://github.com/phax/phase2/wiki/Licensing). 

This library is a fork of [OpenAS2](http://sourceforge.net/projects/openas2/) which did not release updates since 2010 (as per August 2015 they are on GitHub at https://github.com/OpenAS2/OpenAs2App).
I then split the project into a common library part (the "as2-lib" submodule) and a server part (the "as2-server" submodule) which contains a stand alone (socket) server.
The library project also contains a simple AS2 client which can be used to send messages to other AS2 servers (as part of "as2-lib").

Per 09/2025 the library was renamed to "phase2" to match the name of the library [phase4](https://github.com/phax/phase4) which is an OpenSource AS4 implementation.

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.