package com.example.messagesender.domain.message;

import com.example.messagesender.domain.code.Code;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_templates",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_message_templates_code",
               columnNames = "code"
       ),
       indexes = @Index(
               name = "idx_templates_channel_purpose",
               columnList = "channel_type_id, purpose_type_id"
       ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_type_id", nullable = false)
    private Code channelType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purpose_type_id", nullable = false)
    private Code purposeType;

    @Column(length = 150)
    private String title;

    @Lob
    @Column(nullable = false)
    private String body;

    @Column(columnDefinition = "json")
    private String variablesJson;

    @Builder
    private MessageTemplate(String code, String name, Code channelType,
                            Code purposeType, String title,
                            String body, String variablesJson) {
        this.code = code;
        this.name = name;
        this.channelType = channelType;
        this.purposeType = purposeType;
        this.title = title;
        this.body = body;
        this.variablesJson = variablesJson;
    }
}
