package com.android.pushme.utils

import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator

class RSAUtil {
    companion object {
        const val ALGORITHM_RSA = "RSA"

        // 各种加密模式
        const val ECB_PKCS1PADDING = "RSA/ECB/PKCS1Padding"
        const val ECB_OAEP_WITH_SHA1 = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"
        const val ECB_OAEP_WITH_SHA256 = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val NONE_OAEP_WITH_SHA1 = "RSA/NONE/OAEPWithSHA-1AndMGF1Padding"
        const val NONE_OAEP_WITH_SHA256 = "RSA/NONE/OAEPWithSHA-256AndMGF1Padding"

        // 默认分块大小（根据密钥长度和填充模式调整）
        const val DEFAULT_KEY_SIZE = 2048
    }

    private var transformation = ECB_PKCS1PADDING
    private var keySize = DEFAULT_KEY_SIZE

    /**
     * 设置加密模式
     */
    fun setTransformation(transformation: String) {
        this.transformation = transformation
    }

    // 设置密钥大小
    fun setKeySize(size: Int = DEFAULT_KEY_SIZE) {
        keySize = if (size == 1024) 1024 else DEFAULT_KEY_SIZE
    }

    /**
     * 生成RSA密钥对
     */
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_RSA)
        keyPairGenerator.initialize(keySize)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * 获取最大加密块大小
     */
    private fun getMaxEncryptBlockSize(keySize: Int): Int {
        return when {
            transformation.contains("OAEP") -> (keySize / 8) - 42
            transformation.contains("PKCS1Padding") -> (keySize / 8) - 11
            else -> (keySize / 8) - 11 // 默认
        }
    }

    /**
     * 获取最大解密块大小
     */
    private fun getMaxDecryptBlockSize(keySize: Int): Int {
        return keySize / 8
    }

    /**
     * RSA加密 - 支持长字符串分块加密
     * @param data 要加密的数据
     * @param publicKey 公钥
     */
    fun encrypt(data: String, publicKey: String): String {
        return try {
            val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
            val encodedKey = Base64.decode(publicKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(encodedKey)
            val pubKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, pubKey)

            val dataBytes = data.toByteArray()
            val maxBlockSize = getMaxEncryptBlockSize(keySize)

            if (dataBytes.size <= maxBlockSize) {
                // 数据长度小于等于最大块大小，直接加密
                val encryptedBytes = cipher.doFinal(dataBytes)
                Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            } else {
                // 数据长度大于最大块大小，分块加密
                val encryptedBlocks = ArrayList<ByteArray>()
                var offset = 0

                while (offset < dataBytes.size) {
                    val blockSize = minOf(maxBlockSize, dataBytes.size - offset)
                    val block = dataBytes.copyOfRange(offset, offset + blockSize)
                    val encryptedBlock = cipher.doFinal(block)
                    encryptedBlocks.add(encryptedBlock)
                    offset += blockSize
                }

                // 合并所有加密块并Base64编码
                val totalSize = encryptedBlocks.sumOf { it.size }
                val result = ByteArray(totalSize)
                var destPos = 0

                for (block in encryptedBlocks) {
                    System.arraycopy(block, 0, result, destPos, block.size)
                    destPos += block.size
                }

                Base64.encodeToString(result, Base64.DEFAULT)
            }
        } catch (e: Exception) {
            throw RuntimeException("RSA加密失败", e)
        }
    }

    /**
     * RSA解密 - 支持长字符串分块解密
     * @param encryptedData 加密的数据
     * @param privateKey 私钥
     */
    fun decrypt(encryptedData: String, privateKey: String): String {
        return try {
            val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
            val encodedKey = Base64.decode(privateKey, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(encodedKey)
            val privKey = keyFactory.generatePrivate(keySpec)

            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, privKey)

            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            val maxBlockSize = getMaxDecryptBlockSize(keySize)

            if (encryptedBytes.size <= maxBlockSize) {
                // 数据长度小于等于最大块大小，直接解密
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                String(decryptedBytes)
            } else {
                // 数据长度大于最大块大小，分块解密
                val decryptedBlocks = ArrayList<ByteArray>()
                var offset = 0

                while (offset < encryptedBytes.size) {
                    val blockSize = minOf(maxBlockSize, encryptedBytes.size - offset)
                    val block = encryptedBytes.copyOfRange(offset, offset + blockSize)
                    val decryptedBlock = cipher.doFinal(block)
                    decryptedBlocks.add(decryptedBlock)
                    offset += blockSize
                }

                // 合并所有解密块
                val totalSize = decryptedBlocks.sumOf { it.size }
                val result = ByteArray(totalSize)
                var destPos = 0

                for (block in decryptedBlocks) {
                    System.arraycopy(block, 0, result, destPos, block.size)
                    destPos += block.size
                }

                String(result)
            }
        } catch (e: Exception) {
            throw RuntimeException("RSA解密失败", e)
        }
    }

    /**
     * 获取公钥字符串
     */
    fun getPublicKey(keyPair: KeyPair): String {
        val publicKey = keyPair.public
        return Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
    }

    /**
     * 获取私钥字符串
     */
    fun getPrivateKey(keyPair: KeyPair): String {
        val privateKey = keyPair.private
        return Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)
    }
}