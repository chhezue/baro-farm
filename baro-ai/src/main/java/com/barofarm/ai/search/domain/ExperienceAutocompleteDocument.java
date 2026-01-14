package com.barofarm.ai.search.domain;

import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Getter
@Document(indexName = "experience-autocomplete")
@Setting(settingPath = "settings/autocomplete-analyzer.json")
public class ExperienceAutocompleteDocument {

    @Id
    private UUID experienceId;

    @Field(type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    private String experienceName;

    @Field(type = FieldType.Keyword)
    private String status; // 검색 결과 반환 로직 내에서 ON_SALE 상태만 결과에 노출

    public ExperienceAutocompleteDocument(UUID experienceId, String experienceName, String status) {
        this.experienceId = experienceId;
        this.experienceName = experienceName;
        this.status = status;
    }
}
