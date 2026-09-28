package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.AbstractRestClientOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import reactor.netty.transport.ProxyProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Set;

@Slf4j
@Configuration
public class OAuth2ProxyConfig {

    private static final Set<String> GITHUB_HOSTS = Set.of("github.com", "api.github.com");

    private final SystemConfigService systemConfigService;

    @Getter
    private volatile boolean proxyEnabled = false;

    public OAuth2ProxyConfig(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
        refreshProxyState();
    }

    public void refreshProxyState() {
        this.proxyEnabled = Boolean.parseBoolean(systemConfigService.getConfigValue("oauth2.proxy.enabled", "false"));
        log.info("OAuth2 proxy enabled: {}", proxyEnabled);
    }

    /**
     * 返回动态路由工厂：每次请求都会读取最新代理配置。
     * 这样保存、启用或关闭代理后，不需要重启应用，也不需要重建 OAuth2 客户端。
     */
    public ClientHttpRequestFactory createRoutingRequestFactory() {
        return new DynamicRoutingClientHttpRequestFactory(createDirectFactory());
    }

    private volatile ProxyFactorySnapshot proxyFactorySnapshot;

    private ClientHttpRequestFactory resolveProxiedFactory() {
        if (!proxyEnabled) {
            return null;
        }

        String host = systemConfigService.getConfigValue("oauth2.proxy.host", "");
        String portStr = systemConfigService.getConfigValue("oauth2.proxy.port", "1080");
        String username = systemConfigService.getConfigValue("oauth2.proxy.username", "");
        String password = systemConfigService.getConfigValue("oauth2.proxy.password", "");
        String type = systemConfigService.getConfigValue("oauth2.proxy.type", "socks5");

        if (!StringUtils.hasText(host)) {
            return null;
        }

        int port;
        try {
            port = Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            port = 1080;
        }
        if (port < 1 || port > 65535) {
            log.warn("OAuth2 proxy port is invalid, falling back to direct connection: {}", portStr);
            return null;
        }

        boolean isHttp = "http".equalsIgnoreCase(type);
        boolean isSocks5 = "socks5".equalsIgnoreCase(type);
        if (!isHttp && !isSocks5) {
            log.warn("OAuth2 proxy type is invalid, falling back to direct connection: {}", type);
            return null;
        }

        String signature = String.join("|",
                Boolean.toString(proxyEnabled), host, Integer.toString(port), username, password, type.toLowerCase());
        ProxyFactorySnapshot cached = proxyFactorySnapshot;
        if (cached != null && cached.signature().equals(signature)) {
            return cached.factory();
        }

        synchronized (this) {
            cached = proxyFactorySnapshot;
            if (cached != null && cached.signature().equals(signature)) {
                return cached.factory();
            }
            ClientHttpRequestFactory factory = isHttp
                    ? createHttpProxyFactory(host, port, username, password)
                    : createNettySocks5Factory(host, port, username, password);
            proxyFactorySnapshot = new ProxyFactorySnapshot(signature, factory);
            log.info("OAuth2 proxy configuration refreshed: {} {}:{} (auth={})",
                    isHttp ? "HTTP" : "SOCKS5", host, port, StringUtils.hasText(username));
            return factory;
        }
    }

    public RestClient createOAuth2RestClient() {
        return RestClient.builder()
                .requestFactory(createRoutingRequestFactory())
                .build();
    }

    public RestTemplate createOAuth2RestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRoutingRequestFactory());
        return restTemplate;
    }

    public AbstractRestClientOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> createAccessTokenResponseClient() {
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(createOAuth2RestClient());
        return client;
    }

    private ClientHttpRequestFactory createDirectFactory() {
        return new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().build());
    }

    private ClientHttpRequestFactory createNettySocks5Factory(String host, int port, String username, String password) {
        reactor.netty.http.client.HttpClient nettyClient = reactor.netty.http.client.HttpClient.create()
                .proxy(spec -> {
                    var builder = spec.type(ProxyProvider.Proxy.SOCKS5)
                            .host(host)
                            .port(port);
                    if (StringUtils.hasText(username)) {
                        builder = builder.username(username)
                                .password(p -> password);
                    }
                    builder.build();
                });

        return new ReactorClientHttpRequestFactory(nettyClient);
    }

    private ClientHttpRequestFactory createHttpProxyFactory(String host, int port, String username, String password) {
        return createReactorHttpProxyFactory(host, port, username, password);
    }

    public static ClientHttpRequestFactory createTestFactory(String host, int port, String username, String password, boolean isHttp) {
        if (isHttp) {
            return createReactorHttpProxyFactory(host, port, username, password);
        }

        reactor.netty.http.client.HttpClient nettyClient = reactor.netty.http.client.HttpClient.create()
                .responseTimeout(java.time.Duration.ofSeconds(10))
                .proxy(spec -> {
                    var builder = spec.type(ProxyProvider.Proxy.SOCKS5)
                            .host(host)
                            .port(port);
                    if (StringUtils.hasText(username)) {
                        builder = builder.username(username)
                                .password(p -> password);
                    }
                    builder.build();
                });
        return new ReactorClientHttpRequestFactory(nettyClient);
    }

    private static ClientHttpRequestFactory createReactorHttpProxyFactory(
            String host, int port, String username, String password) {
        reactor.netty.http.client.HttpClient nettyClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(java.time.Duration.ofSeconds(10))
                .proxy(spec -> {
                    var builder = spec.type(ProxyProvider.Proxy.HTTP)
                            .host(host)
                            .port(port);
                    if (StringUtils.hasText(username)) {
                        builder = builder.username(username)
                                .password(p -> password);
                    }
                    builder.build();
                });

        return new ReactorClientHttpRequestFactory(nettyClient);
    }

    private record ProxyFactorySnapshot(String signature, ClientHttpRequestFactory factory) {
    }

    private class DynamicRoutingClientHttpRequestFactory implements ClientHttpRequestFactory {

        private final ClientHttpRequestFactory directFactory;

        private DynamicRoutingClientHttpRequestFactory(ClientHttpRequestFactory directFactory) {
            this.directFactory = directFactory;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            String host = uri.getHost();
            boolean shouldProxy = host != null && GITHUB_HOSTS.stream()
                    .anyMatch(proxiedHost -> proxiedHost.equalsIgnoreCase(host));
            if (shouldProxy) {
                ClientHttpRequestFactory proxiedFactory = resolveProxiedFactory();
                if (proxiedFactory != null) {
                    log.debug("Routing {} through OAuth2 proxy", host);
                    return proxiedFactory.createRequest(uri, httpMethod);
                }
            }
            return directFactory.createRequest(uri, httpMethod);
        }
    }
}
