package com.upcraft.stay.reporting.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.reporting.dto.CreateShareLinkRequestDTO;
import com.upcraft.stay.reporting.dto.PublicShareViewResponseDTO;
import com.upcraft.stay.reporting.dto.ShareLinkResponseDTO;

/**
 * Service contract for revocable, hashed, expiring share links and public vacancy/document views.
 */
public interface ShareLinkV1Service {

    ShareLinkResponseDTO createShareLink(String principalEmail, CreateShareLinkRequestDTO requestDTO);

    PageResponseDTO<ShareLinkResponseDTO> listShareLinks(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size
    );

    ShareLinkResponseDTO revokeShareLink(String principalEmail, UUID shareLinkId);

    PublicShareViewResponseDTO getPublicShareView(String rawToken);

    byte[] exportPublicSharePdf(String rawToken);
}

