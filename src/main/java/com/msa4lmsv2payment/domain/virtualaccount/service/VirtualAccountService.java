package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.global.error.VirtualAccountNotFoundException;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.request.VirtualAccountIssueRequestDTO;
import com.msa4lmsv2payment.domain.virtualaccount.response.VirtualAccountResponseDTO;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.client.TossVirtualAccountIssueResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VirtualAccountService {

    // Toss 기본 유효 시간(validHours 미지정 시 168시간=7일)과 맞춘다.
    private static final long DEFAULT_VALID_HOURS = 168;

    private final VirtualAccountRepository virtualAccountRepository;
    private final TuitionBillService tuitionBillService;
    private final TossPaymentsClient tossPaymentsClient;
    private final VirtualAccountRecorderService virtualAccountRecorder;

    // 가상계좌 발급. 입금 Webhook 없이 발급 자체만 완결한다.
    // Toss 호출 동안 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 실행한다.
    // 저장과 감사 로그는 virtualAccountRecorder가 하나의 트랜잭션으로 묶는다(4.6).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VirtualAccountResponseDTO issueVirtualAccount(CurrentUser currentUser, VirtualAccountIssueRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        String orderId = "TB-" + tuitionBill.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        TossVirtualAccountIssueResponse tossResponse = tossPaymentsClient.issueVirtualAccount(
                orderId, "등록금 납부", tuitionBill.getBillingAmount(), request.customerName(), request.bankCode());

        VirtualAccount virtualAccount = new VirtualAccount(
                tuitionBill.getId(),
                orderId,
                tossResponse.secret(),
                tossResponse.virtualAccount().accountNumber(),
                tossResponse.virtualAccount().bankCode(),
                LocalDateTime.now().plusHours(DEFAULT_VALID_HOURS),
                VirtualAccountStatus.ISSUED
        );

        return VirtualAccountResponseDTO.from(virtualAccountRecorder.saveWithAudit(currentUser.id(), virtualAccount));
    }

    /**
     * 다른 도메인(refund 등)이 가상계좌를 조회해야 할 때 이 공개 메서드를 거친다.
     */
    public VirtualAccount getByTuitionBillIdOrThrow(Long tuitionBillId) {
        return virtualAccountRepository.findByTuitionBillId(tuitionBillId)
                .orElseThrow(() -> new VirtualAccountNotFoundException("해당 등록금 고지에 발급된 가상계좌가 없습니다."));
    }
}
