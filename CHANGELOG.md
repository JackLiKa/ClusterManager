# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- English-first README and bilingual documentation structure.
- Apache-2.0 license.
- GitHub Actions CI workflow, issue templates, and pull request template.
- Frontend ESLint and Prettier configuration.

### Changed

- Updated `pom.xml` with open-source metadata: license, developers, SCM, and issue management.
- Standardized Maven build to resolve dependencies from Maven Central without local override.

### Fixed

- Removed redundant `invokeNodeOperation` call in `RocketMqClusterProvider`.
- Stabilized integration tests by enabling auto-start for pseudo runtime tests and isolating Spring contexts.
- Added generic `Exception` fallback in `ApiExceptionHandler` to avoid leaking internal errors.
