package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ComplaintStatusUpdateRequest {

    private String comment;
}
