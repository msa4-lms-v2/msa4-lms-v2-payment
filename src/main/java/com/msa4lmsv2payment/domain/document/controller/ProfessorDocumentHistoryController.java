package com.msa4lmsv2payment.domain.document.controller;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.global.client.ProfessorCertificateClient;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
@RestController @RequiredArgsConstructor @Validated
public class ProfessorDocumentHistoryController {
 private final DocumentRepository repository;
 private final ProfessorCertificateClient professorClient;
 public record Item(Long id,DocumentType documentType,LocalDateTime issuedAt,boolean revoked,boolean downloadable) {
  static Item from(Document d) {return new Item(d.getId(),d.getDocumentType(),d.getIssuedAt(),d.isRevoked(),!d.isRevoked()&&d.getFilePath()!=null&&!d.getFilePath().isBlank());}
 }
 public record History(List<Item> items,long totalCount,int page,int size,boolean hasNext) {}
 @GetMapping("/api/payment/professors/me/certificates") @PreAuthorize("hasRole('PROFESSOR')")
 public GlobalResponseDTO<History> history(@AuthenticationPrincipal CurrentUser user,
    @RequestParam(defaultValue="1") @Min(1) @Max(100000) int page,
    @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
  Long professorId=professorClient.fetch(user).professor().professorId();
  var result=repository.findByProfessorIdOrderByIssuedAtDescIdDesc(professorId,PageRequest.of(page-1,size));
  return GlobalResponseDTO.success(new History(result.getContent().stream().map(Item::from).toList(),result.getTotalElements(),page,size,result.hasNext()));
 }
}
