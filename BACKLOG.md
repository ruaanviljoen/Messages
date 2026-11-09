# Backlog

Notes from testing the debug build on a Samsung Galaxy S26. Nothing here has been started yet.

## Spam folder lists single messages instead of conversations

In the inbox, messages from one sender (for example Vodacom) are grouped into one conversation under the sender's name. The Spam folder instead lists every message separately.

Cause: the Spam folder screen (`SpamActivity`) was built as a flat list of messages, reusing the search results list.

Proposed fix: list Spam by conversation, the way the recycle bin does, with the sender's name as the title.

## Opening a Spam message shows a pop-up instead of the conversation

Tapping a message in Spam opens a dialog with the text and "Restore to inbox" and "Delete" buttons. It should open the normal conversation screen.

Cause: the same design choice as above. A dialog avoided changing the conversation screen.

Proposed fix: open the normal conversation screen in a Spam mode that shows only that conversation's spam, with restore and delete as message actions. The recycle bin already uses this pattern.

## The main screen is empty for a while when the app opens

When the app opens, the conversation list stays empty for some time, with no loading indicator. Fossify Messages showed the list straight away.

Likely cause, not yet confirmed: before showing the saved list, the app now works out which conversations to hide or give a different snippet because of spam (`withoutSpamOnly`). For every conversation whose newest message is spam, it queries the phone's message store. After a filter run there can be many such conversations, so the first display waits for all of them. Fossify shows a loading message only on the very first run, so nothing appears in the meantime.

Proposed fix: measure it first. Then show the saved list straight away using only the Spam database, which is fast, and do the slower checks afterwards. Show a loading indicator whenever the list is still empty while loading.
