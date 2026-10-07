# Changelog

All notable changes to the Simsli shopping list app, Wear OS companion, and server will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.3] - 2026-10-04
### Added
- **Server Version Display**: Added version badge to the Admin Dashboard and Database Browser headers and footers.

## [0.3.2] - 2026-10-04
### Added
- **Interactive Item Links**: URLs in item notes are now automatically detected and displayed as interactive preview chips with webpage titles.

## [0.3.1] - 2026-10-04
### Added
- **Admin Dashboard Auth**: Added password login form, secure session cookies, and logout for the server admin dashboard.
### Fixed
- **PostgreSQL Compatibility**: Transparently rebind SQL parameter placeholders for PostgreSQL database backends.

## [0.3.0] - 2026-10-04
### Added
- **Initial Release**: Complete offline-first shared shopping list with store filtering, item catalog, real-time sync, and household sharing.
