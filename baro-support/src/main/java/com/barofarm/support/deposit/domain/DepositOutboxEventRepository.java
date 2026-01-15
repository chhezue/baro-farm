package com.barofarm.support.deposit.domain;

public interface DepositOutboxEventRepository {

    DepositOutboxEvent save(DepositOutboxEvent event);
}

