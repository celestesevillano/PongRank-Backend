package org.example.pongrankbackend.Club.service;

import org.example.pongrankbackend.Club.dto.ClubAdminTransferRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubAffiliationDocumentRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRegisterRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRejectRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubResubmitRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewHistoryDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubUpdateRequestDTO;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;

// El jugador que actúa en cada operación se lee del SecurityContext (SecurityUtils), no se recibe como parámetro.
public interface ClubService {

    ClubResponseDTO registerClub(ClubRegisterRequestDTO dto);

    PageResponseDTO<ClubResponseDTO> getApprovedClubs(int page, int size);

    ClubResponseDTO getClubById(Long clubId);

    ClubResponseDTO updateClub(Long clubId, ClubUpdateRequestDTO dto);

    ClubReviewResponseDTO getClubReview(Long clubId);

    ClubResponseDTO resubmitClub(Long clubId, ClubResubmitRequestDTO dto);

    ClubResponseDTO replaceAffiliationDocument(Long clubId, ClubAffiliationDocumentRequestDTO dto);

    ClubResponseDTO transferAdministration(Long clubId, ClubAdminTransferRequestDTO dto);

    PageResponseDTO<ClubReviewResponseDTO> getPendingClubs(int page, int size);

    ClubReviewResponseDTO approveClub(Long clubId);

    ClubReviewResponseDTO rejectClub(Long clubId, ClubRejectRequestDTO dto);

    PageResponseDTO<ClubReviewHistoryDTO> getReviewHistory(Long clubId, int page, int size);
}
