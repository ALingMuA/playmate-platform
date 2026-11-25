package com.gameplay.ai;

import com.gameplay.ai.config.AiModelConfigValidator;
import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelConfigValidatorTest {

    @Test
    void blocksPrivateLinkLocalMetadataAndLoopbackInProdMode() {
        for (String url : new String[]{
                "https://10.0.0.1/v1/chat",
                "https://172.16.0.1/v1/chat",
                "https://192.168.1.1/v1/chat",
                "https://169.254.169.254/latest/meta-data",
                "https://100.64.0.1/v1/chat",
                "https://0.0.0.0/v1/chat",
                "https://224.0.0.1/v1/chat",
                "https://[fc00::1]/v1/chat",
                "https://[fe80::1]/v1/chat",
                "https://[::ffff:169.254.169.254]/v1/chat",
                "https://127.0.0.1/v1/chat",
                "http://127.0.0.1:8080/v1/chat",
                "https://[::1]:8080/v1/chat",
                "https://1.1.1.1:8443/v1/chat",
                "http://1.1.1.1/v1/chat"
        }) {
            AiModelProperties p = base();
            p.setEndpointUrl(url);
            assertThatThrownBy(() -> AiModelConfigValidator.validate(p, false, false))
                    .as("Expected SSRF/prod block for %s", url)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void allowsPublicHttps443AndLoopbackWhenExplicitlyPermitted() {
        AiModelProperties publicHttps = base();
        publicHttps.setEndpointUrl("https://1.1.1.1/v1/chat");
        assertThatCode(() -> AiModelConfigValidator.validate(publicHttps, false, false))
                .doesNotThrowAnyException();

        AiModelProperties loopbackHttp = base();
        loopbackHttp.setEndpointUrl("http://127.0.0.1:18080/v1/chat");
        assertThatCode(() -> AiModelConfigValidator.validate(loopbackHttp, false, true))
                .doesNotThrowAnyException();

        // Even with allowLoopback=true, cloud metadata & RFC1918 remain strictly blocked
        AiModelProperties metadata = base();
        metadata.setEndpointUrl("https://169.254.169.254/latest/meta-data");
        assertThatThrownBy(() -> AiModelConfigValidator.validate(metadata, false, true))
                .isInstanceOf(BusinessException.class);
    }

    private AiModelProperties base() {
        AiModelProperties p = new AiModelProperties();
        p.setName("test-model");
        p.setAuthMode("NONE");
        return p;
    }
}
