package biz.ugur.busroutebackend.admin.application.dto.admin;

import lombok.Data;

import java.util.List;


@Data
public class AdminList {

    private List<AdminResult> admins;
    private Integer totalCount;
    private Long activeCount;

    public AdminList(List<AdminResult> admins, Long activeCount) {
        this.admins = admins;
        this.totalCount = admins.size();
        this.activeCount = activeCount;
    }

}
