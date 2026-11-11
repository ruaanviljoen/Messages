# About this fork

This repository is a fork of [Fossify Messages](https://github.com/FossifyOrg/Messages) that adds a spam filter. The working name of the app is "Mzansi Messages". The approach of keeping filtered messages in a Spam folder instead of deleting them follows [Messages+](https://github.com/arikw/messages) by arikw.

The fork is licensed under GPL-3.0, like Fossify Messages. The licence and the copyright notices from the upstream projects must be kept.

## Tracking upstream

Fossify is the only upstream. Merge its release tags into this fork rather than rebasing:

```sh
git remote add upstream https://github.com/FossifyOrg/Messages.git
git fetch upstream --tags
git merge <release-tag>
```

To keep merges simple, changes to Fossify's own files are limited to a few hook points, and new code lives in its own files.

## Guardrails against leaking secrets and personal data

The hooks in `.githooks` run [gitleaks](https://github.com/gitleaks/gitleaks) with the rules in `.gitleaks.toml`. The pre-commit hook checks staged changes, and the pre-push hook checks every commit that is not yet on a remote. Besides gitleaks' default secret rules, they block South African mobile numbers and email addresses. Enable them once per clone, after installing gitleaks:

```sh
git config core.hooksPath .githooks
```

Tests and documentation must use made-up data only: phone numbers of the form 082 000 000x, and addresses at example.com. Never commit real messages, message exports, contacts, screenshots of real conversations, keystores or signing details.

## Tests

The unit tests are in `app/src/test`. Most run on Robolectric, which simulates Android on the computer, so no phone or emulator is needed. `FakeTelephonyProvider` stands in for the phone's SMS and MMS store.

```sh
./gradlew testFossDebugUnitTest
```

The first run downloads Robolectric's Android libraries from Maven Central. Where Maven Central is blocked or rate limited, set a mirror in `~/.gradle/gradle.properties`, for example `robolectricRepoUrl=https://maven-central.storage-download.googleapis.com/maven2/`.

## Before a change is pushed

1. The gitleaks hooks pass.
2. The unit tests, detekt and lint pass.
3. The diff has been reviewed, and the review's findings have been fixed or answered.

Work happens on branches. A change reaches `main` only through a pull request that the repository owner has approved.
