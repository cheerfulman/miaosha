package org.example.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

// 当spring容器内没有TomcatEmbeddedServletContainerFactory这个bean时，会把此bean加载进spring容器
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        // 使用对应工厂类提供给我们的接口定制化我们的tomcat connect
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol protocolHandler = (Http11NioProtocol) connector.getProtocolHandler();
                // 定制化keepalivetimeout,设置30秒没有请求则服务器自动断开keepalive连接
                protocolHandler.setKeepAliveTimeout(30000);
                // 当客户端发送超过10000个请求则自动断开keepalive连接
                protocolHandler.setMaxKeepAliveRequests(10000);
            }
        });
    }
}
