package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Slf4j
@Configuration
public class SvEpgWebClientConfig {

    private static final String SVEPG_CERT_DIR = "certs/svepg";

    private static final String[] EXTRA_INTERMEDIATES = {
            "geotrust-ev-rsa-ca-g2.crt"
    };

    @Bean(name = "svepgWebClient")
    public WebClient svepgWebClient() {
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(buildTrustManagerFactory())
                    .build();

            HttpClient httpClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext));

            log.info("[sv_epg] WebClient initialized with system truststore + {} extra intermediate(s)",
                    EXTRA_INTERMEDIATES.length);

            return WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to build sv_epg WebClient with extended truststore", e);
        }
    }

    private TrustManagerFactory buildTrustManagerFactory() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        copySystemRoots(trustStore);
        loadExtraIntermediates(trustStore);

        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trustStore);
        return factory;
    }

    private void copySystemRoots(KeyStore destination) throws Exception {
        TrustManagerFactory systemFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        systemFactory.init((KeyStore) null);

        List<X509Certificate> systemRoots = new ArrayList<>();
        for (var manager : systemFactory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                for (X509Certificate cert : x509.getAcceptedIssuers()) {
                    systemRoots.add(cert);
                }
            }
        }

        for (int i = 0; i < systemRoots.size(); i++) {
            destination.setCertificateEntry("system-root-" + i, systemRoots.get(i));
        }
        log.debug("[sv_epg] copied {} system root CAs into custom truststore", systemRoots.size());
    }

    private void loadExtraIntermediates(KeyStore destination) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (String fileName : EXTRA_INTERMEDIATES) {
            ClassPathResource resource = new ClassPathResource(SVEPG_CERT_DIR + "/" + fileName);
            if (!resource.exists()) {
                throw new IllegalStateException(
                        "Missing classpath resource: " + SVEPG_CERT_DIR + "/" + fileName);
            }
            try (InputStream in = resource.getInputStream()) {
                Certificate cert = cf.generateCertificate(in);
                String alias = "svepg-" + fileName.replace(".crt", "").replace(".pem", "");
                destination.setCertificateEntry(alias, cert);
                if (cert instanceof X509Certificate x509) {
                    log.info("[sv_epg] trusted extra cert: alias={} subject={}",
                            alias, x509.getSubjectX500Principal());
                }
            }
        }
    }
}
