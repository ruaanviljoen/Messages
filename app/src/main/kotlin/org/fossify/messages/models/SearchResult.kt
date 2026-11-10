package org.fossify.messages.models

// [message] is the matching message; it is null for a conversation whose name matches.
data class SearchResult(
    val messageId: Long,
    val title: String,
    val snippet: String,
    val date: String,
    val threadId: Long,
    var photoUri: String,
    val message: Message? = null
)
