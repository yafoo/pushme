package com.android.pushme.plugin

val PLUGIN_COMPOSE = """
const compose = (plugins) => {
  return function (msg, next) {
    let index = -1
    return dispatch(0)
    function dispatch (i) {
      if (i <= index) return 'next() called multiple times'
      index = i
      let fn = plugins[i]
      if (i === plugins.length) fn = next
      if (!fn) return true
      if(typeof(fn) !== 'function') return 'plugin ' + (i+1) + ' must be composed of functions!'
      try {
        return fn(msg, dispatch.bind(null, i + 1))
      } catch (err) {
        const err_msg = typeof(err) == 'object' ? err.message + '\n' + err.stack : err
        toast('Error:' + err_msg, 1);
        return err
      }
    }
  }
}

""".trimIndent()

val PLUGIN_ARRAY = """
const plugins = [];

""".trimIndent()

val PLUGIN_RUN = """
compose(plugins)(msg);
msg;
""".trimIndent()

val PLUGIN_DEMO_CODE = """
/**
 * @name 插件名
 * @param {Object} msg - 消息对象
 * @param {String} msg.title - 标题
 * @param {String} msg.content - 内容
 * @param {String} msg.date - 时间
 * @param {String} msg.type - 类型
 * @param {Function} next - 下一个插件
 */
function(msg, next) {
    // add msg group
    // msg.title += ENV.GROUP_NAME || '[#😄]';
    // toast the new title
    // PMF.toast(msg.title);
    
    // run next plugin
    next();
    
    // your other code
}
""".trimIndent()

object PluginCode {
    fun getJSCode(plugins: List<String>): String {
        val js = plugins.joinToString("\n") { getPluginCode(it) }
        return "${PLUGIN_COMPOSE}\n${PLUGIN_ARRAY}\n${js}\n${PLUGIN_RUN}"
    }

    private fun getPluginCode(func: String): String {
        return "plugins.push(\n" + func.replaceIndent("    ") + "\n);\n"
    }
}