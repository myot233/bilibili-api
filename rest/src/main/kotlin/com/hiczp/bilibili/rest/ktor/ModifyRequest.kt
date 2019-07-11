package com.hiczp.bilibili.rest.ktor

import com.hiczp.bilibili.rest.BilibiliClientInherent
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import io.ktor.util.StringValuesBuilder

internal class ModifyRequest(private val builder: HttpRequestBuilder.() -> Unit) {
    companion object Feature : HttpClientFeature<HttpRequestBuilder, ModifyRequest> {
        override val key: AttributeKey<ModifyRequest> = AttributeKey("ModifyRequest")

        override fun prepare(block: HttpRequestBuilder.() -> Unit): ModifyRequest =
                ModifyRequest(block)

        override fun install(feature: ModifyRequest, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                val originBody = context.body
                context.apply(feature.builder)
                val newBody = context.body
                if (newBody !== originBody) {
                    proceedWith(newBody)
                }
            }
        }
    }
}

@UseExperimental(InternalAPI::class)
internal fun HttpClientConfig<*>.commonParams(
        block: StringValuesBuilder.() -> Unit
) {
    fun StringValuesBuilder.addCommonParams(request: HttpRequestBuilder): StringValuesBuilder {
        block()
        @Suppress("SpellCheckingInspection")
        appendMissing("appkey", request.attributes.getOrNull(APP_KEY_ATTRIBUTE_KEY) ?: BilibiliClientInherent.appKey)
        sortAndSign(request.attributes.getOrNull(APP_SECRET_ATTRIBUTE_KEY) ?: BilibiliClientInherent.appSecret)
        return this
    }

    install(ModifyRequest) {
        //add params to query
        if (method == HttpMethod.Get || attributes.contains(FORCE_QUERY_COMMON_PARAMS_ATTRIBUTE_KEY)) {
            url.parameters.addCommonParams(this)
        } else if (method == HttpMethod.Post) { //add to body
            when (val originBody = body) {
                is EmptyContent -> {
                    body = FormDataContent(Parameters.build {
                        addCommonParams(this@install)
                    })
                }
                is FormDataContent -> {
                    body = FormDataContent(Parameters.build {
                        originBody.formData.forEach { key, value ->
                            appendAll(key, value)
                        }
                        addCommonParams(this@install)
                    })
                }
            }
        }
    }
}

@UseExperimental(InternalAPI::class)
internal fun StringValuesBuilder.appendMissing(name: String, value: String) {
    if (!contains(name)) {
        append(name, value)
    }
}
