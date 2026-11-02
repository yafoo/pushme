package com.android.pushme.utils

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale

fun getCertInfo(pemString: String = ""): String? {
    return if(pemString == "") "" else try {
        // 移除PEM格式的头部和尾部
        val cleanPem = pemString
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\n", "")
            .trim()

        // 解码Base64
        val decoded = Base64.decode(cleanPem, Base64.DEFAULT)

        // 创建证书工厂
        val factory = CertificateFactory.getInstance("X.509")
        val cert = factory.generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate
        val list = mutableListOf<String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val startData = sdf.format(cert.notBefore)
        val endData = sdf.format(cert.notAfter)
        list.add("StartDate: $startData")
        list.add("EndDate: $endData")
        list.add("CertSubject: ${cert.subjectX500Principal.name}")
        val sanExtension = cert.subjectAlternativeNames
        val dnsName = sanExtension.toString()
            .replace("[[", "")
            .replace("]]", "")
            .replace("2, ", "")
            .replace("7, ", "")
            .replace("], [", ", ")
        list.add("CertDomain: $dnsName")
        list.joinToString("\n")
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}