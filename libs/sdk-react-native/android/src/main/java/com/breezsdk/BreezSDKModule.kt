package com.breezsdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import breez_sdk.*
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import org.json.JSONException
import java.io.File
import java.util.*
import kotlin.collections.HashMap


class BreezSDKModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private lateinit var emitter: RCTDeviceEventEmitter
    private val messenger: Messenger = Messenger(MessageHandler())
    private val requests = HashMap<Int, Promise>()
    private val messageQueue = ArrayList<Message>()
    private var serviceMessenger: Messenger? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i(BreezSDKService.TAG, "onServiceConnected")
            onServiceBound(service)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i(BreezSDKService.TAG, "onServiceDisconnected")
            onServiceUnbound()
        }
    }

    companion object {
        const val MAP_CHAR: Char = '{'
        const val ARRAY_CHAR: Char = '['

        var TAG = "RNBreezSDK"
    }

    override fun initialize() {
        Log.i(BreezSDKService.TAG, "initialize")
        super.initialize()

        emitter = reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)

        Intent(reactApplicationContext, BreezSDKService::class.java).apply {
            reactApplicationContext.startService(this)
            reactApplicationContext.bindService(this, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun getName(): String {
        return TAG
    }

    fun onServiceBound(service: IBinder) {
        Log.i(BreezSDKService.TAG, "onServiceBound")
        if (!serviceBound) {
            serviceMessenger = Messenger(service)
            serviceBound = true

            for (message in messageQueue) {
                sendMessage(message)
                messageQueue.remove(message)
            }
        }
    }

    fun onServiceUnbound() {
        Log.i(BreezSDKService.TAG, "onServiceUnbound")
        serviceMessenger = null
        serviceBound = false
    }

    inner class MessageHandler() : Handler(Looper.getMainLooper()) {
        override fun handleMessage(response: Message) {
            val responseData = response.data

            when (response.what) {
                BreezSDKService.MSG_SERVICE_EVENT -> {
                    val data = responseData.getString("data")

                    emitter.emit("breezSdkEvent", deserializeMap(data))
                }
                BreezSDKService.MSG_SERVICE_LOG_STREAM -> {
                    val data = responseData.getString("data")

                    emitter.emit("breezSdkLog", deserializeMap(data))
                }
                BreezSDKService.MSG_SERVICE_RESPONSE -> {
                    val promise = requests.remove(response.arg1)
                    
                    if (promise != null) {
                        val data = responseData.getString("data")

                        if (data != null && data.length > 1) {
                            if (data[0] == MAP_CHAR) {
                                return promise.resolve(deserializeMap(data))
                            } else if (data[0] == ARRAY_CHAR) {
                                return promise.resolve(deserializeArray(data))
                            }
                        }

                        return promise.resolve(data)
                    }
                }
                BreezSDKService.MSG_SERVICE_ERROR -> {
                    val promise = requests.remove(response.arg1)
                    val error = responseData.getString("error")

                    if (promise != null && error != null) {
                        promise.reject(TAG, error)
                    }
                }
                else -> super.handleMessage(response)
            }
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
    }

    @ReactMethod
    fun removeListeners(count: Int) {
    }

    private fun sendToService(command: Int, data: ReadableMap, promise: Promise) {
        val requestId: Int = Random().nextInt()
        requests[requestId] = promise

        var bundle = Bundle()
        bundle.putString("data", serialize(data))

        val message = Message.obtain(null, command, requestId, 0)
        message.replyTo = messenger
        message.data = bundle

        if (serviceBound && serviceMessenger != null) {
            sendMessage(message)
        } else {
            messageQueue.add(message)
        }
    }
    
    private fun sendMessage(message: Message) {
        try {
            serviceMessenger?.send(message)
        } catch (e: RemoteException) {
            e.printStackTrace()
            val promise = requests.remove(message.arg1)
            promise?.reject(TAG, "Error calling service")
        }
    }

    @ReactMethod
    fun mnemonicToSeed(mnemonic: String, promise: Promise) {
        this.sendToService(BreezSDKService.MSG_MNEMONIC_TO_SEED, readableMapOf("mnemonic" to mnemonic), promise)
    }

    @ReactMethod
    fun parseInput(input: String, promise: Promise) {
        this.sendToService(BreezSDKService.MSG_PARSE_INPUT, readableMapOf("input" to input), promise)
    }

    @ReactMethod
    fun parseInvoice(invoice: String, promise: Promise) {
        this.sendToService(BreezSDKService.MSG_PARSE_INVOICE, readableMapOf("invoice" to invoice), promise)
    }

    @ReactMethod
    fun registerNode(network: String, seed: ReadableArray, registerCredentials: ReadableMap, inviteCode: String, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_REGISTER_NODE,
                readableMapOf(
                        "network" to network,
                        "seed" to seed,
                        "registerCredentials" to registerCredentials,
                        "inviteCode" to inviteCode),
                promise)
    }

    @ReactMethod
    fun recoverNode(network: String, seed: ReadableArray, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_RECOVER_NODE,
                readableMapOf("network" to network, "seed" to seed),
                promise)
    }

    @ReactMethod
    fun startLogStream(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_START_LOG_STREAM, readableMapOf(), promise)
    }

    @ReactMethod
    fun defaultConfig(envType: String, promise: Promise) {
        var workingDir = File(reactApplicationContext.filesDir.toString() + "/breezSdk")

        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }

        this.sendToService(
                BreezSDKService.MSG_DEFAULT_CONFIG,
                readableMapOf("envType" to envType, "workingDir" to workingDir.absolutePath),
                promise)
    }

    @ReactMethod
    fun initServices(config: ReadableMap, deviceKey: ReadableArray, deviceCert: ReadableArray, seed: ReadableArray, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_INIT_SERVICES,
                readableMapOf(
                        "config" to config,
                        "creds" to readableMapOf(
                                "deviceKey" to deviceKey,
                                "deviceCert" to deviceCert),
                        "seed" to seed),
                promise)
    }

    @ReactMethod
    fun start(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_START, readableMapOf(), promise)
    }

    @ReactMethod
    fun sync(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_SYNC, readableMapOf(), promise)
    }

    @ReactMethod
    fun stop(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_STOP, readableMapOf(), promise)
    }

    @ReactMethod
    fun sendPayment(bolt11: String, amountSats: Double, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_SEND_PAYMENT,
                readableMapOf("bolt11" to bolt11, "amountSats" to amountSats),
                promise)
    }

    @ReactMethod
    fun sendSpontaneousPayment(nodeId: String, amountSats: Double, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_SEND_SPONTANEOUS_PAYMENT,
                readableMapOf("nodeId" to nodeId, "amountSats" to amountSats),
                promise)
    }

    @ReactMethod
    fun receivePayment(amountSats: Double, description: String, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_RECEIVE_PAYMENT,
                readableMapOf("amountSats" to amountSats, "description" to description),
                promise)
    }

    @ReactMethod
    fun lnurlAuth(reqData: ReadableMap, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_LNURL_AUTH,
                readableMapOf("reqData" to reqData),
                promise)
    }

    @ReactMethod
    fun payLnurl(reqData: ReadableMap, amountSats: Double, comment: String, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_PAY_LNURL,
                readableMapOf("reqData" to reqData, "amountSats" to amountSats, "comment" to comment),
                promise)
    }

    @ReactMethod
    fun withdrawLnurl(reqData: ReadableMap, amountSats: Double, description: String, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_WITHDRAW_LNURL,
                readableMapOf("reqData" to reqData, "amountSats" to amountSats, "description" to description),
                promise)
    }

    @ReactMethod
    fun nodeInfo(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_NODE_INFO, readableMapOf(), promise)
    }

    @ReactMethod
    fun listPayments(filter: String, fromTimestamp: Double, toTimestamp: Double, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_LIST_PAYMENTS,
                readableMapOf("filter" to filter, "fromTimestamp" to fromTimestamp, "toTimestamp" to toTimestamp),
                promise)
    }

    @ReactMethod
    fun sweep(toAddress: String, feeRateSatsPerVbyte: Double, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_SWEEP,
                readableMapOf("toAddress" to toAddress, "feeRateSatsPerVbyte" to feeRateSatsPerVbyte),
                promise)
    }

    @ReactMethod
    fun fetchFiatRates(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_FETCH_FIAT_RATES, readableMapOf(), promise)
    }

    @ReactMethod
    fun listFiatCurrencies(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_LIST_FIAT_CURRENCIES, readableMapOf(), promise)
    }

    @ReactMethod
    fun listLsps(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_LIST_LSPS, readableMapOf(), promise)
    }

    @ReactMethod
    fun connectLsp(lspId: String, promise: Promise) {
        this.sendToService(BreezSDKService.MSG_CONNECT_LSP, readableMapOf("lspId" to lspId), promise)
    }

    @ReactMethod
    fun fetchLspInfo(lspId: String, promise: Promise) {
        this.sendToService(BreezSDKService.MSG_FETCH_LSP_INFO, readableMapOf("lspId" to lspId), promise)
    }

    @ReactMethod
    fun lspId(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_LSP_ID, readableMapOf(), promise)
    }

    @ReactMethod
    fun closeLspChannels(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_CLOSE_LSP_CHANNELS, readableMapOf(), promise)
    }

    @ReactMethod
    fun receiveOnchain(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_RECEIVE_ONCHAIN, readableMapOf(), promise)
    }

    @ReactMethod
    fun inProgressSwap(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_IN_PROGRESS_SWAP, readableMapOf(), promise)
    }

    @ReactMethod
    fun listRefundables(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_LIST_REFUNDABLES, readableMapOf(), promise)
    }

    @ReactMethod
    fun refund(swapAddress: String, toAddress: String, satPerVbyte: Double, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_REFUND,
                readableMapOf(
                        "swapAddress" to swapAddress,
                        "toAddress" to toAddress,
                        "satPerVbyte" to satPerVbyte),
                promise)
    }

    @ReactMethod
    fun executeDevCommand(command: String, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_EXECUTE_DEV_COMMAND,
                readableMapOf("command" to command),
                promise)
    }

    @ReactMethod
    fun recommendedFees(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_RECOMMENDED_FEES, readableMapOf(), promise)
    }

    @ReactMethod
    fun buyBitcoin(provider: String, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_BUY_BITCOIN,
                readableMapOf("provider" to provider),
                promise)
    }

    @ReactMethod
    fun fetchReverseSwapFees(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_FETCH_REVERSE_SWAP_FEES, readableMapOf(), promise)
    }

    @ReactMethod
    fun inProgressReverseSwaps(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_IN_PROGRESS_REVERSE_SWAPS, readableMapOf(), promise)
    }

    @ReactMethod
    fun sendOnchain(amountSat: Double, onchainRecipientAddress: String, pairHash: String, satPerVbyte: Double, promise: Promise) {
        this.sendToService(
                BreezSDKService.MSG_SEND_ONCHAIN,
                readableMapOf(
                        "amountSat" to amountSat,
                        "onchainRecipientAddress" to onchainRecipientAddress,
                        "pairHash" to pairHash,
                        "satPerVbyte" to satPerVbyte),
                promise)
    }

    @ReactMethod
    fun startBackup(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_START_BACKUP, readableMapOf(), promise)
    }

    @ReactMethod
    fun backupStatus(promise: Promise) {
        this.sendToService(BreezSDKService.MSG_BACKUP_STATUS, readableMapOf(), promise)
    }
}
