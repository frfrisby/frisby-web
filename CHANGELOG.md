# Changelog

## [1.4.0](https://github.com/frfrisby/frisby-web/compare/v1.3.4...v1.4.0) (2026-07-29)


### Features

* **server:** Introduce HttpVerb enum for type-safe method specification. ([#49](https://github.com/frfrisby/frisby-web/issues/49)) ([980368d](https://github.com/frfrisby/frisby-web/commit/980368dfeca46bbbaede41272cb92937a3534149))

## [1.3.4](https://github.com/frfrisby/frisby-web/compare/v1.3.3...v1.3.4) (2026-07-29)


### Bug Fixes

* **server:** Removed duplicate argument name strings and increased unit test coverage. ([#46](https://github.com/frfrisby/frisby-web/issues/46)) ([af17443](https://github.com/frfrisby/frisby-web/commit/af174430ad0f80eba28d02bfc8ba3cf62707a53a))

## [1.3.3](https://github.com/frfrisby/frisby-web/compare/v1.3.2...v1.3.3) (2026-07-29)


### Bug Fixes

* Allow custom Accept header and improve exception handling. ([#43](https://github.com/frfrisby/frisby-web/issues/43)) ([9894cb8](https://github.com/frfrisby/frisby-web/commit/9894cb8ca64dfcea63e8d96e482b44b06244e5bf))

## [1.3.2](https://github.com/frfrisby/frisby-web/compare/v1.3.1...v1.3.2) (2026-07-28)


### Bug Fixes

* **server:** Add Endpoint to RequestCompletedEvent. ([#40](https://github.com/frfrisby/frisby-web/issues/40)) ([c6aa30c](https://github.com/frfrisby/frisby-web/commit/c6aa30c020892bb2abe5e30ab906c7eada7af67c))

## [1.3.1](https://github.com/frfrisby/frisby-web/compare/v1.3.0...v1.3.1) (2026-07-27)


### Bug Fixes

* **client:** Add convenience constructors to all exception classes. ([#38](https://github.com/frfrisby/frisby-web/issues/38)) ([1e6c1cf](https://github.com/frfrisby/frisby-web/commit/1e6c1cfe26199b8bc7ef481ec5b8e0c58a2b2b0b))
* Suppress deployment of root aggregator POM. ([#36](https://github.com/frfrisby/frisby-web/issues/36)) ([ac316ec](https://github.com/frfrisby/frisby-web/commit/ac316eceb59854aa18edb707960f80d36de4a676))

## [1.3.0](https://github.com/frfrisby/frisby-web/compare/v1.2.0...v1.3.0) (2026-07-27)


### Features

* **bom:** pin frisby-core, Jetty, and Jersey BOMs in published BOM ([#33](https://github.com/frfrisby/frisby-web/issues/33)) ([59d80c7](https://github.com/frfrisby/frisby-web/commit/59d80c71b463ce33a0ef489049258374668f1365))

## [1.2.0](https://github.com/frfrisby/frisby-web/compare/v1.1.3...v1.2.0) (2026-07-23)


### Features

* **client:** Add first-class exceptions and public API for client retry policy. ([#30](https://github.com/frfrisby/frisby-web/issues/30)) ([24dceca](https://github.com/frfrisby/frisby-web/commit/24dceca8525c0ed2e3610095d1be844812ae122a))


### Bug Fixes

* **client:** Address Sonar issues — S2140, S3776, coverage gaps. ([#31](https://github.com/frfrisby/frisby-web/issues/31)) ([92aade9](https://github.com/frfrisby/frisby-web/commit/92aade9547f6871b8b64ed23de072abf063d12a7))
* Correct published Project URL and clarify README introduction. ([#28](https://github.com/frfrisby/frisby-web/issues/28)) ([f4f06c1](https://github.com/frfrisby/frisby-web/commit/f4f06c1fa9e0a66431a23215a88fae93b91e2b72))

## [1.1.3](https://github.com/frfrisby/frisby-web/compare/v1.1.2...v1.1.3) (2026-07-21)


### Bug Fixes

* Unit test for empty-password branch in Basic Auth decoder ([#25](https://github.com/frfrisby/frisby-web/issues/25)) ([4654a5f](https://github.com/frfrisby/frisby-web/commit/4654a5fffa8b2178c1408ab0bf25a306a0b499d2))

## [1.1.2](https://github.com/frfrisby/frisby-web/compare/v1.1.1...v1.1.2) (2026-07-21)


### Bug Fixes

* Fix Javadoc [@param](https://github.com/param) warnings for record compact constructors ([#21](https://github.com/frfrisby/frisby-web/issues/21)) ([eea11ca](https://github.com/frfrisby/frisby-web/commit/eea11ca6fb4f3ed909d95694662fd2864c8e4d43))

## [1.1.1](https://github.com/frfrisby/frisby-web/compare/v1.1.0...v1.1.1) (2026-07-21)


### Bug Fixes

* Address remaining Sonar issues and close coverage gaps in server module. ([#12](https://github.com/frfrisby/frisby-web/issues/12)) ([9108780](https://github.com/frfrisby/frisby-web/commit/9108780fedc25d2df24a3a8cfeccc0e964d2e5d1))
* Address remaining SonarCloud maintainability issues in client module. ([#10](https://github.com/frfrisby/frisby-web/issues/10)) ([3ea52dc](https://github.com/frfrisby/frisby-web/commit/3ea52dc3b7de5b06266a9911dfffbe989a7fe841))
* Address Sonar issues in oauth2-security module. ([#13](https://github.com/frfrisby/frisby-web/issues/13)) ([aec8ebc](https://github.com/frfrisby/frisby-web/commit/aec8ebc0a694713fd7232c189d5a7fb90a0f86df))
* Address Sonar issues in server-basic-security module. ([#14](https://github.com/frfrisby/frisby-web/issues/14)) ([de39e90](https://github.com/frfrisby/frisby-web/commit/de39e906027e69a1a17e82135d3a2757cafcae78))
* Address SonarCloud maintainability issues. ([#9](https://github.com/frfrisby/frisby-web/issues/9)) ([cbf3f83](https://github.com/frfrisby/frisby-web/commit/cbf3f836d502ee476afcfe40546f394892236c47))
* Address SonarCloud quality issues. ([#7](https://github.com/frfrisby/frisby-web/issues/7)) ([e9eaacc](https://github.com/frfrisby/frisby-web/commit/e9eaacc18283e25fc633630fd3ab5b248c8d42ab))
* Exclude test-log and test-support modules from Sonar analysis. ([#16](https://github.com/frfrisby/frisby-web/issues/16)) ([f8233aa](https://github.com/frfrisby/frisby-web/commit/f8233aa2ef296a67e97ac70b3a1f054a66827fa8))
* Move S110 suppression to root pom to resolve Sonar module-level warning. ([#15](https://github.com/frfrisby/frisby-web/issues/15)) ([1459481](https://github.com/frfrisby/frisby-web/commit/1459481761a9263497135f803b7c5c5bf99878fb))

## [1.1.0](https://github.com/frfrisby/frisby-web/compare/v1.0.0...v1.1.0) (2026-07-20)


### Features

* Initial release. ([#3](https://github.com/frfrisby/frisby-web/issues/3)) ([b5bfd0b](https://github.com/frfrisby/frisby-web/commit/b5bfd0b29c4487421c36f3fde6d7f0c8e0a2ad57))
