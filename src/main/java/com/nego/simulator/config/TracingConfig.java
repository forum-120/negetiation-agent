package com.nego.simulator.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 追踪配置。
 *
 * <p>
 * 通过 opentelemetry-spring-boot-starter 自动装配的 {@link OpenTelemetry} 实例，
 * 暴露一个命名为 "negotiation-simulator" 的 {@link Tracer} Bean，
 * 供业务层手动埋点使用。
 * </p>
 */
@Configuration
public class TracingConfig {

    /**
     * 创建全局 Tracer。
     *
     * <p>
     * instrumentation-scope-name 设为服务名称，与 Jaeger 中的 service name 对应，
     * 方便按服务名过滤 trace。
     * </p>
     */
    @Bean
    public Tracer negotiationTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("negotiation-simulator", "0.0.1");
    }
}
