package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface BillingUiEvent {
    data class PurchaseVerifiedNeeded(val productId: String, val purchaseToken: String) : BillingUiEvent
    data class Message(val text: String) : BillingUiEvent
}

class PlayBillingManager(context: Context) : PurchasesUpdatedListener {

    private val _isBillingReady = MutableStateFlow(false)
    val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _lastEvent = MutableStateFlow<BillingUiEvent?>(null)
    val lastEvent: StateFlow<BillingUiEvent?> = _lastEvent.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun startConnection(productIds: List<String>) {
        if (billingClient.isReady) {
            querySubscriptions(productIds)
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isBillingReady.value = true
                    if (productIds.isNotEmpty()) {
                        querySubscriptions(productIds)
                    }
                } else {
                    _isBillingReady.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                _isBillingReady.value = false
            }
        })
    }

    fun querySubscriptions(productIds: List<String>) {
        if (!billingClient.isReady || productIds.isEmpty()) return
        val products = productIds.distinct().map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = list.associateBy { it.productId }
            }
        }
    }

    fun launchSubscriptionFlow(activity: Activity, productId: String): Result<Unit> {
        val details = _productDetails.value[productId]
            ?: return Result.failure(
                IllegalStateException("Google Play subscription product '$productId' is not active or Play Store is unavailable on this device.")
            )
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Result.failure(IllegalStateException("No active subscription offer found for '$productId'."))

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Billing launch failed: ${result.debugMessage}"))
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        val productId = purchase.products.firstOrNull().orEmpty()
                        if (!purchase.isAcknowledged) {
                            val ackParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            billingClient.acknowledgePurchase(ackParams) { /* verified server-side */ }
                        }
                        _lastEvent.value = BillingUiEvent.PurchaseVerifiedNeeded(
                            productId = productId,
                            purchaseToken = purchase.purchaseToken
                        )
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _lastEvent.value = BillingUiEvent.Message("Subscription purchase canceled.")
            }
            else -> {
                _lastEvent.value = BillingUiEvent.Message("Play Billing error: ${billingResult.debugMessage}")
            }
        }
    }

    fun clearLastEvent() {
        _lastEvent.value = null
    }
}
