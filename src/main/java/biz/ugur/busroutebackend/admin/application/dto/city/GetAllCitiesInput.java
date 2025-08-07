package biz.ugur.busroutebackend.admin.application.dto.city;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAllCitiesInput {

    @Min(value = 1, message = "Page must be at least 1")
    private Integer page = 1;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must not exceed 100")
    private Integer size = 25;

    @Pattern(regexp = "name|nameTm|displayOrder|createdAt", message = "Invalid sort field")
    private String sort = "name";

    @Pattern(regexp = "asc|desc", message = "Order must be 'asc' or 'desc'")
    private String order = "asc";

    private Boolean active;

    public static GetAllCitiesInput fromParams(Integer page, Integer size, String sort, String order, Boolean active) {
        return new GetAllCitiesInput(
                page != null ? page : 1,
                size != null ? Math.min(size, 100) : 25,
                sort != null ? sort : "name",
                order != null ? order : "asc",
                active
        );
    }
}
