# Third-party notices

LightReader's original source code is MIT licensed. Third-party dependencies
retain their own licenses; the project license does not relicense third-party
libraries, website material, books, or remotely loaded images.

| Component | License / notice |
| --- | --- |
| AndroidX / Jetpack Compose / Material / Room / JavaScriptEngine | Apache-2.0; distributed through Google's Maven repository |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | Apache-2.0 |
| OkHttp | Apache-2.0 |
| Coil | Apache-2.0 |
| jsoup | MIT |
| JUnit | EPL-1.0; test dependency |
| Gradle wrapper | Apache-2.0 |
| Bundled linkedom / HTML parsing dependencies | See `app/src/main/assets/comic-dom-NOTICES.txt`; build inputs and lockfile in `tools/comic-host/` |
| Bundled @noble/ciphers / @noble/hashes | MIT; complete copyright and license header retained in `app/src/main/assets/comic-crypto-vendor.js` |

The script compatibility layer and example adapters are implemented in this
project. Third-party source scripts installed by a user are not bundled with
the application and keep their respective licenses and access requirements.

## Venera and the comic-source community

- [Venera](https://github.com/venera-app/venera), maintained by venera-app:
  upstream application licensed under GPL-3.0. LightReader's script host is an
  independently implemented compatibility layer; the Venera application is not
  bundled or relicensed under this project's MIT license.
- [Venera source configurations](https://github.com/venera-app/venera-configs):
  thanks to the individual source authors and maintainers for documenting and
  maintaining the ecosystem. That repository does not declare one blanket
  license in its repository metadata. Review each script's own terms before
  redistribution; do not assume these scripts are MIT licensed.

Users can import compatible Venera-style source files or source indexes into
LightReader. Compatibility is partial, and unsupported host APIs or login flows
may require adaptation. The public LightReader repository includes its own
example adapters, not a redistributed copy of the entire Venera source catalog.

The public chapter test fixtures use original synthetic paragraphs. Fixture
markup and bibliographic metadata exist to test parser compatibility and are
not a distribution of a novel or a grant of rights in referenced works. The
application includes no book collection or account credentials.

The watercolour application artwork and custom UI icon paths are project
resources. The promotional film uses original demonstration text/comics and
separate media credits; publishing application source does not relicense NASA,
font, or other externally sourced promotional material.
