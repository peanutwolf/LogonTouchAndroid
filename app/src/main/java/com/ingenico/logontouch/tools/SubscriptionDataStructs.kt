package com.ingenico.logontouch.tools

import java.util.*


/**
 * Created by vigursky on 20.11.2017.
 */

data class HostAddressHolder(val ip: String, val port: Int)

data class  ClientSecretKeys(val sessionHash: String,
                             val privateKeyStoreKey: String,
                             val secretCredentialKey: String,
                             val secretCredentialIV: String)

data class ClientCertificate(public val reqHash: String, val cert: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClientCertificate

        if (reqHash != other.reqHash) return false
        if (!Arrays.equals(cert, other.cert)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reqHash.hashCode()
        result = 31 * result + Arrays.hashCode(cert)
        return result
    }
}

data class HostCertificate(
        var sessionHash: String,
        var publicCertificate: ByteArray,
        var publicKeyStoreKey: CharArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HostCertificate

        if (sessionHash != other.sessionHash) return false
        if (!Arrays.equals(publicCertificate, other.publicCertificate)) return false
        if (!Arrays.equals(publicKeyStoreKey, other.publicKeyStoreKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionHash.hashCode()
        result = 31 * result + Arrays.hashCode(publicCertificate)
        result = 31 * result + Arrays.hashCode(publicKeyStoreKey)
        return result
    }
}