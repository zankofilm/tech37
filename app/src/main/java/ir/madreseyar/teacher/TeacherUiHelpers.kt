package ir.madreseyar.teacher

import org.json.JSONObject

/** A student-and-subject route, not a generic jump to an empty messages list. */
internal data class MessageTarget(val studentId: String, val subject: String)

/** Never start a conversation with an arbitrary student outside the server's allowed contacts. */
internal fun findContactForTarget(contacts: List<JSONObject>, target: MessageTarget): JSONObject? {
    if (target.studentId.isBlank() || target.subject.isBlank()) return null
    return contacts.firstOrNull {
        it.optString("studentId") == target.studentId && it.optString("subject") == target.subject
    }
}
