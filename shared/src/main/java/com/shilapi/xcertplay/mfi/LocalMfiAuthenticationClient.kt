package com.shilapi.xcertplay.mfi

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Provider
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Experimental, local P-256 provider. Identity provisioning is owned by the host application.
 * A valid signature here proves key consistency, not that an iPhone trusts the identity.
 */
class LocalMfiAuthenticationClient private constructor(
    private val privateKey: PrivateKey,
    private val certificate: ByteArray,
    private val onSignature: (Int) -> Unit,
    private val signingProvider: Provider?,
) : MfiAuthenticator {
    override fun protocolMajor(): Int = 3

    override fun readCertificate(maximumOutputLength: Int): ByteArray {
        require(maximumOutputLength > 0 && certificate.size <= maximumOutputLength) {
            "Local certificate exceeds the requested output limit"
        }
        return certificate.copyOf()
    }

    override fun signChallenge(challenge: ByteArray): ByteArray {
        require(challenge.size == 32) { "Local MFi v3 expects a 32-byte digest" }
        // The iAP2/AirPlay caller supplies a digest already. Do not hash it a second time.
        val signer = signature(signingProvider)
        signer.initSign(privateKey)
        signer.update(challenge)
        val result = derToRaw(signer.sign())
        onSignature(challenge.size)
        return result
    }

    companion object {
        /** Presence of this private app directory selects offline-only mode, even if incomplete. */
        const val DIRECTORY = "offline-mfi"
        private const val MAX_FILE_BYTES = 16 * 1024
        private const val ALGORITHM = "NONEwithECDSA"
        private const val P256_ORDER =
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551"

        /**
         * Head-unit ROMs are not obliged to ship a JCA provider that can parse this certificate and
         * sign with this key, so the bundled BouncyCastle is the fallback. Whichever provider
         * validates the identity at load time signs every later challenge: mixing them can hand one
         * provider a key object the other refuses.
         */
        private val bundledProvider: Provider by lazy { BouncyCastleProvider() }

        fun load(directory: File, onSignature: (Int) -> Unit = {}): LocalMfiAuthenticationClient {
            require(directory.isDirectory) { "Offline MFi directory is missing or invalid" }
            val certificate = readBounded(File(directory, "certificate.p7b"))
            val certificates = parseCertificates(certificate)
            require(certificates.size == 1) { "Expected one accessory certificate" }
            val publicKey = certificates.single().publicKey as? ECPublicKey
                ?: error("Expected an EC accessory certificate")
            require(publicKey.params.order.toString(16) == P256_ORDER) {
                "Expected a P-256 accessory certificate"
            }
            val encodedKey = readBounded(File(directory, "identity.pk8"))
            val validated = try {
                validatedKey(encodedKey, publicKey)
            } finally {
                encodedKey.fill(0)
            }
            return LocalMfiAuthenticationClient(
                validated.first,
                certificate,
                onSignature,
                validated.second,
            )
        }

        private fun parseCertificates(certificate: ByteArray): Collection<Certificate> {
            val platformFailure = try {
                return CertificateFactory.getInstance("X.509")
                    .generateCertificates(certificate.inputStream())
            } catch (failure: GeneralSecurityException) {
                failure
            }
            try {
                return CertificateFactory.getInstance("X.509", bundledProvider)
                    .generateCertificates(certificate.inputStream())
            } catch (failure: GeneralSecurityException) {
                failure.addSuppressed(platformFailure)
                throw failure
            }
        }

        private fun validatedKey(
            encodedKey: ByteArray,
            publicKey: ECPublicKey,
        ): Pair<PrivateKey, Provider?> {
            val platformFailure = try {
                return validatedKey(encodedKey, publicKey, null) to null
            } catch (failure: GeneralSecurityException) {
                failure
            }
            try {
                return validatedKey(encodedKey, publicKey, bundledProvider) to bundledProvider
            } catch (failure: GeneralSecurityException) {
                failure.addSuppressed(platformFailure)
                throw failure
            }
        }

        /** A key that does not match the certificate is rejected outright: no provider can fix it. */
        private fun validatedKey(
            encodedKey: ByteArray,
            publicKey: ECPublicKey,
            provider: Provider?,
        ): PrivateKey {
            val keyFactory = if (provider == null) {
                KeyFactory.getInstance("EC")
            } else {
                KeyFactory.getInstance("EC", provider)
            }
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
            val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
            val signer = signature(provider)
            signer.initSign(privateKey)
            signer.update(challenge)
            val signature = signer.sign()
            val verifier = signature(provider)
            verifier.initVerify(publicKey)
            verifier.update(challenge)
            require(verifier.verify(signature)) { "Local private key does not match certificate" }
            return privateKey
        }

        private fun signature(provider: Provider?): Signature = if (provider == null) {
            Signature.getInstance(ALGORITHM)
        } else {
            Signature.getInstance(ALGORITHM, provider)
        }

        private fun readBounded(file: File): ByteArray = file.inputStream().use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_FILE_BYTES) { "Local identity file is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray().also { require(it.isNotEmpty()) { "Local identity file is empty" } }
        }

        internal fun derToRaw(der: ByteArray): ByteArray {
            val sequence = ASN1Sequence.getInstance(der)
            require(sequence.size() == 2) { "Invalid ECDSA signature" }
            val result = ByteArray(64)
            for (index in 0..1) {
                val value = ASN1Integer.getInstance(sequence.getObjectAt(index)).value
                require(value.signum() > 0 && value.bitLength() <= 256) { "Invalid ECDSA integer" }
                val bytes = value.toByteArray()
                val skip = if (bytes.size == 33 && bytes[0] == 0.toByte()) 1 else 0
                val length = bytes.size - skip
                bytes.copyInto(result, index * 32 + 32 - length, skip)
            }
            return result
        }
    }
}
