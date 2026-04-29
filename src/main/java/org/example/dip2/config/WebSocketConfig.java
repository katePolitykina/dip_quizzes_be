package org.example.dip2.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.example.dip2.websocket.JwtStompChannelInterceptor;
import org.example.dip2.websocket.TrackingWebSocketSessionDecoratorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompChannelInterceptor jwtStompChannelInterceptor;
    private final TrackingWebSocketSessionDecoratorFactory trackingWebSocketSessionDecoratorFactory;

    public WebSocketConfig(
            JwtStompChannelInterceptor jwtStompChannelInterceptor,
            TrackingWebSocketSessionDecoratorFactory trackingWebSocketSessionDecoratorFactory
    ) {
        this.jwtStompChannelInterceptor = jwtStompChannelInterceptor;
        this.trackingWebSocketSessionDecoratorFactory = trackingWebSocketSessionDecoratorFactory;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
        registration.taskExecutor(websocketTaskExecutor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(websocketTaskExecutor());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(trackingWebSocketSessionDecoratorFactory);
    }

    @Bean
    public ThreadPoolTaskExecutor websocketTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ws-vt-");
        executor.setThreadFactory(createVirtualThreadFactory("ws-vt-"));
        executor.setQueueCapacity(0);
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }

    private ThreadFactory createVirtualThreadFactory(String prefix) {
        try {
            Method ofVirtual = Thread.class.getMethod("ofVirtual");
            Object builder = ofVirtual.invoke(null);
            Method name = builder.getClass().getMethod("name", String.class, long.class);
            Object namedBuilder = name.invoke(builder, prefix, 0L);
            Method factory = namedBuilder.getClass().getMethod("factory");
            return (ThreadFactory) factory.invoke(namedBuilder);
        } catch (ReflectiveOperationException exception) {
            return Executors.defaultThreadFactory();
        }
    }
}
