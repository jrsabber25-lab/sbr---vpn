package com.example.data.remote

import com.example.data.model.AuthRequest
import com.example.data.model.AuthResponse
import com.example.data.model.GenericMessageResponse
import com.example.data.model.GuestAuthRequest
import com.example.data.model.PasswordResetConfirm
import com.example.data.model.PasswordResetRequest
import com.example.data.model.PlayVerifyRequest
import com.example.data.model.PublicIpResponse
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SupportTicketRequest
import com.example.data.model.SupportTicketResponse
import com.example.data.model.UserProfile
import com.example.data.model.VpnConnectRequest
import com.example.data.model.VpnDisconnectRequest
import com.example.data.model.VpnProvisionResponse
import com.example.data.model.VpnServer
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface SbrVpnApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("api/v1/auth/guest")
    suspend fun guestAuth(@Body request: GuestAuthRequest): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(): GenericMessageResponse

    @POST("api/v1/auth/password-reset/request")
    suspend fun requestPasswordReset(@Body request: PasswordResetRequest): GenericMessageResponse

    @POST("api/v1/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body request: PasswordResetConfirm): GenericMessageResponse

    @GET("api/v1/auth/me")
    suspend fun getProfile(): UserProfile

    @GET("api/v1/servers")
    suspend fun getServers(): List<VpnServer>

    @POST("api/v1/vpn/connect")
    suspend fun provisionTunnel(@Body request: VpnConnectRequest): VpnProvisionResponse

    @POST("api/v1/vpn/disconnect")
    suspend fun disconnectTunnel(@Body request: VpnDisconnectRequest): GenericMessageResponse

    @GET("api/v1/subscriptions/plans")
    suspend fun getSubscriptionPlans(): List<SubscriptionPlan>

    @POST("api/v1/subscriptions/verify-play")
    suspend fun verifyPlaySubscription(@Body request: PlayVerifyRequest): UserProfile

    @POST("api/v1/support/tickets")
    suspend fun submitSupportTicket(@Body request: SupportTicketRequest): SupportTicketResponse

    @GET("api/v1/network/ip")
    suspend fun getClientPublicIp(): PublicIpResponse
}
