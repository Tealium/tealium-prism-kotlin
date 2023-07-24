package com.tealium.core.internal.network

sealed class NetworkResult
class Success(val responseData: HttpResponseData) : NetworkResult()
class Failure(val networkError: NetworkError) :NetworkResult()