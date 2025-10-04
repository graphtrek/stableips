package co.grtk.stableips.config;

import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xrpl.xrpl4j.client.XrplClient;

@Configuration
public class XrpConfig {

    @Value("${xrp.network.url:https://s.altnet.rippletest.net:51234}")
    private String networkUrl;

    @Bean
    public XrplClient xrplClient() {
        HttpUrl rippledUrl = HttpUrl.get(networkUrl);
        return new XrplClient(rippledUrl);
    }
}
