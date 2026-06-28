package com.stalker.player.data.model

import com.google.gson.annotations.SerializedName

data class PortalResponse(
    val js: JsData? = null
)

data class JsData(
    val token: String? = null,
    val random: String? = null,
    val data: List<Map<String, Any?>>? = null,
    @SerializedName("total_items") val totalItems: Any? = null,
    val url: String? = null,
    val cmd: String? = null,
    val mac: String? = null,
    val fname: String? = null,
    @SerializedName("expire_billing_date") val expireBillingDate: String? = null,
    @SerializedName("expire_date") val expireDate: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    val phone: String? = null,
    @SerializedName("parent_password") val parentPassword: String? = null,
    @SerializedName("max_online") val maxOnline: Any? = null,
    val storages: Map<String, Map<String, Any?>>? = null,
    @SerializedName("device_mac") val deviceMac: String? = null,
    @SerializedName("mac_address") val macAddress: String? = null
)
