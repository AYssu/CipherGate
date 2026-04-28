package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryRequest;
import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryResponse;

public interface PublicAppUserSelfService {

    PublicAppUserExpireQueryResponse queryExpire(PublicAppUserExpireQueryRequest req);
}
