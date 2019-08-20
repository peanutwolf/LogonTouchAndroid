package com.ingenico.logontouch.tools

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import io.reactivex.Observable
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.*
import javax.security.auth.x500.X500Principal

/**
 * Created by vigursky on 17.11.2017.
 */

class AppLocalKeystore(private val context: Context){
    private var mKeyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore")
    init {
        mKeyStore.load(null)
    }

    fun masterKeyAvailable(): Boolean{
        return try {
            when{
                mKeyStore.containsAlias(MASTER_KEY_ALIAS).not() -> false
                mKeyStore.getEntry(MASTER_KEY_ALIAS, null) !is KeyStore.PrivateKeyEntry -> false
                else -> true
            }
        }catch (ex: UnrecoverableKeyException){
            tryDeleteEntry(MASTER_KEY_ALIAS)
        }
    }


    private fun generateKeyAlgoParameterSpec(keyAlias: String, subject: X500Principal): AlgorithmParameterSpec {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT )
                    .setCertificateSubject(subject)
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setKeyValidityStart(start.time)
                    .setKeyValidityEnd(end.time)
                    .setKeySize(2048)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                    .build()
        } else {
            KeyPairGeneratorSpec.Builder(context)
                    .setAlias(keyAlias)
                    .setSubject(subject)
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start.time)
                    .setEndDate(end.time)
                    .setKeySize(2048)
                    .setKeyType(KeyProperties.KEY_ALGORITHM_RSA)
                    .setEncryptionRequired()
                    .build()
        }
    }

    fun generateMasterKey(): Boolean {
        val keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        when{
            mKeyStore.containsAlias(MASTER_KEY_ALIAS) -> tryDeleteEntry(MASTER_KEY_ALIAS)
        }

        val spec = generateKeyAlgoParameterSpec(MASTER_KEY_ALIAS, X500Principal("CN=MasterK, O=LogonTouch"))
        keyGen.initialize(spec)
         keyGen.genKeyPair() ?: return false

        return true
    }

     private fun cipherData(data: ByteArray): ByteArray{
        val masterKey = mKeyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val mPublicKey = masterKey.certificate.publicKey

        try{
            val cipher = Cipher.getInstance(cipherTransformation).also {
                it.init(Cipher.ENCRYPT_MODE, mPublicKey)
            }

            return cipher.doFinal(data)
        }catch (e: Exception) {
            throw e
        }
    }

    private fun decipherData(data: ByteArray): ByteArray {
        val masterKey = mKeyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val mPrivateKey = masterKey.privateKey

        try{
            val cipher = Cipher.getInstance(cipherTransformation).also {
                it.init(Cipher.DECRYPT_MODE, mPrivateKey)
            }

            return cipher.doFinal(data)
        }catch (e: Exception){
            throw e
        }
    }

    fun workingKeyAvailable(keyAlias: String): Boolean = keyAlias in context.fileList()

    fun installWorkingSecretKey(keyArray: String, keyAlias: String){
        val cipheredKey = cipherData(keyArray.toByteArray())
        context.openFileOutput(keyAlias, Context.MODE_PRIVATE).use {
            it.write(cipheredKey)
        }
    }

    fun readWorkingSecretKey(keyAlias: String): String {

        val keyBytesCiphered = context.openFileInput(keyAlias).use {
            it.readBytes()
        }

        val keyBytes = decipherData(keyBytesCiphered)
        return String(keyBytes)
    }

    fun readKeyStore(keyFilename: String, keyPass: String): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12")
        val keyStoreBytes = context.openFileInput(keyFilename)
                .use {
                    it.readBytes()
                }

        ByteArrayInputStream(keyStoreBytes).use {
            keyStore.load(it, keyPass.toCharArray())
        }
        return keyStore
    }

    private fun tryDeleteEntry(alias: String): Boolean{
        return try {
            mKeyStore.deleteEntry(alias)
            true
        }catch (ex: KeyStoreException){
            Log.w(TAG, "Failed to delete alias=[$alias]", ex)
            false
        }
    }

    companion object {
        private val TAG = AppLocalKeystore::class.java.simpleName

        private const val MASTER_KEY_ALIAS = "MASTER_KEY_ALIAS"
        private const val AUTHENTICATION_DURATION_SECONDS = 5 * 60

        private const val cipherTransformation = "${KeyProperties.KEY_ALGORITHM_RSA}" +
                "/${KeyProperties.BLOCK_MODE_ECB}" +
                "/${KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1}"

        const val CREDENTIAL_WORKING_KEY_ALIAS = "CREDENTIAL_WORKING_KEY_ALIAS"
        const val CREDENTIAL_WORKING_IV_ALIAS = "CREDENTIAL_WORKING_IV_ALIAS"
        const val CLIENT_CERT_PASSPHRASE_ALIAS = "CLIENT_CERT_PASSPHRASE_ALIAS"
        const val SERVER_CERT_PASSPHRASE_ALIAS = "SERVER_CERT_PASSPHRASE_ALIAS"

        const val CLIENT_PRIVATE_CERT_FILENAME = "client_cert.pkcs12"
        const val SERVER_PUBLIC_CERT_FILENAME  = "server_cert.pkcs12"
    }

}

