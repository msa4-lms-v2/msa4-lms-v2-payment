package com.msa4lmsv2payment.global.contract;

import com.msa4lmsv2payment.domain.document.controller.DocumentController;
import com.msa4lmsv2payment.domain.payment.controller.PaymentController;
import com.msa4lmsv2payment.domain.paymenthealth.controller.PaymentHealthController;
import com.msa4lmsv2payment.domain.refund.controller.RefundController;
import com.msa4lmsv2payment.domain.scholarship.controller.ScholarshipController;
import com.msa4lmsv2payment.domain.tuitionbill.controller.TuitionBillController;
import com.msa4lmsv2payment.domain.virtualaccount.controller.VirtualAccountController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiPrefixContractTest {

    private static final String CANONICAL_PREFIX = "/api/payment/";

    @Test
    void 모든_Payment_Controller_경로는_단수형_소유_prefix를_사용한다() {
        List<Class<?>> controllers = List.of(
                TuitionBillController.class,
                ScholarshipController.class,
                PaymentController.class,
                VirtualAccountController.class,
                RefundController.class,
                DocumentController.class,
                PaymentHealthController.class
        );

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                assertThat(mapping.path())
                        .as("%s#%s API 경로", controller.getSimpleName(), method.getName())
                        .isNotEmpty()
                        .allMatch(path -> path.startsWith(CANONICAL_PREFIX));
            }
        }
    }
}
