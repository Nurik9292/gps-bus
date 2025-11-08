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
    private int page = 1;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must not exceed 100")
    private int size = 25;

    @Pattern(regexp = "name|nameTm|displayOrder|createdAt", message = "Invalid sort field")
    private String sort = "name";

    @Pattern(regexp = "asc|desc", message = "Order must be 'asc' or 'desc'")
    private String order = "asc";

    private Boolean active;

    private String search;

    public static GetAllCitiesInput fromParams(
            int page, int size, String sort, String order, Boolean active, String search) {
        return new GetAllCitiesInput(
                page,
                Math.min(size, 100),
                sort != null ? sort : "name",
                order != null ? order : "asc",
                active,
                search

        );
    }
}
