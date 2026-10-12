package com.tealium.prism.contextapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.modules.VisitorIdProvider
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.pubsub.onFailure
import com.tealium.prism.core.api.pubsub.onSuccess
import com.tealium.prism.core.internal.misc.SynchronousScheduler
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import com.tealium.prism.contextapi.internal.ContextApiModule
import com.tealium.prism.contextapi.internal.ContextApiService
import com.tealium.prism.contextapi.internal.ContextApiWrapper
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ContextApiWrapperTests {

    @RelaxedMockK
    private lateinit var contextApiModule: ContextApiModule

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<TealiumResult<ModuleManager>>
    private lateinit var proxy: ModuleProxy<ContextApiModule>

    private lateinit var contextApiWrapper: ContextApiWrapper
    private val scheduler = SynchronousScheduler()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { contextApiModule.id } returns ContextApiModule.Factory().moduleType
        modules = Observables.stateSubject(listOf(contextApiModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(TealiumResult.success(moduleManager))

        proxy = ModuleProxyImpl(ContextApiModule::class.java, onModuleManager, scheduler)
        contextApiWrapper = ContextApiWrapper(proxy)
    }

    @Test
    fun fetchEngineResponse_Reports_ModuleNotEnabled() {
        modules.onNext(emptyList())

        val completion = mockk<Callback<Exception>>(relaxed = true)
        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is ModuleNotEnabledException }) }
    }

    @Test
    fun fetchEngineResponse_Reports_TealiumShutdown_WhenNoModuleManager() {
        onModuleManager.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("Tealium Instance has been shutdown.")))

        val completion = mockk<Callback<Exception>>(relaxed = true)
        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is Tealium.TealiumShutdownException }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WhenModuleEnabled() {
        val engineResponse = EngineResponse(flags = mapOf("test" to true))
        val completion = mockk<Callback<EngineResponse>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(engineResponse))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(engineResponse) }
    }

    @Test
    fun fetchEngineResponse_PassesCorrectEngineID_ToModule() {
        val testEngineId = "test-engine-123"
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(EngineResponse()))
        }

        contextApiWrapper.fetchEngineResponse(testEngineId)

        verify { contextApiModule.fetchEngineResponse(testEngineId, any()) }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsError() {
        val exception = IllegalArgumentException("visitorId cannot be empty")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is NetworkException }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsHttpError() {
        val exception = NetworkException.Non200Exception(400)
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(EngineResponse()))
        }

        val single = contextApiWrapper.fetchEngineResponse("engine-id")

        assertNotNull(single)
    }

    @Test
    fun constructor_WithTealium_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<ContextApiModule>>()
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(ContextApiModule::class.java) } returns proxy

        ContextApiWrapper(tealium)

        verify {
            tealium.createModuleProxy(ContextApiModule::class.java)
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsInvalidEngineIDException() {
        val exception = IllegalArgumentException("engineId cannot be empty")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it.message == "Failed to parse JSON" }) }
    }

    @Test
    fun fetchEngineResponse_ReturnsFailure_WhenModuleReturnsConfigurationException() {
        val exception = ContextApiConfigurationException("Invalid configuration")
        val completion = mockk<Callback<Exception>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.failure(exception))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) { completion.onComplete(match { it is ContextApiConfigurationException }) }
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
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(engineResponse))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(engineResponse) }
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WithEmptyEngineResponse() {
        val engineResponse = EngineResponse()
        val completion = mockk<Callback<EngineResponse>>(relaxed = true)
        
        val callbackCapture = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            contextApiModule.fetchEngineResponse(any(), capture(callbackCapture))
        } answers {
            callbackCapture.captured.onComplete(TealiumResult.success(engineResponse))
        }

        contextApiWrapper.fetchEngineResponse("engine-id").onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(engineResponse) }
    }

    @Test
    fun fetchEngineResponse_Reports_ModuleNotEnabled_When_VisitorId_Module_Disabled() {
        val service = mockk<ContextApiService>(relaxed = true)
        // the manager holds the Context API module but no VisitorIdProvider; it is seeded with a
        // placeholder so that it counts as ready without a settings pass
        val realModules = Observables.stateSubject(listOf(mockk<Module>(relaxed = true)))
        val realModuleManager = ModuleManagerImpl(scheduler, realModules)
        realModules.onNext(listOf(ContextApiModule(realModuleManager, SystemLogger, service)))
        val wrapper = createWrapper(realModuleManager)

        val completion = mockk<Callback<Exception>>(relaxed = true)
        wrapper.fetchEngineResponse("engine-id").onFailure(completion)

        verify(exactly = 1) {
            completion.onComplete(match {
                it is ModuleNotEnabledException && it.message!!.contains(Modules.Types.VISITOR_ID)
            })
        }
        verify(inverse = true) { service.fetchEngineResponse(any(), any(), any()) }
    }

    @Test
    fun fetchEngineResponse_Reports_ModuleShutdown_When_Module_Shut_Down_While_Pending() {
        val service = mockk<ContextApiService>(relaxed = true)
        // the VisitorIdProvider lookup is withheld, as if a settings pass was still running
        val loadingModuleManager = mockk<ModuleManager>()
        every {
            loadingModuleManager.getModuleOfType(VisitorIdProvider::class.java, any())
        } returns Disposables.subscription()
        val module = ContextApiModule(loadingModuleManager, SystemLogger, service)
        val realModuleManager = ModuleManagerImpl(scheduler, Observables.stateSubject(listOf(module)))
        val wrapper = createWrapper(realModuleManager)

        val completion = mockk<Callback<Exception>>(relaxed = true)
        wrapper.fetchEngineResponse("engine-id").onFailure(completion)
        verify(inverse = true) { completion.onComplete(any()) }

        module.onShutdown()

        verify(exactly = 1) { completion.onComplete(match { it is ModuleShutdownException }) }
        verify(inverse = true) { service.fetchEngineResponse(any(), any(), any()) }
    }

    @Test
    fun fetchEngineResponse_Reports_ModuleShutdown_When_Shut_Down_While_Network_Call_In_Flight() {
        val service = mockk<ContextApiService>(relaxed = true)
        // the network call is withheld, as if the response had not arrived yet
        every { service.fetchEngineResponse(any(), any(), any()) } returns Disposables.subscription()
        val visitorIdProvider = mockk<VisitorIdProvider>(relaxed = true)
        every { visitorIdProvider.visitorId } returns Observables.stateSubject("test-visitor-id")
        // the manager is seeded with a placeholder so that it counts as ready without a settings pass
        val realModules = Observables.stateSubject(listOf(mockk<Module>(relaxed = true)))
        val realModuleManager = ModuleManagerImpl(scheduler, realModules)
        val module = ContextApiModule(realModuleManager, SystemLogger, service)
        realModules.onNext(listOf(module, visitorIdProvider))
        val wrapper = createWrapper(realModuleManager)

        val completion = mockk<Callback<Exception>>(relaxed = true)
        wrapper.fetchEngineResponse("engine-id").onFailure(completion)
        verify(exactly = 1) { service.fetchEngineResponse("engine-id", "test-visitor-id", any()) }
        verify(inverse = true) { completion.onComplete(any()) }

        module.onShutdown()

        verify(exactly = 1) { completion.onComplete(match { it is ModuleShutdownException }) }
    }

    private fun createWrapper(moduleManager: ModuleManager): ContextApiWrapper =
        ContextApiWrapper(
            ModuleProxyImpl(
                ContextApiModule::class.java,
                Observables.stateSubject(TealiumResult.success(moduleManager)),
                scheduler
            )
        )
}
