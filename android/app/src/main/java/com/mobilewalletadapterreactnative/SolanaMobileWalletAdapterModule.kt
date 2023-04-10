package com.examplewallet

import android.app.Application
import android.app.Activity
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import com.solana.mobilewalletadapter.fakewallet.usecase.ClientTrustUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.charset.StandardCharsets

class SolanaMobileWalletAdapterModule(val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {//, CoroutineScope {

    // sets the name of the module in React, accessible at ReactNative.NativeModules.WalletLib
    override fun getName() = "WalletLib"
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest
        object LowPowerNoConnection : MobileWalletAdapterServiceRequest

        sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) : MobileWalletAdapterServiceRequest
        data class AuthorizeDapp(override val request: AuthorizeRequest, val sourceVerificationState: ClientTrustUseCase.VerificationState) : MobileWalletAdapterRemoteRequest(request)
        data class ReauthorizeDapp(override val request: ReauthorizeRequest) : MobileWalletAdapterRemoteRequest(request)

        sealed class SignPayloads(override val request: SignPayloadsRequest) : MobileWalletAdapterRemoteRequest(request)
        data class SignTransactions(override val request: SignTransactionsRequest) : SignPayloads(request)
        data class SignMessages(override val request: SignMessagesRequest) : SignPayloads(request)
    }

    private var request: MobileWalletAdapterServiceRequest? = null
        set(value) {
            field = value
            value?.let { request -> sendWalletRequestToReact(request) }
        }

    private var scenario: Scenario? = null
    private var clientTrustUseCase: ClientTrustUseCase? = null

    @ReactMethod
    fun log(message: String) {
        Log.d(TAG, "message from react: $message")
    }

    @ReactMethod
    fun createScenario(
        walletName: String, // our wallet's name (Backpack)
        uriStr: String,
        callback: Callback
    ) {
        // Not production code! Testing purposes only START
        scenario?.close()

        scenario = null
        // Not production code! Testing purposes only END

        val uri = Uri.parse(uriStr)

        val associationUri = AssociationUri.parse(uri)
        if (associationUri == null) {
            Log.e(TAG, "Unsupported association URI: $uri")
            callback.invoke("ERROR", "Unsupported association URI")
            return
        } else if (associationUri !is LocalAssociationUri) {
            callback.invoke("ERROR", "Current implementation of fakewallet does not support remote clients")
            return
        }
        
        clientTrustUseCase = ClientTrustUseCase(
            backgroundScope,
            currentActivity?.getApplication()?.packageManager!!,
            currentActivity?.getCallingPackage(),
            associationUri
        )

        // created a scenario, told it to start (kicks off some threads in the background)
        // we've kept a reference to it in the global state of this module (scenario)
        // this won't be garbage collected and will just run, sit & wait for an incoming connection
        scenario = associationUri.createScenario(
            reactContext,
            MobileWalletAdapterConfig(
                true,
                10,
                10,
                arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION, 0),
                3000L
            ),
            AuthIssuerConfig(walletName),
            MobileWalletAdapterScenarioCallbacks()
        ).also { it.start() }

        Log.d(TAG, "scenario created: $walletName")
        callback.invoke("SUCCESS")
    }

    @ReactMethod
    fun authorizeDapp(publicKey: ReadableArray) {
        Log.d(TAG, "authorizeDapp: authorized public key = $publicKey")
        (request as? MobileWalletAdapterServiceRequest.AuthorizeDapp)?.request?.let { authRequest ->
            authRequest.completeWithAuthorize(
                Arguments.toList(publicKey)?.let { shouldBeBytes ->
                    ByteArray(shouldBeBytes.size) {
                        (shouldBeBytes.get(it) as? Number)?.toByte() ?: 0
                    }
                }!!,
                "Backpack",
                null,
                null
            )
        }
    }

    @ReactMethod
    fun completeSignPayloadsRequest(signedPayloads: ReadableArray) {
        Log.d(TAG, "completeSignPaylaodsRequest: signedPayloads = ")
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
            // temp
            val signedPayloads = signRequest.payloads
            signRequest.completeWithSignedPayloads(signedPayloads)
        }
    }

    @ReactMethod
    fun completeWithInvalidPayloads(validArray: ReadableArray) {
        Log.d(TAG, "completeSignPaylaodsRequest: signedPayloads = ")
        val validBoolArray = BooleanArray(validArray.size()) { index -> validArray.getBoolean(index) }
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithInvalidPayloads(validBoolArray)
        }
    }

    private fun sendWalletRequestToReact(request: MobileWalletAdapterServiceRequest) {

        // pretty dirty implementation :thug lyfe:
        val eventInfo = when(request) {
            is MobileWalletAdapterServiceRequest.None -> null
            is MobileWalletAdapterServiceRequest.SessionTerminated -> Arguments.createMap().apply {
                putString("type", "SESSION_TERMINATED")
            }
            is MobileWalletAdapterServiceRequest.LowPowerNoConnection -> Arguments.createMap().apply {
                putString("type", "LOW_POWER_NO_CONNECTION")
            }
            is MobileWalletAdapterServiceRequest.AuthorizeDapp -> Arguments.createMap().apply {
                putString("type", "AUTHORIZE_DAPP")
            }
            is MobileWalletAdapterServiceRequest.ReauthorizeDapp -> Arguments.createMap().apply {
                putString("type", "REAUTHORIZE_DAPP")
            }
            is MobileWalletAdapterServiceRequest.SignMessages -> Arguments.createMap().apply {
                putString("type", "SIGN_MESSAGES")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterServiceRequest.SignTransactions -> Arguments.createMap().apply {
                putString("type", "SIGN_TRANSACTIONS")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
        }

        eventInfo?.let { params ->
            sendEvent(reactContext, "MobileWalletAdapterServiceEvent", params)
        }
    }

    private fun sendEvent(reactContext: ReactContext, eventName: String, params: WritableMap? = null) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private inner class MobileWalletAdapterScenarioCallbacks : LocalScenario.Callbacks {
        override fun onScenarioReady() = Unit
        override fun onScenarioServingClients() = Unit
        override fun onScenarioServingComplete() {
            scenario?.close()
            this@SolanaMobileWalletAdapterModule.request = null
        }

        override fun onScenarioComplete() = Unit
        override fun onScenarioError() = Unit
        override fun onScenarioTeardownComplete() {
            this@SolanaMobileWalletAdapterModule.request =
                MobileWalletAdapterServiceRequest.SessionTerminated
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            val clientTrustUseCase = clientTrustUseCase!! // should never be null if we get here
            val verify = clientTrustUseCase.verifyAuthorizationSourceAsync(request.identityUri)

            backgroundScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    verify.await()
                 } ?: clientTrustUseCase.verificationTimedOut
                if (verificationState is ClientTrustUseCase.VerificationSucceeded) {
                    print("debug: success")

                    this@SolanaMobileWalletAdapterModule.request =
                        MobileWalletAdapterServiceRequest.AuthorizeDapp(request, verificationState)
                } else {
                    print("debug: fail")

                    request.completeWithDecline()
                }
            }

        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            Log.i(TAG, "Reauthorization request: atuo completeing, DO NOT DO THIS IN PRODUCTION")
            val clientTrustUseCase = clientTrustUseCase!! // should never be null if we get here
            val reverify = clientTrustUseCase.verifyReauthorizationSourceAsync(
                String(request.authorizationScope, StandardCharsets.UTF_8),
                request.identityUri
            )
            backgroundScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    reverify.await()
                } ?: clientTrustUseCase.verificationTimedOut
                when (verificationState) {
                    is ClientTrustUseCase.VerificationInProgress -> throw IllegalStateException()
                    is ClientTrustUseCase.VerificationSucceeded -> {
                        Log.i(TAG, "Reauthorization source verification succeeded")
                        request.completeWithReauthorize()
                    }
                    is ClientTrustUseCase.NotVerifiable -> {
                        Log.i(TAG, "Reauthorization source not verifiable; approving")
                        request.completeWithReauthorize()
                    }
                    is ClientTrustUseCase.VerificationFailed -> {
                        Log.w(TAG, "Reauthorization source verification failed")
                        request.completeWithDecline()
                    }
                    null -> {
                        Log.w(TAG, "Timed out waiting for reauthorization source verification")
                        request.completeWithDecline()
                    }
                }
            }
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                this@SolanaMobileWalletAdapterModule.request =
                    MobileWalletAdapterServiceRequest.SignTransactions(request)
            } else {
                request.completeWithDecline()
            }         
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                this@SolanaMobileWalletAdapterModule.request =
                    MobileWalletAdapterServiceRequest.SignMessages(request)
            } else {
                request.completeWithDecline()
            }    
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            // TODO
        }

        private fun verifyPrivilegedMethodSource(request: VerifiableIdentityRequest): Boolean {
            return clientTrustUseCase!!.verifyPrivilegedMethodSource(
                String(request.authorizationScope, StandardCharsets.UTF_8),
                request.identityUri
            )
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            Log.d(TAG, "'${event.identityName}' deauthorized")
            event.complete()
        }

        override fun onLowPowerAndNoConnection() {
            Log.w(TAG, "Device is in power save mode and no connection was made. The connection was likely suppressed by power save mode.")
            // TODO: should notify react so it can draw UI informing the user
        }
    }

    companion object {
        private val TAG = SolanaMobileWalletAdapterModule::class.simpleName
        private const val SOURCE_VERIFICATION_TIMEOUT_MS = 3000L
        private const val LOW_POWER_NO_CONNECTION_TIMEOUT_MS = 3000L
    }
}