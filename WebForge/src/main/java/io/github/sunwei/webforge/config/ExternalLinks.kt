package io.github.sunwei.webforge.config

/** WebView 遇到第三方应用链接时的处理方式。ASK 对应用市场链接请求确认。  */
enum class ExternalLinks {
    ASK,
    OPEN,
    BLOCK
}
