package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.pubsub.onFailure
import com.tealium.prism.core.api.pubsub.onSuccess
import com.tealium.prism.core.internal.misc.SynchronousScheduler
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import com.tealium.prism.momentsapi.internal.MomentsApiModule
import com.tealium.prism.momentsapi.internal.MomentsApiWrapper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class MomentsApiWrapperTests {

    @RelaxedMockK
    private lateinit var momentsApiModule: MomentsApiModule

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<ModuleManager?>
    private lateinit var proxy: ModuleProxy<MomentsApiModule>

    private lateinit var momentsApiWrapper: MomentsApiWrapper
    private val scheduler = SynchronousScheduler()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { momentsApiModule.id } returns MomentsApiModule.Factory().moduleType
        modules = Observables.stateSubject(listOf(momentsApiModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(moduleManager)

        proxy = ModuleProxyImpl(MomentsApiModule::class.java, onModuleManager, scheduler)
        momentsApiWrapper = MomentsApiWrapper(proxy)
    }

    @Test
    fun fetchEngineResponse_Reports_ModuleNotEnabled() {
        modules.onNext(emptyList())

        val completion = mockk<Callback<Exception>>(relaxed = true)
        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is ModuleNotEnabledException }) }
    }

    @Test
    fun fetchEngineResponse_Reports_TealiumShutdown_WhenNoModuleManager() {
        onModuleManager.onNext(null)

        val completion = mockk<Callback<Exception>>(relaxed = true)
        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is Tealium.TealiumShutdownException }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WhenModuleEnabled() {
        val engineResponse = EngineResponse(flags = mapOf("test" to true))
        val completion = mockk<Callback<EngineResponse>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(engineResponse))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(engineResponse) }
    }

    @Test
    fun fetchEngineResponse_PassesCorrectEngineID_ToModule() {
        val testEngineId = "test-engine-123"
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(EngineResponse()))
        }

        momentsApiWrapper.fetchEngineResponse(testEngineId)

        verify { momentsApiModule.fetchEngineResponse(testEngineId, any()) }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsError() {
        val exception = IllegalArgumentException("visitorId cannot be empty")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { 
            completion.onComplete(match { 
                it is IllegalArgumentException &&
                (it as IllegalArgumentException).message?.contains("visitorId") == true
            }) 
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsNetworkError() {
        val exception = NetworkException.UnexpectedException(Exception("Network error"))
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is NetworkException }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsHttpError() {
        val exception = NetworkException.Non200Exception(400)
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { 
            completion.onComplete(match { 
                it is NetworkException.Non200Exception && 
                (it as NetworkException.Non200Exception).statusCode == 400
            }) 
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsSingle() {
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(EngineResponse()))
        }

        val single = momentsApiWrapper.fetchEngineResponse("engine-id")

        assertNotNull(single)
    }

    @Test
    fun constructor_WithTealium_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<MomentsApiModule>>()
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(MomentsApiModule::class.java) } returns proxy

        MomentsApiWrapper(tealium)

        verify {
            tealium.createModuleProxy(MomentsApiModule::class.java)
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsInvalidEngineIDException() {
        val exception = IllegalArgumentException("engineId cannot be empty")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { 
            completion.onComplete(match { 
                it is IllegalArgumentException &&
                (it as IllegalArgumentException).message?.contains("engineId") == true
            }) 
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsHttpForbiddenException() {
        val exception = NetworkException.Non200Exception(403)
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { 
            completion.onComplete(match { 
                it is NetworkException.Non200Exception && 
                (it as NetworkException.Non200Exception).statusCode == 403
            }) 
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsHttpNotFoundException() {
        val exception = NetworkException.Non200Exception(404)
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { 
            completion.onComplete(match { 
                it is NetworkException.Non200Exception && 
                (it as NetworkException.Non200Exception).statusCode == 404
            }) 
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsHttpUnsuccessfulException() {
        val exception = NetworkException.Non200Exception(500)
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { 
            completion.onComplete(match { 
                it is NetworkException.Non200Exception && 
                (it as NetworkException.Non200Exception).statusCode == 500
            }) 
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsJsonParsingException() {
        val exception = Exception("Failed to parse JSON")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it.message == "Failed to parse JSON" }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsConfigurationException() {
        val exception = MomentsApiConfigurationException("Invalid configuration")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is MomentsApiConfigurationException }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WithFullEngineResponse() {
        val engineResponse = EngineResponse(
            flags = mapOf("flag1" to true, "flag2" to false),
            metrics = mapOf("metric1" to 1.5, "metric2" to 2.0),
            properties = mapOf("prop1" to "value1", "prop2" to "value2")
        )
        val completion = mockk<Callback<EngineResponse>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(engineResponse))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(engineResponse) }
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WithEmptyEngineResponse() {
        val engineResponse = EngineResponse()
        val completion = mockk<Callback<EngineResponse>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            momentsApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(engineResponse))
        }

        momentsApiWrapper.fetchEngineResponse("engine-id").onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(engineResponse) }
    }

}
