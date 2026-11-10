package com.cachewipe.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.cachewipe.app.R

/**
 * Pro = one-time purchase (no subscription): no ads, unlimited fast mode.
 * The product "pro_unlock" must be created in Play Console before purchases work.
 */
class BillingManager(context: Context, private val onProChanged: (Boolean) -> Unit) {

    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty())
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                Log.i(TAG, "billing setup ${result.responseCode} ${result.debugMessage}")
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun refresh() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases)
        }
    }

    private fun handle(purchases: List<Purchase>) {
        val owned = purchases.filter { PRO_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        owned.filterNot { it.isAcknowledged }.forEach {
            // Unacknowledged purchases are refunded by Play after 3 days
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
        }
        onProChanged(owned.isNotEmpty())
    }

    /** Opens the Play purchase screen, or calls [onError] with what to tell the user. */
    fun buyPro(activity: Activity, onError: (String) -> Unit) {
        if (!client.isReady) {
            onError(activity.getString(R.string.billing_unavailable))
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, details ->
            val pro = details.productDetailsList.firstOrNull()
            Log.i(TAG, "product query ${result.responseCode} found=${pro != null}")
            activity.runOnUiThread {
                if (pro == null) {
                    onError(activity.getString(R.string.billing_not_found))
                } else {
                    val flow = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pro).build()),
                        )
                        .build()
                    client.launchBillingFlow(activity, flow)
                }
            }
        }
    }

    fun close() = client.endConnection()

    companion object {
        const val PRO_ID = "pro_unlock"
        private const val TAG = "CacheWipe"
    }
}
