# Content and source notice

This public repository contains the Basair Android source code and unchanged original book files and individually extracted reading pages used by the application. Public availability is not a blanket commercial reuse license.

Each book has its own source and rights statement in `content/books.json`, also embedded in the application. Some editions are classified as public domain by their source; others are included under the source's republication policy or a specific permission requiring attribution and unchanged content. Those statements do not apply automatically to other editions or to commercial use. See `docs/CONTENT_SOURCES_AR.md` and `docs/EXPANSION_V04_AR.md`.

No license covering all repository content as one work is granted. Rights in original books, modern editorial contributions, third-party dependencies, source-site snapshots, and application code remain with their respective owners unless an applicable permission says otherwise.

Signing keys, passwords, account credentials and local machine settings are excluded from the repository. Generate your own upload key for a separately maintained release. The original application's Play upload key must not be committed or distributed.

The four school-specific additions in 0.8.1 are documented edition by edition in `docs/MADHHAB_BOOKS_V081_AR.md` and `content/madhhab-review.json`. Three specific scans are classified as public domain by Wikisource. The modern Umdat al-Fiqh edition uses IslamHouse republication permission with attribution and unchanged content; it is not represented as public domain. An explicitly reserved modern Abu Shuja edition was excluded.

Version 0.8.2 keeps originals and extracted pages outside the application package. Page extraction preserves original page drawing content, resources and annotations; it is a delivery format, not a new edition. Source attribution and edition-specific rights are retained in the reader and catalog. See `docs/FEATURES_V082_AR.md`.


Version 0.12.0 adds AndroidX Media3 ExoPlayer and MediaSession (1.11.1), licensed under Apache License 2.0. The license is included in `app/src/main/assets/licenses/androidx-media3-LICENSE.txt` and in the APK. Upstream: https://github.com/androidx/media . Dependency rights are separate from audiobook permissions.

Audiobook metadata and source permissions are documented in `content/audiobooks/` and `docs/AUDIO_SOURCES_V012_AR.md`. The original recordings remain on IslamHouse hosting; they are not committed or rehosted. The adopted permission covers noncommercial use with source attribution and unchanged scholarly content. No general commercial audio license is asserted.
