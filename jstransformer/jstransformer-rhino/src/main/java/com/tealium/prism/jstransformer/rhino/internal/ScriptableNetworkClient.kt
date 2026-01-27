package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkResult
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.annotations.JSFunction

class ScriptableNetworkClient(
    private val httpClient: NetworkClient,
    private val ctx: Context,
    private val scope: ScriptableObject,
) {
    // TODO - add custom header support
    @JSFunction
    fun get(url: String, callback: Function) =
        sendRequest(HttpRequest.get(url, null).build(), callback)

    @JSFunction
    fun post(url: String, body: String, callback: Function) =
        sendRequest(HttpRequest.post(url, body).build(), callback)

    private fun sendRequest(request: HttpRequest, callback: Function) {
        httpClient.sendRequest(request) { result: NetworkResult ->
            when (result) {
                is NetworkResult.Success -> {
                    // TODO - align property names (javaToJs will wrap methods of HttpResponse)
                    val httpResponse = Context.javaToJS(result.httpResponse, scope)
                    callback.call(ctx, scope, scope, arrayOf(httpResponse))
                }

                is NetworkResult.Failure -> {
                    val httpResponse = Context.javaToJS(result.networkException, scope)
                    callback.call(ctx, scope, scope, arrayOf(httpResponse))
                }
            }
        }
    }
}