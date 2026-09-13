package com.gameplay.ai.dto;

import com.gameplay.ai.config.AiModelProperties;
import lombok.Getter;
import lombok.Setter;

/** 凭据为只写字段；不要为此类生成 toString。 */
@Getter
@Setter
public class AiSettingsRequest extends AiModelProperties {
    private long version;
    private boolean clearApiKey;
}
