package com.tealium.prism.core.api.misc

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TealiumResultUtilsTests {

    @RelaxedMockK
    private lateinit var failureCallback: Callback<Throwable>

    @RelaxedMockK
    private lateinit var successCallback: Callback<Boolean>

    @RelaxedMockK
    private lateinit var failureLambda: (Throwable) -> Unit

    @RelaxedMockK
    private lateinit var successLambda: (Boolean) -> Unit

    private val exception = Exception()
    private lateinit var result: TealiumResult<Boolean>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun onSuccess_Executes_Handler_When_Successful() {
        result = TealiumResult.success(true)

        result.onSuccess(successLambda)
        result.onSuccess(successCallback)

        verify {
            successLambda.invoke(true)
            successCallback.onComplete(true)
        }
    }

    @Test
    fun onSuccess_Does_Not_Execute_Failure_Handler_When_Successful() {
        result = TealiumResult.success(true)

        result.onFailure(failureLambda)
        result.onFailure(failureCallback)

        verify {
            failureLambda wasNot Called
            failureCallback wasNot Called
        }
    }

    @Test
    fun onFailure_Executes_Handler_When_Failure() {
        result = TealiumResult.failure(exception)

        result.onFailure(failureLambda)
        result.onFailure(failureCallback)

        verify {
            failureLambda.invoke(exception)
            failureCallback.onComplete(exception)
        }
    }

    @Test
    fun onFailure_Does_Not_Execute_Success_Handler_When_Success() {
        result = TealiumResult.failure(exception)

        result.onSuccess(successLambda)
        result.onSuccess(successCallback)

        verify {
            successLambda wasNot Called
            successCallback wasNot Called
        }
    }

    @Test
    fun result_Calls_The_Correct_Handler_When_Chained() {
        result = TealiumResult.success(true)

        result.onSuccess(successLambda)
            .onSuccess(successCallback)
            .onFailure(failureLambda)
            .onFailure(failureCallback)

        verify {
            successLambda.invoke(true)
            successCallback.onComplete(true)
            failureLambda wasNot Called
            failureCallback wasNot Called
        }
    }
}