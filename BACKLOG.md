# Backlog

Notes from testing the debug build on a Samsung Galaxy S26.

## Fixed, waiting for testing on the phone

- The Spam folder lists conversations instead of single messages, and opening one shows the normal conversation screen with only its spam, instead of a pop-up.
- The main screen shows its saved list straight away on start, and shows a loading indicator while the list is still empty.

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
