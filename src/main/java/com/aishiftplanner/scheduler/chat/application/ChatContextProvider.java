package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;

/** Resolves the trusted schedule context for one chat request. */
public interface ChatContextProvider {

    ChatContext resolve(AuthenticatedUser user, String planningPeriodId);
}
