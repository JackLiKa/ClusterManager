# Contributing to Cluster Manager

Thank you for your interest in contributing! This document outlines how to get started and what we expect from pull requests.

## Prerequisites

- Java 17
- Node.js 20+ and npm
- Maven 3.9+ (or use the included Maven wrapper)
- Git

## Getting started

1. Fork the repository and clone your fork.
2. Create a new branch from `main`:
   ```powershell
   git checkout -b feature/your-feature-name
   ```
3. Make your changes.
4. Run the tests and build:
   ```powershell
   .\mvnw.cmd clean verify
   cd frontend
   npm run lint
   npm run typecheck
   npm run build
   ```
5. Push your branch and open a pull request.

## Branch naming

- `feature/short-description` — new features
- `fix/short-description` — bug fixes
- `docs/short-description` — documentation changes
- `chore/short-description` — build, dependency, or repository maintenance

## Commit messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<scope>): <short summary>

<body>
```

Common types:

- `feat` — new feature
- `fix` — bug fix
- `docs` — documentation only
- `style` — formatting, no logic change
- `refactor` — code change that neither fixes a bug nor adds a feature
- `test` — adding or correcting tests
- `chore` — build or tooling changes

Example:

```text
fix(pseudo): validate virtual IP conflict before registration
```

## Code style

- Java: follow the existing style in the codebase; use meaningful variable names.
- TypeScript / Vue: follow the ESLint and Prettier configuration in `frontend/`.
- Keep changes focused. A pull request should address one concern.

## Pull request process

1. Ensure CI passes.
2. Update documentation if your change affects behavior.
3. Add or update tests for bug fixes and new features.
4. Request review from a maintainer.
5. Squash merge is preferred once approved.

## Reporting issues

Use GitHub Issues and choose the appropriate template. Include:

- A clear description of the problem.
- Steps to reproduce.
- Expected vs. actual behavior.
- Environment details (OS, Java version, browser if UI-related).

## Community

Be respectful and constructive. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
