# Releasing

This document is for maintainers. It describes how to publish a new release
of `api-doc-maven-plugin` to Maven Central through the central portal.

## Prerequisites

1. An account on [central.sonatype.com](https://central.sonatype.com) with a
   **token** written to `~/.central/credentials` (single line
   `token=<your-token>`), plus the same credentials under
   `<server><id>central</id>` in `~/.m2/settings.xml`.
2. A **GPG signing key** whose public key has been published to a key
   server. The `gpg.keyname` property in the pom holds the release key
   fingerprint; change it there (or override with `-Dgpg.keyname=...`) when
   rotating keys. The passphrase is passed through the `GPG_PASSPHRASE`
   environment variable and must never be committed.
3. A non-SNAPSHOT version number that has never been published to Central
   before. Bump the version in `pom.xml` (and in the `scm` tag), and update
   the plugin version references in the README files, `CHANGELOG.md` and
   `examples/spring-api/pom.xml`.

## Publish

```bash
export GPG_PASSPHRASE='<signing-key-passphrase>'
mvn -P central clean verify org.sonatype.central:central-publishing-maven-plugin:publish
```

The `central` profile builds the sources and javadoc jars, signs all
artifacts with GPG and uploads them.

## Finalize

After the upload, the deployment is in the VALIDATED state. Open the
[central.sonatype.com Deployments page](https://central.sonatype.com/publishing/deployments)
and click **Publish** to release it (a first-time release of new
coordinates also requires verifying the `io.github.advelix` namespace on the
[Namespaces page](https://central.sonatype.com/publishing) beforehand).

Track validation and propagation on the
[publisher health page](https://central.sonatype.com/publisher/health);
it usually completes within minutes.

Finally, tag the release commit and create the GitHub release. The tag is
identical to the version in the pom's `<scm><tag>` — no `v` prefix (e.g.
`1.0.1`) — so that the tag, the scm tag and the `CHANGELOG.md` heading
always agree. Use the corresponding `CHANGELOG.md` entry as the release
notes body, and do not attach any artifacts (the jar is distributed through
Maven Central).
