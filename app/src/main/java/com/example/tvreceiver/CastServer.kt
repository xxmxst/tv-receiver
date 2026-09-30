package com.example.tvreceiver

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class CastServer(
    private val onUrlReceived: (CastMediaRequest) -> Unit,
    private val statusProvider: () -> String = { "{\"state\":\"idle\"}" },
    private val onControlAction: (String) -> Pair<Boolean, String>
) : NanoHTTPD(PORT) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/health" -> {
                newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
            }

            session.method == Method.GET && session.uri == "/status" -> {
                newFixedLengthResponse(Response.Status.OK, "application/json", statusProvider())
            }

            session.method == Method.POST && session.uri == "/cast" -> {
                val files = HashMap<String, String>()
                return try {
                    session.parseBody(files)
                    val rawBody = files["postData"].orEmpty()
                    val json = JSONObject(rawBody)
                    val url = json.optString("url", "")
                    if (url.isBlank()) {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing url")
                    } else {
                        val headers = linkedMapOf<String, String>()
                        json.optJSONObject("headers")?.let { objectValue ->
                            objectValue.keys().forEach { key ->
                                val value = objectValue.optString(key).trim()
                                if (value.isNotBlank() && key.length <= 80) headers[key] = value
                            }
                        }
                        onUrlReceived(
                            CastMediaRequest(
                                url = url,
                                mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
                                title = json.optString("title").takeIf { it.isNotBlank() },
                                headers = headers
                            )
                        )
                        newFixedLengthResponse(Response.Status.OK, "text/plain", "received")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error")
                }
            }

            session.method == Method.POST && session.uri == "/control" -> {
                val files = HashMap<String, String>()
                return try {
                    session.parseBody(files)
                    val rawBody = files["postData"].orEmpty()
                    val json = JSONObject(rawBody)
                    val action = json.optString("action", "")
                    if (action.isBlank()) {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing action")
                    } else {
                        val (ok, msg) = onControlAction(action)
                        if (ok) {
                            newFixedLengthResponse(Response.Status.OK, "text/plain", msg)
                        } else {
                            newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", msg)
                        }
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error")
                }
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    companion object {
        const val PORT = 9527
    }
}
