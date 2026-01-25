package com.apr.aprbackendassignment.common.request;

import com.apr.aprbackendassignment.common.exception.CommException;
import com.apr.aprbackendassignment.common.response.CommResponseStatus;
import com.apr.aprbackendassignment.model.constant.SORT_GROUP;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
/**
 * =====================================================
 * Class Name   : PageRequestParam
 * Description  :
 *  - 페이지 요청 파라미터를 관리하는 클래스
 *
 * 주요 기능
 *  - 페이지 번호, 최대 크기, 정렬 정보를 포함
 * =====================================================
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PageRequestParam {

    private static final String DELIMITER = ",";

    @Schema(description = "Page index (0-based)", defaultValue = "0", example = "0")
    @Min(0)
    private int page = 0;

    @Schema(description = "Maximum page size (1 to 100)", defaultValue = "10", example = "10")
    @Min(1)
    @Max(100)
    private int maxSize = 10;

    @Schema(description = "Sort field and direction, format: field,dir")
    @NotNull
    private String sort;
    public Pageable toPageable() {

        try {
            String[] sortInfo = sort.split(DELIMITER);
            String sortField = sortInfo[0];
            String sortDirection = sortInfo[1];

            // Enum 매핑
            String fieldName = SORT_GROUP.from(sortField).getFieldName();

            Sort.Direction direction =
                    Sort.Direction.fromString(sortDirection);

            return PageRequest.of(page, maxSize, Sort.by(direction, fieldName));
        } catch (Exception e) {
            throw new CommException(CommResponseStatus.BAD_REQUEST);
        }

    }

}
