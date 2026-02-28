package com.mike.leadfarmfinder.service;

public interface MxLookUp {
    enum MxStatus {VALID, INVALID, UNKNOWN}

    MxStatus checkDomain(String domain);
}
