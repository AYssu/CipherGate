package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.PublicLicenseQueryRequest;
import com.ayssu.ciphergate.dto.PublicLicenseQueryResponse;
import com.ayssu.ciphergate.dto.PublicLicenseUnbindRequest;

public interface PublicLicenseSelfService {

    PublicLicenseQueryResponse queryRemaining(PublicLicenseQueryRequest req);

    void unbind(PublicLicenseUnbindRequest req);
}
