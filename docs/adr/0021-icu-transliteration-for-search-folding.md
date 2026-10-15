# 0021. ICU transliteration for search folding

Status: Accepted

Date: 2026-10-15

## Context

Search (SEARCH-1) matches names case-insensitively. Umlauts and accents break
that: searching "Apfel" should find "Äpfel", "strasse" should find "Straße".
Two implementation options:

1. `java.text.Normalizer` (NFD + strip combining marks + small hand-rolled map
   for undecomposable letters like ß/æ/ø). Works on device *and* in plain JVM
   unit tests, stateless, fast.
2. `android.icu.text.Transliterator` ("Any-Latin; Latin-ASCII"). Covers the
   same cases plus full-script transliteration ("Хлеб" → "hleb") with no
   hand-maintained tables — but exists only on device (API 24+, minSdk 26),
   and Transliterator instances are stateful (not thread-safe) while the fold
   runs inside `combine` on arbitrary dispatcher threads.

## Decision

Use ICU transliteration (option 2) behind the existing `SearchFold.fold` API.
One Transliterator per thread via ThreadLocal. The fold is not unit-tested:
its behavior is ICU's; our contribution is the single transform ID, verified
by a manual emulator smoke test. The one JVM-tested consumer
(`applyListSearch`) receives the fold as an injected `(String) -> String` and
is tested with a stub.

## Consequences

- Cross-script search for free (Cyrillic, Greek, …); no diacritic table to maintain.
- JVM unit tests never touch android.icu — no Robolectric needed.
- The ICU fold itself ships without automated tests; regressions would come
  from our transform ID / threading, not ICU's rules.
- "Any-Latin" over-matches by design: typing a Latin transliteration finds
  items named in other scripts (desirable for search, occasionally surprising).
- Swapping the fold implementation later touches one file plus the injected
  call sites.
