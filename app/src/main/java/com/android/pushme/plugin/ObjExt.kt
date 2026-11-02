package com.android.pushme.plugin

import com.quickjs.JSObject
import android.webkit.JavascriptInterface
import com.quickjs.JavaCallback

abstract class ObjExt {
    abstract fun setup(jsObject: JSObject)

    abstract fun close(jsObject: JSObject)
}

fun JSObject.addObjExt(objExt: ObjExt?) {
    objExt?.setup(this)
}


fun JSObject.addObjInterface(obj: ObjExt, interfaceName: String) {
    val jsObject = JSObject(context)
    val methods = obj.javaClass.declaredMethods
        .filter { method ->
            method.getAnnotation(JavascriptInterface::class.java) != null
        }

    methods.forEach { method ->
        val functionName = method.name
        if(method.returnType.equals(Void.TYPE)) {
            jsObject.registerJavaMethod({ _, args ->
                if(method.parameterCount > 0) method.invoke(obj, args) else method.invoke(obj)
            }, functionName)
        } else {
            jsObject.registerJavaMethod(JavaCallback { _, args ->
                if(method.parameterCount > 0) method.invoke(obj, args) else method.invoke(obj)
            }, functionName)
        }
    }

    set(interfaceName, jsObject)
}