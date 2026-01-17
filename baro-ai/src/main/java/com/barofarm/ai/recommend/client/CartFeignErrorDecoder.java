package com.barofarm.ai.recommend.client;

import com.barofarm.ai.common.exception.CommonErrorCode;
import com.barofarm.ai.common.exception.CustomException;
import com.barofarm.ai.recommend.exception.RecommendErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;

public class CartFeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new CustomException(
                RecommendErrorCode.CART_NOT_FOUND
            );

            default -> new CustomException(
                CommonErrorCode.FEIGN_ERROR
            );
        };
    }
}
