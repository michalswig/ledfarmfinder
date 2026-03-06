package com.mike.leadfarmfinder.service.outreach;

public record SendResult(
        boolean sent,
        boolean hardBounce) {
}
