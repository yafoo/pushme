package com.android.pushme.plugin

import android.content.Context
import com.android.pushme.model.PmfManager
import com.quickjs.JSObject

class PmfObjExt(val context: Context): ObjExt() {
    private val objExtList = mutableMapOf<String, ObjExt>()

    override fun setup(jsObject: JSObject) {
        PmfManager.pmfStateMap.forEach { (key, value) ->
            if(value && !objExtList.contains(key)) {
                objExtList[key] = when(key) {
                    "md5" -> Md5ObjExt()
                    "base64" -> Base64ObjExt(context)
                    "rsa" -> RsaObjExt(context)
                    "aes" -> AesObjExt(context)
                    "toast" -> ToastObjExt(context)
                    "fetch" -> FetchObjExt(context)
                    else -> throw Exception(key + "ObjExt未定义！")
                }
            } else if(!value && objExtList.contains(key)) {
                objExtList.remove(key)
            }
        }
        objExtList.forEach { (_, objExt) ->
            jsObject.addObjExt(objExt)
        }
    }

    override fun close(jsObject: JSObject) { }
}