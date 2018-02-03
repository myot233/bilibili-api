package com.hiczp.bilibili.api.interceptor;

import com.google.gson.*;
import com.hiczp.bilibili.api.ServerErrorCode;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

//由于服务器返回错误时的 data 字段类型不固定, 会导致 json 反序列化出错.
//该拦截器将在返回的 code 不为 0 时, 将 response 转换为包含一个空 data 的 json 字符串.
public class ErrorResponseBodyConverterInterceptor implements Interceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorResponseBodyConverterInterceptor.class);
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final Gson GSON = new Gson();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        ResponseBody responseBody = response.body();

        BufferedSource bufferedSource = responseBody.source();
        bufferedSource.request(Long.MAX_VALUE);
        Buffer buffer = bufferedSource.buffer();
        //必须要 clone 一次, 否则将导致流关闭
        String json = buffer.clone().readString(StandardCharsets.UTF_8);

        JsonObject jsonObject = JSON_PARSER.parse(json).getAsJsonObject();
        JsonElement code = jsonObject.get("code");
        //code 字段不存在
        if (code == null) {
            return response;
        }
        //code 为 0
        if (code.getAsInt() == ServerErrorCode.Common.OK) {
            return response;
        }
        //打印 body
        LOGGER.error("Get error response below: \n{}",
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(jsonObject)
        );
        //data 字段不存在
        if (jsonObject.get("data") == null) {
            return response;
        }
        jsonObject.add("data", JsonNull.INSTANCE);
        return response.newBuilder()
                .body(ResponseBody.create(
                        responseBody.contentType(),
                        GSON.toJson(jsonObject))
                ).build();
    }
}