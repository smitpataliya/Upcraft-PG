package com.upcraft.stay.operations.service;

import com.upcraft.stay.operations.dto.NotificationDispatchCommand;
import com.upcraft.stay.operations.dto.ProviderDispatchResult;
import com.upcraft.stay.operations.enums.NotificationChannel;

/**
 * Pluggable delivery provider boundary for push, email, SMS, and WhatsApp channels.
 */
public interface NotificationChannelProvider {

    /**
     * Channel served by this provider.
     *
     * @return notification channel
     */
    NotificationChannel channel();

    /**
     * Executes delivery or returns {@code SKIPPED_UNCONFIGURED} when credentials/tokens are absent.
     *
     * @param command dispatch payload
     * @return delivery outcome
     */
    ProviderDispatchResult send(NotificationDispatchCommand command);
}

