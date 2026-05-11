package io.github.choffmann.chatwsandroid.support

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class TestServerExtension : BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private val NS = ExtensionContext.Namespace.create(TestServerExtension::class.java)
    private val KEY = "server"

    override fun beforeEach(context: ExtensionContext) {
        val server = TestServer().also { it.start() }
        context.getStore(NS).put(KEY, server)
    }

    override fun afterEach(context: ExtensionContext) {
        (context.getStore(NS).get(KEY) as? TestServer)?.stop()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == TestServer.Control::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        (extensionContext.getStore(NS).get(KEY) as TestServer).control
}
