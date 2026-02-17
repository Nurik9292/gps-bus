package biz.ugur.busroutebackend.complaint.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("complaints")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintEntity {

    private String id;
    private String type;
    private String title;
    private String description;
    private String status;
    private String clientId;
    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
}
