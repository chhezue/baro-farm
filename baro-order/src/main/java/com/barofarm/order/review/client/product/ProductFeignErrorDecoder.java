package com.barofarm.order.review.client.product;

import com.barofarm.exception.CommonErrorCode;
import com.barofarm.exception.CustomException;
import com.barofarm.order.review.exception.ReviewErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;

public class ProductFeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new CustomException(
                ReviewErrorCode.PRODUCT_NOT_FOUND
            );

            default -> new CustomException(
                CommonErrorCode.FEIGN_ERROR
            );
        };
    }
}
