package com.barofarm.support.experience.application.event;

import com.barofarm.support.experience.domain.Experience;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ExperienceTransactionEvent {

    public enum ExperienceOperation {
        CREATED, UPDATED, DELETED
    }

    private final Experience experience;
    private final ExperienceOperation operation;
}
