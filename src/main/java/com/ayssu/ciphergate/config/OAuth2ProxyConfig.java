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

    public ClientHttpRequestFactory createRoutingRequestFactory() {
        ClientHttpRequestFactory directFactory = createDirectFactory();
        if (!proxyEnabled) {
            return directFactory;
        }

        String host = systemConfigService.getConfigValue("oauth2.proxy.host", "");
        String portStr = systemConfigService.getConfigValue("oauth2.proxy.port", "1080");
        String username = systemConfigService.getConfigValue("oauth2.proxy.username", "");
        String password = systemConfigService.getConfigValue("oauth2.proxy.password", "");
        String type = systemConfigService.getConfigValue("oauth2.proxy.type", "socks5");

        if (!StringUtils.hasText(host)) {
            log.warn("OAuth2 proxy enabled but host is empty, falling back to direct connection");
            return directFactory;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            port = 1080;
        }

        boolean isHttp = "http".equalsIgnoreCase(type);
        ClientHttpRequestFactory proxiedFactory = isHttp
                ? createHttpProxyFactory(host, port, username, password)
                : createNettySocks5Factory(host, port, username, password);
        log.info("OAuth2 proxy configured: {} {}:{} (auth={})", isHttp ? "HTTP" : "SOCKS5", host, port, StringUtils.hasText(username));

        return new RoutingClientHttpRequestFactory(directFactory, proxiedFactory, GITHUB_HOSTS);
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
        log.info("SOCKS5 factory: host={}, port={}, user='{}', pass='{}'", host, port, username, password != null ? "***" + password.substring(Math.max(0, password.length() - 2)) : "null");
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
        InetSocketAddress proxyAddress = new InetSocketAddress(host, port);

        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
                .proxy(new java.net.ProxySelector() {
                    @Override
                    public List<java.net.Proxy> select(URI uri) {
                        return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP, proxyAddress));
                    }

                    @Override
                    public void connectFailed(URI uri, java.net.SocketAddress sa, IOException e) {
                        log.warn("HTTP proxy connect failed for {}: {}", uri, e.getMessage());
                    }
                })
                .connectTimeout(java.time.Duration.ofSeconds(10));

        if (StringUtils.hasText(username)) {
            builder.authenticator(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new java.net.PasswordAuthentication(username, password.toCharArray());
                    }
                    return null;
                }
            });
        }

        return new JdkClientHttpRequestFactory(builder.build());
    }

    public static ClientHttpRequestFactory createTestFactory(String host, int port, String username, String password, boolean isHttp) {
        if (isHttp) {
            InetSocketAddress proxyAddress = new InetSocketAddress(host, port);
            java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
                    .proxy(new java.net.ProxySelector() {
                        @Override
                        public List<java.net.Proxy> select(URI uri) {
                            return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP, proxyAddress));
                        }

                        @Override
                        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException e) {}
                    })
                    .connectTimeout(java.time.Duration.ofSeconds(10));
            if (StringUtils.hasText(username)) {
                builder.authenticator(new java.net.Authenticator() {
                    @Override
                    protected java.net.PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new java.net.PasswordAuthentication(username, password.toCharArray());
                        }
                        return null;
                    }
                });
            }
            return new JdkClientHttpRequestFactory(builder.build());
        }

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

    private static class RoutingClientHttpRequestFactory implements ClientHttpRequestFactory {

        private final ClientHttpRequestFactory directFactory;
        private final ClientHttpRequestFactory proxiedFactory;
        private final Set<String> proxiedHosts;

        RoutingClientHttpRequestFactory(ClientHttpRequestFactory directFactory,
                                        ClientHttpRequestFactory proxiedFactory,
                                        Set<String> proxiedHosts) {
            this.directFactory = directFactory;
            this.proxiedFactory = proxiedFactory;
            this.proxiedHosts = proxiedHosts;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            String host = uri.getHost();
            if (host != null && proxiedHosts.contains(host)) {
                log.debug("Routing {} through proxy", uri.getHost());
                return proxiedFactory.createRequest(uri, httpMethod);
            }
            return directFactory.createRequest(uri, httpMethod);
        }
    }
}
