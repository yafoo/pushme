package com.android.pushme.utils

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESUtil {
    companion object {
        const val ALGORITHM_AES = "AES"
        const val CBC_PKCS5 = "AES/CBC/PKCS5Padding"
        const val CBC_PKCS7 = "AES/CBC/PKCS7Padding"
        const val CTR = "AES/CTR/NoPadding"
        const val GCM = "AES/GCM/NoPadding"
        const val ECB = "AES/ECB/PKCS5Padding"

        const val GCM_TAG_LENGTH = 128 // bits
    }

    private var transformation: String = CBC_PKCS5
    private var keySizeInBytes: Int = 16 // 128 bits = 16 bytes

    /**
     * 设置加密模式
     */
    fun setTransformation(transformation: String) {
        this.transformation = transformation
    }

    // 设置密钥大小
    fun setKeySize(sizeInBits: Int = 128) {
        require(sizeInBits in listOf(128, 192, 256)) { "Key size must be 128, 192, or 256 bits" }
        keySizeInBytes = sizeInBits / 8
    }

    // 改进的模式判断
    private fun isECBMode(): Boolean = transformation.contains("ECB")
    private fun isGCMMode(): Boolean = transformation.contains("GCM")
    private fun requiresIV(): Boolean = !isECBMode()

    private fun getIVLength(): Int {
        return when {
            isGCMMode() -> 12 // GCM推荐使用12字节IV
            isECBMode() -> 0
            else -> 16 // CBC, CTR等模式
        }
    }

    /**
     * 生成随机IV
     */
    private fun generateIV(): ByteArray {
        val ivLength = getIVLength()
        return ByteArray(ivLength).apply {
            SecureRandom().nextBytes(this)
        }
    }

    /**
     * 处理密钥，确保长度正确
     */
    private fun processKey(key: ByteArray): SecretKeySpec {
        return when {
            key.size == keySizeInBytes -> SecretKeySpec(key, ALGORITHM_AES)
            key.size > keySizeInBytes -> SecretKeySpec(key.copyOf(keySizeInBytes), ALGORITHM_AES)
            else -> {
                // 对于过短的密钥，使用PKCS5Padding风格的填充
                val paddedKey = ByteArray(keySizeInBytes)
                System.arraycopy(key, 0, paddedKey, 0, key.size)
                // 剩余部分填充0（或者可以考虑其他填充策略）
                SecretKeySpec(paddedKey, ALGORITHM_AES)
            }
        }
    }

    /**
     * 加密数据 - 改进版本
     */
    fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray? = null): String {
        val secretKey = processKey(key)
        val cipher = Cipher.getInstance(transformation)

        val (actualIV, encrypted) = when {
            isECBMode() -> {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                null to cipher.doFinal(data)
            }
            isGCMMode() -> {
                val gcmIV = iv ?: generateIV()
                val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, gcmIV)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
                gcmIV to cipher.doFinal(data)
            }
            else -> {
                val cipherIV = iv ?: generateIV()
                val parameterSpec = IvParameterSpec(cipherIV)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
                cipherIV to cipher.doFinal(data)
            }
        }

        return if (!requiresIV()) {
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } else {
            val result = ByteArray(actualIV!!.size + encrypted.size)
            System.arraycopy(actualIV, 0, result, 0, actualIV.size)
            System.arraycopy(encrypted, 0, result, actualIV.size, encrypted.size)
            Base64.encodeToString(result, Base64.DEFAULT)
        }
    }

    /**
     * 加密字符串数据（密钥为字符串）
     */
    fun encrypt(data: String, key: String, iv: String? = null): String {
        val keyBytes = Base64.decode(key, Base64.DEFAULT)
        val ivBytes = if(iv != null) Base64.decode(iv, Base64.DEFAULT) else null
        return encrypt(data.toByteArray(Charsets.UTF_8), keyBytes, ivBytes)
    }

    /**
     * 解密数据 - 改进版本
     */
    fun decrypt(encryptedBytes: ByteArray, key: ByteArray): ByteArray {
        val secretKey = processKey(key)
        val cipher = Cipher.getInstance(transformation)

        return if (isECBMode()) {
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            cipher.doFinal(encryptedBytes)
        } else {
            val ivLength = getIVLength()
            require(encryptedBytes.size > ivLength) { "Encrypted data too short" }

            val iv = encryptedBytes.copyOfRange(0, ivLength)
            val encrypted = encryptedBytes.copyOfRange(ivLength, encryptedBytes.size)

            when {
                isGCMMode() -> {
                    val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
                }
                else -> {
                    val parameterSpec = IvParameterSpec(iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
                }
            }
            cipher.doFinal(encrypted)
        }
    }

    /**
     * 解密为字符串（密钥为字符串）
     */
    fun decryptToString(encryptedData: String, key: String): String {
        return String(decrypt(Base64.decode(encryptedData, Base64.DEFAULT), Base64.decode(key, Base64.DEFAULT)), Charsets.UTF_8)
    }

    /**
     * 生成随机密钥（根据当前加密模式）
     */
    fun getRandomKey(): String {
        return Base64.encodeToString(ByteArray(keySizeInBytes).apply {
            SecureRandom().nextBytes(this)
        }, Base64.NO_WRAP)
    }

    /**
     * 生成随机IV（根据当前加密模式）
     */
    fun getRandomIV(): String {
        return Base64.encodeToString(generateIV(), Base64.NO_WRAP)
    }
}