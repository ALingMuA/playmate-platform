package com.gameplay.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 结束服务请求；履约凭证可选，最多五张。 */
@Data
public class EndServiceRequest {

    @Valid
    @Size(max = 5, message = "履约凭证最多5张")
    private List<DeliveryEvidenceRequest> evidences;
}
