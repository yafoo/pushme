package com.android.pushme.plugin

import com.android.pushme.model.EnvManager
import com.quickjs.JSObject

class EnvObjExt: ObjExt() {
    override fun setup(jsObject: JSObject) {
        EnvManager.envDataList.filter { it.enabled }.forEach {
            jsObject.set(it.key, it.value)
        }
    }

    override fun close(jsObject: JSObject) { }
}