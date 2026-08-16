# Security Policy

## Supported Versions

MQCluster is a local-first learning tool and is currently in early development (`0.1.x`).
Security fixes are applied to the latest `main` branch and the most recent release.

| Version | Supported |
| --- | --- |
| latest `main` | ✅ |
| latest release tag | ✅ |
| older releases | ❌ |

---

## Reporting a Vulnerability

**Do NOT open a public GitHub issue for security vulnerabilities.**

If you discover a security vulnerability in MQCluster, please report it responsibly:

1. **Use GitHub Security Advisories** — go to
   [https://github.com/JackLiKa/MQCluster/security/advisories/new](https://github.com/JackLiKa/MQCluster/security/advisories/new)
   and submit a private vulnerability report.
2. Include the following in your report:
   - A description of the vulnerability and its potential impact.
   - Steps to reproduce (proof-of-concept if possible).
   - Affected versions / commits.
   - Any suggested mitigations or fixes.
3. You will receive an acknowledgment within **72 hours**.
4. We will work with you to validate the issue and coordinate a fix and disclosure timeline.

### Coordinated disclosure

We ask that you:

- Do not publicly disclose the vulnerability until a fix has been released.
- Give us a reasonable window (typically 90 days) to develop and ship a patch.
- Credit will be given in the release notes unless you prefer to remain anonymous.

---

## Scope

This policy applies to the **MQCluster** repository at
[https://github.com/JackLiKa/MQCluster](https://github.com/JackLiKa/MQCluster), including:

- The Java backend (`java/`)
- The Next.js frontend (`next/`)
- CI workflows and build configuration

### Out of scope

- Vulnerabilities in third-party dependencies — report these to the upstream project.
- Issues that require already-compromised access to the user's machine.
- Denial-of-service via intentionally resource-exhausting local inputs (the tool runs locally by design).

---

## Security best practices for users

MQCluster is designed to run **locally** on a single machine for learning purposes. Keep in mind:

- The backend listens on **port 8088** and the frontend on **port 3000** — both bind to `localhost` by default.
  Do **not** expose these ports to the public internet.
- The embedded RocketMQ runtime stores data under `java/run/pseudo-cluster/` — this directory may contain
  message payloads. Avoid running MQCluster on shared or untrusted machines if you handle sensitive data.
- Always run from source or official releases. Do not trust unverified binaries.

---

## Acknowledgements

We thank all security researchers and contributors who report vulnerabilities responsibly.
Your efforts keep the open-source community safe.
