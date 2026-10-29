package io.curblink.demo

/** Correlation and ordering only; the existing interlock remains the authorization authority. */
class BridgeSession {
    var sessionId: String? = null
        private set
    private var sequence = -1L
    var pending: Pending? = null
        private set
    data class Pending(val id: String, val action: String, val at: Long, val accepted: Boolean = false)

    fun acceptFrame(session: String, seq: Long): Boolean {
        if (session.isBlank() || seq < 0) return false
        if (sessionId != session) {
            sessionId = session
            sequence = -1
            pending = null
        }
        if (seq <= sequence) return false
        sequence = seq
        return true
    }
    fun begin(id: String, action: String, now: Long): Boolean {
        if (sessionId == null || pending != null || id.isBlank() || action !in listOf("OPEN", "CLOSE")) return false
        pending = Pending(id, action, now)
        return true
    }
    fun result(session: String, id: String, accepted: Boolean): Boolean {
        val p = pending ?: return false
        if (session != sessionId || id != p.id) return false
        pending = if (accepted) p.copy(accepted = true) else null
        return true
    }
    fun observe(tailgate: Tailgate, fresh: Boolean): String? {
        val p = pending ?: return null
        if (!fresh || !p.accepted) return null
        if (tailgate != if (p.action == "OPEN") Tailgate.OPEN else Tailgate.CLOSED) return null
        pending = null
        return p.action
    }
    fun timedOut(now: Long): Boolean = pending?.let { now - it.at >= 5000 } ?: false
}
