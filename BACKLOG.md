# Backlog

Notes from testing the debug build on a Samsung Galaxy S26.

## Fixed, waiting for testing on the phone

- The Spam folder lists conversations instead of single messages, and opening one shows the normal conversation screen with only its spam, instead of a pop-up.
- The main screen shows its saved list straight away on start, and shows a loading indicator while the list is still empty.
- The newest messages in a Spam conversation are no longer hidden behind the navigation buttons.
- Search results can be selected with press and hold (copy, share, delete, select all), acting on the selected messages only.
- Search results stay after rotating the phone.

## Proposal: spam filter settings

At the moment, everything about the spam filter is in the menu of the keywords screen: running the filter, the action, the saved-contacts check box and the exempt numbers. The labels are cut off, and the menu mixes actions with settings.

Proposal: move the settings to a "Spam filter" section in the main Settings screen, like the existing sections for notifications, archived messages and the recycle bin. Each row uses a kind of row the Settings screen already has.

| Row | Kind of row | What it does |
|---|---|---|
| Spam keywords | Plain row, like "Manage blocked numbers" | Opens the keyword list. That screen's menu keeps only add, import and export. |
| When a message matches | Row with its current value underneath, like "Font size" | Opens a choice between "Move it to Spam" and "Delete it". |
| Filter messages from saved contacts | Switch, like "Send on enter" | Off by default. |
| Numbers never filtered | Plain row | Opens a list screen like the keyword list, with one number or sender name per row, which can be added, edited and deleted. This replaces the current text box. |
| Run filter on existing messages | Plain row | Works out the counts and asks before applying, as now. |

The section would sit directly below "General", where the blocked numbers and blocked keywords rows are now. "Manage blocked keywords" would move into the new section as "Spam keywords". Changing any of these settings would still offer to run the filter with counts, as it does now.

Alternative: a separate "Spam filter" screen with the same rows, opened from one row in Settings. It keeps the main Settings screen shorter, but adds a screen, and Fossify keeps most settings on the single Settings screen. I suggest the section.

Messages+ also put its spam settings in a section of the main Settings screen.

## Upstream contribution: search results lost on rotation

The fix for search results disappearing after rotation is a Fossify bug, not one from this fork, so it should go to Fossify Messages.

- Branch: `fix/search-rotation` in this fork. It is based on Fossify's `main` and contains only this fix, as one commit (`fix: keep search results after screen rotation`) with a "Fixed" line in `CHANGELOG.md`.
- The same fix is in the work branch as two commits ("Keep search results when the phone rotates" and "Reopen an empty search after rotation too").
- This branch does not contain the fork's `.githooks`, so run gitleaks by hand before pushing to it.

### After Fossify merges a fix for this

The fix is in both this fork's work branch and the upstream branch, so a later merge from Fossify needs this check:

- If Fossify merges the code unchanged, the merge is clean and nothing needs doing. This was tested: merging `fix/search-rotation` into a copy of the work branch brought in only the changelog line, with no conflicts.
- If Fossify changes the code during review, the merge conflicts in `onRestoreInstanceState` in `MainActivity.kt`. Keep Fossify's version and drop ours.
- If Fossify fixes it in Fossify Commons (`MySearchMenu`) instead, it arrives as a Commons version update and Git may not flag anything. Delete our `onRestoreInstanceState` from `MainActivity.kt` after that merge, so the search is not reopened twice.

### Fossify's rules that apply

From Fossify's contribution guide (FossifyOrg/General-Discussion):

- A pull request needs an open issue labelled `help wanted`. Pull requests without one are closed without review.
- Keep it to one focused change, make sure all CI checks pass, and test it well.
- Test UI changes on every theme (Light, Dark, Black & White, System default) and with the largest system font size.
- Prefer conventional commit messages. Most pull requests are squash merged.
- AI-assisted contributions are allowed if they follow the guidelines.

### Steps

1. Reproduce the bug in the latest official Fossify Messages from its releases page, not in this fork. The issue form asks you to confirm this.
2. Open an issue on FossifyOrg/Messages using the text below, and fill in the version, Android version and how it was installed.
3. Wait until a maintainer labels it `help wanted`. If they would rather fix it in Fossify Commons (see "Additional information"), the fix moves there.
4. Test the branch on the phone: search, rotate both ways, keep typing, press back; with each theme and the largest font size.
5. Add the issue number to the `CHANGELOG.md` line in the branch, in Fossify's format (`([#123])` with a link at the bottom of the file), rebase the branch on Fossify's latest `main`, and push.
6. Open the pull request from `fix/search-rotation` to FossifyOrg/Messages `main`, link the issue, and check that CI passes.

### Draft issue text

Title: Search results disappear after rotating the screen

Affected app version: 1.10.2 (fill in the version you reproduced it with)

Affected Android/Custom ROM version: (fill in, for example One UI 8 / Android 16)

Affected device model: Samsung Galaxy S26

How did you install the app: (fill in: GitHub releases, F-Droid or Google Play)

Steps to reproduce the bug:
1. Open the app and tap the search box.
2. Type a word that appears in at least one message, so that results are shown.
3. Rotate the phone.

Expected behavior: The search stays open with the same text, and the results are shown again.

Actual behavior: The text stays in the search box, but the results disappear and the screen asks you to type something to search. Typing more letters does not bring results back. The search only works again after closing it and opening it again.

Additional information: Rotating recreates MainActivity. The search field restores its text, but `MySearchMenu` in Fossify Commons does not save that the search was open (`isSearchOpen` is a plain field). When the restored text triggers `onSearchTextChangedListener`, `searchTextChanged()` returns early because the search counts as closed. I have a small fix in Messages that reopens the search after a restore and runs it again (`onRestoreInstanceState` in MainActivity). I am happy to open a pull request for it, or to fix it in `MySearchMenu` instead if you prefer, since that would cover the other Fossify apps with a search bar.
