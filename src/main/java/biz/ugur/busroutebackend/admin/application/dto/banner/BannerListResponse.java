package biz.ugur.busroutebackend.admin.application.dto.banner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BannerListResponse {

    @JsonProperty("banners")
    private List<BannerResponse> banners;

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("active_count")
    private Long activeCount;

    @JsonProperty("has_more")
    private Boolean hasMore;

    public BannerListResponse(List<BannerResponse> banners, Long activeCount) {
        this.banners = banners;
        this.totalCount = banners.size();
        this.activeCount = activeCount;
    }

    public BannerListResponse(List<BannerResponse> banners, Long activeCount,  Boolean hasMore) {
        this.banners = banners;
        this.activeCount = activeCount;
        this.totalCount = banners.size();
        this.hasMore = hasMore;
    }
}
