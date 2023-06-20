package com.breezsdk

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import breez_sdk.*
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

class BreezSDKService : Service() {
    private var breezServices: BlockingBreezServices? = null
    private var messenger = Messenger(MessageHandler())

    companion object {
        const val MSG_SERVICE_EVENT = 1
        const val MSG_SERVICE_LOG_STREAM = 2
        const val MSG_SERVICE_RESPONSE = 3
        const val MSG_SERVICE_ERROR = 4
        const val MSG_MNEMONIC_TO_SEED = 5
        const val MSG_PARSE_INPUT = 6
        const val MSG_PARSE_INVOICE = 7
        const val MSG_REGISTER_NODE = 8
        const val MSG_RECOVER_NODE = 9
        const val MSG_START_LOG_STREAM = 10
        const val MSG_DEFAULT_CONFIG = 11
        const val MSG_INIT_SERVICES = 12
        const val MSG_START = 13
        const val MSG_SYNC = 14
        const val MSG_STOP = 15
        const val MSG_SEND_PAYMENT = 16
        const val MSG_SEND_SPONTANEOUS_PAYMENT = 17
        const val MSG_RECEIVE_PAYMENT = 18
        const val MSG_LNURL_AUTH = 19
        const val MSG_PAY_LNURL = 20
        const val MSG_WITHDRAW_LNURL = 21
        const val MSG_NODE_INFO = 22
        const val MSG_LIST_PAYMENTS = 23
        const val MSG_SWEEP = 24
        const val MSG_FETCH_FIAT_RATES = 25
        const val MSG_LIST_FIAT_CURRENCIES = 26
        const val MSG_LIST_LSPS = 27
        const val MSG_CONNECT_LSP = 28
        const val MSG_FETCH_LSP_INFO = 29
        const val MSG_LSP_ID = 30
        const val MSG_CLOSE_LSP_CHANNELS = 31
        const val MSG_RECEIVE_ONCHAIN = 32
        const val MSG_IN_PROGRESS_SWAP = 33
        const val MSG_LIST_REFUNDABLES = 34
        const val MSG_REFUND = 35
        const val MSG_EXECUTE_DEV_COMMAND = 36
        const val MSG_RECOMMENDED_FEES = 37
        const val MSG_BUY_BITCOIN = 38
        const val MSG_FETCH_REVERSE_SWAP_FEES = 39
        const val MSG_IN_PROGRESS_REVERSE_SWAPS = 40
        const val MSG_SEND_ONCHAIN = 41
        const val MSG_START_BACKUP = 42
        const val MSG_BACKUP_STATUS = 43

        const val TAG = "BreezSDKService"
    }

    @Throws(SdkException::class)
    fun getBreezServices(): BlockingBreezServices {
        if (breezServices != null) {
            return breezServices!!
        }

        throw SdkException.Exception("BreezServices not initialized")
    }

    private inner class ServiceEventListener(val replyTo: Messenger) : EventListener {
        override fun onEvent(e: BreezEvent) {
            var bundle = Bundle()

            when (e) {
                is BreezEvent.InvoicePaid -> bundle.putString("data", serialize(readableMapOf("type" to "invoicePaid", "data" to readableMapOf(e.details))))
                is BreezEvent.NewBlock -> bundle.putString("data", serialize(readableMapOf("type" to "newBlock", "data" to e.block)))
                is BreezEvent.PaymentFailed -> bundle.putString("data", serialize(readableMapOf("type" to "paymentFailed", "data" to readableMapOf(e.details))))
                is BreezEvent.PaymentSucceed -> bundle.putString("data", serialize(readableMapOf("type" to "paymentSucceed", "data" to readableMapOf(e.details))))
                is BreezEvent.Synced -> bundle.putString("data", serialize(readableMapOf("type" to "synced")))
                is BreezEvent.BackupStarted -> bundle.putString("data", serialize(readableMapOf("type" to "backupStarted")))
                is BreezEvent.BackupSucceeded -> bundle.putString("data", serialize(readableMapOf("type" to "backupSucceeded")))
                is BreezEvent.BackupFailed -> bundle.putString("data", serialize(readableMapOf("type" to "backupFailed", "data" to readableMapOf(e.details))))
            }

            replyToRequest(replyTo, MSG_SERVICE_EVENT, 0, bundle)
        }
    }

    private inner class ServiceLogStream(val replyTo: Messenger) : LogStream {
        override fun log(logEntry: LogEntry) {
            var bundle = Bundle()
            bundle.putString("data", serialize(readableMapOf(logEntry)))

            replyToRequest(replyTo, MSG_SERVICE_LOG_STREAM, 0, bundle)
        }
    }

    private inner class MessageHandler() : Handler() {
        override fun handleMessage(request: Message) {
            val data = request.data.getString("data")?.let { data -> deserializeMap(data) }
            val requestId = request.arg1
            val response = Bundle()

            try {
                if (data == null) {
                    throw Exception("Missing data")
                }

                when (request.what) {
                    MSG_MNEMONIC_TO_SEED -> response.putString("data", this.mnemonicToSeed(data))
                    MSG_PARSE_INPUT -> response.putString("data", this.parseInput(data))
                    MSG_PARSE_INVOICE -> response.putString("data", this.parseInvoice(data))
                    MSG_REGISTER_NODE -> response.putString("data", this.registerNode(data))
                    MSG_RECOVER_NODE -> response.putString("data", this.recoverNode(data))
                    MSG_START_LOG_STREAM -> response.putString("data", this.startLogStream(request.replyTo))
                    MSG_DEFAULT_CONFIG -> response.putString("data", this.defaultConfig(data))
                    MSG_INIT_SERVICES -> response.putString("data", this.initServices(request.replyTo, data))
                    MSG_START -> response.putString("data", this.start())
                    MSG_SYNC -> response.putString("data", this.sync())
                    MSG_STOP -> response.putString("data", this.stop())
                    MSG_SEND_PAYMENT -> response.putString("data", this.sendPayment(data))
                    MSG_SEND_SPONTANEOUS_PAYMENT -> response.putString("data", this.sendSpontaneousPayment(data))
                    MSG_RECEIVE_PAYMENT -> response.putString("data", this.receivePayment(data))
                    MSG_LNURL_AUTH -> response.putString("data", this.lnurlAuth(data))
                    MSG_PAY_LNURL -> response.putString("data", this.payLnurl(data))
                    MSG_WITHDRAW_LNURL -> response.putString("data", this.withdrawLnurl(data))
                    MSG_NODE_INFO -> response.putString("data", this.nodeInfo())
                    MSG_LIST_PAYMENTS -> response.putString("data", this.listPayments(data))
                    MSG_SWEEP -> response.putString("data", this.sweep(data))
                    MSG_FETCH_FIAT_RATES -> response.putString("data", this.fetchFiatRates())
                    MSG_LIST_FIAT_CURRENCIES -> response.putString("data", this.listFiatCurrencies())
                    MSG_LIST_LSPS -> response.putString("data", this.listLsps())
                    MSG_CONNECT_LSP -> response.putString("data", this.connectLsp(data))
                    MSG_FETCH_LSP_INFO -> response.putString("data", this.fetchLspInfo(data))
                    MSG_LSP_ID -> response.putString("data", this.lspId())
                    MSG_CLOSE_LSP_CHANNELS -> response.putString("data", this.closeLspChannels())
                    MSG_RECEIVE_ONCHAIN -> response.putString("data", this.receiveOnchain())
                    MSG_IN_PROGRESS_SWAP -> response.putString("data", this.inProgressSwap())
                    MSG_LIST_REFUNDABLES -> response.putString("data", this.listRefundables())
                    MSG_REFUND -> response.putString("data", this.refund(data))
                    MSG_EXECUTE_DEV_COMMAND -> response.putString("data", this.executeDevCommand(data))
                    MSG_RECOMMENDED_FEES -> response.putString("data", this.recommendedFees())
                    MSG_BUY_BITCOIN -> response.putString("data", this.buyBitcoin(data))
                    MSG_FETCH_REVERSE_SWAP_FEES -> response.putString("data", this.fetchReverseSwapFees())
                    MSG_IN_PROGRESS_REVERSE_SWAPS -> response.putString("data", this.inProgressReverseSwaps())
                    MSG_SEND_ONCHAIN -> response.putString("data", this.sendOnchain(data))
                    MSG_START_BACKUP -> response.putString("data", this.startBackup())
                    MSG_BACKUP_STATUS -> response.putString("data", this.backupStatus())
                    else -> throw Exception("Error unknown command")
                }

                replyToRequest(request.replyTo, MSG_SERVICE_RESPONSE, requestId, response)
            } catch (e: Exception) {
                response.putString("error", e.message)

                replyToRequest(request.replyTo, MSG_SERVICE_ERROR, requestId, response)
            }
        }

        private fun mnemonicToSeed(data: ReadableMap): String {
            val mnemonic = data.getString("mnemonic")

            if (mnemonic != null) {
                try {
                    val seed = mnemonicToSeed(mnemonic)

                    return serialize(readableArrayOf(seed))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling mnemonicToSeed")
                }
            }

            throw Exception("Missing data")
        }

        private fun parseInput(data: ReadableMap): String {
            val input = data.getString("input")

            if (input != null) {
                try {
                    val inputType = parseInput(input)

                    return serialize(readableMapOf(inputType))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling parseInput")
                }
            }

            throw Exception("Missing data")
        }

        private fun parseInvoice(data: ReadableMap): String {
            val invoice = data.getString("invoice")

            if (invoice != null) {
                try {
                    val lnInvoice = parseInvoice(invoice)

                    return serialize(readableMapOf(lnInvoice))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling parseInvoice")
                }
            }

            throw Exception("Missing data")
        }

        private fun registerNode(data: ReadableMap): String {
            val network = data.getString("network")?.let { network -> asNetwork(network) }
            val seed = data.getArray("seed")?.let { seed -> asUByteList(seed) }
            val registerCredentials = data.getMap("registerCredentials")?.let { registerCredentials -> asGreenlightCredentials(registerCredentials) }
            val inviteCode = data.getString("inviteCode")?.takeUnless { it.isEmpty() }

            if (network != null && seed != null) {
                try {
                    val greenlightCredentials = registerNode(network, seed, registerCredentials, inviteCode)
                    return serialize(readableMapOf(greenlightCredentials))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling registerNode")
                }
            }

            throw Exception("Missing data")
        }

        private fun recoverNode(data: ReadableMap): String {
            val network = data.getString("network")?.let { network -> asNetwork(network) }
            val seed = data.getArray("seed")?.let { seed -> asUByteList(seed) }

            if (network != null && seed != null) {
                try {
                    val greenlightCredentials = recoverNode(network, seed)
                    return serialize(readableMapOf(greenlightCredentials))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling recoverNode")
                }
            }

            throw Exception("Missing data")
        }

        private fun startLogStream(replyTo: Messenger): String {
            try {
                setLogStream(ServiceLogStream(replyTo))
                return serialize(readableMapOf("status" to "ok"))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling setLogStream")
            }
        }

        private fun defaultConfig(data: ReadableMap): String {
            val envType = data.getString("envType")?.let { envType -> asEnvironmentType(envType) }
            val workingDir = data.getString("workingDir")?.takeUnless { it.isEmpty() }

            if (envType != null) {
                try {
                    var config = defaultConfig(envType)

                    if (workingDir != null) {
                        config.workingDir = workingDir
                    }

                    return serialize(readableMapOf(config))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling defaultConfig")
                }
            }

            throw Exception("Missing data")
        }

        private fun initServices(replyTo: Messenger, data: ReadableMap): String {
            if (breezServices != null) {
                throw Exception("BreezServices already initialized")
            }

            val config = data.getMap("config")?.let { config -> asConfig(config) }
            val creds = data.getMap("creds")?.let { creds -> asGreenlightCredentials(creds) }
            val seed = data.getArray("seed")?.let { seed -> asUByteList(seed) }

            if (config != null && creds != null && seed != null) {
                try {
                    breezServices = initServices(config, seed, creds, ServiceEventListener(replyTo))
                    return serialize(readableMapOf("status" to "ok"))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling initServices")
                }
            }

            throw Exception("Missing data")
        }

        private fun start(): String {
            try {
                getBreezServices().start()
                return serialize(readableMapOf("status" to "ok"))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling start")
            }
        }

        private fun sync(): String {
            try {
                getBreezServices().sync()
                return serialize(readableMapOf("status" to "ok"))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling sync")
            }
        }

        private fun stop(): String {
            try {
                getBreezServices().stop()
                return serialize(readableMapOf("status" to "ok"))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling stop")
            }
        }

        private fun sendPayment(data: ReadableMap): String {
            val bolt11 = data.getString("bolt11")
            val amountSats = data.getDouble("amountSats").takeUnless { it == 0.0 }

            if (bolt11 != null) {
                try {
                    val payment = getBreezServices().sendPayment(bolt11, amountSats?.toULong())
                    return serialize(readableMapOf(payment))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling sendPayment")
                }
            }

            throw Exception("Missing data")
        }

        private fun sendSpontaneousPayment(data: ReadableMap): String {
            val nodeId = data.getString("nodeId")
            val amountSats = data.getDouble("amountSats")

            if (nodeId != null) {
                try {
                    val payment = getBreezServices().sendSpontaneousPayment(nodeId, amountSats.toULong())
                    return serialize(readableMapOf(payment))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling sendSpontaneousPayment")
                }
            }

            throw Exception("Missing data")
        }

        private fun receivePayment(data: ReadableMap): String {
            val amountSats = data.getDouble("amountSats")
            val description = data.getString("description")

            if (description != null) {
                try {
                    val payment = getBreezServices().receivePayment(amountSats.toULong(), description)
                    return serialize(readableMapOf(payment))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling receivePayment")
                }
            }

            throw Exception("Missing data")
        }

        private fun lnurlAuth(data: ReadableMap): String {
            val reqData = data.getMap("reqData")?.let { reqData -> asLnUrlAuthRequestData(reqData) }

            if (reqData != null) {
                try {
                    val lnUrlCallbackStatus = getBreezServices().lnurlAuth(reqData)
                    return serialize(readableMapOf(lnUrlCallbackStatus))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling lnurlAuth")
                }
            }

            throw Exception("Missing data")
        }

        private fun payLnurl(data: ReadableMap): String {
            val reqData = data.getMap("reqData")?.let { reqData -> asLnUrlPayRequestData(reqData) }
            val amountSats = data.getDouble("amountSats")
            val comment = data.getString("comment")?.takeUnless { it.isEmpty() }

            if (reqData != null) {
                try {
                    val lnUrlPayResult = getBreezServices().payLnurl(reqData, amountSats.toULong(), comment)
                    return serialize(readableMapOf(lnUrlPayResult))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling payLnurl")
                }
            }

            throw Exception("Missing data")
        }

        private fun withdrawLnurl(data: ReadableMap): String {
            val reqData = data.getMap("reqData")?.let { reqData -> asLnUrlWithdrawRequestData(reqData) }
            val amountSats = data.getDouble("amountSats")
            val description = data.getString("description")?.takeUnless { it.isEmpty() }

            if (reqData != null) {
                try {
                    val lnUrlPayResult = getBreezServices().withdrawLnurl(reqData, amountSats.toULong(), description)
                    return serialize(readableMapOf(lnUrlPayResult))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling withdrawLnurl")
                }
            }

            throw Exception("Missing data")
        }

        private fun nodeInfo(): String {
            try {
                val nodeState = getBreezServices().nodeInfo()

                if (nodeState != null) {
                    return serialize(readableMapOf(nodeState))
                }
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling nodeInfo")
            }

            throw Exception("No available node info")
        }

        private fun listPayments(data: ReadableMap): String {
            val filter = data.getString("filter")?.let { filter -> asPaymentTypeFilter(filter) }
            val fromTimestamp = data.getDouble("fromTimestamp").takeUnless { it == 0.0 }
            val toTimestamp = data.getDouble("toTimestamp").takeUnless { it == 0.0 }

            if (filter != null) {
                try {
                    val payments = getBreezServices().listPayments(filter, fromTimestamp?.toLong(), toTimestamp?.toLong())
                    return serialize(readableArrayOf(payments))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling listPayments")
                }
            }

            throw Exception("Missing data")
        }

        private fun sweep(data: ReadableMap): String {
            val toAddress = data.getString("toAddress")
            val feeRateSatsPerVbyte = data.getDouble("feeRateSatsPerVbyte")

            if (toAddress != null) {
                try {
                    getBreezServices().sweep(toAddress, feeRateSatsPerVbyte.toULong())
                    return serialize(readableMapOf("status" to "ok"))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling sweep")
                }
            }

            throw Exception("Missing data")
        }

        private fun fetchFiatRates(): String {
            try {
                val fiatRates = getBreezServices().fetchFiatRates()
                return serialize(readableArrayOf(fiatRates))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling fetchFiatRates")
            }
        }

        private fun listFiatCurrencies(): String {
            try {
                val fiatCurrencies = getBreezServices().listFiatCurrencies()
                return serialize(readableArrayOf(fiatCurrencies))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling listFiatCurrencies")
            }
        }

        private fun listLsps(): String {
            try {
                val lsps = getBreezServices().listLsps()
                return serialize(readableArrayOf(lsps))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling listLsps")
            }
        }

        private fun connectLsp(data: ReadableMap): String {
            val lspId = data.getString("lspId")

            if (lspId != null) {
                try {
                    getBreezServices().connectLsp(lspId)
                    return serialize(readableMapOf("status" to "ok"))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling connectLsp")
                }
            }

            throw Exception("Missing data")
        }

        private fun fetchLspInfo(data: ReadableMap): String {
            val lspId = data.getString("lspId")

            if (lspId != null) {
                try {
                    val lspInfo = getBreezServices().fetchLspInfo(lspId)

                    if (lspInfo != null) {
                        return serialize(readableMapOf(lspInfo))
                    }
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling fetchLspInfo")
                }

                throw Exception("No available lsp info")
            }

            throw Exception("Missing data")
        }

        private fun lspId(): String {
            try {
                val lspId = getBreezServices().lspId()

                if (lspId != null) {
                    return lspId
                }
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling lspId")
            }

            throw Exception("No available lsp id")
        }

        private fun closeLspChannels(): String {
            try {
                getBreezServices().closeLspChannels()
                return serialize(readableMapOf("status" to "ok"))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling closeLspChannels")
            }
        }

        private fun receiveOnchain(): String {
            try {
                val swapInfo = getBreezServices().receiveOnchain()
                return serialize(readableMapOf(swapInfo))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling receiveOnchain")
            }
        }

        private fun inProgressSwap(): String {
            try {
                val swapInfo = getBreezServices().inProgressSwap()

                if (swapInfo != null) {
                    return serialize(readableMapOf(swapInfo))
                }
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling inProgressSwap")
            }

            throw Exception("No available in progress swap")
        }

        private fun listRefundables(): String {
            try {
                val swapInfos = getBreezServices().listRefundables()
                return serialize(readableArrayOf(swapInfos))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling listRefundables")
            }
        }

        private fun refund(data: ReadableMap): String {
            val swapAddress = data.getString("swapAddress")
            val toAddress = data.getString("toAddress")
            val satPerVbyte = data.getDouble("satPerVbyte")

            if (swapAddress != null && toAddress != null) {
                try {
                    return getBreezServices().refund(swapAddress, toAddress, satPerVbyte.toUInt())
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling refund")
                }
            }

            throw Exception("Missing data")
        }

        private fun executeDevCommand(data: ReadableMap): String {
            val command = data.getString("command")

            if (command != null) {
                try {
                    return getBreezServices().executeDevCommand(command)
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling executeDevCommand")
                }
            }

            throw Exception("Missing data")
        }

        private fun recommendedFees(): String {
            try {
                val fees = getBreezServices().recommendedFees()
                return serialize(readableMapOf(fees))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling recommendedFees")
            }
        }

        private fun buyBitcoin(data: ReadableMap): String {
            val provider = data.getString("provider")?.let { provider -> asBuyBitcoinProvider(provider) }

            if (provider != null) {
                try {
                    return getBreezServices().buyBitcoin(provider)
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling buyBitcoin")
                }
            }

            throw Exception("Missing data")
        }

        private fun fetchReverseSwapFees(): String {
            try {
                val reverseSwapFees = getBreezServices().fetchReverseSwapFees()
                return serialize(readableMapOf(reverseSwapFees))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling fetchReverseSwapFees")
            }
        }

        private fun inProgressReverseSwaps(): String {
            try {
                val inProgressReverseSwaps = getBreezServices().inProgressReverseSwaps()
                return serialize(readableArrayOf(inProgressReverseSwaps))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling inProgressReverseSwaps")
            }
        }

        private fun sendOnchain(data: ReadableMap): String {
            val amountSat = data.getDouble("amountSat")
            val onchainRecipientAddress = data.getString("onchainRecipientAddress")
            val pairHash = data.getString("pairHash")
            val satPerVbyte = data.getDouble("satPerVbyte")

            if (onchainRecipientAddress != null && pairHash != null) {
                try {
                    val reverseSwapInfo =  getBreezServices().sendOnchain(amountSat.toULong(), onchainRecipientAddress, pairHash, satPerVbyte.toULong())
                    return serialize(readableMapOf(reverseSwapInfo))
                } catch (e: SdkException) {
                    e.printStackTrace()
                    throw Exception(e.message ?: "Error calling sendOnchain")
                }
            }

            throw Exception("Missing data")
        }

        private fun startBackup(): String {
            try {
                getBreezServices().startBackup()
                return serialize(readableMapOf("status" to "ok"))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling startBackup")
            }
        }

        private fun backupStatus(): String {
            try {
                val status = getBreezServices().backupStatus()
                return serialize(readableMapOf(status))
            } catch (e: SdkException) {
                e.printStackTrace()
                throw Exception(e.message ?: "Error calling backupStatus")
            }
        }
    }

    private fun replyToRequest(messenger: Messenger, what: Int, arg1: Int, data: Bundle) {
        val response = Message.obtain(null, what, arg1, 0)
        response.data = data

        messenger.send(response)
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "onBind")
        return messenger.binder
    }

    override fun onRebind(intent: Intent) {
        Log.i(TAG, "onRebind")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "onUnbind")
        return true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }
}