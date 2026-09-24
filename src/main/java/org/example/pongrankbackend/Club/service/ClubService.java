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


public interface ClubService {

    ClubResponseDTO registerClub(Long requesterId, ClubRegisterRequestDTO dto);

    PageResponseDTO<ClubResponseDTO> getApprovedClubs(int page, int size);

    ClubResponseDTO getClubById(Long clubId);

    ClubResponseDTO updateClub(Long clubId, Long actingPlayerId, ClubUpdateRequestDTO dto);

    ClubReviewResponseDTO getClubReview(Long clubId, Long actingPlayerId);

    ClubResponseDTO resubmitClub(Long clubId, Long actingPlayerId, ClubResubmitRequestDTO dto);

    ClubResponseDTO replaceAffiliationDocument(Long clubId, Long actingPlayerId, ClubAffiliationDocumentRequestDTO dto);

    ClubResponseDTO transferAdministration(Long clubId, Long actingPlayerId, ClubAdminTransferRequestDTO dto);

    PageResponseDTO<ClubReviewResponseDTO> getPendingClubs(Long actingPlayerId, int page, int size);

    ClubReviewResponseDTO approveClub(Long clubId, Long actingPlayerId);

    ClubReviewResponseDTO rejectClub(Long clubId, Long actingPlayerId, ClubRejectRequestDTO dto);

    PageResponseDTO<ClubReviewHistoryDTO> getReviewHistory(Long clubId, Long actingPlayerId, int page, int size);
}
