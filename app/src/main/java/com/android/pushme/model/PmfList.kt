package com.android.pushme.model

import com.android.pushme.utils.AESUtil
import com.android.pushme.utils.RSAUtil

// 内置方法数据类
data class PmfData(
    val key: String = "",
    val content: String = ""
)

// 内置方法介绍
class PmfList {
    val pmfList = listOf(
        PmfData(
            key = "md5",
            content = """
                PMF.md5(str: string): string
            """.trimIndent()
        ),
        PmfData(
            key = "base64",
            content = """
                //flag: {0: DEFAULT, 1: NO_PADDING, 2: NO_WRAP, 4: CRLF, 8: URL_SAFE, 16: NO_CLOSE}
                
                //编码
                PMF.base64.encode(str: string, flag: number = 2): string
                
                //解码
                PMF.base64.decode(str: string, flag: number = 0): string
            """.trimIndent()
        ),
        PmfData(
            key = "rsa",
            content = """
                //默认参数
                //transformation: '${RSAUtil.ECB_PKCS1PADDING}'
                //keySize: ${RSAUtil.DEFAULT_KEY_SIZE}
                
                //加密
                PMF.rsa.encrypt(str: String, publicKey: String): String
                
                //解密
                PMF.rsa.decrypt(str: String, privateKey: String): String
                
                //设置transformation
                //transformation: {
                //    ECB_PKCS1PADDING: '${RSAUtil.ECB_PKCS1PADDING}',
                //    ECB_OAEP_WITH_SHA1: '${RSAUtil.ECB_OAEP_WITH_SHA1}',
                //    ECB_OAEP_WITH_SHA256: '${RSAUtil.ECB_OAEP_WITH_SHA256}',
                //    NONE_OAEP_WITH_SHA1: '${RSAUtil.NONE_OAEP_WITH_SHA1}',
                //    NONE_OAEP_WITH_SHA256: '${RSAUtil.NONE_OAEP_WITH_SHA256}'
                //}
                PMF.rsa.setTransformation(transformation: String)
                
                //设置size
                //size: {1024, 2048}
                PMF.rsa.setKeySize(size: Int)
                
                //生成publicKey和privateKey
                PMF.rsa.generateKey(): Boolean
                
                //获取publicKey，如果不存在则会生成
                PMF.rsa.getPublicKey(): String
                
                //获取privateKey，如果不存在则会生成
                PMF.rsa.getPrivateKey(): String
            """.trimIndent()
        ),
        PmfData(
            key = "aes",
            content = """
                //默认参数
                //transformation: '${AESUtil.CBC_PKCS5}'
                //keySize: 128
                
                //加密
                //返回：iv+encryptData
                PMF.aes.encrypt(str: String, key: String, iv: String? = null): String
                
                //解密
                //str：iv+encryptData
                PMF.aes.decrypt(str: String, key: String): String
                
                //设置size
                //size: {128, 192, 256}
                PMF.aes.setKeySize(keySize: Int)
                
                //设置transformation
                //transformation: {
                //    CBC_PKCS5: '${AESUtil.CBC_PKCS5}',
                //    CBC_PKCS7: '${AESUtil.CBC_PKCS7}',
                //    CTR: '${AESUtil.CTR}',
                //    GCM: '${AESUtil.GCM}',
                //    ECB: '${AESUtil.ECB}'
                //}
                PMF.aes.setTransformation(transformation: String)
                
                //获取随机key
                PMF.aes.getRandomKey(): String
                
                //获取随机iv
                PMF.aes.getRandomIV(): String
            """.trimIndent()
        ),
        PmfData(
            key = "toast",
            content = """
                //type: {0: 'SHORT', 1: 'LONG'}
                PMF.toast(str: string = '', type: number = 0)
            """.trimIndent()
        ),
        PmfData(
            key = "fetch",
            content = """
                //同步HTTP请求方法（会阻塞直到请求完成）
                //options: {
                //    headers: {key: value} = {},
                //    body: string = null,
                //    bodyType: 'json' | 'form' | 'text' = 'json',
                //    cookies: string = null,
                //    timeout: number = 30000
                //}
                //返回: {
                //    status: number,
                //    statusText: string,
                //    headers: {key: value},
                //    body: string,
                //    cookies: string
                //}
                PMF.fetch.fetch(url: string, options: string = '{}'): string
                PMF.fetch.get(url: string, options: string = '{}'): string
                PMF.fetch.post(url: string, options: string = '{}'): string
                
                //异步HTTP请求方法（不阻塞，立即返回，无响应结果）
                //适用于不关注请求结果的场景（如通知、日志上报等）
                //返回: {status: 0, statusText: 'async'}
                PMF.fetch.asyncFetch(url: string, options: string = '{}'): string
                PMF.fetch.asyncGet(url: string, options: string = '{}'): string
                PMF.fetch.asyncPost(url: string, options: string = '{}'): string
            """.trimIndent()
        )
    )
}