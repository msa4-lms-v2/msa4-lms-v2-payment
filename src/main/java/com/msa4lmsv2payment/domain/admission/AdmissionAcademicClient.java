package com.msa4lmsv2payment.domain.admission;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import java.util.Map;
@Component
public class AdmissionAcademicClient {
    public record Candidate(Long id,String name,short admissionYear,String status,Long tuitionBillId,boolean tuitionPaid,Long studentId,Long advisorProfessorId) {}
    private final RestClient client;
    public AdmissionAcademicClient(@Value("${ADMISSION_ACADEMIC_BASE_URL:${ACADEMIC_INTERNAL_BASE_URL:http://localhost:8082}}") String url) {
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public Candidate get(Long id){return data(client.get().uri("/api/academic/internal/admissions/{id}",id).retrieve().body(new ParameterizedTypeReference<GlobalResponseDTO<Candidate>>(){}));}
    public Candidate post(Long id,String action,Long billId){return data(client.post().uri("/api/academic/internal/admissions/{id}/{action}",id,action).body(Map.of("tuitionBillId",billId)).retrieve().body(new ParameterizedTypeReference<GlobalResponseDTO<Candidate>>(){}));}
    private Candidate data(GlobalResponseDTO<Candidate> r){if(r==null || r.data()==null)throw new IllegalStateException("입학 정보 응답 없음");return r.data();}
}
