package com.proofu.api.identity

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * A stand-in identity provider for tests: serves a JWKS over HTTP and mints tokens signed with
 * the matching key. It plays any OIDC-compliant IdP (ADR-0010 결정 1 is provider-neutral).
 */
class FakeIdp : AutoCloseable {
    private val key: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val issuer: String get() = "http://127.0.0.1:${server.address.port}/realms/proofu"
    val jwksUri: String get() = "http://127.0.0.1:${server.address.port}/jwks"

    init {
        val jwks = JWKSet(key.toPublicJWK()).toString().toByteArray()
        server.createContext("/jwks") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, jwks.size.toLong())
            exchange.responseBody.use { it.write(jwks) }
        }
        server.start()
    }

    fun token(
        subject: String = UUID.randomUUID().toString(),
        email: String = "$subject@example.com",
        emailVerified: Boolean = true,
        audience: String = "proofu-api",
        issuer: String = this.issuer,
        authTime: Instant = Instant.now(),
        expiresAt: Instant = Instant.now().plusSeconds(300),
        name: String = "Test User",
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", name)
                .claim("auth_time", authTime.epochSecond)
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
        jwt.sign(RSASSASigner(key))
        return jwt.serialize()
    }

    override fun close() = server.stop(0)
}
